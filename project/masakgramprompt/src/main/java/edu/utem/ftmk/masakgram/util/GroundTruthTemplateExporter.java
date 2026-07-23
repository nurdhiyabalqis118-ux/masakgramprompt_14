package edu.utem.ftmk.masakgram.util;

import edu.utem.ftmk.masakgram.llm.DatabaseConnection;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class GroundTruthTemplateExporter {

    private GroundTruthTemplateExporter() {
    }

    public static void main(String[] args) {

        JFileChooser chooser = new JFileChooser();

        chooser.setDialogTitle(
                "Save Ground Truth CSV Template"
        );

        chooser.setSelectedFile(
                new File(
                        "ground_truth_ingredients.csv"
                )
        );

        if (chooser.showSaveDialog(null)
                != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile =
                chooser.getSelectedFile();

        /*
         * Add .csv automatically if the user
         * did not include the extension.
         */
        if (!selectedFile.getName()
                .toLowerCase()
                .endsWith(".csv")) {

            selectedFile = new File(
                    selectedFile.getParentFile(),
                    selectedFile.getName() +
                            ".csv"
            );
        }

        try {
            int transcriptCount =
                    exportTemplate(selectedFile);

            JOptionPane.showMessageDialog(
                    null,
                    "Ground-truth template created.\n\n" +
                    "Transcripts exported: " +
                    transcriptCount + "\n\n" +
                    selectedFile.getAbsolutePath(),
                    "Template Created",
                    JOptionPane.INFORMATION_MESSAGE
            );

        } catch (Exception exception) {
            exception.printStackTrace();

            JOptionPane.showMessageDialog(
                    null,
                    "Unable to create template:\n" +
                    exception.getMessage(),
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static int exportTemplate(
            File outputFile
    ) throws Exception {

        String sql =
                "SELECT transcript_id, file_name " +
                "FROM transcript " +
                "ORDER BY transcript_id";

        int count = 0;

        try (Connection connection =
                     DatabaseConnection
                             .getConnection();

             PreparedStatement statement =
                     connection.prepareStatement(sql);

             ResultSet result =
                     statement.executeQuery();

             PrintWriter writer =
                     new PrintWriter(
                             outputFile,
                             StandardCharsets.UTF_8
                                     .name()
                     )) {

            writeHeader(writer);

            while (result.next()) {

                int transcriptId =
                        result.getInt(
                                "transcript_id"
                        );

                String fileName =
                        result.getString(
                                "file_name"
                        );

                writeBlankIngredientRow(
                        writer,
                        transcriptId,
                        fileName
                );

                count++;
            }
        }

        return count;
    }

    private static void writeHeader(
            PrintWriter writer
    ) {
        writer.println(
                "transcript_id," +
                "file_name," +
                "name_original," +
                "language_mentioned," +
                "name_en," +
                "quantity_expression," +
                "quantity_category," +
                "quantity_unit_culinary," +
                "quantity_value_culinary," +
                "estimated_weight_g," +
                "calories," +
                "total_fat_g," +
                "saturated_fat_g," +
                "cholesterol_mg," +
                "sodium_mg," +
                "total_carbohydrate_g," +
                "dietary_fiber_g," +
                "total_sugars_g," +
                "protein_g," +
                "vitamin_d_mcg," +
                "calcium_mg," +
                "iron_mg," +
                "potassium_mg," +
                "annotation_layer," +
                "annotator_matric," +
                "annotator_name"
        );
    }

    private static void writeBlankIngredientRow(
            PrintWriter writer,
            int transcriptId,
            String fileName
    ) {

        String[] row = {
                String.valueOf(transcriptId),
                fileName,

                "", // name_original
                "", // language_mentioned
                "", // name_en
                "", // quantity_expression
                "", // quantity_category
                "", // quantity_unit_culinary
                "", // quantity_value_culinary
                "", // estimated_weight_g
                "", // calories
                "", // total_fat_g
                "", // saturated_fat_g
                "", // cholesterol_mg
                "", // sodium_mg
                "", // total_carbohydrate_g
                "", // dietary_fiber_g
                "", // total_sugars_g
                "", // protein_g
                "", // vitamin_d_mcg
                "", // calcium_mg
                "", // iron_mg
                "", // potassium_mg

                "layer2",
                "", // annotator_matric
                ""  // annotator_name
        };

        for (int i = 0; i < row.length; i++) {

            if (i > 0) {
                writer.print(",");
            }

            writer.print(
                    csv(row[i])
            );
        }

        writer.println();
    }

    private static String csv(String value) {

        if (value == null) {
            return "";
        }

        return "\"" +
                value.replace(
                        "\"",
                        "\"\""
                ) +
                "\"";
    }
}