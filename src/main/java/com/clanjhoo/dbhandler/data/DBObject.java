package com.clanjhoo.dbhandler.data;

import com.clanjhoo.dbhandler.utils.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface DBObject {
    /**
     * Return a string with the name of the table (without prefixes) used to store objects of this class
     * @return The name of the table associated with this class
     */
    @NotNull String getTableName();

    /**
     * Return an array containing the name of the fields used as primary keys in conjunction (at least one element guaranteed)
     * @return The array with the name of the primary key fields, no repeated values
     */
    @NotNull String[] getPrimaryKeyName();

    /**
     * Return the set of all the fields this object has (only persistent data fields, at least one field)
     * @return A set containing the field names
     */
    @NotNull Set<String> getFields();

    /**
     * Return the value of a specified field
     * @param field The name of the field
     * @return The contents of the field
     * @throws IllegalArgumentException if the field does not exist
     */
    Serializable getFieldValue(@NotNull String field) throws IllegalArgumentException;

    /**
     * Get whether a certain field is nullable or not
     * @param field The name of the field
     * @return true if the field can contain null values, false otherwise
     * @throws IllegalArgumentException if the field does not exist
     */
    boolean isFieldNullable(@NotNull String field) throws IllegalArgumentException;

    /**
     * Get the list of unique valued sets of fields (excluding the Primary Key)
     * @return The list of unique valued sets of fields
     */
    @NotNull List<Set<String>> getUniqueFields();

    /**
     * Get a map that relates a field of this class to a pair of (other field name, other table data)
     * @return The specified map of foreign fields
     */
    @NotNull Map<String, Pair<String, TableData>> getForeignFields();

    /**
     * Get a string containing a valid SQL data type (can include collation when used with a string type) if you want your object to be stored on an SQL database
     * @param field The name of the field
     * @return The string with the SQL type of the specified field
     * @throws IllegalArgumentException if the field does not exist
     */
    @NotNull String getFieldType(@NotNull String field) throws IllegalArgumentException;


    /**
     * Set the value of the specified field to the specified value
     * @param field The name of the field
     * @param value The new value of the field
     * @throws IllegalArgumentException if the field does not exist, the value is of the wrong type or value is null and the field does not admit null values
     */
    void setFieldValue(@NotNull String field, Serializable value) throws IllegalArgumentException;


    /**
     * Saves any DBObject persisten data to a Map relating field name with field value
     * @param object The object to save to a Map
     * @return The generated Map of name -> value
     */
    static @NotNull Map<String, Serializable> toMap(@NotNull DBObject object) {
        Map<String, Serializable> data = new HashMap<>();
        for (String field : object.getFields()) {
            data.put(field, object.getFieldValue(field));
        }
        return data;
    }
}
