package com.clanjhoo.dbhandler.data;

import com.clanjhoo.dbhandler.annotations.*;
import com.clanjhoo.dbhandler.events.LoadedDataEvent;
import com.clanjhoo.dbhandler.utils.Tuple;
import com.clanjhoo.dbhandler.utils.TriFunction;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * Instances of this class handle all input / output from the specified database
 * @param <T> the type of the objects that will be handled by this manager
 */
public final class DBObjectManager<T> {
    private final Map<List<Serializable>, Long> lastChecked = new ConcurrentHashMap<>();
    private final Map<List<Serializable>, T> itemData = new ConcurrentHashMap<>();
    private final Map<List<Serializable>, BukkitTask> loadTasks = new ConcurrentHashMap<>();
    private final DatabaseDriver<T> driver;
    private final JavaPlugin plugin;
    private final Logger logger;
    private final long inactiveTime;
    private final Class<T> meself;
    private final TriFunction<List<Serializable>, T, Exception, ? extends LoadedDataEvent<T>> eventFactory;
    private final Predicate<T> saveCondition;
    private TableData tableData;
    private boolean dataInitialized;
    private Map<String, FieldData> fieldDataList;
    private Map<String, Tuple<String, String>> foreigns;


    private static Object stringToSerializable(Class<?> type, String value) {
        Object defVal;
        if (String.class.isAssignableFrom(type)) {
            defVal = value;
        }
        else if (type.isPrimitive()) {
            defVal = primitiveFromString(type, value);
        }
        else if (Number.class.isAssignableFrom(type)) {
            try {
                Method strToNum = type.getDeclaredMethod("valueOf", String.class);
                defVal = strToNum.invoke(null, value);
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
                return 0;
            }
        }
        else if (UUID.class.isAssignableFrom(type)) {
            defVal = UUID.fromString(value);
        }
        else {
            defVal = null;
        }
        return defVal;
    }

    private static Object primitiveFromString(Class<?> primitive, String value) {
        Object val;
        if (byte.class.equals(primitive)) {
            val = Byte.parseByte(value);
        }
        else if (short.class.equals(primitive)) {
            val = Short.parseShort(value);
        }
        else if (int.class.equals(primitive)) {
            val = Integer.parseInt(value);
        }
        else if (long.class.equals(primitive)) {
            val = Long.parseLong(value);
        }
        else if (float.class.equals(primitive)) {
            val = Float.parseFloat(value);
        }
        else if (double.class.equals(primitive)) {
            val = Double.parseDouble(value);
        }
        else if (boolean.class.equals(primitive)) {
            val = Boolean.parseBoolean(value);
        }
        else {
            val = value.charAt(0);
        }
        return val;
    }

    private static String sqlTypeFromType(Class<?> type) {
        if (byte.class.equals(type) || Byte.class.isAssignableFrom(type)) {
            return "TINYINT";
        }
        else if (short.class.equals(type) || Short.class.isAssignableFrom(type)) {
            return "SMALLINT";
        }
        else if (int.class.equals(type) || Integer.class.isAssignableFrom(type)) {
            return "INT";
        }
        else if (long.class.equals(type) || Long.class.isAssignableFrom(type)) {
            return "BIGINT";
        }
        else if (float.class.equals(type) || Float.class.isAssignableFrom(type)) {
            return "FLOAT";
        }
        else if (double.class.equals(type) || Double.class.isAssignableFrom(type)) {
            return "DOUBLE";
        }
        else if (boolean.class.equals(type) || Boolean.class.isAssignableFrom(type)) {
            return "TINYINT(1)";
        }
        else if (char.class.equals(type) || Character.class.isAssignableFrom(type)) {
            return "CHAR(1)";
        }
        else if (String.class.isAssignableFrom(type)) {
            return "VARCHAR";
        }
        else if (UUID.class.isAssignableFrom(type)) {
            return "VARCHAR(36)";
        }
        throw new IllegalArgumentException("Only primitive types and UUIDs are currently supported!");
    }

