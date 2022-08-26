package com.clanjhoo.dbhandler.samples;

import com.clanjhoo.dbhandler.annotations.DataField;
import com.clanjhoo.dbhandler.annotations.Entity;
import com.clanjhoo.dbhandler.annotations.PrimaryKey;
import com.clanjhoo.dbhandler.data.DBObject;

import java.util.UUID;

@Entity(table = "thebath")
public class MyEntity extends DBObject {
    // static fields will be ignored
    public static float flotacion = 2;

    // this field will be marked as the primary key
    @PrimaryKey
    public UUID id;

    // transient fields won't be stored
    public transient int extra;

    // this field will be stored with a custom name
    @DataField(name = "salsa", value = "3.2")
    public double bolognesa;

    // fields don't need to be public nor have getters/setters for this to work
    @DataField(value = "albricias")
    private String thyPassword;
}
