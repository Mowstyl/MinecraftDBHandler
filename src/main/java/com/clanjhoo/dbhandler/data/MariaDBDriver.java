package com.clanjhoo.dbhandler.data;

import com.clanjhoo.dbhandler.utils.TriFunction;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

class MariaDBDriver<T> implements DatabaseDriver<T> {
    private final Logger logger;
    private final HikariConfig config;
    private final HikariDataSource dataSource;
    private final String prefix;
    private final DBObjectManager<T> manager;

    /**
     * Instantiates a new JSON Driver object. Used when StorageType.MARIADB or StorageType.MYSQL is selected when instantiating DBObjectManager
     * @param plugin The plugin that has created the object. This will be passed automatically by DBObjectManager constructor
     * @param manager The DBObjectManager that is using this driver. This will be passed automatically by DBObjectManager constructor
     * @param host The address of the SQL server. Must be passed in the config array of the DBObjectManager constructor
     * @param port The port the SQL server is listening to. Must be passed in the config array of the DBObjectManager constructor
     * @param database The name of the database to use. Must be passed in the config array of the DBObjectManager constructor
     * @param username The user of the database the driver will use. Must be passed in the config array of the DBObjectManager constructor
     * @param password The password of the specified user. Must be passed in the config array of the DBObjectManager constructor
     * @param prefix The prefix to add to the name of all tables. Must be passed in the config array of the DBObjectManager constructor
     * @see DBObjectManager#DBObjectManager(Class clazz, JavaPlugin plugin, StorageType type, TriFunction eventFactory, Predicate saveCondition, int inactiveTime, Object... config)
     */
    MariaDBDriver(@NotNull JavaPlugin plugin, @NotNull DBObjectManager<T> manager, @NotNull String host, int port, @NotNull String database, @NotNull String username, @NotNull String password, @NotNull String prefix) {
        this.logger = plugin.getLogger();
        this.prefix = prefix;
        this.manager = manager;
        this.config = new HikariConfig();
        this.config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        this.config.setUsername(username);
        this.config.setPassword(password);
        this.config.addDataSourceProperty("cachePrepStmts", "true");
        this.config.addDataSourceProperty("prepStmtCacheSize", "250");
        this.config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        this.dataSource = new HikariDataSource(config);
    }

