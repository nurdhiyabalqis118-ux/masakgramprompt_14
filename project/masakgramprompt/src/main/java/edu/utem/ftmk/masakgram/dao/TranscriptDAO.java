package edu.utem.ftmk.masakgram.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TranscriptDAO {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/masakgram_db";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    public List<String[]> getAllTranscripts() {
        List<String[]> list = new ArrayList<>();
        // Ambil data terus dari column yang sepatutnya
        String sql = "SELECT transcript_id, file_name FROM transcript";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                list.add(new String[] {
                    rs.getString("transcript_id"), // Reel ID
                    "",                            // Influencer Name (Akan di-override oleh DashboardView)
                    "N/A",                         // Instagram Code
                    rs.getString("file_name"),     // Reel URL/File Name
                    "",                            // Identified By (Akan di-override oleh DashboardView)
                    "2026-06-26"                   // Date
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}