package com.clanjhoo.dbhandler.drivers;

import com.clanjhoo.dbhandler.DBHandler;
import com.clanjhoo.dbhandler.data.DBObject;
import com.clanjhoo.dbhandler.data.TableData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class JSONDriver<T extends DBObject> implements DatabaseDriver<T> {
    private final File storage;
    private final Logger logger;
    // private static final Pattern jsonFile = Pattern.compile("(.*)\\.json");
    private static final Pattern filePattern = Pattern.compile("^(?!.{256,})(?!.*\\.\\..*)(?!(aux|clock\\$|con|nul|prn|com[1-9]|lpt[1-9])(?:\\$|\\.))[\\.\\w\\-$()+=\\[\\\\\\];#@~,&'][ \\.\\w\\-$()+=\\[\\\\\\];#@~,&']+[\\w\\-$()+=\\[\\\\\\];#@~,&']$");


    public JSONDriver(JavaPlugin plugin, String storageFolderName) {
        logger = plugin.getLogger();
        storage = new File(plugin.getDataFolder(), storageFolderName);
        if (storage.mkdirs()) {
            logger.log(Level.FINE, "Created local storage folder for raw JSON data");
        }
    }

    /*
    @Override
    @NotNull
    public Set<String> getTables() {
        String[] directories = storage.list((current, name) -> new File(current, name).isDirectory());
        if (directories == null) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(directories));
    }
     */

    @Override
    public boolean contains(@NotNull String table, @NotNull Serializable[] ids) {
        if (!filePattern.matcher(table).matches()) {
            throw new IllegalArgumentException("Invalid table name");
        }
        String[] strIds = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            strIds[i] = ids[i].toString();
        }
        String id = String.join("_", strIds);
        if (!filePattern.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid identifier");
        }
        File dataFile = new File(storage, table + "/" + id + ".json");
        return dataFile.exists() && dataFile.isFile();
    }

    @Override
    public T loadData(@NotNull String table, @NotNull Serializable[] ids, Function<Serializable[], T> defaultGenerator) throws IOException, SQLException {
        if (!filePattern.matcher(table).matches()) {
            throw new IllegalArgumentException("Invalid table name");
        }
        String[] strIds = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            strIds[i] = ids[i].toString();
        }
        String id = String.join("_", strIds);
        if (!filePattern.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid identifier");
        }
        File tableFolder = new File(storage, table);
        File dataFile = new File(tableFolder,  id + ".json");
        T dbObject = defaultGenerator.apply(ids);
        String[] pKeyNames = dbObject.getPrimaryKeyName();
        if (pKeyNames.length != ids.length) {
            throw new IllegalArgumentException("You must specify a value for each primary key defined for the object");
        }
        Arrays.sort(pKeyNames);
        for (int i = 0; i < ids.length; i++) {
            dbObject.setFieldValue(pKeyNames[i], ids[i]);
        }
        if (dataFile.exists()) {
            Map<String, Serializable> data;
            try (FileReader reader = new FileReader(dataFile)) {
                Gson gson = new Gson();
                Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
                data = gson.fromJson(reader, mapType);
            }
            for (String field : dbObject.getFields()) {
                if (data.containsKey(field)) {
                    dbObject.setFieldValue(field, data.get(field));
                }
            }
        }
        else {
            dbObject = null;
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

    private String getPrimaryKeyConcat(@NotNull T item) {
        String[] primaryKeys = item.getPrimaryKeyName();
        String[] pKeyVals = new String[primaryKeys.length];

        Arrays.sort(primaryKeys);
        for (int i = 0; i < primaryKeys.length; i++) {
            pKeyVals[i] = item.getFieldValue(primaryKeys[i]).toString();
        }

        return String.join("_", pKeyVals);
    }

    @Override
    public boolean saveData(@NotNull String table, @NotNull T item) {
        if (!filePattern.matcher(table).matches()) {
            throw new IllegalArgumentException("Invalid table name");
        }
        String id = getPrimaryKeyConcat(item);
        if (!filePattern.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid identifier");
        }
        File dataFile = new File(storage, table + "/" + id + ".json");
        Gson gson = new Gson();
        String serializedData = gson.toJson(DBObject.toMap(item));
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
    public Map<List<Serializable>, Boolean> saveData(@NotNull String table, @NotNull List<T> items) {
        Map<List<Serializable>, Boolean> results = new HashMap<>();
        if (items.size() == 0) {
            return results;
        }
        String[] keyNames = items.get(0).getPrimaryKeyName();
        Arrays.sort(keyNames);
        Serializable[] keys = new Serializable[keyNames.length];
        for (T item : items) {
            for (int i = 0; i < keyNames.length; i++) {
                keys[i] = item.getFieldValue(keyNames[i]);
            }
            results.put(Arrays.asList(keys), saveData(table, item));
        }
        return results;
    }
}
