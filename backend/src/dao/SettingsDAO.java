package dao;

import config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Hospital information shown on the public page, stored in the database. */
public class SettingsDAO {

    public Map<String, String> getAll() throws SQLException {
        Map<String, String> values = new LinkedHashMap<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT setting_key, setting_value FROM hospital_settings");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) values.put(rs.getString(1), rs.getString(2));
        }
        return values;
    }

    public String get(String key) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(
                 "SELECT setting_value FROM hospital_settings WHERE setting_key = ?")) {
            st.setString(1, key);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public void putAll(Map<String, String> values) throws SQLException {
        String sql = """
            INSERT INTO hospital_settings (setting_key, setting_value) VALUES (?, ?)
            ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)
            """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                st.setString(1, entry.getKey());
                st.setString(2, entry.getValue());
                st.addBatch();
            }
            st.executeBatch();
        }
    }
}
