package com.clanjhoo.dbhandler.data;


import java.lang.reflect.Field;

class FieldData {
    final boolean isPrimary;
    final String name;
    final Object defaultValue;
    final Field field;
    final boolean nullable;

    FieldData(boolean isPrimary, String name, Object defaultValue, Field field, boolean nullable) {
        this.isPrimary = isPrimary;
        this.name = name;
        this.defaultValue = defaultValue;
        this.field = field;
        this.nullable = nullable;
    }
}
