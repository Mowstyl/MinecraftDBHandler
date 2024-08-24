package com.clanjhoo.dbhandler.samples;

import com.clanjhoo.dbhandler.events.LoadedDataEvent;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.List;


/**
 * An example on how to create the event that will be fired when a data load task finishes
 */
public class SampleLoadEvent extends LoadedDataEvent<SampleEntity> {

    private static final HandlerList handlers = new HandlerList();


    /**
     * Always call super constructor
     */
    public SampleLoadEvent(@NotNull List<Serializable> keys, @Nullable SampleEntity data, @Nullable Exception exception) {
        super(keys, data, exception);
    }

    /**
     * Should you need to always modify the default object before using it, do it in this method
     * @return the data that has been loaded (if not errored)
     */
    @Override
    @Nullable
    public SampleEntity getData() {
        return super.getData();
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