    private static <T> T getDefaultValue(Class<T> clazz) {
        return (T) Array.get(Array.newInstance(clazz, 1), 0);
    }

    private static <T> Map<String, FieldData> getFieldInfo(Class<T> meself) {
        return Arrays.stream(meself.getDeclaredFields())
                .filter(f -> !Modifier.isTransient(f.getModifiers())
                        && !Modifier.isFinal(f.getModifiers())
                        && !Modifier.isStatic(f.getModifiers()))
                .map(f -> {
                    String name = f.getName();
                    Class<?> type = f.getType();
                    Object defVal = getDefaultValue(type);
                    if (Number.class.isAssignableFrom(type)) {
                        try {
                            Method strToNum = type.getDeclaredMethod("valueOf", String.class);
                            defVal = strToNum.invoke(null, "0");
                        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                    DataField dann = f.getAnnotation(DataField.class);
                    if (dann != null) {
                        if (!dann.name().isEmpty())
                            name = dann.name();
                        if (!dann.value().isEmpty() || dann.enforceValue())
                            defVal = stringToSerializable(type, dann.value());
                    }
                    return new FieldData(f.isAnnotationPresent(PrimaryKey.class), name, defVal, f, !f.isAnnotationPresent(NotNullField.class));
                }).collect(Collectors.toMap(fd -> fd.name, fd -> fd));
    }

    private void initializeTableData() {
        String tableName = meself.getSimpleName();
        if (!meself.isAnnotationPresent(Entity.class)) {
            throw new IllegalArgumentException("The class needs to be annotated with Entity");
        }
        Entity entityAnn = meself.getAnnotation(Entity.class);
        if (!entityAnn.table().isEmpty()) {
            tableName = entityAnn.table();
        }
        dataInitialized = TableData.findTableData(tableName) != null;
        tableData = TableData.getTableData(tableName);
        fieldDataList = DBObjectManager.getFieldInfo(meself);
        fieldDataList.forEach((n, fd) -> {
            String fieldType = null;
            if (fd.field.isAnnotationPresent(DataField.class)) {
                DataField dfAnn = fd.field.getAnnotation(DataField.class);
                if (!dfAnn.sqltype().isEmpty()) {
                    fieldType = dfAnn.sqltype();
                }
            }
            if (fieldType == null) {
                fieldType = sqlTypeFromType(fd.field.getType());
            }
            if (!dataInitialized) {
                tableData.addField(n, fieldType, fd.nullable && !fd.isPrimary);
            }
        });

        if (!dataInitialized) {
            tableData.setPrimaryKeys(fieldDataList.values().stream()
                    .filter(fd -> fd.isPrimary)
                    .map(fd -> fd.name)
                    .sorted()
                    .toArray(String[]::new));
        }

        if (!dataInitialized) {
            Map<String, Set<String>> groupedUniques = new HashMap<>();
            for (FieldData fd : fieldDataList.values()) {
                if (!fd.field.isAnnotationPresent(UniqueField.class)) {
                    continue;
                }
                UniqueField uniqueAnn = fd.field.getAnnotation(UniqueField.class);
                String group = uniqueAnn.group();
                if (group.isEmpty()) {
                    tableData.addUniqueConstraint(fd.name);
                } else {
                    Set<String> groupSet = groupedUniques.getOrDefault(group, new HashSet<>());
                    groupSet.add(fd.name);
                    groupedUniques.put(group, groupSet);
                }
            }
            for (Set<String> uniqueSet : groupedUniques.values()) {
                tableData.addUniqueConstraint(uniqueSet.toArray(new String[0]));
            }
        }

        foreigns = fieldDataList.values().stream()
                .filter(fd -> fd.field.isAnnotationPresent(ForeignKey.class))
                .collect(Collectors.toMap(
                        fd -> fd.name,
                        fd -> {
                            ForeignKey fkAnn = fd.field.getAnnotation(ForeignKey.class);
                            return new Tuple<>(fkAnn.name(), fkAnn.table());
                        }
                ));
    }

    /**
     * Initializes all foreign key related stuff. Must be called AFTER instantiating all the related DBObjectManagers
     */
    public void initialize() {
        if (dataInitialized) {
            return;
        }
        for (Map.Entry<String, Tuple<String, String>> entry : foreigns.entrySet()) {
            Tuple<String, String> name_table = entry.getValue();
            TableData other = TableData.findTableData(name_table.getSecond());
            if (other == null) {
                throw new IllegalArgumentException("Table " + name_table.getSecond() + " is not defined");
            }
            tableData.addForeignKey(entry.getKey(), other, name_table.getFirst());
        }
    }

    /**
     * Instantiates a new DBObjectManager object
     * @param clazz The class of the object to manage
     * @param plugin The plugin that has created the object
     * @param type Type of the storage driver
     * @param eventFactory A supplier that returns the event that will be fired whenever the data has been successfully loaded
     * @param saveCondition A predicate that determines if an item has to be stored in the database or deleted. null means save all
     * @param inactiveTime Time in milliseconds to remove inactive items from the manager. A negative number means never inactive
     * @param config Any config options needed by the selected storage driver type
     * @see JSONDriver#JSONDriver(JavaPlugin plugin, DBObjectManager manager, String storageFolderName)
     * @see MariaDBDriver#MariaDBDriver(JavaPlugin plugin, DBObjectManager manager, String host, int port, String database, String username, String password, String prefix)
     * @throws IOException if there was an error while creating the table / folder
     * @throws IllegalArgumentException if the chosen storage type has not yet been implemented
     */
    public DBObjectManager(@NotNull Class<T> clazz,
                           @NotNull JavaPlugin plugin,
                           @NotNull StorageType type,
                           @Nullable TriFunction<List<Serializable>, T, Exception, ? extends LoadedDataEvent<T>> eventFactory,
                           @Nullable Predicate<T> saveCondition,
                           int inactiveTime,
                           Object... config) throws IOException {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.meself = clazz;
        this.eventFactory = eventFactory;
        this.saveCondition = saveCondition;

        initializeTableData();

        if (inactiveTime < 0) {
            this.inactiveTime = Long.MAX_VALUE;
        }
        else {
            this.inactiveTime = inactiveTime;
        }

        switch(type) {
            case JSON:
                if (config.length < 1 || !(config[0] instanceof String)) {
                    logger.log(Level.SEVERE, "JSON driver needs: <name of the storage folder>");
                    throw new IllegalArgumentException("Wrong config parameters, check the console for further details");
                }
                this.driver = new JSONDriver<>(plugin, this, (String) config[0]);
                break;
            case MYSQL:
            case MARIADB:
                if (config.length < 6 ||
                        !(config[0] instanceof String) ||
                        !(config[1] instanceof Integer) ||
                        !(config[2] instanceof String) ||
                        !(config[3] instanceof String) ||
                        !(config[4] instanceof String) ||
                        !(config[5] instanceof String)) {
                    logger.log(Level.SEVERE, "MySQL driver needs: <hostname> <port> <database> <username> <password> <table_prefix>");
                    throw new IllegalArgumentException("Wrong config parameters, check the console for further details");
                }
                this.driver = new MariaDBDriver<>(plugin, this, (String) config[0], (int) config[1], (String) config[2], (String) config[3], (String) config[4], (String) config[5]);
                break;
            default:
                this.driver = null;
        }
        if (this.driver == null) {
            throw new IllegalArgumentException("Unsupported storage type " + type);
        }
        createTable();
    }

    /**
     * Instantiates a default object of the handled type T
     * @return the object with default values
     * @throws ReflectiveOperationException if there was an error while modifying any field of the object
     */
    @NotNull
    protected T getDefault() throws ReflectiveOperationException {
        T def = meself.getDeclaredConstructor().newInstance();
        for (FieldData fd : fieldDataList.values()) {
            if (fd.field.isAnnotationPresent(DataField.class)) {
                DataField fAnn = fd.field.getAnnotation(DataField.class);
                if (!fAnn.value().isEmpty() || fAnn.enforceValue()) {
                    setValue(def, fd.field, fd.defaultValue);
                }
            }
        }
        return def;
    }

    /**
     * Instantiates an object of the default type with the data contained in the data map
     * @param data a map which maps the name of a field with the data to put in said field
     * @param strict whether to raise an exception if there is no data for every field or not and leave them with default values
     * @return the new instance of the object, initialized with the data in the data map. If there is no data for any field, this will return a default object of type T.
     * @throws ReflectiveOperationException if there was an error while modifying any field of the object
     * @throws IllegalArgumentException if strict is true and there was at least one field not contained in the data map
     */
    @NotNull
    protected T getInstance(@NotNull Map<String, Serializable> data, boolean strict) throws ReflectiveOperationException {
        T def = meself.getDeclaredConstructor().newInstance();
        if (strict) {
            for (String name : data.keySet()) {
                if (!fieldDataList.containsKey(name)) {
                    throw new IllegalArgumentException("The field " + name + " is not defined for the table " + tableData.getName());
                }
            }
        }
        for (Map.Entry<String, FieldData> entry : fieldDataList.entrySet()) {
            FieldData fd = entry.getValue();
            Serializable value = data.get(entry.getKey());
            if (value != null) {
                setValue(def, fd.field, value);
            }
            else {
                setValue(def, fd.field, fd.defaultValue);
            }
        }
        return def;
    }

    /**
     * Returns a TableData object with information about the table in which items of type T will be stored
     * @return the TableData object
     */
    protected @NotNull TableData getTableData() {
        return tableData;
    }

    /**
     * Sets the value of the specified field in the given object
     * @param obj the object we want to modify
     * @param field the name of the field that we want to modify
     * @param value the value we want to assign
     * @throws ReflectiveOperationException if there was an error while accessing the field
     * @throws IllegalArgumentException if the specified field does not exist in the table
     */
    protected void setValue(T obj, String field, Serializable value) throws ReflectiveOperationException {
        FieldData fd = fieldDataList.get(field);
        if (fd == null) {
            throw new IllegalArgumentException("The field " + field + " is not defined for the table " + tableData.getName());
        }
        setValue(obj, fd.field, value);
    }

    private void setValue(T obj, Field field, Object value) throws ReflectiveOperationException {
        Class<?> type = field.getType();
        if (UUID.class.isAssignableFrom(type) && value instanceof String) {
            value = UUID.fromString((String) value);
        }
        else if (value instanceof Number) {
            if (Byte.class.isAssignableFrom(type)) {
                value = ((Number) value).byteValue();
            }
            else if (Short.class.isAssignableFrom(type)) {
                value = ((Number) value).shortValue();
            }
            else if (Integer.class.isAssignableFrom(type)) {
                value = ((Number) value).intValue();
            }
            else if (Long.class.isAssignableFrom(type)) {
                value = ((Number) value).longValue();
            }
            else if (Float.class.isAssignableFrom(type)) {
                value = ((Number) value).floatValue();
            }
            else if (Double.class.isAssignableFrom(type)) {
                value = ((Number) value).doubleValue();
            }
        }

        try {
            field.set(obj, value);
        }
        catch (IllegalAccessException ex) {
            field.setAccessible(true);
            field.set(obj, value);
        }
    }

    /**
     * Returns the value of the specified field in the given object
     * @param obj the object containing the data
     * @param field the name of the field which contains the data
     * @return the data inside of the field of the object
     * @throws ReflectiveOperationException if there was an error while accessing the field
     * @throws IllegalArgumentException if the specified field does not exist in the table
     */
    protected Serializable getValue(T obj, String field) throws ReflectiveOperationException {
        FieldData fd = fieldDataList.get(field);
        if (fd == null) {
            throw new IllegalArgumentException("The field " + field + " is not defined for the table " + tableData.getName());
        }
        return (Serializable) getValue(obj, fd.field);
    }

    private Object getValue(T obj, Field field) throws ReflectiveOperationException {
        Object val;
        try {
            val = field.get(obj);
        }
        catch (IllegalAccessException ex) {
            field.setAccessible(true);
            val = field.get(obj);
        }
        return val;
    }

    /**
     * Returns the class of the specified field
     * @param field the name of the field in the handled class
     * @return the class of the field
     * @throws ClassCastException if the field is not of a serializable class
     */
    protected Class<?> getType(String field) {
        FieldData fd = fieldDataList.get(field);
        if (fd == null) {
            throw new IllegalArgumentException("The field " + field + " is not defined for the table " + tableData.getName());
        }
        Class<?> fieldType = fd.field.getType();
        if (!fieldType.isPrimitive() && !Serializable.class.isAssignableFrom(fieldType)) {
            throw new ClassCastException("The field " + field + " is not serializable. Class: " + fieldType.getName());
        }
        return fieldType;
    }

    /**
     * Creates a map containing the data of the specified object
     * @param obj the object to turn into a map
     * @return the map with the data in the format fieldName -- value
     * @throws ReflectiveOperationException if there was an error accessing any field
     */
    protected Map<String, Serializable> toMap(T obj) throws ReflectiveOperationException {
        Map<String, Serializable> data = new HashMap<>();
        for (Map.Entry<String, FieldData> entry : fieldDataList.entrySet()) {
            FieldData fd = entry.getValue();
            data.put(entry.getKey(), (Serializable) this.getValue(obj, fd.field));
        }
        return data;
    }

    @SafeVarargs
    @NotNull
    private <K> List<K> concatenateArgs(K head, @Nullable K... tail) {
        if (tail == null)
            return List.of(head);
        List<K> result = new ArrayList<>(tail.length + 1);
        result.add(head);
        result.addAll(Arrays.asList(tail));
        return result;
    }

    /**
     * Create the table to store data associated with this kind of object
     * @throws IOException if there was an error while trying to
     */
    private void createTable() throws IOException {
        boolean result = driver.createTable(tableData);
        if (!result) {
            throw new IOException("Unable to create the table " + tableData.getName() + " for the plugin " + plugin.getName());
        }
    }

    /**
     * Stops all load data tasks that are still running
     */
    public void stopRunningTasks() {
        loadTasks.values().forEach(BukkitTask::cancel);
        loadTasks.clear();
    }

    /**
     * Loads the item associated with the specified primary key asynchronously. Then fires an event indicating the result
     * @param key The primary key (if there is a composite primary key, this is the first alphabetically by their field names)
     * @param keys The rest of the primary key in case it's a composite one (sorted alphabetically by their field names)
     * @return The load data bukkit asynchronous task
     */
    @NotNull
    public BukkitTask loadData(@NotNull Serializable key, @Nullable Serializable... keys) {
        return loadData(concatenateArgs(key, keys));
    }

    @NotNull
    private BukkitTask loadDataLambda(@NotNull List<Serializable> keys) {
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            T data = null;
            Exception throwable = null;
            try {
                data = driver.loadData(tableData.getName(), keys.toArray(new Serializable[0]));
                itemData.put(keys, data);
            }
            catch (Exception ex) {
                throwable = ex;
            }
            loadTasks.remove(keys);
            LoadedDataEvent<T> event = eventFactory.apply(keys, data, throwable);
            Bukkit.getPluginManager().callEvent(event);
        });
    }

