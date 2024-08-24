package com.clanjhoo.dbhandler.data;

import com.clanjhoo.dbhandler.utils.TriFunction;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Predicate;

/**
 * The possible save operations to perform
 */
public enum SaveOperation {
    /**
     * Save all the data currently loaded
     */
    SAVE_ALL,
    /**
     * Save and remove from memory the data that has not been queried for the amount of time set in the DBObjectManager instance. This does not delete anything from the database
     * @see DBObjectManager#DBObjectManager(Class clazz, JavaPlugin plugin, StorageType type, TriFunction eventFactory, Predicate saveCondition, int inactiveTime, Object... config)
     */
    SAVE_AND_REMOVE_INACTIVE,
    /**
     * Save and remove from memory all the data currently loaded. This does not delete anything from the database
     */
    SAVE_ALL_AND_REMOVE_ALL,
    /**
     * Save all the data currently loaded and remove from memory the data that has not been queried for the amount of time set in the DBObjectManager instance. This does not delete anything from the database
     * @see DBObjectManager#DBObjectManager(Class clazz, JavaPlugin plugin, StorageType type, TriFunction eventFactory, Predicate saveCondition, int inactiveTime, Object... config)
     */
    SAVE_ALL_AND_REMOVE_INACTIVE;
}
