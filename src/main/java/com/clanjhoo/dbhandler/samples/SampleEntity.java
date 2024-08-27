package com.clanjhoo.dbhandler.samples;

import com.clanjhoo.dbhandler.annotations.DataField;
import com.clanjhoo.dbhandler.annotations.Entity;
import com.clanjhoo.dbhandler.annotations.PrimaryKey;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// All classes you want to store have to be marked with @Entity
// With @Entity(table = "something") you change the name of the table in which
// the objects of this class will be stored (defaults to the name of the class)
@Entity(table = "thebath")
public class SampleEntity implements Serializable, Comparable<SampleEntity> {
    // static fields will be ignored
    public static float flotacion = 2;
    public static final Set<SampleEntity> entities = ConcurrentHashMap.newKeySet();

    // this field will be marked as the primary key
    @PrimaryKey
    public UUID id;

    // transient fields will also be ignored
    public transient int extra;

    // this field will be stored with a custom name and a custom default value (doubles usually default to 0.0)
    @DataField(name = "salsa", value = "3.2")
    public double bolognesa;

    // fields don't need to be annotated with @DataField
    public int ravioliRavioli;

    // fields don't need to be public nor have getters/setters for this to work
    @DataField(value = "albricias")
    private String thyPassword;

    @Override
    @Contract(pure = true)
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(Object other) {
        if (!(other instanceof SampleEntity))
            return false;
        return id.equals(((SampleEntity) other).id);
    }

    @Override
    @Contract(pure = true)
    public int compareTo(@NotNull SampleEntity o) {
        return id.compareTo(o.id);
    }
}
