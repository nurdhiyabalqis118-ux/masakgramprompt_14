package edu.utem.ftmk.masakgram.util;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class TranscriptImporter {
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/masakgram_db";
    private static final String DB_USER = "root";
    private static final String DB_PASS = ""; 

    public static void main(String[] args) {
        // Lokasi folder transkrip korang
        String folderPath = "C:/Users/alyag/eclipse-DADLABTEST/transcriber/transcriptions"; 
        File folder = new File(folderPath);
        File[] listOfFiles = folder.listFiles();

        if (listOfFiles == null) {
            System.err.println("Error: Folder tidak dijumpai atau path salah!");
            return;
        }

        String sql = "INSERT INTO transcript (reel_id, audio_id, file_name, file_path, file_format) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            System.out.println("Memulakan proses import...");

            for (File file : listOfFiles) {
                if (file.getName().endsWith(".txt")) {
                    try {
                        // Hardcoded ID 1 sebab korang ada 1 reel induk
                        int reelId = 1; 
                        int audioId = 1;
                        
                        pstmt.setInt(1, reelId);
                        pstmt.setInt(2, audioId);
                        pstmt.setString(3, file.getName());
                        pstmt.setString(4, file.getAbsolutePath());
                        pstmt.setString(5, "txt");
                        
                        pstmt.executeUpdate();
                        System.out.println("Berjaya import: " + file.getName());
                        
                    } catch (SQLException e) {
                        System.err.println("Gagal import fail " + file.getName() + ": " + e.getMessage());
                    }
                }
            }
            System.out.println("=================================================");
            System.out.println("Selesai! Proses import transkrip tamat.");
            System.out.println("=================================================");
            
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}