package edu.utem.ftmk.masakgram.util;

import edu.utem.ftmk.masakgram.llm.DatabaseConnection;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

public final class GroundTruthCsvImporter {

    private GroundTruthCsvImporter() {
    }

    public static void main(String[] args) {

        JFileChooser chooser = new JFileChooser();

        chooser.setDialogTitle(
                "Select Ground Truth CSV"
        );

        if (chooser.showOpenDialog(null)
                != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            ImportResult result = importCsv(
                    chooser.getSelectedFile()
                            .toPath()
            );

            JOptionPane.showMessageDialog(
                    null,
                    "Ground truth imported successfully.\n\n" +
                    "Transcripts: " +
                    result.transcriptsImported + "\n" +
                    "Ingredients: " +
                    result.ingredientsImported,
                    "Import Completed",
                    JOptionPane.INFORMATION_MESSAGE
            );

        } catch (Exception exception) {
            exception.printStackTrace();

            JOptionPane.showMessageDialog(
                    null,
                    "Import failed:\n" +
                    exception.getMessage(),
                    "Import Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    public static ImportResult importCsv(
            Path csvPath
    ) throws Exception {

        List<String> lines =
                Files.readAllLines(
                        csvPath,
                        StandardCharsets.UTF_8
                );

        if (lines.size() < 2) {
            throw new IOException(
                    "CSV contains no ingredient rows."
            );
        }

        List<String> headers =
                parseCsvLine(lines.get(0));

        Map<String, Integer> columns =
                createColumnMap(headers);

        validateRequiredColumns(columns);

        Set<Integer> preparedTranscripts =
                new HashSet<>();

        Map<Integer, Integer> reelIds =
                new HashMap<>();

        int ingredientCount = 0;

        try (Connection connection =
                     DatabaseConnection
                             .getConnection()) {

            connection.setAutoCommit(false);

            try {
                for (int lineNumber = 2;
                     lineNumber <= lines.size();
                     lineNumber++) {

                    String line =
                            lines.get(lineNumber - 1);

                    if (line.isBlank()) {
                        continue;
                    }

                    List<String> values =
                            parseCsvLine(line);

                    int transcriptId =
                            parseRequiredInteger(
                                    get(
                                            values,
                                            columns,
                                            "transcript_id"
                                    ),
                                    "transcript_id",
                                    lineNumber
                            );

                    String annotatorMatric =
                            requireText(
                                    get(
                                            values,
                                            columns,
                                            "annotator_matric"
                                    ),
                                    "annotator_matric",
                                    lineNumber
                            );

                    String annotatorName =
                            requireText(
                                    get(
                                            values,
                                            columns,
                                            "annotator_name"
                                    ),
                                    "annotator_name",
                                    lineNumber
                            );

                    int gtReelId;

                    if (!preparedTranscripts.contains(
                            transcriptId
                    )) {

                        gtReelId =
                                prepareGroundTruthReel(
                                        connection,
                                        transcriptId,
                                        annotatorMatric,
                                        annotatorName
                                );

                        preparedTranscripts.add(
                                transcriptId
                        );

                        reelIds.put(
                                transcriptId,
                                gtReelId
                        );

                    } else {
                        gtReelId =
                                reelIds.get(
                                        transcriptId
                                );
                    }

                    insertIngredient(
                            connection,
                            gtReelId,
                            values,
                            columns,
                            lineNumber
                    );

                    ingredientCount++;
                }

                connection.commit();

                return new ImportResult(
                        preparedTranscripts.size(),
                        ingredientCount
                );

            } catch (Exception exception) {
                connection.rollback();
                throw exception;

            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static int prepareGroundTruthReel(
            Connection connection,
            int transcriptId,
            String annotatorMatric,
            String annotatorName
    ) throws SQLException {

        confirmTranscriptExists(
                connection,
                transcriptId
        );

        String findSql =
                "SELECT gt_reel_id " +
                "FROM ground_truth_reel " +
                "WHERE transcript_id = ? " +
                "ORDER BY gt_reel_id DESC " +
                "LIMIT 1";

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             findSql
                     )) {

            statement.setInt(
                    1,
                    transcriptId
            );

            try (ResultSet result =
                         statement.executeQuery()) {

                if (result.next()) {
                    int gtReelId =
                            result.getInt(
                                    "gt_reel_id"
                            );

                    deleteExistingIngredients(
                            connection,
                            gtReelId
                    );

                    updateGroundTruthReel(
                            connection,
                            gtReelId,
                            annotatorMatric,
                            annotatorName
                    );

                    return gtReelId;
                }
            }
        }

        String insertSql =
                "INSERT INTO ground_truth_reel (" +
                "transcript_id, " +
                "annotator_matric, " +
                "annotator_name, " +
                "annotated_at" +
                ") VALUES (?, ?, ?, CURRENT_TIMESTAMP)";

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             insertSql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {

            statement.setInt(
                    1,
                    transcriptId
            );

            statement.setString(
                    2,
                    annotatorMatric
            );

            statement.setString(
                    3,
                    annotatorName
            );

            statement.executeUpdate();

            try (ResultSet keys =
                         statement.getGeneratedKeys()) {

                if (!keys.next()) {
                    throw new SQLException(
                            "Ground-truth reel ID " +
                            "was not generated."
                    );
                }

                return keys.getInt(1);
            }
        }
    }

    private static void insertIngredient(
            Connection connection,
            int gtReelId,
            List<String> values,
            Map<String, Integer> columns,
            int lineNumber
    ) throws SQLException {

        String sql =
                "INSERT INTO ground_truth_ingredient (" +
                "gt_reel_id, " +
                "name_original, " +
                "language_mentioned, " +
                "name_en, " +
                "quantity_expression, " +
                "quantity_category, " +
                "quantity_unit_culinary, " +
                "quantity_value_culinary, " +
                "estimated_weight_g, " +
                "calories, " +
                "total_fat_g, " +
                "saturated_fat_g, " +
                "cholesterol_mg, " +
                "sodium_mg, " +
                "total_carbohydrate_g, " +
                "dietary_fiber_g, " +
                "total_sugars_g, " +
                "protein_g, " +
                "vitamin_d_mcg, " +
                "calcium_mg, " +
                "iron_mg, " +
                "potassium_mg, " +
                "annotation_layer, " +
                "annotator_matric, " +
                "annotator_name, " +
                "annotated_at" +
                ") VALUES (" +
                "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                "?, ?, ?, ?, ?, CURRENT_TIMESTAMP" +
                ")";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            int index = 1;

            statement.setInt(
                    index++,
                    gtReelId
            );

            statement.setString(
                    index++,
                    requireText(
                            get(
                                    values,
                                    columns,
                                    "name_original"
                            ),
                            "name_original",
                            lineNumber
                    )
            );

            statement.setString(
                    index++,
                    requireText(
                            get(
                                    values,
                                    columns,
                                    "language_mentioned"
                            ),
                            "language_mentioned",
                            lineNumber
                    )
            );

            setNullableString(
                    statement,
                    index++,
                    get(values, columns, "name_en")
            );

            setNullableString(
                    statement,
                    index++,
                    get(
                            values,
                            columns,
                            "quantity_expression"
                    )
            );

            setNullableString(
                    statement,
                    index++,
                    get(
                            values,
                            columns,
                            "quantity_category"
                    )
            );

            setNullableString(
                    statement,
                    index++,
                    get(
                            values,
                            columns,
                            "quantity_unit_culinary"
                    )
            );

            setNullableDouble(
                    statement,
                    index++,
                    get(
                            values,
                            columns,
                            "quantity_value_culinary"
                    ),
                    "quantity_value_culinary",
                    lineNumber
            );

            String[] numericColumns = {
                    "estimated_weight_g",
                    "calories",
                    "total_fat_g",
                    "saturated_fat_g",
                    "cholesterol_mg",
                    "sodium_mg",
                    "total_carbohydrate_g",
                    "dietary_fiber_g",
                    "total_sugars_g",
                    "protein_g",
                    "vitamin_d_mcg",
                    "calcium_mg",
                    "iron_mg",
                    "potassium_mg"
            };

            for (String column : numericColumns) {
                setNullableDouble(
                        statement,
                        index++,
                        get(
                                values,
                                columns,
                                column
                        ),
                        column,
                        lineNumber
                );
            }

            statement.setString(
                    index++,
                    requireText(
                            get(
                                    values,
                                    columns,
                                    "annotation_layer"
                            ),
                            "annotation_layer",
                            lineNumber
                    )
            );

            statement.setString(
                    index++,
                    requireText(
                            get(
                                    values,
                                    columns,
                                    "annotator_matric"
                            ),
                            "annotator_matric",
                            lineNumber
                    )
            );

            statement.setString(
                    index,
                    requireText(
                            get(
                                    values,
                                    columns,
                                    "annotator_name"
                            ),
                            "annotator_name",
                            lineNumber
                    )
            );

            statement.executeUpdate();
        }
    }

    private static void confirmTranscriptExists(
            Connection connection,
            int transcriptId
    ) throws SQLException {

        String sql =
                "SELECT 1 FROM transcript " +
                "WHERE transcript_id = ?";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, transcriptId);

            try (ResultSet result =
                         statement.executeQuery()) {

                if (!result.next()) {
                    throw new SQLException(
                            "Transcript ID does not exist: " +
                            transcriptId
                    );
                }
            }
        }
    }

