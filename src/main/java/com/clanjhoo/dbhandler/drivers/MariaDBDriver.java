package com.clanjhoo.dbhandler.drivers;

import com.clanjhoo.dbhandler.data.DBObject;
import com.clanjhoo.dbhandler.data.TableData;
import com.clanjhoo.dbhandler.utils.Pair;
import org.bukkit.plugin.java.JavaPlugin;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Serializable;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MariaDBDriver<T extends DBObject> implements DatabaseDriver<T> {
    private final String host, database, username, password, prefix;
    private final int port;
    private final Logger logger;
    private final T sample;

    public MariaDBDriver(JavaPlugin plugin, T sample, String host, int port, String database, String username, String password, String prefix) {
        new org.mariadb.jdbc.Driver();
        this.logger = plugin.getLogger();
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.prefix = prefix;
        this.sample = sample;
    }

    // DO NOT CALL DIRECTLY, USE getConnection instead
    private static Connection openConnection(String host, int port, String database, String username, String password) throws SQLException {
        new org.mariadb.jdbc.Driver();
        return DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database, username, password);
    }

    private Connection getConnection() {
        Connection connection;
        try {
            connection = openConnection(this.host, this.port, this.database, this.username, this.password);
        }
        catch (SQLException e) {
            logger.log(Level.INFO, "Could NOT connect to MariaDB!");
            e.printStackTrace();
            return null;
        }

        try (PreparedStatement stmt = connection.prepareStatement("SELECT 1")) {
            stmt.execute();
        }
        catch (SQLException e) {
            logger.log(Level.INFO, "MySQL SELECT 1 failed. Reconnecting");
            try {
                connection = openConnection(this.host, this.port, this.database, this.username, this.password);
            }
            catch (SQLException e1) {
                logger.log(Level.WARNING, "Couldn't reconnect to MySQL!");
                e1.printStackTrace();
                return null;
            }
        }

        return connection;
    }

    // DO NOT CALL DIRECTLY
    private PreparedStatement prepareStatement(@NotNull Connection conn, @Language("sql") String query, Object... vars) {
        try {
            PreparedStatement ps = conn.prepareStatement(query);

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

            return ps;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "MySQL error");
            e.printStackTrace();
        }

        return null;
    }

    private boolean execute(@NotNull Connection connection, @Language("sql") final String query, final Object... vars) {
        try (PreparedStatement ps = prepareStatement(connection, query, vars)) {
            assert ps != null;
            ps.execute();
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() == 1060) {
                return false;
            }
            logger.log(Level.WARNING, "MySQL error");
            e.printStackTrace();
        }
        finally {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error closing connection");
                e.printStackTrace();
            }
        }
        return false;
    }

    private boolean execute(@Language("sql") final String query, final Object... vars) {
        Connection connection = getConnection();
        if (connection == null) {
            logger.log(Level.WARNING, "Could not get the connection!");
            return false;
        }

        return execute(connection, query, vars);
    }

    private <E> E query(@NotNull Connection connection, @Language("sql") final String query, Function<ResultSet, E> function, final Object... vars) {
        E result = null;

        try {
            try (PreparedStatement ps = prepareStatement(connection, query, vars)) {
                assert ps != null;

                try (ResultSet rs = ps.executeQuery()) {
                    result = function.apply(rs);
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "MySQL error");
            e.printStackTrace();
        }
        finally {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error closing connection");
                e.printStackTrace();
            }
        }

        return result;
    }

    private <E> E query(@Language("sql") final String query, Function<ResultSet, E> function, final Object... vars) {
        Connection connection = getConnection();
        if (connection == null) {
            logger.log(Level.WARNING, "Could not get the connection!");
            return null;
        }
        /*
        logger.log(Level.WARNING, "query sql: " + query);
        if (vars != null) {
            for (int i = 0; i < vars.length; i++) {
                logger.log(Level.WARNING, "query data " + i + ": " + vars[i].toString());
            }
        }
         */

        return query(connection, query, function, vars);
    }

    private Pair<String, Serializable[]> getSQLConditionKey(@NotNull T item) {
        String[] pKeyNames = item.getPrimaryKeyName();
        String[] pKeyConds = new String[pKeyNames.length];
        Serializable[] pKeyValues = new Serializable[pKeyNames.length];
        for (int i = 0; i < pKeyNames.length; i++) {
            Serializable pKeyValue = item.getFieldValue(pKeyNames[i]);
            String compOp = "=";
            if (pKeyValue instanceof UUID) {
                pKeyValue = pKeyValue.toString();
            }
            if (pKeyValue instanceof String) {
                compOp = "LIKE";
            }
            pKeyConds[i] = "`" + pKeyNames[i] + "` " + compOp + " ?";
            pKeyValues[i] = pKeyValue;
        }
        return new Pair<>(String.join(" AND ", pKeyConds), pKeyValues);
    }

    @Override
    public boolean contains(@NotNull String table, @NotNull Serializable[] ids) {
        Pair<String, Serializable[]> condKeyVal = getSQLConditionKey(sample);
        @Language("sql") String sqlQuery = "SELECT COUNT(*) FROM (SELECT * FROM `" + prefix + table + "` WHERE " + condKeyVal.getFirst() + " LIMIT 1) s;";
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
    public T loadData(@NotNull String table, @NotNull Serializable[] ids, Function<Serializable[], T> defaultGenerator) throws IOException, SQLException {
        T item = defaultGenerator.apply(ids);
        String[] pKeyNames = item.getPrimaryKeyName();
        if (ids.length != pKeyNames.length) {
            throw new IllegalArgumentException("You must specify a value for each primary key defined for the object");
        }
        Arrays.sort(pKeyNames);
        for (int i = 0; i < ids.length; i++) {
            item.setFieldValue(pKeyNames[i], ids[i]);
        }
        Pair<String, Serializable[]> condKeyVal = getSQLConditionKey(item);
        SQLException[] exception = new SQLException[]{null};
        @Language("sql") String sqlQuery = "SELECT * FROM `" + prefix + table + "` WHERE " + condKeyVal.getFirst() + " LIMIT 1;";
        // logger.log(Level.INFO, "Load Query");
        T aux = query(sqlQuery, (rs) -> {
            T result = item;
            try {
                if (rs != null && rs.next()) {
                    for (String field : result.getFields()) {
                        Serializable original = result.getFieldValue(field);
                        Serializable data;
                        if (original instanceof UUID) {
                            data = UUID.fromString(rs.getString(field));
                        }
                        else if (original instanceof String) {
                            data = rs.getString(field);
                        }
                        else if (original instanceof Byte) {
                            data = rs.getByte(field);
                        }
                        else if (original instanceof Short) {
                            data = rs.getShort(field);
                        }
                        else if (original instanceof Integer) {
                            data = rs.getInt(field);
                        }
                        else if (original instanceof Long) {
                            data = rs.getLong(field);
                        }
                        else if (original instanceof Float) {
                            data = rs.getFloat(field);
                        }
                        else if (original instanceof Double) {
                            data = rs.getDouble(field);
                        }
                        else{
                            data = (Serializable) rs.getObject(field);
                        }

                        result.setFieldValue(field, data);
                        // logger.log(Level.WARNING, field + ": " + data.toString());
                    }
                }
                else {
                    // logger.log(Level.WARNING, "Data not found in " + prefix + table);
                    result = null;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "MySQL error getting data in " + prefix + table);
                exception[0] = e;
            }
            return result;
        }, (Object[]) condKeyVal.getSecond());
        if (exception[0] != null) {
            throw exception[0];
        }
        return aux;
    }

    @Override
    public boolean createTable(TableData table) {
        return execute(table.getCreateString(prefix));
    }

    @Override
    public boolean dropTable(String table) {
        return execute("DROP TABLE IF EXISTS `" + prefix + table + "`;");
    }

    private boolean saveData(Connection connection, @NotNull String table, @NotNull T item) {
        @Language("sql") String sqlQuery = "INSERT INTO `" + prefix + table + "` (`";
        String[] fields = item.getFields().toArray(new String[0]);
        sqlQuery += String.join("`, `", fields) + "`) VALUES (";
        sqlQuery += String.join(", ", Collections.nCopies(fields.length, "?")) + ") ON DUPLICATE KEY UPDATE `";
        sqlQuery += String.join("` = ?, `", fields) + "` = ?;";
        Object[] fieldData = new Serializable[fields.length*2];
        for (int i = 0; i < fields.length; i++) {
            Serializable data = item.getFieldValue(fields[i]);
            if (data instanceof UUID) {
                data = data.toString();
            }
            fieldData[i] = data;
            fieldData[i + fields.length] = data;
        }
        if (connection == null) {
            return execute(sqlQuery, fieldData);
        }
        else {
            return execute(connection, sqlQuery, fieldData);
        }
    }

    @Override
    public boolean saveData(@NotNull String table, @NotNull T item) {
        Connection connection = getConnection();
        if (connection == null) {
            logger.log(Level.WARNING, "Could not get the connection!");
            return false;
        }
        return saveData(connection, table, item);
    }

    @Override
    public Map<List<Serializable>, Boolean> saveData(@NotNull String table, @NotNull List<T> items) {
        Map<List<Serializable>, Boolean> results = new HashMap<>();
        if (items.size() == 0) {
            return results;
        }
        Connection connection = getConnection();
        if (connection == null) {
            logger.log(Level.WARNING, "Could not get the connection!");
            return results;
        }
        String[] keyNames = items.get(0).getPrimaryKeyName();
        Arrays.sort(keyNames);
        Serializable[] keys = new Serializable[keyNames.length];
        for (T item : items) {
            for (int i = 0; i < keyNames.length; i++) {
                keys[i] = item.getFieldValue(keyNames[i]);
            }
            results.put(Arrays.asList(keys), saveData(connection, table, item));
        }
        return results;
    }
}
