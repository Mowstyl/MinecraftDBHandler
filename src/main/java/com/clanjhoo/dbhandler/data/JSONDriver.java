package com.clanjhoo.dbhandler.data;

import com.clanjhoo.dbhandler.utils.TriFunction;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

class JSONDriver<T> implements DatabaseDriver<T> {
    // private static final Pattern jsonFile = Pattern.compile("(.*)\\.json");
    private static final Pattern filePattern = Pattern.compile("^(?!.{256,})(?!.*\\.\\..*)(?!(aux|clock\\$|con|nul|prn|com[1-9]|lpt[1-9])(?:\\$|\\.))[\\.\\w\\-$()+=\\[\\\\\\];#@~,&'][ \\.\\w\\-$()+=\\[\\\\\\];#@~,&']+[\\w\\-$()+=\\[\\\\\\];#@~,&']$");

    private final File storage;
    private final Logger logger;
    private final DBObjectManager<T> manager;

    /**
     * Instantiates a new JSON Driver object. Used when StorageType.JSON is selected when instantiating DBObjectManager
     * @param plugin The plugin that has created the object. This will be passed automatically by DBObjectManager constructor
     * @param manager The DBObjectManager that is using this driver. This will be passed automatically by DBObjectManager constructor
     * @param storageFolderName The name of the folder containing the database, created inside the plugin data folder. Must be passed in the config array of the DBObjectManager constructor
     * @see DBObjectManager#DBObjectManager(Class clazz, JavaPlugin plugin, StorageType type, TriFunction eventFactory, Predicate saveCondition, int inactiveTime, Object... config)
     */
    JSONDriver(@NotNull JavaPlugin plugin, @NotNull DBObjectManager<T> manager, @NotNull String storageFolderName) {
        logger = plugin.getLogger();
        storage = new File(plugin.getDataFolder(), storageFolderName);
        if (storage.mkdirs()) {
            logger.log(Level.FINE, "Created local storage folder for raw JSON data");
        }
        this.manager = manager;
    }

    private static String getId(@NotNull Serializable[] ids) {
        String[] strIds = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            strIds[i] = ids[i].toString();
        }
        String id = String.join("_", strIds);
        if (!filePattern.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid identifier");
        }
        return id;
    }

    @Override
    public boolean contains(@NotNull String table, @NotNull Serializable[] ids) {
        if (!filePattern.matcher(table).matches()) {
            throw new IllegalArgumentException("Invalid table name");
        }
        String id = getId(ids);
        File dataFile = new File(storage, table + "/" + id + ".json");
        return dataFile.exists() && dataFile.isFile();
    }

    @Override
    public @NotNull T loadData(@NotNull String table, @NotNull Serializable[] ids) throws IOException, ReflectiveOperationException {
        if (!filePattern.matcher(table).matches()) {
            throw new IllegalArgumentException("Invalid table name");
        }
        String id = getId(ids);
        File tableFolder = new File(storage, table);
        File dataFile = new File(tableFolder,  id + ".json");
        T dbObject;
        String[] pKeyNames = manager.getTableData().getPrimaryKeys().toArray(new String[0]);
        if (pKeyNames.length != ids.length) {
            throw new IllegalArgumentException("You must specify a value for each primary key defined for the object");
        }
        Arrays.sort(pKeyNames);
        Map<String, Serializable> data;
        if (dataFile.exists()) {
            try (FileReader reader = new FileReader(dataFile)) {
                Gson gson = new Gson();
                Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
                data = gson.fromJson(reader, mapType);
            }
        }
        else {
            data = Collections.emptyMap();
        }
        dbObject = manager.getInstance(data, false);
        for (int i = 0; i < ids.length; i++) {
            manager.setValue(dbObject, pKeyNames[i], ids[i]);
        }
        return dbObject;
    }

    @Override
    public boolean createTable(TableData table) {
        String name = table.getName();
        if (!filePattern.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid table name");
        }
        File tableFile = new File(storage, name);
        if (tableFile.exists()) {
            return true;
        }
        return tableFile.mkdirs();
    }

    @Override
    public boolean dropTable(String table) {
        if (!filePattern.matcher(table).matches()) {
            throw new IllegalArgumentException("Invalid table name");
        }
        File tableFile = new File(storage, table);
        if (!tableFile.exists()) {
            return true;
        }
        return tableFile.delete();
    }

    private String getPrimaryKeyConcat(@NotNull T item) throws ReflectiveOperationException {
        String[] primaryKeys = manager.getTableData().getPrimaryKeys().toArray(new String[0]);
        String[] pKeyVals = new String[primaryKeys.length];

        Arrays.sort(primaryKeys);
        for (int i = 0; i < primaryKeys.length; i++) {
            pKeyVals[i] = manager.getValue(item, primaryKeys[i]).toString();
        }

        String id =  String.join("_", pKeyVals);
        if (!filePattern.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid identifier");
        }
        return id;
    }

    @Override
    public boolean saveData(@NotNull String table, @NotNull T item) throws ReflectiveOperationException {
        if (!filePattern.matcher(table).matches()) {
            throw new IllegalArgumentException("Invalid table name");
        }
        String id = getPrimaryKeyConcat(item);
        File dataFile = new File(storage, table + "/" + id + ".json");
        Gson gson = new Gson();
        String serializedData = gson.toJson(manager.toMap(item));
        try (FileWriter writer = new FileWriter(dataFile)) {
            writer.write(serializedData);
            writer.flush();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Raw JSON store data error on table " + table);
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public Map<List<Serializable>, Boolean> saveData(@NotNull String table, @NotNull List<T> items) throws ReflectiveOperationException {
        Map<List<Serializable>, Boolean> results = new HashMap<>();
        if (items.isEmpty()) {
            return results;
        }
        String[] keyNames = manager.getTableData().getPrimaryKeys().toArray(new String[0]);
        Arrays.sort(keyNames);
        Serializable[] keys = new Serializable[keyNames.length];
        for (T item : items) {
            for (int i = 0; i < keyNames.length; i++) {
                keys[i] = manager.getValue(item, keyNames[i]);
            }
            results.put(Arrays.asList(keys), saveData(table, item));
        }
        return results;
    }

    @Override
    public boolean deleteData(@NotNull String table, @NotNull T item) throws ReflectiveOperationException {
        if (!filePattern.matcher(table).matches()) {
            throw new IllegalArgumentException("Invalid table name");
        }
        String id = getPrimaryKeyConcat(item);
        File dataFile = new File(storage, table + "/" + id + ".json");
        return !dataFile.exists() || dataFile.delete();
    }

    @Override
    public Map<List<Serializable>, Boolean> deleteData(@NotNull String table, @NotNull List<T> items) throws ReflectiveOperationException {
        Map<List<Serializable>, Boolean> results = new HashMap<>();
        if (items.isEmpty()) {
            return results;
        }
        String[] keyNames = manager.getTableData().getPrimaryKeys().toArray(new String[0]);
        Arrays.sort(keyNames);
        Serializable[] keys = new Serializable[keyNames.length];
        for (T item : items) {
            for (int i = 0; i < keyNames.length; i++) {
                keys[i] = manager.getValue(item, keyNames[i]);
            }
            results.put(Arrays.asList(keys), deleteData(table, item));
        }
        return results;
    }
}
