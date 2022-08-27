package com.clanjhoo.dbhandler.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

interface DatabaseDriver<T> {
    /**
     * @param table name of the table to query
     * @param ids primary keys of the object to query. Considered sorted in alphabetical order by field name
     * @return whether the specified item exists in this table or not
     * @throws IllegalArgumentException if the specified table name could not be used
     * @throws NoSuchElementException if the specified table does not exist
     */
    boolean contains(@NotNull String table, @NotNull Serializable[] ids) throws IOException, SQLException;

    /**
     * @param table name of the table to query
     * @param ids primary keys of the object to query. Considered sorted in alphabetical order by field name
     * @return The queried item, null if it doesn't exist
     * @throws IllegalArgumentException if the specified table name or identifier could not be used
     * @throws NoSuchElementException if the specified table or identifier do not exist
     */
    @NotNull T loadData(@NotNull String table, @NotNull Serializable[] ids) throws IOException, SQLException, ReflectiveOperationException;

    /**
     * @param table name of the table to create
     * @return true if it already existed or it has just been created, false otherwise
     * @throws IllegalArgumentException if the specified table name could not be used
     */
    boolean createTable(TableData table);

    /**
     * @param table name of the table to drop
     * @return false if the table continues to exist, true otherwise
     * @throws IllegalArgumentException if the specified table name could not be used
     */
    boolean dropTable(String table);

    /**
     * @param table name of the table to create
     * @param item database object to store
     * @return true if the data has been saved, false otherwise
     * @throws IllegalArgumentException if the specified table name or identifier could not be used
     */
    boolean saveData(@NotNull String table, @NotNull T item) throws ReflectiveOperationException;

    /**
     * @param table name of the table to create
     * @param items database objects to store
     * @return true if all the data has been saved, false if any of the items failed to be saved
     * @throws IllegalArgumentException if the specified table name or identifier could not be used
     */
    Map<List<Serializable>, Boolean> saveData(@NotNull String table, @NotNull List<T> items) throws ReflectiveOperationException;
}