    // DO NOT CALL DIRECTLY
    private Object prepareStatement(@NotNull Connection conn, Function<PreparedStatement, Object> function, final String query, Object... vars) {
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            for (int i = 0; i < vars.length; i++) {
                if (vars[i] instanceof UUID) {
                    vars[i] = vars[i].toString();
                }
                if (vars[i] instanceof String) {
                    ps.setString(i + 1, (String) vars[i]);
                }
                if (vars[i] instanceof Byte) {
                    ps.setByte(i + 1, (byte) vars[i]);
                }
                else if (vars[i] instanceof Short) {
                    ps.setShort(i + 1, (short) vars[i]);
                }
                else if (vars[i] instanceof Integer) {
                    ps.setInt(i + 1, (int) vars[i]);
                }
                else if (vars[i] instanceof Long) {
                    ps.setLong(i + 1, (long) vars[i]);
                }
                else if (vars[i] instanceof Float) {
                    ps.setFloat(i + 1, (float) vars[i]);
                }
                else if (vars[i] instanceof Double) {
                    ps.setDouble(i + 1, (double) vars[i]);
                }
                else{
                    ps.setObject(i + 1, vars[i]);
                }
            }

            return function.apply(ps);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "MySQL error");
            e.printStackTrace();
        }

        return null;
    }

    private int update(@NotNull Connection connection, final String query, final Object... vars) throws SQLException {
        Object result = prepareStatement(connection, (ps) -> {
            try {
                return ps.executeUpdate();
            } catch (SQLException ex) {
                if (ex.getErrorCode() == 1060) {
                    return -1;
                }
                return ex;
            }
        }, query, vars);
        if (result instanceof SQLException) {
            throw (SQLException) result;
        }
        return (int) result;
    }

    private int update(final String query, final Object... vars) throws SQLException {
        Connection connection = dataSource.getConnection();
        if (connection == null) {
            logger.log(Level.WARNING, "Could not get the connection!");
            return -1;
        }

        int result = update(connection, query, vars);

        try {
            connection.close();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error closing connection");
            e.printStackTrace();
        }

        return result;
    }

    private boolean execute(@NotNull Connection connection, final String query, final Object... vars) throws SQLException {
        Object result = prepareStatement(connection, (ps) -> {
            try {
                ps.execute();
                return true;
            } catch (SQLException ex) {
                if (ex.getErrorCode() == 1060) {
                    return false;
                }
                return ex;
            }
        }, query, vars);
        if (result instanceof SQLException) {
            throw (SQLException) result;
        }
        return (boolean) result;
    }

    private boolean execute(final String query, final Object... vars) throws SQLException {
        Connection connection = dataSource.getConnection();
        if (connection == null) {
            logger.log(Level.WARNING, "Could not get the connection!");
            return false;
        }

        boolean result = execute(connection, query, vars);

        try {
            connection.close();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error closing connection");
            e.printStackTrace();
        }

        return result;
    }

    private <E> E query(@NotNull Connection connection, final String query, Function<ResultSet, E> function, final Object... vars) throws SQLException {
        Object result = prepareStatement(connection, (ps) -> {
            try (ResultSet rs = ps.executeQuery()) {
                return function.apply(rs);
            } catch (SQLException ex) {
                return ex;
            }
        }, query, vars);
        if (result instanceof SQLException) {
            throw (SQLException) result;
        }
        return (E) result;
    }

    private <E> E query(final String query, Function<ResultSet, E> function, final Object... vars) throws SQLException {
        Connection connection = dataSource.getConnection();
        if (connection == null) {
            logger.log(Level.WARNING, "Could not get the connection!");
            return null;
        }

        E result = query(connection, query, function, vars);

        try {
            connection.close();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error closing connection");
            e.printStackTrace();
        }

        return result;
    }

    private String getSQLConditionKey() {
        String[] pKeyNames = manager.getTableData().getPrimaryKeys().toArray(new String[0]);
        String[] pKeyConds = new String[pKeyNames.length];
        for (int i = 0; i < pKeyNames.length; i++) {
            Class<?> clazz = manager.getType(pKeyNames[i]);
            String compOp = "=";
            if (UUID.class.isAssignableFrom(clazz) || String.class.isAssignableFrom(clazz)) {
                compOp = "LIKE";
            }
            pKeyConds[i] = "`" + pKeyNames[i] + "` " + compOp + " ?";
        }
        return String.join(" AND ", pKeyConds);
    }

    @Override
    public boolean contains(@NotNull String table, @NotNull Serializable[] ids) throws SQLException {
        String condKey = getSQLConditionKey();
        String sqlQuery = "SELECT COUNT(*) FROM (SELECT * FROM `" + prefix + table + "` WHERE " + condKey + " LIMIT 1) s;";
        // logger.log(Level.INFO, "Exists?");
        return Boolean.TRUE.equals(query(sqlQuery, (rs) -> {
            boolean res = false;
            try {
                if (rs != null && rs.next()) {
                    res = rs.getInt(1) == 1;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "MySQL error checking existence in " + prefix + table);
                e.printStackTrace();
            }
            return res;
        }, (Object[]) ids));
    }

    @Override
    public @NotNull T loadData(@NotNull String table, @NotNull Serializable[] ids) throws SQLException, ReflectiveOperationException {
        String[] pKeyNames = manager.getTableData().getPrimaryKeys().toArray(new String[0]);
        if (ids.length != pKeyNames.length) {
            throw new IllegalArgumentException("You must specify a value for each primary key defined for the object");
        }
        Arrays.sort(pKeyNames);
        String condKey = getSQLConditionKey();
        SQLException[] exception = new SQLException[]{null};
        String sqlQuery = "SELECT * FROM `" + prefix + table + "` WHERE " + condKey + " LIMIT 1;";
        // logger.log(Level.INFO, "Load Query");
        Map<String, Serializable> data = new HashMap<>();
        query(sqlQuery, (rs) -> {
            try {
                if (rs != null && rs.next()) {
                    for (String field : manager.getTableData().getFields()) {
                        Serializable item;
                        Class<?> type = manager.getType(field);
                        if (byte.class.equals(type) || Byte.class.isAssignableFrom(type)) {
                            item = rs.getByte(field);
                        }
                        else if (short.class.equals(type) || Short.class.isAssignableFrom(type)) {
                            item = rs.getShort(field);
                        }
                        else if (int.class.equals(type) || Integer.class.isAssignableFrom(type)) {
                            item = rs.getInt(field);
                        }
                        else if (long.class.equals(type) || Long.class.isAssignableFrom(type)) {
                            item = rs.getLong(field);
                        }
                        else if (float.class.equals(type) || Float.class.isAssignableFrom(type)) {
                            item = rs.getFloat(field);
                        }
                        else if (double.class.equals(type) || Double.class.isAssignableFrom(type)) {
                            item = rs.getDouble(field);
                        }
                        else if (boolean.class.equals(type) || Boolean.class.isAssignableFrom(type)) {
                            item = rs.getBoolean(field);
                        }
                        else if (char.class.equals(type) || Character.class.isAssignableFrom(type)) {
                            item = rs.getString(field).charAt(0);
                        }
                        else if (String.class.isAssignableFrom(type)) {
                            item = rs.getString(field);
                        }
                        else if (UUID.class.isAssignableFrom(type)) {
                            String rawUUID = rs.getString(field);
                            item = rawUUID != null ? UUID.fromString(rawUUID) : null;
                        }
                        else{
                            item = (Serializable) rs.getObject(field);
                        }

                        data.put(field, item);
                    }
                }
            } catch (SQLException e) {
                exception[0] = e;
            }
            return data;
        }, (Object[]) ids);
        if (exception[0] != null) {
            throw exception[0];
        }

        T dbObject = manager.getInstance(data, false);
        for (int i = 0; i < ids.length; i++) {
            manager.setValue(dbObject, pKeyNames[i], ids[i]);
        }
        return dbObject;
    }

    @Override
    public boolean createTable(TableData table) {
        try {
            return execute(table.getCreateString(prefix));
        } catch (SQLException e) {
            logger.log(Level.WARNING, "SQLException while creating table " + table.getName());
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean dropTable(String table) {
        try {
            return execute("DROP TABLE IF EXISTS `" + prefix + table + "`;");
        } catch (SQLException e) {
            logger.log(Level.WARNING, "SQLException while dropping table " + table);
            e.printStackTrace();
        }
        return false;
    }

    private boolean saveData(Connection connection, @NotNull String table, @NotNull T item) throws ReflectiveOperationException {
        String[] fields = manager.getTableData().getFields().toArray(new String[0]);
        final String sqlQuery = "INSERT INTO `" + prefix + table
                + "` (`" + String.join("`, `", fields) + "`) VALUES ("
                + String.join(", ", Collections.nCopies(fields.length, "?"))
                + ") ON DUPLICATE KEY UPDATE `" +
                String.join("` = ?, `", fields) + "` = ?;";
        Object[] fieldData = new Serializable[fields.length*2];
        for (int i = 0; i < fields.length; i++) {
            Serializable data = manager.getValue(item, fields[i]);
            if (data instanceof UUID) {
                data = data.toString();
            }
            fieldData[i] = data;
            fieldData[i + fields.length] = data;
        }
        try {
            if (connection == null) {
                return execute(sqlQuery, fieldData);
            } else {
                return execute(connection, sqlQuery, fieldData);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "SQLException while saving item to table " + table);
            e.printStackTrace();
        }
        return false;
    }

    private boolean deleteData(Connection connection, @NotNull String table, @NotNull T item) throws ReflectiveOperationException {
        String[] pKeyNames = manager.getTableData().getPrimaryKeys().toArray(new String[0]);
        Arrays.sort(pKeyNames);
        Serializable[] ids = new Serializable[pKeyNames.length];
        for (int i = 0; i < pKeyNames.length; i++) {
            ids[i] = manager.getValue(item, pKeyNames[i]);
        }
        String condKey = getSQLConditionKey();
        String sqlQuery = "DELETE FROM `" + prefix + table + "` WHERE " + condKey + ";";
        int res = -1;
        try {
            if (connection == null) {
                res = update(sqlQuery, (Object[]) ids);
            } else {
                res = update(connection, sqlQuery, (Object[]) ids);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "SQLException while deleting item from table " + table);
            e.printStackTrace();
        }

        return res == 1;
    }

    @Override
    public boolean saveData(@NotNull String table, @NotNull T item) throws ReflectiveOperationException {
        return saveData(null, table, item);
    }

    @Override
    public Map<List<Serializable>, Boolean> saveData(@NotNull String table, @NotNull List<T> items) throws ReflectiveOperationException {
        Map<List<Serializable>, Boolean> results = new HashMap<>();
        if (items.isEmpty()) {
            return results;
        }
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
        }
        catch (SQLException ex) {
            logger.log(Level.WARNING, "Could not get the connection!");
            return results;
        }
        String[] keyNames = manager.getTableData().getPrimaryKeys().toArray(new String[0]);
        Arrays.sort(keyNames);
        Serializable[] keys = new Serializable[keyNames.length];
        for (T item : items) {
            for (int i = 0; i < keyNames.length; i++) {
                keys[i] = manager.getValue(item, keyNames[i]);
            }
            results.put(Arrays.asList(keys), saveData(connection, table, item));
        }
        try {
            connection.close();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error while closing connection after saving item list");
            e.printStackTrace();
        }
        return results;
    }

    @Override
    public boolean deleteData(@NotNull String table, @NotNull T item) throws ReflectiveOperationException {
        return deleteData(null, table, item);
    }

    @Override
    public Map<List<Serializable>, Boolean> deleteData(@NotNull String table, @NotNull List<T> items) throws ReflectiveOperationException {
        Map<List<Serializable>, Boolean> results = new HashMap<>();
        if (items.isEmpty()) {
            return results;
        }
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
        }
        catch (SQLException ex) {
            logger.log(Level.WARNING, "Could not get the connection!");
            return results;
        }
        String[] keyNames = manager.getTableData().getPrimaryKeys().toArray(new String[0]);
        Arrays.sort(keyNames);
        Serializable[] keys = new Serializable[keyNames.length];
        for (T item : items) {
            for (int i = 0; i < keyNames.length; i++) {
                keys[i] = manager.getValue(item, keyNames[i]);
            }
            results.put(Arrays.asList(keys), deleteData(connection, table, item));
        }
        try {
            connection.close();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error while closing connection after saving item list");
            e.printStackTrace();
        }
        return results;
    }
}
