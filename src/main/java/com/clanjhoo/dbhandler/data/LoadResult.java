package com.clanjhoo.dbhandler.data;

/**
 * The possible results of a load data task
 */
public enum LoadResult {
    /**
     * The task finished successfully
     */
    SUCCESS,
    /**
     * The task finished with an exception and the data could not be loaded
     */
    ERROR;
}
