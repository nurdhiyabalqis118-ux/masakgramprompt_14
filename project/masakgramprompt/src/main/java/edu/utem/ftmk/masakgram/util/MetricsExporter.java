package edu.utem.ftmk.masakgram.util;

import edu.utem.ftmk.masakgram.llm.DatabaseConnection;
import java.io.*;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class MetricsExporter {
    
    private static final String SQL_FILE_PATH = "/metrics_evaluation_queries.sql";
    private static final String EXPORT_DIR = "exported_layers/";

    public static void exportLayer(String layerLabel, String outputFileName) {
        File dir = new File(EXPORT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        Map<String, String> queries = loadQueries();
        String sql = queries.get(layerLabel.toUpperCase().trim());

        if (sql == null || sql.isEmpty()) {
            System.err.println("Query label not found or empty for: " + layerLabel);
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             FileWriter writer = new FileWriter(EXPORT_DIR + outputFileName)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Write CSV headers
            for (int i = 1; i <= columnCount; i++) {
                writer.append(metaData.getColumnName(i));
                if (i < columnCount) writer.append(",");
            }
            writer.append("\n");

            // Write rows
            int rowCount = 0;
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    Object value = rs.getObject(i);
                    String valStr = (value != null) ? value.toString().replace(",", " ").replace("\n", " ") : "";
                    writer.append(valStr);
                    if (i < columnCount) writer.append(",");
                }
                writer.append("\n");
                rowCount++;
            }
            
            System.out.println("Successfully exported " + rowCount + " rows to: " + outputFileName);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Map<String, String> loadQueries() {
        Map<String, String> queryMap = new HashMap<>();
        try (InputStream is = MetricsExporter.class.getResourceAsStream(SQL_FILE_PATH)) {
            if (is == null) {
                System.err.println("Could not find " + SQL_FILE_PATH + " in resources!");
                return queryMap;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String currentLabel = null;
            StringBuilder currentSql = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                
                // Look for labels like -- LAYER 1A or -- name: LAYER 1A
                if (trimmed.startsWith("-- LAYER") || trimmed.startsWith("-- name:")) {
                    if (currentLabel != null && currentSql.length() > 0) {
                        queryMap.put(currentLabel.toUpperCase().trim(), currentSql.toString().trim());
                        currentSql = new StringBuilder();
                    }
                    currentLabel = trimmed.replace("-- LAYER", "").replace("-- name:", "").trim();
                } else if (!trimmed.startsWith("--") && !trimmed.isEmpty()) {
                    currentSql.append(line).append(" ");
                }
            }
            if (currentLabel != null && currentSql.length() > 0) {
                queryMap.put(currentLabel.toUpperCase().trim(), currentSql.toString().trim());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return queryMap;
    }
}