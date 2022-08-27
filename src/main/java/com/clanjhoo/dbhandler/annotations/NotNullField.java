package com.clanjhoo.dbhandler.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a field as not null. This annotation will only be used when creating the database.
 * It won't perform any checks on the field, the user is in charge of doing so. If trying to save an item
 * with this field set to null, an exception might be raised.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NotNullField {
    
}
