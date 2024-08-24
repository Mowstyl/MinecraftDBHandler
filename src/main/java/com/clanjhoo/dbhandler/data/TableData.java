package com.clanjhoo.dbhandler.data;

import com.clanjhoo.dbhandler.utils.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * A class used to store all the information related to the structure of a database table
 */
public class TableData {
    private final static Map<String, TableData> definedTables = new ConcurrentHashMap<>();

    private final String name;
    private Set<String> primaryKeys;
    private final List<Set<String>> uniqueKeys;
    private final Map<String, FieldData> data;
    private final Map<String, Pair<String, TableData>> foreignKeys;


    /**
     * Returns the table with the given name. If it does not exist, the method will try to instantiate a new one
     * @param name the name of the table
     * @return the object containing table data
     */
    @NotNull
    public static TableData getTableData(String name) {
        if (definedTables.containsKey(name)) {
            return definedTables.get(name);
        }
        TableData table = new TableData(name);
        definedTables.put(name, table);
        return table;
    }

    /**
     * Returns the table with the given name. If it does not exist, returns null
     * @param name the name of the table
     * @return the object containing table data or null if it does not exist
     */
    @Nullable
    public static TableData findTableData(String name) {
        return definedTables.get(name);
    }

    private TableData(String name) {
        this.name = name;
        primaryKeys = null;
        data = new ConcurrentHashMap<>();
        foreignKeys = new ConcurrentHashMap<>();
        uniqueKeys = new CopyOnWriteArrayList<>();
    }

    /**
     * Set a field as a foreign key
     * @param thisField The name of the field
     * @param otherTable The data of the other table
     * @param otherField The name of the field of the other table
     */
    public void addForeignKey(String thisField, TableData otherTable, String otherField) {
        if (data.containsKey(thisField)) {
            throw new IllegalArgumentException("Field already exists on this table");
        }
        FieldData otherData = otherTable.data.get(otherField);
        data.put(thisField, otherData);
        foreignKeys.put(thisField, new Pair<>(otherField, otherTable));
    }

    /**
     * Returns a map that maps fields with their associated field in another table
     * @return the map containing the foreign keys
     */
    public Map<String, Pair<String, TableData>> getForeignKeys() {
        return foreignKeys;
    }

    /**
     * Add a field to the table
     * @param name The name of the field
     * @param type The type of the field
     * @param canBeNull If the field admits null values
     */
    public void addField(String name, String type, boolean canBeNull) {
        for (String field : data.keySet()) {
            if (field.equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("Field already exists on this table");
            }
        }
        data.put(name, new FieldData(type, canBeNull));
    }

    /**
     * Set a list of existing fields as the Primary Key
     * @param keys The name of the fields to be used as primary keys in conjunction
     */
    public void setPrimaryKeys(String... keys) {
        primaryKeys = getFieldSet(keys);
    }

    /**
     * Returns a set of existing fields marked as Primary Key
     * @return the name of the primary key fields
     */
    public Set<String> getPrimaryKeys() {
        return primaryKeys;
    }

    /**
     * Set a list of existing fields as unique
     * @param fields The name of the fields to be set as unique in conjunction
     */
    public void addUniqueConstraint(String... fields) {
        uniqueKeys.add(getFieldSet(fields));
    }

    /**
     * Returns a list of all the sets of fields marked as unique
     * @return the list of unique sets of fields
     */
    public List<Set<String>> getUniques() {
        return uniqueKeys;
    }

    private Set<String> getFieldSet(String... rawFields) {
        Set<String> fields = ConcurrentHashMap.newKeySet();
        for (String field : rawFields) {
            if (!data.containsKey(field)) {
                throw new IllegalArgumentException("Field " + field + " does not exist");
            }
            fields.add(field);
        }
        return fields;
    }

    /**
     * Returns a set containing the names of the columns in this table
     * @return the names of the columns
     */
    public Set<String> getFields() {
        return data.keySet();
    }

    /**
     * Returns the name of the table, without any prefixes
     * @return the name of the table
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the SQL query used to create the table associated with this object
     * @param prefix the prefix to prepend to the table name
     * @return the SQL query
     */
    public String getCreateString(String prefix) {
        if (prefix == null) {
            prefix = "";
        }
        StringBuilder createString = new StringBuilder("CREATE TABLE IF NOT EXISTS `" + prefix + name + "` (");
        for (String field : data.keySet()) {
            createString.append(getCreateField(field)).append(", ");
        }
        createString.append("CONSTRAINT PK_").append(name).append(" PRIMARY KEY (").append(String.join(",", primaryKeys)).append(")");
        for (int i = 0; i < uniqueKeys.size(); i++) {
            createString.append(", CONSTRAINT UC_").append(name).append(i).append(" UNIQUE (").append(String.join(",", uniqueKeys.get(i))).append(")");
        }
        for (Map.Entry<String, Pair<String, TableData>> entry : foreignKeys.entrySet()) {
            String localField = entry.getKey();
            Pair<String, TableData> foreignField = entry.getValue();
            createString.append(", CONSTRAINT FK_").append(name).append(localField).append(" FOREIGN KEY (").append(localField).append(")");
            createString.append(" REFERENCES").append(foreignField.getSecond().name).append("(").append(foreignField.getFirst()).append(")");
        }
        createString.append(") DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;");

        return createString.toString();
    }

    private String getCreateField(String name) {
        FieldData field = data.get(name);
        String fieldCreate = "`" + name + "` " + field.type;
        if (!field.canBeNull) {
            fieldCreate += " NOT NULL";
        }
        return fieldCreate;
    }

    private static class FieldData {
        private final String type;
        private final boolean canBeNull;

        private FieldData(String type, boolean canBeNull) {
            this.type = type;
            this.canBeNull = canBeNull;
        }
    }
}
