package edu.utem.ftmk.masakgram.llm;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import edu.utem.ftmk.masakgram.tcp.PromptManager; // Import PromptManager

public class ExperimentEngine {
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int processed, int total, int succeeded, int failed);
    }

    public static void runPipeline(
            String modelTag,
            String techName,
            ProgressListener progress 
    ) {
        LLMService llm = new LLMService();

        int modelId = getModelId(modelTag);
        int techniqueId = getTechniqueId(techName);

        String transcriptSql = "SELECT t.transcript_id, t.file_path " +
                               "FROM transcript t " +
                               "LEFT JOIN experiment e ON t.transcript_id = e.transcript_id " +
                               "AND e.model_id = ? " +
                               "AND e.technique_id = ? " +
                               "AND e.status = 'COMPLETED' " +
                               "WHERE e.experiment_id IS NULL " +
                               "ORDER BY t.transcript_id";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement transcriptStatement = connection.prepareStatement(transcriptSql)) {
            
            transcriptStatement.setInt(1, modelId);
            transcriptStatement.setInt(2, techniqueId);

            try (ResultSet transcripts = transcriptStatement.executeQuery()) {
                
                int total = 50;
                int processed = 0;
                int succeeded = 0;
                int failed = 0;

                while (transcripts.next()) {
                    int transcriptId = transcripts.getInt("transcript_id");
                    String filePath = transcripts.getString("file_path");

                    int experimentId = insertRunningExperiment(
                            connection,
                            transcriptId,
                            modelId,
                            techniqueId
                    );

                    System.out.println(
                            "[RUNNING] Experiment " + experimentId +
                            " | Transcript " + transcriptId +
                            " | Model ID: " + modelId +
                            " | Technique: " + techName
                    );

                    try {
                        String content = Files.readString(Paths.get(filePath));

                        // --- PEMBETULAN UTAMA DI SINI ---
                        // Panggil PromptManager supaya ia menyambungkan fail system & user prompt yang sebenar
                        String prompt = PromptManager.buildPrompt(techniqueId, content);

                        String rawResponse = llm.prompt(
                                modelTag,
                                prompt
                        );

                        String response = llm.extractResponseText(
                                rawResponse
                        );
                        
                        boolean structuredDataSaved = NutritionResultSaver.save(
                                experimentId,
                                response
                        );

                        if (!structuredDataSaved) {
                            updateExperiment(connection, experimentId, "FAILED", response);
                            System.err.println("[FAILED] Invalid nutritional JSON for experiment " + experimentId);
                            failed++;
                            processed++;
                            if (progress != null) progress.onProgress(processed, total, succeeded, failed);
                            continue;
                        }

                        updateExperiment(connection, experimentId, "COMPLETED", response);
                        succeeded++;
                        processed++;

                        if (progress != null) progress.onProgress(processed, total, succeeded, failed);
                        System.out.println("[COMPLETED] Experiment " + experimentId);

                    } catch (Exception exception) {
                        updateExperiment(connection, experimentId, "FAILED", exception.getMessage());
                        failed++;
                        processed++;
                        if (progress != null) progress.onProgress(processed, total, succeeded, failed);
                        System.err.println("[FAILED] Experiment " + experimentId + ": " + exception.getMessage());
                    }
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static int insertRunningExperiment(Connection connection, int transcriptId, int modelId, int techniqueId) throws SQLException {
        String sql = "INSERT INTO experiment (transcript_id, model_id, technique_id, rag_enabled, status, executed_at, result_json) " +
                     "VALUES (?, ?, ?, 0, 'RUNNING', CURRENT_TIMESTAMP, '')";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, transcriptId);
            statement.setInt(2, modelId);
            statement.setInt(3, techniqueId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Experiment ID was not generated.");
                return keys.getInt(1);
            }
        }
    }

    private static void updateExperiment(Connection connection, int experimentId, String status, String result) throws SQLException {
        String sql = "UPDATE experiment SET status = ?, result_json = ?, executed_at = CURRENT_TIMESTAMP WHERE experiment_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, result == null ? "" : result);
            statement.setInt(3, experimentId);
            statement.executeUpdate();
        }
    }

    private static int getModelId(String tag) {
        if (tag.equals("llama3.2:3b")) return 1;
        if (tag.equals("phi4-mini")) return 2;
        if (tag.equals("qwen2.5:3b")) return 3;
        if (tag.equals("aisingapore/Gemma-SEA-LION-v4-4B-VL") || tag.equals("aisingapore/Gemma-SEA-LION-v4-4B-VL:latest")) return 4;
        return 5;
    }

    private static int getTechniqueId(String technique) {
        if (technique.equals("Zero-Shot")) return 1;
        if (technique.equals("Few-Shot")) return 2;
        if (technique.equals("Chain-of-Thought")) return 3;
        return 4; // Structured-Output
    }
}