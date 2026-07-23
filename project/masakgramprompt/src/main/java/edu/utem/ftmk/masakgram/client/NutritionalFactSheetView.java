package edu.utem.ftmk.masakgram.client;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class NutritionalFactSheetView extends JDialog {

    private JTable tableAI, tableGroundTruth;
    private DefaultTableModel modelAI, modelGroundTruth;
    private JLabel lblRecipeProfile, lblEstimatedServings, lblJsonStatus;

    public NutritionalFactSheetView(Frame parent, String rawNutritionData) {
        super(parent, "Nutritional Fact Sheet View Panel (Side-by-Side Evaluation)", true);
        setSize(1100, 700);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        // --- 1. Top Panel (Recipe Profile Info) ---
        JPanel panelTop = new JPanel(new GridLayout(2, 1, 5, 5));
        panelTop.setBorder(BorderFactory.createEmptyBorder(15, 15, 5, 15));
        
        lblRecipeProfile = new JLabel("RECIPE PROFILE: Loading...");
        lblRecipeProfile.setFont(new Font("SansSerif", Font.BOLD, 16));
        lblEstimatedServings = new JLabel("Estimated Servings: -- | Experiment Source Context: --");
        lblEstimatedServings.setFont(new Font("SansSerif", Font.PLAIN, 12));
        
        panelTop.add(lblRecipeProfile);
        panelTop.add(lblEstimatedServings);
        add(panelTop, BorderLayout.NORTH);

        // --- 2. Tabbed Panel (Only 1 Tab remains as requested) ---
        JTabbedPane tabbedPane = new JTabbedPane();

        // Single Tab layout
        JPanel panelComparisonTab = new JPanel(new GridLayout(1, 2, 10, 10));
        panelComparisonTab.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Left Side Table
        JPanel panelAI = new JPanel(new BorderLayout(5, 5));
        panelAI.setBorder(BorderFactory.createTitledBorder("AI Predicted Ingredients Output"));
        
        modelAI = new DefaultTableModel(new Object[]{"LLM Extracted Ingredient", "Qty", "Unit", "Est. Weight", "Hallucination Flag"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tableAI = new JTable(modelAI);
        panelAI.add(new JScrollPane(tableAI), BorderLayout.CENTER);

        // Right Side Table
        JPanel panelGT = new JPanel(new BorderLayout(5, 5));
        panelGT.setBorder(BorderFactory.createTitledBorder("Human Annotated Ground Truth"));
        
        modelGroundTruth = new DefaultTableModel(new Object[]{"Ground Truth Ingredient", "Expression", "Weight"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tableGroundTruth = new JTable(modelGroundTruth);
        panelGT.add(new JScrollPane(tableGroundTruth), BorderLayout.CENTER);

        panelComparisonTab.add(panelAI);
        panelComparisonTab.add(panelGT);

        // Renamed active tab header
        tabbedPane.addTab("Nutritional Analysis Comparison", panelComparisonTab);
        add(tabbedPane, BorderLayout.CENTER);

        // --- 3. South Panel (Status Flag Bar) ---
        JPanel panelSouth = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelSouth.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));
        
        lblJsonStatus = new JLabel("JSON Syntax Parsing Quality Flag: PENDING");
        lblJsonStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        panelSouth.add(lblJsonStatus);
        add(panelSouth, BorderLayout.SOUTH);

        // Parse protocol and populate tables
        parseAndPopulateData(rawNutritionData);
    }

    private void parseAndPopulateData(String rawData) {
        if (rawData == null || rawData.startsWith("ERROR")) {
            lblRecipeProfile.setText("RECIPE PROFILE: Unable to Retrieve Data");
            lblEstimatedServings.setText("The server was unable to fetch database entries.");
            return;
        }

        try {
            if (rawData.startsWith("SUCCESS:")) {
                rawData = rawData.substring(8);
            }

            // Split sections: [0] Header Info, [1] AI Predicted List, [2] GT List
            String[] sections = rawData.split("#");
            if (sections.length < 1) return;

            // Header properties
            String[] headers = sections[0].split("\\|");
            if (headers.length >= 4) {
                lblRecipeProfile.setText("RECIPE PROFILE: " + headers[0]);
                lblEstimatedServings.setText("Estimated Servings: " + headers[1] + " | Experiment Source Context: ID #" + headers[2]);
                lblJsonStatus.setText("JSON Syntax Parsing Quality Flag: " + 
                        (headers[3].equals("VALID") ? "VALID SCHEMA MATCH" : "SYNTAX PARSING ERROR"));
            }

            // AI Ingredients Table
            modelAI.setRowCount(0);
            if (sections.length > 1 && !sections[1].trim().isEmpty()) {
                String[] rows = sections[1].split(";");
                for (String row : rows) {
                    String[] cols = row.split(",");
                    if (cols.length >= 5) {
                        String name = cols[0].trim().isEmpty() ? "-" : cols[0].trim();
                        String qty = cols[1].trim().isEmpty() ? "1.0" : cols[1].trim();
                        String unit = cols[2].trim().isEmpty() ? "-" : cols[2].trim();
                        String weight = cols[3].trim().isEmpty() ? "0.0g" : cols[3].trim();
                        String flag = cols[4].trim();
                        
                        modelAI.addRow(new Object[]{name, qty, unit, weight, flag});
                    }
                }
            }

            // Human Ground Truth Table
            modelGroundTruth.setRowCount(0);
            if (sections.length > 2 && !sections[2].trim().isEmpty()) {
                String[] rows = sections[2].split(";");
                for (String row : rows) {
                    String[] cols = row.split(",");
                    if (cols.length >= 3) {
                        modelGroundTruth.addRow(new Object[]{cols[0], cols[1], cols[2]});
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            lblRecipeProfile.setText("RECIPE PROFILE: Protocol Parsing Error");
            lblJsonStatus.setText("JSON Syntax Parsing Quality Flag: INTERFACE INTERPRETATION EXCEPTION");
        }
    }
}