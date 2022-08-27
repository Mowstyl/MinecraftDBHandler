package com.clanjhoo.dbhandler.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a class as available to be used with DBObjectManagers
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Entity {
    /**
     * Name of the table in which instances of the marked entity will be stored. If not set defaults to class name
     * @return The name of the table. Defaults to ""
     */
    String table() default "";
}