    private static void deleteExistingIngredients(
            Connection connection,
            int gtReelId
    ) throws SQLException {

        String sql =
                "DELETE FROM ground_truth_ingredient " +
                "WHERE gt_reel_id = ?";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, gtReelId);
            statement.executeUpdate();
        }
    }

    private static void updateGroundTruthReel(
            Connection connection,
            int gtReelId,
            String annotatorMatric,
            String annotatorName
    ) throws SQLException {

        String sql =
                "UPDATE ground_truth_reel " +
                "SET annotator_matric = ?, " +
                "annotator_name = ?, " +
                "annotated_at = CURRENT_TIMESTAMP " +
                "WHERE gt_reel_id = ?";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    annotatorMatric
            );

            statement.setString(
                    2,
                    annotatorName
            );

            statement.setInt(
                    3,
                    gtReelId
            );

            statement.executeUpdate();
        }
    }

    private static Map<String, Integer>
    createColumnMap(List<String> headers) {

        Map<String, Integer> map =
                new HashMap<>();

        for (int i = 0;
             i < headers.size();
             i++) {

            String header =
                    headers.get(i)
                            .trim()
                            .toLowerCase();

            if (i == 0) {
                header = header.replace(
                        "\uFEFF",
                        ""
                );
            }

            map.put(header, i);
        }

        return map;
    }

    private static void validateRequiredColumns(
            Map<String, Integer> columns
    ) {

        String[] required = {
                "transcript_id",
                "name_original",
                "language_mentioned",
                "annotation_layer",
                "annotator_matric",
                "annotator_name"
        };

        for (String column : required) {
            if (!columns.containsKey(column)) {
                throw new IllegalArgumentException(
                        "Required CSV column is missing: " +
                        column
                );
            }
        }
    }

    private static String get(
            List<String> values,
            Map<String, Integer> columns,
            String column
    ) {

        Integer index = columns.get(column);

        if (index == null ||
                index >= values.size()) {
            return "";
        }

        return values.get(index).trim();
    }

    private static String requireText(
            String value,
            String column,
            int lineNumber
    ) {

        if (value == null ||
                value.isBlank()) {

            throw new IllegalArgumentException(
                    "Missing " + column +
                    " at CSV line " +
                    lineNumber
            );
        }

        return value.trim();
    }

    private static int parseRequiredInteger(
            String value,
            String column,
            int lineNumber
    ) {

        try {
            return Integer.parseInt(
                    requireText(
                            value,
                            column,
                            lineNumber
                    )
            );

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid integer for " +
                    column + " at CSV line " +
                    lineNumber + ": " + value
            );
        }
    }

    private static void setNullableString(
            PreparedStatement statement,
            int index,
            String value
    ) throws SQLException {

        if (value == null ||
                value.isBlank()) {

            statement.setNull(
                    index,
                    Types.VARCHAR
            );

        } else {
            statement.setString(
                    index,
                    value.trim()
            );
        }
    }

    private static void setNullableDouble(
            PreparedStatement statement,
            int index,
            String value,
            String column,
            int lineNumber
    ) throws SQLException {

        if (value == null ||
                value.isBlank()) {

            statement.setNull(
                    index,
                    Types.FLOAT
            );

            return;
        }

        try {
            statement.setDouble(
                    index,
                    Double.parseDouble(
                            value.trim()
                    )
            );

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid number for " +
                    column + " at CSV line " +
                    lineNumber + ": " + value
            );
        }
    }

    /**
     * Small CSV parser supporting quoted commas
     * and escaped double quotes.
     */
    private static List<String> parseCsvLine(
            String line
    ) {

        List<String> values =
                new ArrayList<>();

        StringBuilder current =
                new StringBuilder();

        boolean insideQuotes = false;

        for (int i = 0;
             i < line.length();
             i++) {

            char character =
                    line.charAt(i);

            if (character == '"') {

                if (insideQuotes &&
                        i + 1 < line.length() &&
                        line.charAt(i + 1) == '"') {

                    current.append('"');
                    i++;

                } else {
                    insideQuotes =
                            !insideQuotes;
                }

            } else if (character == ',' &&
                    !insideQuotes) {

                values.add(
                        current.toString()
                );

                current.setLength(0);

            } else {
                current.append(character);
            }
        }

        values.add(current.toString());

        return values;
    }

    public static final class ImportResult {

        public final int transcriptsImported;
        public final int ingredientsImported;

        public ImportResult(
                int transcriptsImported,
                int ingredientsImported
        ) {
            this.transcriptsImported =
                    transcriptsImported;

            this.ingredientsImported =
                    ingredientsImported;
        }
    }
}