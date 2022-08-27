package com.clanjhoo.dbhandler.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a field or fields as unique. This annotation will only be used when creating the database.
 *  * It won't perform any checks on the field, the user is in charge of doing so. If trying to save an item
 *  * containing unique fields with the same values as other already stored item, an exception might be thrown.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface UniqueField {
    /**
     * Used when a field is unique when in conjunction with other field.
     * Two fields annotated with UniqueField and the same group value will be in the same group.
     * If left unset or set to "" the field will be unique by itself.
     * @return The name of the group this field belongs. Defaults to "" (no group)
     */
    String group() default "";
}
