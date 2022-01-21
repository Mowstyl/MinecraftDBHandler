package com.clanjhoo.dbhandler.data;

import com.clanjhoo.dbhandler.utils.Pair;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TableData {
    private static final Map<String, TableData> tableMap = new ConcurrentHashMap<>();

    private final String name;
    private Set<String> primaryKeys;
    private final List<Set<String>> uniqueKeys;
    private final Map<String, FieldData> data;
    private final Map<String, Pair<String, TableData>> foreignKeys;


    public TableData(String name) {
        if (tableMap.containsKey(name)) {
            throw new IllegalArgumentException("Table already exists");
        }
        tableMap.put(name, this);
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
        FieldData field = data.put(name, new FieldData(type, canBeNull));
    }

    /**
     * Set a list of existing fields as the Primary Key
     * @param keys The name of the fields to be used as primary keys in conjunction
     */
    public void setPrimaryKeys(String... keys) {
        primaryKeys = getFieldSet(keys);
    }

    /**
     * Set a list of existing fields as the Primary Key
     * @param fields The name of the fields to be set as unique in conjunction
     */
    public void addUniqueConstraint(String... fields) {
        uniqueKeys.add(getFieldSet(fields));
    }

    private Set<String> getFieldSet(String... rawFields) {
        Set<String> fields = ConcurrentHashMap.newKeySet();
        for (String field : rawFields) {
            if (!data.containsKey(name)) {
                throw new IllegalArgumentException("Field doesn't exist");
            }
            fields.add(field);
        }
        return fields;
    }

    public Set<String> getFields() {
        return data.keySet();
    }

    public String getName() {
        return name;
    }

    public String getCreateString(String prefix) {
        if (prefix == null) {
            prefix = "";
        }
        String createString = "CREATE TABLE IF NOT EXISTS `" + prefix + name + "` (";
        for (String field : data.keySet()) {
            createString += getCreateField(field) + ", ";
        }
        createString += "CONSTRAINT PK_" + name + " PRIMARY KEY (" + String.join(",", primaryKeys) + ")";
        for (int i = 0; i < uniqueKeys.size(); i++) {
            createString += ", CONSTRAINT UC_" + name + i + " UNIQUE (" + String.join(",", uniqueKeys.get(i)) + ")";
        }
        for (Map.Entry<String, Pair<String, TableData>> entry : foreignKeys.entrySet()) {
            String localField = entry.getKey();
            Pair<String, TableData> foreignField = entry.getValue();
            createString += ", CONSTRAINT FK_" + name + localField + " FOREIGN KEY (" + localField + ")";
            createString += " REFERENCES" + foreignField.getSecond().name + "(" + foreignField.getFirst() + ")";
        }
        createString += ") DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci;";

        return createString;
    }

    private String getCreateField(String name) {
        FieldData field = data.get(name);
        String fieldCreate = "`" + name + "` " + field.type;
        if (field.canBeNull) {
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
