package com.clanjhoo.dbhandler.data;

import com.clanjhoo.dbhandler.annotations.*;
import com.clanjhoo.dbhandler.utils.Pair;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DBObjectManager<T> {
    private final Map<List<Serializable>, Long> lastChecked = new ConcurrentHashMap<>();
    private final Map<List<Serializable>, T> itemData = new ConcurrentHashMap<>();
    private final Map<List<Serializable>, Boolean> loadedData = new ConcurrentHashMap<>();
    private final Map<List<Serializable>, Boolean> loadingData = new ConcurrentHashMap<>();
    private final DatabaseDriver<T> driver;
    private final JavaPlugin plugin;
    private final Logger logger;
    private final long inactiveTime;
    private final Class<T> meself;
    private final Consumer<T> afterTask;
    private TableData tableData;
    private boolean dataInitialized;
    private Map<String, FieldData> fieldDataList;
    private Map<String, Pair<String, String>> foreigns;


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
            val = Byte.valueOf(value).byteValue();
        }
        else if (short.class.equals(primitive)) {
            val = Short.valueOf(value).shortValue();
        }
        else if (int.class.equals(primitive)) {
            val = Integer.valueOf(value).intValue();
        }
        else if (long.class.equals(primitive)) {
            val = Long.valueOf(value).longValue();
        }
        else if (float.class.equals(primitive)) {
            val = Float.valueOf(value).floatValue();
        }
        else if (double.class.equals(primitive)) {
            val = Double.valueOf(value).doubleValue();
        }
        else if (boolean.class.equals(primitive)) {
            val = Boolean.valueOf(value).booleanValue();
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
                        if (dann.name().length() > 0)
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
                tableData.addField(n, fieldType, fd.nullable);
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
                            return new Pair<>(fkAnn.name(), fkAnn.table());
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
        for (String name : foreigns.keySet()) {
            Pair<String, String> name_table = foreigns.get(name);
            TableData other = TableData.findTableData(name_table.getSecond());
            if (other == null) {
                throw new IllegalArgumentException("Table " + name_table.getSecond() + " is not defined");
            }
            tableData.addForeignKey(name, other, name_table.getFirst());
        }
    }

    /**
     * Instantiates a new DBObjectManager object
     * @param clazz The class of the object to manage
     * @param afterTask A consumer that takes an item that has just been loaded from database. Can be null
     * @param plugin The plugin that has created the object
     * @param inactiveTime Time in seconds to remove inactive items from the manager
     * @param type Type of the storage driver
     * @param config Any config options needed by the selected storage driver type
     * @see JSONDriver#JSONDriver(JavaPlugin plugin, DBObjectManager manager, String storageFolderName)
     * @see MariaDBDriver#MariaDBDriver(JavaPlugin plugin, DBObjectManager manager, String host, int port, String database, String username, String password, String prefix)
     */
    public DBObjectManager(@NotNull Class<T> clazz, @Nullable Consumer<T> afterTask, @NotNull JavaPlugin plugin, Integer inactiveTime, @NotNull StorageType type, Object... config) throws IOException {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.meself = clazz;
        this.afterTask = afterTask;

        initializeTableData();

        if (inactiveTime == null) {
            this.inactiveTime = 5 * 60 * 1000;  // 5 minutes inactive
        }
        else if (inactiveTime < 0) {
            this.inactiveTime = Long.MAX_VALUE;
        }
        else {
            this.inactiveTime = inactiveTime * 1000;
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

    protected @NotNull T getDefault() throws ReflectiveOperationException {
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

    protected @NotNull T getInstance(@NotNull Map<String, Serializable> data, boolean strict) throws ReflectiveOperationException {
        T def = meself.getDeclaredConstructor().newInstance();
        if (strict) {
            for (String name : data.keySet()) {
                if (!fieldDataList.containsKey(name)) {
                    throw new IllegalArgumentException("The field " + name + " is not defined for the table " + tableData.getName());
                }
            }
        }
        for (String name : fieldDataList.keySet()) {
            FieldData fd = fieldDataList.get(name);
            Serializable value = data.get(name);
            if (value != null) {
                setValue(def, fd.field, value);
            }
            else {
                setValue(def, fd.field, fd.defaultValue);
            }
        }
        return def;
    }

    protected @NotNull TableData getTableData() {
        return tableData;
    }

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
            Number v = (Number) value;
            if (byte.class.equals(type) || Byte.class.isAssignableFrom(type)) {
                value = v.byteValue();
            }
            else if (short.class.equals(type) || Short.class.isAssignableFrom(type)) {
                value = v.shortValue();
            }
            else if (int.class.equals(type) || Integer.class.isAssignableFrom(type)) {
                value = v.intValue();
            }
            else if (long.class.equals(type) || Long.class.isAssignableFrom(type)) {
                value = v.longValue();
            }
            else if (float.class.equals(type) || Float.class.isAssignableFrom(type)) {
                value = v.floatValue();
            }
            else if (double.class.equals(type) || Double.class.isAssignableFrom(type)) {
                value = v.doubleValue();
            }
        }
        else if (type.isPrimitive()) {
            if (byte.class.equals(type) || Byte.class.isAssignableFrom(type)) {
                value = (byte) value;
            }
            else if (short.class.equals(type) || Short.class.isAssignableFrom(type)) {
                value = (short) value;
            }
            else if (int.class.equals(type) || Integer.class.isAssignableFrom(type)) {
                value = (int) value;
            }
            else if (long.class.equals(type) || Long.class.isAssignableFrom(type)) {
                value = (long) value;
            }
            else if (float.class.equals(type) || Float.class.isAssignableFrom(type)) {
                value = (float) value;
            }
            else if (double.class.equals(type) || Double.class.isAssignableFrom(type)) {
                value = (double) value;
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

    protected Class<Serializable> getType(String field) {
        FieldData fd = fieldDataList.get(field);
        if (fd == null) {
            throw new IllegalArgumentException("The field " + field + " is not defined for the table " + tableData.getName());
        }
        return (Class<Serializable>) fd.field.getType();
    }

    protected Map<String, Serializable> toMap(T obj) throws ReflectiveOperationException {
        Map<String, Serializable> data = new HashMap<>();
        for (String name : fieldDataList.keySet()) {
            FieldData fd = fieldDataList.get(name);
            data.put(name, (Serializable) this.getValue(obj, fd.field));
        }
        return data;
    }

    @SafeVarargs
    private final <K> List<K> concatenateArgs(K head, K... tail) {
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

    private @NotNull T getDataBlocking(@NotNull List<Serializable> keys) throws ReflectiveOperationException, SQLException, IOException {
        T data = driver.loadData(tableData.getName(), keys.toArray(new Serializable[0]));
        if (afterTask != null) {
            afterTask.accept(data);
        }
        itemData.put(keys, data);
        loadedData.put(keys, true);
        return data;
    }

    /**
     * Loads the item associated with the specified primary key on the main thread
     * @param key The primary key (if there is a composite primary key, this is the first alphabetically by their field names)
     * @param keys The rest of the primary key in case it's a composite one (sorted alphabetically by their field names)
     * @return The object associated with the key or null if there was some error
     */
    public @Nullable T getDataBlocking(@NotNull Serializable key, Serializable... keys) {
        T item = null;
        List<Serializable> aKeys = concatenateArgs(key, keys);
        lastChecked.put(aKeys, System.currentTimeMillis());
        // If we aren't loading the specified player
        if (!loadingData.getOrDefault(aKeys, false)) {
            // we check if we have to load it, or we have already done it
            if (!loadedData.getOrDefault(aKeys, false)) {
                // not loaded, async task
                loadingData.put(aKeys, true);
                try {
                    item = getDataBlocking(aKeys);
                } catch (IOException | SQLException e) {
                    logger.log(Level.WARNING, "The driver returned an error while retrieving an item from the database");
                    e.printStackTrace();
                } catch (ReflectiveOperationException e) {
                    logger.log(Level.WARNING, "There was an error while accessing a field");
                    e.printStackTrace();
                } finally {
                    loadingData.put(aKeys, true);
                }
            }
            else {
                item = itemData.get(aKeys);
            }
        }
        return item;
    }

    /**
     * Return the object associated with the specified primary key if it's already in memory. Otherwise raises an exception
     * @param key The primary key (if there is a composite primary key, this is the first alphabetically by their field names)
     * @param keys The rest of the primary key in case it's a composite one (sorted alphabetically by their field names)
     * @return The item associated with the key
     * @throws AssertionError If the data couldn't be loaded on the main thread in this tick
     */
    public @NotNull T tryGetDataNow(@NotNull Serializable key, Serializable... keys) throws AssertionError {
        Object[] aux = new Object[]{null};
        boolean result = getDataAsynchronous(concatenateArgs(key, keys),
                                             (data) -> aux[0] = data,
                                             () -> {}, true, false, 0);
        if (!result) {
            throw new AssertionError("Couldn't load the data on a synchronous way, loading later");
        }
        return (T) aux[0];
    }

    /**
     * Try to get the specified item from the database and pass it to a consumer if found
     * @param success Consumer function that gets an object of the type T and works with it
     * @param error Function to be executed if there is an error while trying to get the data
     * @param canRunLater Whether the success function has to be run on this tick or not
     * @param key The primary key (if there is a composite primary key, this is the first alphabetically by their field names)
     * @param keys The rest of the primary key in case it's a composite one (sorted alphabetically by their field names)
     * @return Whether the success method could be executed (or plan to execute it on a later task) or not
     */
    public boolean getDataSynchronous(Consumer<T> success, Runnable error, boolean canRunLater, @NotNull Serializable key, Serializable... keys) {
        return getDataAsynchronous(concatenateArgs(key, keys), success, error, true, canRunLater, 0);
    }

    /**
     * Try to get the specified data with the defined database driver and pass it to a consumer if found
     * @param success Consumer function that gets an object of the type T and works with it
     * @param error Function to be executed if there is an error while trying to get the data
     * @param key The primary key (if there is a composite primary key, this is the first alphabetically by their field names)
     * @param keys The rest of the primary key in case it's a composite one (sorted alphabetically by their field names)
     * @return Whether the success method could be executed (or plan to execute it on a later task) or not
     */
    public boolean getDataAsynchronous(Consumer<T> success, Runnable error, @NotNull Serializable key, Serializable... keys) {
        return getDataAsynchronous(concatenateArgs(key, keys), success, error, false, true, 0);
    }


    /**
     * Return if the specified object is already stored in the database
     * @param key The primary key (if there is a composite primary key, this is the first alphabetically by their field names)
     * @param keys The rest of the primary key in case it's a composite one (sorted alphabetically by their field names)
     * @return Whether the item exists or not
     */
    public boolean exists(@NotNull Serializable key, Serializable... keys) throws IOException, SQLException {
        return driver.contains(tableData.getName(), concatenateArgs(key, keys).toArray(new Serializable[0]));
    }

    /**
     * Try to get the specified data with the defined database driver and pass it to a consumer if found
     * @param keys List of values the primary keys of the queried object has
     * @param success Consumer function that gets an object of the type T and works with it
     * @param error Function to be executed if there is an error while trying to get the data
     * @param mainThread Whether the success function has to be run on the main thread or not
     * @param canRunLater Whether the success function has to be run on this tick or not
     * @param attempt Number of attempts done trying to get this data. Should be 0
     * @return Whether the success method could be executed (or plan to execute it on a later task) or not
     */
    private boolean getDataAsynchronous(@NotNull List<Serializable> keys, Consumer<T> success, Runnable error, boolean mainThread, boolean canRunLater, int attempt) {
        lastChecked.put(keys, System.currentTimeMillis());
        // If we aren't loading the specified player
        if (!loadingData.getOrDefault(keys, false)) {
            // we check if we have to load it, or we have already done it
            if (!loadedData.getOrDefault(keys, false)) {
                // not loaded, async task
                loadingData.put(keys, true);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        T data = getDataBlocking(keys);
                        if (!mainThread) {
                            success.accept(data);
                        }
                    }
                    catch (Exception ex) {
                        logger.log(Level.WARNING, "Couldn't load data from table " + tableData.getName());
                        ex.printStackTrace();
                        error.run();
                    }
                    finally {
                        loadingData.put(keys, false);
                    }
                });
                if (mainThread && canRunLater) {
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> getDataAsynchronous(keys, success, error, true, true, attempt + 1), 1);
                }
                return !mainThread || canRunLater;
            } else {
                // already loaded
                success.accept(itemData.get(keys));
                return true;
            }
        }
        else if (canRunLater) {
            if (attempt < 10) {
                if (mainThread) {
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> getDataAsynchronous(keys, success, error, true, true, attempt + 1), 1);
                }
                else {
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin,
                            () -> getDataAsynchronous(keys, success, error, false, true, attempt + 1), 1);
                }
            }
            else {
                logger.log(Level.WARNING, "Exceeded maximum attempts while loading data from table " + tableData.getName());
                error.run();
            }
        }
        return canRunLater;
    }

    private void rawSave(boolean delete, @NotNull List<T> items) {
        if (items.size() == 0) {
            return;
        }
        try {
            Map<List<Serializable>, Boolean> results = driver.saveData(tableData.getName(), items);
            if (delete) {
                for (List<Serializable> result : results.keySet()) {
                    if (results.get(result)) {
                        loadedData.remove(result);
                        itemData.remove(result);
                        lastChecked.remove(result);
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
        if (keys.size() == 0) {
            return;
        }
        List<T> items = new ArrayList<>();
        for (List<Serializable> key : keys) {
            if (!loadedData.getOrDefault(key, false)) {
                continue;
            }
            T item = itemData.get(key);
            if (item == null) {
                loadedData.remove(key);
                lastChecked.remove(key);
                logger.log(Level.WARNING, "Data not loaded but marked as loaded! Please report this bug!");
            }
            else {
                items.add(item);
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

    private void save(boolean delete, @NotNull List<Serializable>... keys) {
        save(delete, Arrays.asList(keys));
    }

    private void save(@NotNull List<Serializable> keys) {
        this.save(false, keys);
    }

    /**
     * Save the data of the object associated with the specified Primary Key and remove it from memory if the save process was successful
     * @param keys The primary key (if there is more than one field set as the primary key, their values have to be sorted alphabetically by their field names)
     */
    private void saveAndRemove(@NotNull List<Serializable> keys) {
        this.save(true, keys);
    }

    /**
     * Save the data of the object associated with the specified Primary Key
     * @param key The primary key (if there is a composite primary key, this is the first alphabetically by their field names)
     * @param keys The rest of the primary key in case it's a composite one (sorted alphabetically by their field names)
     */
    public void save(@NotNull Serializable key, Serializable... keys) {
        List<Serializable> keyList = concatenateArgs(key, keys);
        this.save(false, keyList);
    }

    /**
     * Save the data of the object associated with the specified Primary Key and remove it from memory if the save process was successful
     * @param keys The primary key (if there is more than one field set as the primary key, their values have to be sorted alphabetically by their field names)
     */
    public void saveAndRemove(@NotNull Serializable key, Serializable... keys) {
        List<Serializable> keyList = concatenateArgs(key, keys);
        this.save(true, keyList);
    }

    private void saveFromMap(boolean async, @NotNull Map<List<Serializable>, T> dict, boolean remove) {
        save(async, remove, dict.keySet());
    }

    /**
     * Save the data of all the objects currently loaded
     */
    public void saveAll() {
        saveFromMap(true, itemData, false);
    }

    /**
     * Save the data of all the objects currently loaded and remove the objects that were successfully saved
     */
    public void saveAndRemoveAll() {
        saveFromMap(true, itemData, true);
    }

    /**
     * Save the data of all the objects currently loaded and remove the objects that were successfully saved without tasks (may cause lots of lag, for onDisable only)
     */
    public void saveAndRemoveAllSync() {
        saveFromMap(false, itemData, true);
    }

    /**
     * Save the data of all the objects currently loaded that have expired their max inactive time and remove those that were successfully saved
     */
    public void saveAndRemoveUnactive() {
        long now = System.currentTimeMillis();
        for (Map.Entry<List<Serializable>, Long> entry : lastChecked.entrySet()) {
            List<Serializable> keys = entry.getKey();
            long lastCheck = entry.getValue();
            if (now - lastCheck >= inactiveTime) {
                saveAndRemove(keys);
            }
        }
    }
}
