package com.clanjhoo.dbhandler.utils;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class KeyedLock {
    ConcurrentHashMap<List<Serializable>, Lock> lockMap;

    public KeyedLock(){
        lockMap = new ConcurrentHashMap<>();
    }

    public @NotNull Lock getLock(List<Serializable> keys) {
        Lock initValue = new ReentrantLock();
        Lock lock = lockMap.putIfAbsent(keys, initValue);
        if (lock == null) {
            lock = initValue;
        }
        return lock;
    }
}
