package com.clanjhoo.dbhandler.collections;

import com.clanjhoo.dbhandler.data.DBObject;
import com.clanjhoo.dbhandler.drivers.StorageType;
import com.clanjhoo.dbhandler.data.TableData;
import com.clanjhoo.dbhandler.drivers.DatabaseDriver;
import com.clanjhoo.dbhandler.drivers.JSONDriver;
import com.clanjhoo.dbhandler.drivers.MariaDBDriver;
import com.clanjhoo.dbhandler.utils.Pair;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DBObjectManager<T extends DBObject> {
    private final Map<List<Serializable>, Long> lastChecked = new ConcurrentHashMap<>();
    private final Map<List<Serializable>, T> itemData = new ConcurrentHashMap<>();
    private final Map<List<Serializable>, Boolean> loadedData = new ConcurrentHashMap<>();
    private final Map<List<Serializable>, Boolean> loadingData = new ConcurrentHashMap<>();
    private final DatabaseDriver<T> driver;
    private final JavaPlugin plugin;
    private final Logger logger;
    private final Function<Serializable[], T> defaultGenerator;
    private final TableData table;
    private final long inactiveTime;

    /**
     * Instantiates a new DBObjectManager object
     * @param plugin The plugin that has created the object
     * @param inactiveTime Time in seconds to remove inactive objects from the manager
     * @param type Type of the storage driver
     * @param defaultGenerator Function that receives the primary key and returns a sample object of the type T. Must admit null as input for dummy items
     * @param config Any config options needed by the selected storage driver type
     */
    public DBObjectManager(@NotNull JavaPlugin plugin, Integer inactiveTime, @NotNull StorageType type, Function<Serializable[], T> defaultGenerator, Object... config) throws IOException {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        if (inactiveTime == null) {
            this.inactiveTime = 5 * 60 * 1000;  // 5 minutes inactive
        }
        else if (inactiveTime < 0) {
            this.inactiveTime = Long.MAX_VALUE;
        }
        else {
            this.inactiveTime = inactiveTime * 1000;
        }
        this.defaultGenerator = defaultGenerator;
        T defItem = defaultGenerator.apply(null);
        table = new TableData(defItem.getTableName());
        for (String field : defItem.getFields()) {
            table.addField(field, defItem.getFieldType(field), defItem.isFieldNullable(field));
        }
        table.setPrimaryKeys(defItem.getPrimaryKeyName());
        for (Set<String> uniqueFields : defItem.getUniqueFields()) {
            table.addUniqueConstraint(uniqueFields.toArray(new String[0]));
        }
        Map<String, Pair<String, TableData>> foreignFields = defItem.getForeignFields();
        for (String field : foreignFields.keySet()) {
            Pair<String, TableData> foreignData = foreignFields.get(field);
            table.addForeignKey(field, foreignData.getSecond(), foreignData.getFirst());
        }

        switch(type) {
            case JSON:
                if (config.length < 1 || !(config[0] instanceof String)) {
                    logger.log(Level.SEVERE, "JSON driver needs: <name of the storage folder>");
                    throw new IllegalArgumentException("Wrong config parameters, check the console for further details");
                }
                this.driver = new JSONDriver<>(plugin, (String) config[0]);
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
                this.driver = new MariaDBDriver<>(plugin, defItem, (String) config[0], (int) config[1], (String) config[2], (String) config[3], (String) config[4], (String) config[5]);
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
     * Create the table to store data associated with this kind of object
     * @throws IOException if there was an error while trying to
     */
    private void createTable() throws IOException {
        boolean result = driver.createTable(table);
        if (!result) {
            throw new IOException("Unable to create the table " + table.getName() + " for the plugin " + plugin.getName());
        }
    }

    /**
     * Return the object associated with the specified primary key
     * @param keys The primary key (if there is more than one field set as the primary key, their values will be sorted alphabetically by their field names)
     * @return The object associated with the key
     * @throws AssertionError If the data couldn't be loaded on the main thread in this tick
     */
    public @NotNull T getDataNow(@NotNull Serializable[] keys) throws AssertionError {
        Object[] aux = new Object[]{null};
        boolean result = getDataAsynchronous(Arrays.asList(keys),
                                             (data) -> aux[0] = data,
                                             () -> {}, true, false, 0);
        if (!result) {
            throw new AssertionError("Couldn't load the data on a synchronous way, loading later");
        }
        return (T) aux[0];
    }

    /**
     * Try to get the specified data with the defined database driver and pass it to a consumer if found
     * @param keys List of values the primary keys of the queried object has
     * @param success Consumer function that gets an object of the type T and works with it
     * @param error Function to be executed if there is an error while trying to get the data
     * @param canRunLater Whether the success function has to be run on this tick or not
     * @return Whether the success method could be executed (or plan to execute it on a later task) or not
     */
    public boolean getDataSynchronous(@NotNull Serializable[] keys, Consumer<T> success, Runnable error, boolean canRunLater) {
        return getDataAsynchronous(Arrays.asList(keys), success, error, true, canRunLater, 0);
    }

    /**
     * Try to get the specified data with the defined database driver and pass it to a consumer if found
     * @param keys List of values the primary keys of the queried object has
     * @param success Consumer function that gets an object of the type T and works with it
     * @param error Function to be executed if there is an error while trying to get the data
     * @return Whether the success method could be executed (or plan to execute it on a later task) or not
     */
    public boolean getDataAsynchronous(@NotNull Serializable[] keys, Consumer<T> success, Runnable error) {
        return getDataAsynchronous(Arrays.asList(keys), success, error, false, true, 0);
    }


    /**
     * Return if the specified object is already stored in the database
     * @param keys List of values the primary keys of the queried object has
     * @return Whether the item exists or not
     */
    public boolean exists(@NotNull Serializable[] keys) throws IOException, SQLException {
        return driver.contains(table.getName(), keys);
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
                        Serializable[] arrayKeys = keys.toArray(new Serializable[0]);
                        T data = driver.loadData(table.getName(), arrayKeys, defaultGenerator);
                        if (data == null) {
                            data = defaultGenerator.apply(keys.toArray(new Serializable[0]));
                        }
                        itemData.put(keys, data);
                        loadedData.put(keys, true);
                        if (!mainThread) {
                            success.accept(data);
                        }
                    }
                    catch (Exception ex) {
                        logger.log(Level.WARNING, "Couldn't load data from table " + table.getName());
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
                logger.log(Level.WARNING, "Exceeded maximum attempts while loading data from table " + table.getName());
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
            Map<List<Serializable>, Boolean> results = driver.saveData(table.getName(), items);
            if (delete) {
                for (List<Serializable> result : results.keySet()) {
                    if (results.get(result)) {
                        loadedData.remove(result);
                        itemData.remove(result);
                        lastChecked.remove(result);
                    }
                    else {
                        logger.log(Level.SEVERE, "Could not save an item on table " + table.getName() + "!");
                    }
                }
            }
        }
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Could not save data on table " + table.getName() + "!");
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

    /**
     * Save the data of the object associated with the specified Primary Key
     * @param keys The Primary Key associated with the object as a list of the values of the fields that compose it (sorted by name alphabetically)
     */
    public void save(@NotNull List<Serializable> keys) {
        this.save(false, keys);
    }

    /**
     * Save the data of the object associated with the specified Primary Key and remove it from memory if the save process was successful
     * @param keys The Primary Key associated with the object as a list of the values of the fields that compose it (sorted by name alphabetically)
     */
    public void saveAndRemove(@NotNull List<Serializable> keys) {
        this.save(true, keys);
    }

    /**
     * Save the data of the object associated with the specified Primary Key
     * @param keys The Primary Key associated with the object as an array containing the values of the fields that compose it (sorted by name alphabetically)
     */
    public void save(@NotNull Serializable[] keys) {
        List<Serializable> keyList = Arrays.asList(keys);
        this.save(false, keyList);
    }

    /**
     * Save the data of the object associated with the specified Primary Key and remove it from memory if the save process was successful
     * @param keys The Primary Key associated with the object as an array containing the values of the fields that compose it (sorted by name alphabetically)
     */
    public void saveAndRemove(@NotNull Serializable[] keys) {
        List<Serializable> keyList = Arrays.asList(keys);
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
