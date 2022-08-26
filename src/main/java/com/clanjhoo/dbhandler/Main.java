package com.clanjhoo.dbhandler;

import com.clanjhoo.dbhandler.samples.MyEntity;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        Map<String, Serializable> data = new HashMap<>();
        data.put("salsa", 2.4);
        data.put("thyPassword", "aaaa");
        MyEntity entity = MyEntity.loadData(MyEntity.class, data);
        entity.printData();
    }
}