    /**
     * Loads the item associated with the specified primary key asynchronously. Then fires an event indicating the result
     * @param keys List of values the primary keys of the queried object has
     * @return The load data bukkit asynchronous task
     */
    @NotNull
    public BukkitTask loadData(@NotNull List<Serializable> keys) {
        return loadTasks.computeIfAbsent(keys, this::loadDataLambda);
    }

    /**
     * Return the object associated with the specified primary key if it's already in memory. Otherwise return null
     * @param keys The primary key (if there is more than one field set as the primary key, their values have to be sorted alphabetically by their field names)
     * @return The item associated with the key, null if it has not been loaded
     */
    public @Nullable T tryGetDataNow(@NotNull List<Serializable> keys) {
        return itemData.computeIfAbsent(keys, (k) -> {
            loadTasks.computeIfAbsent(k, this::loadDataLambda);
            return null;
        });
    }


    /**
     * Return the object associated with the specified primary key if it's already in memory. Otherwise return null
     * @param key The primary key (if there is a composite primary key, this is the first alphabetically by their field names)
     * @param keys The rest of the primary key in case it's a composite one (sorted alphabetically by their field names)
     * @return The item associated with the key, null if it has not been loaded
     */
    public @Nullable T tryGetDataNow(@NotNull Serializable key, @Nullable Serializable... keys) {
        return tryGetDataNow(concatenateArgs(key, keys));
    }


