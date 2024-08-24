package com.clanjhoo.dbhandler.events;

import com.clanjhoo.dbhandler.data.LoadResult;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

/**
 * The asynchronous event that will be fired whenever a load data task finishes
 * @param <T> the type of the data associated with this event
 */
public abstract class LoadedDataEvent<T> extends Event {
    private final LoadResult result;
    private final T data;
    private final Exception exception;


    /**
     * Creates a new asynchronous event that will be fired whenever a load data task finishes
     * @param data The data that has been loaded (null if there was an Exception during the load)
     * @param exception The exception that has been thrown in case of error, null otherwise
     * @see Event#Event(boolean isAsync)
     */
    public LoadedDataEvent(@Nullable T data, @Nullable Exception exception) {
        super(true);
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
     * Returns the result of a data load task
     * @return The result of the data load task
     */
    public final LoadResult getResult() {
        return result;
    }

    /**
     * Returns the data that has been loaded
     * @return The data that has been loaded
     */
    public @Nullable T getData() {
        return data;
    }

    /**
     * Returns the exception thrown during the data load task
     * @return The exception that has been thrown
     */
    public @Nullable Exception getException() {
        return exception;
    }
}
