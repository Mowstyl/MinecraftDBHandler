package com.clanjhoo.dbhandler.data;

/**
 * The currently supported storage types
 */
public enum StorageType {
    /**
     * Store the data in json format. Tables will be folders and rows will be json files
     */
    JSON,
    /**
     * Store the data in a MariaDB database.
     */
    MARIADB,
    /**
     * Store the data in a MySQL database.
     */
    MYSQL;
}