    /**
     * Return if the specified object is already stored in the database
     * @param keys The primary key (if there is more than one field set as the primary key, their values have to be sorted alphabetically by their field names)
     * @return Whether the item exists or not
     * @throws SQLException if the selected StorageType uses an SQL database and there was an exception while querying it
     * @throws IOException if the selected StorageType stores data using files and folders and there was an exception while accessing them
     */
    public boolean exists(@NotNull List<Serializable> keys) throws IOException, SQLException {
        return driver.contains(tableData.getName(), keys.toArray(new Serializable[0]));
    }


    /**
     * Return if the specified object is already stored in the database
     * @param key The primary key (if there is a composite primary key, this is the first alphabetically by their field names)
     * @param keys The rest of the primary key in case it's a composite one (sorted alphabetically by their field names)
     * @return Whether the item exists or not
     * @throws SQLException if the selected StorageType uses an SQL database and there was an exception while querying it
     * @throws IOException if the selected StorageType stores data using files and folders and there was an exception while accessing them
     */
    public boolean exists(@NotNull Serializable key, @Nullable Serializable... keys) throws IOException, SQLException {
        return exists(concatenateArgs(key, keys));
    }


    /**
     * Delete an item from the database
     * @param item The item to delete
     * @return Whether the item has been deleted or not
     * @throws SQLException if the selected StorageType uses an SQL database and there was an exception while querying it
     * @throws IOException if the selected StorageType stores data using files and folders and there was an exception while accessing them
     */
    public boolean delete(@NotNull T item) throws IOException, SQLException {
        boolean res = false;
        try {
            res = driver.deleteData(tableData.getName(), item);
        }
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Could not save data on table " + tableData.getName() + "!");
            ex.printStackTrace();
        }
        return res;
    }

    private void rawSave(boolean delete, @NotNull List<T> items) {
        if (items.isEmpty()) {
            return;
        }
        try {
            if (saveCondition != null) {
                List<T> toDelete = items.stream().filter((item) -> !saveCondition.test(item)).collect(Collectors.toList());
                items = items.stream().filter(saveCondition).collect(Collectors.toList());
                driver.deleteData(tableData.getName(), toDelete);
            }
            Map<List<Serializable>, Boolean> results = driver.saveData(tableData.getName(), items);
            if (delete) {
                for (Map.Entry<List<Serializable>, Boolean> entry : results.entrySet()) {
                    if (entry.getValue()) {
                        itemData.remove(entry.getKey());
                        lastChecked.remove(entry.getKey());
                    }
                    else {
                        logger.log(Level.SEVERE, "Could not save an item on table " + tableData.getName() + "!");
                    }
                }
            }
        }
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Could not save data on table " + tableData.getName() + "!");
            ex.printStackTrace();
        }
    }

    private void save(boolean async, boolean delete, @NotNull Collection<List<Serializable>> keys) {
        if (keys.isEmpty()) {
            return;
        }
        List<T> items = new ArrayList<>();
        for (List<Serializable> key : keys) {
            try {
                T item = tryGetDataNow(key);
                items.add(item);
            }
            catch (Exception ex) {
                logger.log(Level.WARNING, "Error while preparing item for saving it. Key: "
                        + key.stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(", ")));
            }
        }
        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> rawSave(delete, items));
        }
        else {
            rawSave(delete, items);
        }
    }

    private void save(boolean delete, @NotNull Collection<List<Serializable>> keys) {
        save(true, delete, keys);
    }

    @SafeVarargs
    private void save(boolean delete, @NotNull List<Serializable>... keys) {
        save(delete, Arrays.asList(keys));
    }

    /**
     * Save the data of the object associated with the specified Primary Key
     * @param key The primary key (if the primary key is composite, this is the first alphabetically by their field names)
     * @param keys The rest of the primary key in case it's a composite one (sorted alphabetically by their field names)
     */
    public void save(@NotNull Serializable key, @Nullable Serializable... keys) {
        save(concatenateArgs(key, keys));
    }

    /**
     * Save the data of the object associated with the specified Primary Key
     * @param keys The primary key (if there is more than one field set as the primary key, their values have to be sorted alphabetically by their field names)
     */
    public void save(@NotNull List<Serializable> keys) {
        this.save(false, keys);
    }

    /**
     * Save the data of the object associated with the specified Primary Key and remove it from memory if the save process was successful
     * @param key The primary key (if the primary key is composite, this is the first alphabetically by their field names)
     * @param keys The rest of the primary key in case it's a composite one (sorted alphabetically by their field names)
     */
    public void saveAndRemove(@NotNull Serializable key, @Nullable Serializable... keys) {
        List<Serializable> keyList = concatenateArgs(key, keys);
        this.save(true, keyList);
    }

    /**
     * Save the data of the object associated with the specified Primary Key and remove it from memory if the save process was successful
     * @param keys The primary key (if there is more than one field set as the primary key, their values have to be sorted alphabetically by their field names)
     */
    public void saveAndRemove(@NotNull List<Serializable> keys) {
        this.save(true, keys);
    }

    private void saveFromMap(boolean async, @NotNull Map<List<Serializable>, T> dict, boolean remove) {
        save(async, remove, dict.keySet());
    }

    private void saveAll(boolean async, SaveOperation operation) {
        if (inactiveTime < 0) {
            if (operation == SaveOperation.SAVE_AND_REMOVE_INACTIVE)
                return;
            if (operation == SaveOperation.SAVE_ALL_AND_REMOVE_INACTIVE)
                operation = SaveOperation.SAVE_ALL;
        }
        switch (operation) {
            case SAVE_ALL:
                saveFromMap(async, itemData, false);
                break;
            case SAVE_ALL_AND_REMOVE_ALL:
                saveFromMap(async, itemData, true);
                break;
            case SAVE_AND_REMOVE_INACTIVE:
                saveAndRemoveInactive(async, true);
                break;
            case SAVE_ALL_AND_REMOVE_INACTIVE:
                saveAndRemoveInactive(async, false);
        }
    }

    /**
     * Save the data of all the objects currently loaded
     * @param operation a SaveOperation indicating the elements to save and the elements to remove
     */
    public void saveAll(SaveOperation operation) {
        saveAll(true, operation);
    }

    /**
     * Save the data of all the objects currently loaded synchronously (may cause lag)
     * @param operation a SaveOperation indicating the elements to save and the elements to remove
     */
    public void saveAllSync(SaveOperation operation) {
        saveAll(false, operation);
    }

    /**
     * Save the data of all the objects currently loaded and remove those that were successfully saved and have not been queried past its max inactive time
     */
    private void saveAndRemoveInactive(boolean async, boolean onlySaveInactive) {
        long now = System.currentTimeMillis();
        if (onlySaveInactive) {
            List<List<Serializable>> toSave = lastChecked.entrySet().stream()
                    .filter((e) -> now - e.getValue() >= inactiveTime)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            save(async, true, toSave);
        }
        else {
            Map<Boolean, List<List<Serializable>>> partitions =
                    lastChecked.keySet().stream()
                            .collect(Collectors.partitioningBy(
                                    (e) -> now - lastChecked.get(e) >= inactiveTime));
            save(async, true, partitions.get(true));
            save(async, false, partitions.get(false));
        }
    }
}
