package com.clanjhoo.dbhandler.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a field as foreign
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ForeignKey {
    /**
     * Name of the field of other table this one references
     * @return The name this field has in the other table
     */
    String name();
    /**
     * Name of the table containing the field referenced by this one
     * @return The name of the other table
     */
    String table();
}
