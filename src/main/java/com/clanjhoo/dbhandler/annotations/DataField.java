package com.clanjhoo.dbhandler.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to set any extra information to a field of a database Entity
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DataField {
    /**
     * Used to set the name this field will have on the database table. If not set defaults to field name
     * @return The name this field will have on the table. Defaults to ""
     */
    String name() default "";
    /**
     * Whether to force setting the default value or leaving the one written by the default constructor
     * @return true if we are enforcing the default value, false otherwise. Defaults to false
     */
    boolean enforceValue() default false;

    /**
     * Used to set the default value this field will have. When using it to set an empty String, set enforceValue to true
     * @return The default value of this field. Defaults to "" (will be ignored unless enforceValue is true)
     */
    String value() default "";

    /**
     * Used to set the sql type of this field. If not set it will be automatically calculated on runtime from the type of the field
     * @return The sql type of this field. Defaults to ""
     */
    String sqltype() default "";
}
