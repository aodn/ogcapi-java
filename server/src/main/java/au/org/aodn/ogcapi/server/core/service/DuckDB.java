package au.org.aodn.ogcapi.server.core.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DuckDB {
    private static Connection connection;

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("org.duckdb.DuckDBDriver");
                connection = DriverManager.getConnection("jdbc:duckdb:");
            } catch (ClassNotFoundException e) {
                throw new SQLException("DuckDB Driver not found", e);
            }
        }
        return connection;
    }
}
