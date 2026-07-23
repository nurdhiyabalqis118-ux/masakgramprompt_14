package edu.utem.ftmk.masakgram.llm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    // 1. Tukar 'nama_database_member1' kepada nama database yang kau cipta kat phpMyAdmin tadi
    private static final String URL = "jdbc:mysql://localhost:3306/masakgram_db";
    
    // 2. Default username untuk XAMPP ialah "root"
    private static final String USER = "root"; 
    
    // 3. Default password untuk XAMPP ialah kosong/tiada password
    private static final String PASSWORD = ""; 

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL Driver tidak dijumpai: " + e.getMessage());
        }
    }
}