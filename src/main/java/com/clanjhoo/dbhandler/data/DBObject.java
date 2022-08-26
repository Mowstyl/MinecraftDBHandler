package com.clanjhoo.dbhandler.data;

import com.clanjhoo.dbhandler.annotations.*;
import com.clanjhoo.dbhandler.utils.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

public abstract class DBObject {
    private static boolean isLoaded = false;
    private static Class<? extends DBObject> meself = null;
    private static Map<String, FieldData> fieldDataList = null;
    private static TableData tableData = null;
    private static Map<String, Pair<String, String>> foreigns;

    private static class FieldData {
        private final boolean isPrimary;
        private final String name;
        private final Object defaultValue;
        private final Field field;

        private FieldData(boolean isPrimary, String name, Object defaultValue, Field field) {
            this.isPrimary = isPrimary;
            this.name = name;
            this.defaultValue = defaultValue;
            this.field = field;
        }
    }

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
        if (short.class.equals(type) || Short.class.isAssignableFrom(type)) {
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

    private static Map<String, FieldData> getFieldInfo() {
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
                    else {
                        defVal = getDefaultValue(type);
                    }
                    DataField dann = f.getAnnotation(DataField.class);
                    if (dann != null) {
                        if (dann.name().length() > 0) {
                            name = dann.name();
                        }
                        defVal = stringToSerializable(type, dann.value());
                        System.out.println(" ");
                    }
                    return new FieldData(f.isAnnotationPresent(PrimaryKey.class), name, defVal, f);
                }).collect(Collectors.toMap(fd -> fd.name, fd -> fd));
    }

    public static <T extends DBObject> void load(Class<T> clazz) {
        meself = clazz;

        String tableName = meself.getSimpleName();
        if (!meself.isAnnotationPresent(Entity.class)) {
            throw new IllegalArgumentException("The class needs to be annotated with Entity");
        }
        Entity entityAnn = meself.getAnnotation(Entity.class);
        if (!entityAnn.table().isEmpty()) {
            tableName = entityAnn.table();
        }
        tableData = TableData.getTableData(tableName);

        fieldDataList = getFieldInfo();
        fieldDataList.forEach((n, fd) -> {
            String type = null;
            if (fd.field.isAnnotationPresent(DataField.class)) {
                DataField dfAnn = fd.field.getAnnotation(DataField.class);
                if (!dfAnn.sqltype().isEmpty()) {
                    type = dfAnn.sqltype();
                }
            }
            if (type == null) {
                type = sqlTypeFromType(fd.field.getType());
            }
            tableData.addField(n, type, !fd.field.isAnnotationPresent(NotNullField.class));
        });

        tableData.setPrimaryKeys(fieldDataList.values().stream()
                .filter(fd -> fd.isPrimary)
                .map(fd -> fd.name)
                .sorted()
                .toArray(String[]::new));

        Map<String, Set<String>> groupedUniques = new HashMap<>();
        for (FieldData fd : fieldDataList.values()) {
            if (!fd.field.isAnnotationPresent(UniqueField.class)) {
                continue;
            }
            UniqueField uniqueAnn = fd.field.getAnnotation(UniqueField.class);
            String group = uniqueAnn.group();
            if (group.isEmpty()) {
                tableData.addUniqueConstraint(fd.name);
            }
            else {
                Set<String> groupSet = groupedUniques.getOrDefault(group, new HashSet<>());
                groupSet.add(fd.name);
                groupedUniques.put(group, groupSet);
            }
        }
        for (Set<String> uniqueSet : groupedUniques.values()) {
            tableData.addUniqueConstraint(uniqueSet.toArray(new String[0]));
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

        isLoaded = true;
    }

    public static void loadForeignKeys() {
        if (!isLoaded) {
            throw new IllegalStateException("You have to call load before executing any other method!");
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

    public static <T extends DBObject> T loadData(Class<T> clazz, Map<String, Serializable> data) {
        T instance = null;

        if (!isLoaded) {
            load(clazz);
        }
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
            for (String name : data.keySet()) {
                FieldData fd = fieldDataList.get(name);
                if (fd == null) {
                    throw new IllegalArgumentException("Field " + name + " doesn't exist");
                }
                Field field = fd.field;
                try {
                    /* Java 9+
                    if (field.canAccess(instance) || field.trySetAccessible()) {
                        field.set(instance, data.get(name));
                    } else {
                        throw new RuntimeException("Field " + name + " can't be accessed");
                    }
                     */
                    field.set(instance, data.get(name));
                } catch (IllegalAccessException ex) {
                    field.setAccessible(true);
                    field.set(instance, data.get(name));
                    // throw new RuntimeException("Illegal access that should never happen");
                }
            }
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            e.printStackTrace();
        }

        return instance;
    }

    public void printData() {
        load(this.getClass());
        Map<String, FieldData> thyFields = getFieldInfo();
        for (FieldData fieldData : thyFields.values()) {
            System.out.print(fieldData.name + " -> ");
            Field field = fieldData.field;
            if (fieldData.isPrimary)
                System.out.print("PK ");
            System.out.print(field.getType().getSimpleName() + " " + field.getName() + ": ");
            try {
                System.out.print(field.get(this));
            }
            catch (IllegalAccessException ex) {
                try {
                    field.setAccessible(true);
                    System.out.print(field.get(this));
                }
                catch (IllegalAccessException ex2) {
                    System.out.println("ROAD OF NOPES");
                }
            }
            System.out.println(" defaults to " + fieldData.defaultValue);
        }
    }

    /**
     * Return a string with the name of the table (without prefixes) used to store objects of this class
     * @return The name of the table associated with this class
     */
    public static @NotNull String getTableName() {
        if (!isLoaded) {
            throw new IllegalStateException("You have to call load before executing any other method!");
        }
        return tableData.getName();
    }

    /**
     * Return an array containing the name of the fields used as primary keys in conjunction (at least one element guaranteed)
     * @return The array with the name of the primary key fields, no repeated values
     */
    public static @NotNull String[] getPrimaryKeyName() {
        if (!isLoaded) {
            throw new IllegalStateException("You have to call load before executing any other method!");
        }
        return tableData.getPrimaryKeys().stream().sorted().toArray(String[]::new);
    }

    /**
     * Return the set of all the fields this object has (only persistent data fields, at least one field)
     * @return A set containing the field names
     */
    public static @NotNull Set<String> getFields() {
        if (!isLoaded) {
            throw new IllegalStateException("You have to call load before executing any other method!");
        }
        return fieldDataList.keySet();
    }

    /**
     * Return the value of a specified field
     * @param field The name of the field
     * @return The contents of the field
     * @throws IllegalArgumentException if the field does not exist or is of the wrong type
     */
    public Serializable getFieldValue(@NotNull String field) throws IllegalArgumentException, IllegalAccessException {
        if (!isLoaded) {
            load(this.getClass());
        }

        FieldData fd = fieldDataList.get(field);
        if (fd == null) {
            throw new IllegalArgumentException("Specified field doesn't exist");
        }

        Serializable res;
        try {
            res = (Serializable) fd.field.get(this);
        }
        catch (IllegalAccessException ex) {
            fd.field.setAccessible(true);
            res = (Serializable) fd.field.get(this);
        }

        return res;
    }

    /**
     * Get whether a certain field is nullable or not
     * @param field The name of the field
     * @return true if the field can contain null values, false otherwise
     * @throws IllegalArgumentException if the field does not exist
     */
    public static boolean isFieldNullable(@NotNull String field) throws IllegalArgumentException {
        if (!isLoaded) {
            throw new IllegalStateException("You have to call load before executing any other method!");
        }

        FieldData fd = fieldDataList.get(field);
        if (fd == null) {
            throw new IllegalArgumentException("Specified field doesn't exist");
        }

        return fd.field.isAnnotationPresent(NotNullField.class);
    }

    /**
     * Get the list of unique valued sets of fields (excluding the Primary Key)
     * @return The list of unique valued sets of fields
     */
    public static @NotNull List<Set<String>> getUniqueFields() {
        if (!isLoaded) {
            throw new IllegalStateException("You have to call load before executing any other method!");
        }

        return tableData.getUniques();
    }

    /**
     * Get a map that relates a field of this class to a pair of (other field name, other table name)
     * @return The specified map of foreign fields
     */
    public static @NotNull Map<String, Pair<String, TableData>> getForeignFields() {
        if (!isLoaded) {
            throw new IllegalStateException("You have to call load before executing any other method!");
        }

        return tableData.getForeignKeys();
    }

    /**
     * Get a string containing a valid SQL data type (can include collation when used with a string type) if you want your object to be stored on an SQL database
     * @param field The name of the field
     * @return The string with the SQL type of the specified field
     * @throws IllegalArgumentException if the field does not exist
     */
    public static @NotNull String getFieldType(@NotNull String field) throws IllegalArgumentException {
        if (!isLoaded) {
            throw new IllegalStateException("You have to call load before executing any other method!");
        }

        FieldData fd = fieldDataList.get(field);
        if (fd == null) {
            throw new IllegalArgumentException("Specified field doesn't exist");
        }

        String type = null;
        if (fd.field.isAnnotationPresent(DataField.class)) {
            DataField dfAnn = fd.field.getAnnotation(DataField.class);
            if (!dfAnn.sqltype().isEmpty()) {
                type = dfAnn.sqltype();
            }
        }
        if (type == null) {
            type = sqlTypeFromType(fd.field.getType());
        }
        return type;
    }


    /**
     * Set the value of the specified field to the specified value
     * @param field The name of the field
     * @param value The new value of the field
     * @throws IllegalArgumentException if the field does not exist, the value is of the wrong type or value is null and the field does not admit null values
     */
    public void setFieldValue(@NotNull String field, Serializable value) throws IllegalArgumentException, IllegalAccessException {
        if (!isLoaded) {
            load(this.getClass());
        }

        FieldData fd = fieldDataList.get(field);
        if (fd == null) {
            throw new IllegalArgumentException("Specified field doesn't exist");
        }

        try {
            fd.field.set(this, value);
        }
        catch (IllegalAccessException ex) {
            fd.field.setAccessible(true);
            fd.field.set(this, value);
        }
    }


    /**
     * Saves any DBObject persisten data to a Map relating field name with field value
     * @param object The object to save to a Map
     * @return The generated Map of name -> value
     */
    static @NotNull Map<String, Serializable> toMap(@NotNull DBObject object) throws IllegalAccessException {
        Map<String, Serializable> data = new HashMap<>();
        for (String field : object.getFields()) {
            data.put(field, object.getFieldValue(field));
        }
        return data;
    }
}
