package com.paradox;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Gives JDBC connections to the paradox_db database.
 *
 * The settings are read once from config/database.properties so that no
 * password is written inside the source code.
 */
public class DatabaseConnection {

    private static final Properties SETTINGS = loadSettings();

    private static Properties loadSettings() {
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream("config/database.properties")) {
            properties.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Could not read config/database.properties. "
                    + "Copy database.properties.example and enter your MySQL password.", e);
        }
        return properties;
    }

    /** Returns a fresh connection; the caller must close it. */
    public static Connection getConnection() throws SQLException {
        String url = SETTINGS.getProperty("db.url");
        String user = SETTINGS.getProperty("db.user");
        String password = SETTINGS.getProperty("db.password");
        return DriverManager.getConnection(url, user, password);
    }
}
