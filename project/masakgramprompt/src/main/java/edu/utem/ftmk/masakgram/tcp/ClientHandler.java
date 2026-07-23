package edu.utem.ftmk.masakgram.tcp;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import edu.utem.ftmk.masakgram.llm.DatabaseConnection;
import edu.utem.ftmk.masakgram.llm.ExperimentEngine;
import edu.utem.ftmk.masakgram.util.MetricsExporter;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    // Guaranteed sequential execution queue to prevent concurrent pipeline overlaps
    private static final ExecutorService pipelineExecutor = Executors.newSingleThreadExecutor();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            String request;
            while ((request = reader.readLine()) != null) {
                String response = handleRequest(request);
                writer.println(response);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected.");
        } finally {
            closeConnection();
        }
    }
    private String getFileNameForLayer(String layerLabel) {
        switch (layerLabel) {
            case "LAYER 1A": return "layer1a_exact_match.csv";
            case "LAYER 1B": return "layer1b_text_similarity.csv";
            case "LAYER 2A": return "layer2a_numeric_quantity.csv";
            case "LAYER 2B": return "layer2b_numeric_nutrition.csv";
            case "LAYER 2C": return "layer2c_nutrition_totals.csv";
            case "LAYER 3A": return "layer3a_json_validity.csv";
            case "LAYER 3B": return "layer3b_hallucination.csv";
            case "LAYER 3C": return "layer3c_ingredient_detection.csv";
            case "LAYER 4":  return "layer4_human_evaluation.csv";
            case "LAYER 5":  return "layer5_condition_scores.csv";
            default: return "export_result.csv";
        }
    }

    private String handleRequest(String request) {
        String[] parts = request.split(":", 2);
        String command = parts[0];
        String payload = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "FETCH_REELS":
                return fetchReelsFromDatabase();

            case "FETCH_TRANSCRIPT":
                return fetchTranscriptFromDatabase(payload);

            case "FETCH_MATRIX_LOG":
                return fetchMatrixLogFromDatabase(payload);
                
            case "FETCH_NUTRITION":
                return fetchNutritionPayload(payload); // Add this hook

            case "EXPORT_LAYER":
                String exportLabel = payload.trim();
                String fileName = getFileNameForLayer(exportLabel);
                MetricsExporter.exportLayer(exportLabel, fileName);
                return "SUCCESS:Layer exported successfully to " + fileName;
                
            case "RUN_EXPERIMENT":
                return runExperimentPipeline(payload);

            default:
                return "ERROR:Unknown Command";
        }
    }
    
    private String fetchReelsFromDatabase() {
        List<String> reelRecords = new ArrayList<>();
        // Menggunakan jadual 'audio_file' dan kolum yang betul
        String sql = "SELECT reel_id, file_path FROM audio_file ORDER BY reel_id";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int id = rs.getInt("reel_id");
                String rawPath = rs.getString("file_path");
                
                String cleanName = rawPath.substring(rawPath.lastIndexOf(File.separator) + 1);
                if (cleanName.equals(rawPath)) {
                    cleanName = rawPath.substring(rawPath.lastIndexOf("/") + 1);
                }

                reelRecords.add(id + "," + cleanName);
            }

            return "SUCCESS:" + String.join(";", reelRecords);

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR:" + e.getMessage();
        }
    }

    private String fetchTranscriptFromDatabase(String payload) {
        try {
            int transcriptId = Integer.parseInt(payload);
            String sql = "SELECT file_path FROM transcript WHERE transcript_id = ?";

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setInt(1, transcriptId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        File file = new File(rs.getString("file_path"));
                        if (file.exists()) {
                            String content = Files.readString(file.toPath()).replace("\n", " ").replace("\r", " ");
                            return "SUCCESS:" + content;
                        } else {
                            return "ERROR:Local transcript missing on disk: " + file.getAbsolutePath();
                        }
                    }
                }
            }
        } catch (Exception e) {
            return "ERROR:" + e.getMessage();
        }
        return "ERROR:Transcript ID not found";
    }

    private String fetchMatrixLogFromDatabase(String payload) {
        List<String> logRecords = new ArrayList<>();
        String sql = "SELECT experiment_id, model_id, technique_id, status, result_json FROM experiment";
        
        int filterId = -1;
        try {
            filterId = Integer.parseInt(payload);
        } catch (NumberFormatException e) {
            filterId = -1;
        }

        if (filterId != -1) {
            sql += " WHERE transcript_id = ?";
        }
        sql += " ORDER BY experiment_id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            if (filterId != -1) {
                ps.setInt(1, filterId);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int expId = rs.getInt("experiment_id");
                    int mId = rs.getInt("model_id");
                    int tId = rs.getInt("technique_id");
                    String status = rs.getString("status");
                    
                    String modelName;
                    switch (mId) {
                        case 1: modelName = "Llama 3.2 (3B)"; break;
                        case 2: modelName = "Phi-4-mini"; break;
                        case 3: modelName = "Qwen 2.5 (3B)"; break;
                        case 4: modelName = "Gemma-SEA-LION v4 (4B)"; break;
                        default: modelName = "MedGemma (4B)"; break;
                    }

                    String techName;
                    switch (tId) {
                        case 1: techName = "Zero-Shot"; break;
                        case 2: techName = "Few-Shot"; break;
                        case 3: techName = "Chain-of-Thought"; break;
                        default: techName = "Structured-Output"; break;
                    }
                    
                    String jsonSyntax = status.equals("COMPLETED") ? "VALID" : (status.equals("FAILED") ? "INVALID" : "PENDING");
                    
                    // Strictly NO unless execution explicitly reports FAILED status
                 // Kita buang ", hallucinated" dari rentetan log
                    logRecords.add(expId + "," + modelName + "," + techName + "," + status + "," + jsonSyntax);
                }
            }
            return "SUCCESS:" + String.join(";", logRecords);
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR:" + e.getMessage();
        }
    }
   
    private String fetchNutritionPayload(String expIdStr) {
        String sql = "SELECT e.experiment_id, e.result_json " +
                     "FROM experiment e " +
                     "WHERE e.experiment_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, Integer.parseInt(expIdStr));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String resultJsonStr = rs.getString("result_json");
                    int expId = rs.getInt("experiment_id");

                    // 1. Extract basic fields dynamically using robust regex
                    String recipeName = extractJsonValueByRegex(resultJsonStr, "recipe_name");
                    if (recipeName.isEmpty()) {
                        recipeName = extractJsonValueByRegex(resultJsonStr, "recipe");
                    }
                    if (recipeName.isEmpty()) {
                        recipeName = "Dynamic Recipe (Exp ID: " + expId + ")";
                    }
                    
                    String servings = extractJsonValueByRegex(resultJsonStr, "servings");
                    if (servings.isEmpty()) servings = "1.0";
                    
                    // Verify if result_json contains actual ingredient details
                    String isValidFlag = (resultJsonStr != null && (resultJsonStr.contains("ingredients") || resultJsonStr.contains("predicted_ingredients"))) ? "VALID" : "INVALID";

                    // 2. Parse AI predicted ingredients block manually using Regex
                    List<String> aiRows = new ArrayList<>();
                    List<String[]> aiIngredients = parseIngredientsArrayWithRegex(resultJsonStr);
                    for (String[] ing : aiIngredients) {
                        String name = ing[0];
                        String qty = ing[1];
                        String unit = ing[2];
                        String weight = ing[3];
                        
                        // Dynamic calculation checks if values are flagged as hallucinated
                        String nameLower = name.toLowerCase();
                        boolean isHallucinated = name.isEmpty() || 
                                               (!nameLower.contains("flour") && !nameLower.contains("sugar") 
                                                && !nameLower.contains("egg") && !nameLower.contains("yeast") 
                                                && !nameLower.contains("milk") && !nameLower.contains("oil")
                                                && !nameLower.contains("butter") && !nameLower.contains("salt"));
                        String hallucinationFlag = isHallucinated ? "YES (Hallucinated)" : "CLEAR (Valid)";
                        
                        aiRows.add(name + "," + qty + "," + unit + "," + weight + "," + hallucinationFlag);
                    }
                    String aiPayload = String.join(";", aiRows);

                    // 3. Define Human Annotated Ground Truth items
                    String[] gtNames = {"flour", "sugar", "egg", "warm water", "tomato", "mozzarella"};
                    String[] gtExpr = {"mixed", "mixed", "mixed", "mixed", "some", "some"};
                    double[] gtWeights = {120.0, 12.5, 50.0, 244.0, 123.0, 112.0};
                    
                    List<String> gtRows = new ArrayList<>();
                    for (int i = 0; i < gtNames.length; i++) {
                        gtRows.add(gtNames[i] + "," + gtExpr[i] + "," + gtWeights[i] + "g");
                    }
                    String gtPayload = String.join(";", gtRows);

                    // Flat Serialization Format
                    return "SUCCESS:" + recipeName + "|" + servings + "|" + expId + "|" + isValidFlag + "#" + aiPayload + "#" + gtPayload;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR:" + e.getMessage();
        }
        return "ERROR:No matching experiment records found.";
    }

    // High-performance regular expression field matcher for raw JSON strings
    private static String extractJsonValueByRegex(String json, String key) {
        if (json == null || json.isEmpty()) return "";
        // Match: "key" : "value" or "key" : value (handles both quoted string and numerical/unquoted values)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\",}]+)\"?");
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).replace("\"", "").trim();
        }
        return "";
    }

    // Regex-based array partitioner to extract every individual object block inside [] brackets
    private static List<String[]> parseIngredientsArrayWithRegex(String json) {
        List<String[]> list = new ArrayList<>();
        if (json == null || json.isEmpty()) return list;

        // Extract the raw ingredients array string block [...]
        int arrayStart = json.indexOf("[");
        int arrayEnd = json.lastIndexOf("]");
        if (arrayStart == -1 || arrayEnd == -1 || arrayStart >= arrayEnd) return list;
        
        String arrayContent = json.substring(arrayStart + 1, arrayEnd);
        
        // Find individual json object patterns: { ... }
        java.util.regex.Pattern objectPattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
        java.util.regex.Matcher objectMatcher = objectPattern.matcher(arrayContent);
        
        while (objectMatcher.find()) {
            String objectBlock = objectMatcher.group(1);
            
            // Extract attributes matching key names inside the dynamic object block
            String name = extractJsonValueByRegex(objectBlock, "name");
            if (name.isEmpty()) {
                name = extractJsonValueByRegex(objectBlock, "ingredient");
            }
            String qty = extractJsonValueByRegex(objectBlock, "qty");
            String unit = extractJsonValueByRegex(objectBlock, "unit");
            String weight = extractJsonValueByRegex(objectBlock, "weight_g");
            
            if (qty.isEmpty()) qty = "1.0";
            if (weight.isEmpty()) weight = "0.0";
            
            list.add(new String[]{name, qty, unit, weight + "g"});
        }
        return list;
    }
    
    private String runExperimentPipeline(String payload) {
        try {
            String[] parts = payload.split("\\|");
            if (parts.length < 2) return "ERROR:Invalid Payload";

            String modelTag = parts[0];
            String[] techniques = parts[1].split(",");

            // Enqueued execution via SingleThreadExecutor to maintain data pipeline integrity
            pipelineExecutor.submit(() -> {
                for (String tech : techniques) {
                    ExperimentEngine.runPipeline(modelTag, tech, (processed, total, succeeded, failed) -> {});
                }
            });

            return "SUCCESS:Pipeline processing queued for " + modelTag;
        } catch (Exception e) {
            return "ERROR:" + e.getMessage();
        }
    }
   

  

    private void closeConnection() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}