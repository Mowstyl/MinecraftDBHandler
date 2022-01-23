package com.clanjhoo.dbhandler.samples;

import com.clanjhoo.dbhandler.data.DBObject;
import com.clanjhoo.dbhandler.data.TableData;
import com.clanjhoo.dbhandler.utils.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.*;

public class SampleEconomyPlayer implements DBObject {
    private UUID playerId;
    private long currency;

    @Override
    public @NotNull String getTableName() {
        return "EconomyPlayer";
    }

    @Override
    public @NotNull String[] getPrimaryKeyName() {
        return new String[]{"uuid"};
    }

    @Override
    public @NotNull Set<String> getFields() {
        Set<String> fieldNames = new HashSet<>();
        fieldNames.add("uuid");
        fieldNames.add("currency");
        return fieldNames;
    }

    @Override
    public Serializable getFieldValue(@NotNull String field) throws IllegalArgumentException {
        switch (field) {
            case "uuid":
                return playerId;
            case "currency":
                return currency;
            default:
                throw new IllegalArgumentException("Unknown field: " + field);
        }
    }

    @Override
    public boolean isFieldNullable(@NotNull String field) throws IllegalArgumentException {
        return false;  // All our fields are non null
    }

    @Override
    public @NotNull List<Set<String>> getUniqueFields() {
        return new ArrayList<>();  // We have no unique fields other than the Primary Key
    }

    @Override
    public @NotNull Map<String, Pair<String, TableData>> getForeignFields() {
        return new HashMap<>();  // We have no Foreign Keys
    }

    @Override
    public @NotNull String getFieldType(@NotNull String field) throws IllegalArgumentException {
        switch (field) {
            case "uuid":
                return "VARCHAR(36)";  // UUIDs are 36 character strings
            case "currency":
                return "BIGINT";
            default:
                throw new IllegalArgumentException("Unknown field: " + field);
        }
    }

    @Override
    public void setFieldValue(@NotNull String field, Serializable value) throws IllegalArgumentException {
        switch (field) {
            case "uuid":
                if (value == null) {
                    throw new IllegalArgumentException("uuid can't be null");
                }
                if (value instanceof String) {
                    value = UUID.fromString((String) value);
                }
                if (value instanceof UUID) {
                    playerId = (UUID) value;
                }
                else {
                    throw new IllegalArgumentException("uuid can only be a valid java UUID");
                }
                break;
            case "currency":
                if (value == null) {
                    throw new IllegalArgumentException("currency can't be null");
                }
                if (value instanceof Number) {
                    long aux = ((Number) value).longValue();
                    if (aux != ((Number) value).doubleValue()) {
                        throw new IllegalArgumentException("currency can't have decimals");
                    }
                    currency = aux;
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown field: " + field);
        }
    }
}
