package com.clanjhoo.dbhandler.events;

import com.clanjhoo.dbhandler.data.LoadResult;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.List;

/**
 * The asynchronous event that will be fired whenever a load data task finishes
 * @param <T> the type of the data associated with this event
 */
public abstract class LoadedDataEvent<T> extends Event {
    private final LoadResult result;
    private final T data;
    private final Exception exception;
    private final List<Serializable> keys;


    /**
     * Creates a new asynchronous event that will be fired whenever a load data task finishes
     * @param keys the primary key of the data being loaded
     * @param data the data that has been loaded (null if there was an Exception during the load)
     * @param exception the exception that has been thrown in case of error, null otherwise
     * @see Event#Event(boolean isAsync)
     */
    public LoadedDataEvent(@NotNull List<Serializable> keys, @Nullable T data, @Nullable Exception exception) {
        super(true);
        this.keys = keys;
        this.data = data;
        this.exception = exception;
        if (data != null) {
            result = LoadResult.SUCCESS;
        }
        else if (exception != null) {
            result = LoadResult.ERROR;
        }
        else {
            throw new IllegalArgumentException("No data loaded and no error thrown");
        }
    }

    /**
     * Returns the primary key of the data whose load task has finished
     * @return the list of values composing the primary key
     */
    @NotNull
    public final List<Serializable> getKeys() {
        return keys;
    }

    /**
     * Returns the result of a data load task
     * @return the result of the data load task
     */
    @NotNull
    public final LoadResult getResult() {
        return result;
    }

    /**
     * Returns the data that has been loaded
     * @return the data that has been loaded
     */
    @Nullable
    public T getData() {
        return data;
    }

    /**
     * Returns the exception thrown during the data load task
     * @return the exception that has been thrown
     */
    @Nullable
    public Exception getException() {
        return exception;
    }
}
