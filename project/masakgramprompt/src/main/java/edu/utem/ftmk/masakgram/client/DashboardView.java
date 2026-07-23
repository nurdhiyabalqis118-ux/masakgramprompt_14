package edu.utem.ftmk.masakgram.client;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import edu.utem.ftmk.masakgram.tcp.ProjectClient;

public class DashboardView extends JFrame {

    // Swing UI Components
    private JRadioButton rbLlama, rbPhi, rbQwen, rbSealion, rbMedgemma;
    private JCheckBox cbZero, cbFew, cbCot, cbStructured;
    private JTable tableReels, tableMatrix;
    private JTextArea taTranscript;
    private JButton btnRun, btnViewNutrient, btnExport; // Removed btnCompare
    private DefaultTableModel modelReels, modelMatrix;
    private JProgressBar batchProgressBar;
    private JLabel batchProgressLabel;

    private int currentSelectedTranscriptId = -1;

    // TCP Client Connection
    private ProjectClient client;

    public DashboardView() {
        // 1. Initialize TCP Client Connection
        client = new ProjectClient("localhost", 8888);
        if (!client.connect()) {
            JOptionPane.showMessageDialog(this,
                    "Could not connect to Masakgram Server on port 8888!\nSome network features will be unavailable.",
                    "Connection Warning", JOptionPane.WARNING_MESSAGE);
        }

        // 2. Main Window Properties
        setTitle("MasakGramPrompt Dashboard — Nutritional Analytics & Evaluation");
        setSize(1350, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // 3. Construct Top Panel (Models & Techniques)
        JPanel panelTop = new JPanel(new GridLayout(2, 1));
        panelTop.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel panelModel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelModel.setBorder(BorderFactory.createTitledBorder("LLM Model"));
        rbLlama = new JRadioButton("Llama 3.2 (3B)", true);
        rbPhi = new JRadioButton("Phi-4-mini");
        rbQwen = new JRadioButton("Qwen 2.5 (3B)");
        rbSealion = new JRadioButton("Gemma-SEA-LION v4 (4B)");
        rbMedgemma = new JRadioButton("MedGemma (4B)");

        ButtonGroup bgModel = new ButtonGroup();
        bgModel.add(rbLlama);
        bgModel.add(rbPhi);
        bgModel.add(rbQwen);
        bgModel.add(rbSealion);
        bgModel.add(rbMedgemma);

        panelModel.add(rbLlama);
        panelModel.add(rbPhi);
        panelModel.add(rbQwen);
        panelModel.add(rbSealion);
        panelModel.add(rbMedgemma);

        JPanel panelTechnique = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelTechnique.setBorder(BorderFactory.createTitledBorder("Prompt Engineering Technique"));
        cbZero = new JCheckBox("Zero-Shot", true);
        cbFew = new JCheckBox("Few-Shot");
        cbCot = new JCheckBox("Chain-of-Thought");
        cbStructured = new JCheckBox("Structured-Output");

        panelTechnique.add(cbZero);
        panelTechnique.add(cbFew);
        panelTechnique.add(cbCot);
        panelTechnique.add(cbStructured);

        panelTop.add(panelModel);
        panelTop.add(panelTechnique);
        add(panelTop, BorderLayout.NORTH);

        // 4. Construct Center Panel (Tables & Text Preview)
        JPanel panelCenter = new JPanel(new GridLayout(1, 3, 10, 10));
        panelCenter.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        modelReels = new DefaultTableModel(new Object[]{"Transcript ID", "Cooking Reel Name"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tableReels = new JTable(modelReels);
        JScrollPane scrollReels = new JScrollPane(tableReels);
        scrollReels.setBorder(BorderFactory.createTitledBorder("Available Cooking Reels"));

        taTranscript = new JTextArea();
        taTranscript.setLineWrap(true);
        taTranscript.setWrapStyleWord(true);
        taTranscript.setEditable(false);
        JScrollPane scrollTranscript = new JScrollPane(taTranscript);
        scrollTranscript.setBorder(BorderFactory.createTitledBorder("Audio Transcript Text Preview"));

        modelMatrix = new DefaultTableModel(new Object[]{"Exp ID", "Model", "Technique", "Status", "JSON Syntax"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tableMatrix = new JTable(modelMatrix);
        JScrollPane scrollMatrix = new JScrollPane(tableMatrix);
        scrollMatrix.setBorder(BorderFactory.createTitledBorder("Experiment Execution Matrix Log"));

        panelCenter.add(scrollReels);
        panelCenter.add(scrollTranscript);
        panelCenter.add(scrollMatrix);
        add(panelCenter, BorderLayout.CENTER);

        // 5. Construct South Panel (Buttons & Progress Bar)
        JPanel panelSouth = new JPanel(new BorderLayout(10, 10));
        panelSouth.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel panelButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRun = new JButton("Run Pipeline Execution");
        btnViewNutrient = new JButton("View Nutritional Fact Sheet");
        btnExport = new JButton("Export Performance Evaluation (10 Layers CSV)"); // btnCompare completely removed
        JButton btnShowAll = new JButton("Show All Logs");

        btnShowAll.addActionListener(ae -> {
            currentSelectedTranscriptId = -1;
            tableReels.clearSelection();
            taTranscript.setText("");
            loadExperimentMatrixLogFromServer(-1);
        });

        panelButtons.add(btnRun);
        panelButtons.add(btnViewNutrient);
        panelButtons.add(btnExport);
        panelButtons.add(btnShowAll);

        JPanel panelProgress = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        batchProgressLabel = new JLabel("System idle.");
        batchProgressBar = new JProgressBar(0, 100);
        batchProgressBar.setStringPainted(true);
        panelProgress.add(batchProgressLabel);
        panelProgress.add(batchProgressBar);

        panelSouth.add(panelButtons, BorderLayout.WEST);
        panelSouth.add(panelProgress, BorderLayout.EAST);
        add(panelSouth, BorderLayout.SOUTH);

        // 6. Event Handlers & Initial Server Data Fetch
        setupEventHandlers();
        loadReelsFromServer();
        loadExperimentMatrixLogFromServer(-1);
    }

    // --- Server Communication Methods ---

    private void loadReelsFromServer() {
        String response = client.sendRequest("FETCH_REELS");
        modelReels.setRowCount(0);

        if (response != null && response.startsWith("SUCCESS:")) {
            String rawData = response.substring(8).trim();
            if (!rawData.isEmpty()) {
                String[] items = rawData.split(";");
                for (String item : items) {
                    String[] parts = item.split(",");
                    if (parts.length == 2) {
                        modelReels.addRow(new Object[]{Integer.parseInt(parts[0]), parts[1]});
                    }
                }
            }
        }
    }

    private void loadExperimentMatrixLogFromServer(int transcriptId) {
        String response = client.sendRequest("FETCH_MATRIX_LOG:" + transcriptId);
        modelMatrix.setRowCount(0);

        if (response != null && response.startsWith("SUCCESS:")) {
            String rawData = response.substring(8).trim();
            if (!rawData.isEmpty()) {
                String[] rows = rawData.split(";");
                for (String row : rows) {
                    String[] cols = row.split(",");
                    // Tukar kepada >= 5 untuk menyokong sekurang-kurangnya 5 kolum
                    if (cols.length >= 5) { 
                        modelMatrix.addRow(new Object[]{
                                Integer.parseInt(cols[0]),
                                cols[1],
                                cols[2],
                                cols[3],
                                cols[4]
                        });
                    }
                }
            }
        }
    }

    private void loadTranscriptTextFromServer(int transcriptId) {
        String response = client.sendRequest("FETCH_TRANSCRIPT:" + transcriptId);
        if (response != null && response.startsWith("SUCCESS:")) {
            taTranscript.setText(response.substring(8));
        } else {
            taTranscript.setText("Unable to load transcript text from server.");
        }
    }

    private void runExperimentViaServer() {
        String modelTag = getSelectedModelTag();
        List<String> techniques = getSelectedTechniqueNames();

        if (techniques.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please tick at least one technique.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnRun.setEnabled(false);
        batchProgressBar.setValue(0);
        batchProgressBar.setIndeterminate(true);
        batchProgressLabel.setText("Initializing server pipeline...");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                String payload = modelTag + "|" + String.join(",", techniques);
                return client.sendRequest("RUN_EXPERIMENT:" + payload);
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    batchProgressBar.setIndeterminate(false);
                    
                    if (response != null && response.startsWith("SUCCESS")) {
                        batchProgressLabel.setText("Pipeline active. Monitoring live progress...");
                        
                        Timer progressTimer = new Timer(1500, null);
                        progressTimer.addActionListener(e -> {
                            loadExperimentMatrixLogFromServer(currentSelectedTranscriptId);
                            
                            int totalRows = modelMatrix.getRowCount();
                            int completedRows = 0;
                            boolean ongoingWork = false;
                            
                            for (int i = 0; i < totalRows; i++) {
                                String status = (String) modelMatrix.getValueAt(i, 3);
                                if (status.equalsIgnoreCase("RUNNING") || status.equalsIgnoreCase("PENDING")) {
                                    ongoingWork = true;
                                }
                                if (status.equalsIgnoreCase("COMPLETED") || status.equalsIgnoreCase("FAILED")) {
                                    completedRows++;
                                }
                            }
                            
                            if (totalRows > 0) {
                                int percent = (int) (((double) completedRows / totalRows) * 100);
                                batchProgressBar.setValue(percent);
                                batchProgressLabel.setText("Processed: " + completedRows + " / " + totalRows);
                            }
                            
                            if (!ongoingWork && totalRows > 0) {
                                progressTimer.stop();
                                btnRun.setEnabled(true);
                                batchProgressLabel.setText("All tasks finalized.");
                                batchProgressBar.setValue(100);
                            }
                        });
                        progressTimer.start();
                        
                    } else {
                        btnRun.setEnabled(true);
                        batchProgressLabel.setText("Failed to deploy task pipeline.");
                        JOptionPane.showMessageDialog(DashboardView.this, "Server rejected request: " + response);
                    }
                } catch (Exception ex) {
                    btnRun.setEnabled(true);
                    batchProgressLabel.setText("Network communication exception encountered.");
                    ex.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void setupEventHandlers() {
        tableReels.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = tableReels.getSelectedRow();
                if (row != -1) {
                    int modelRow = tableReels.convertRowIndexToModel(row);
                    currentSelectedTranscriptId = (int) modelReels.getValueAt(modelRow, 0);
                    loadTranscriptTextFromServer(currentSelectedTranscriptId);
                    loadExperimentMatrixLogFromServer(currentSelectedTranscriptId);
                } else {
                    currentSelectedTranscriptId = -1;
                    loadExperimentMatrixLogFromServer(-1);
                }
            }
        });

        btnRun.addActionListener(ae -> runExperimentViaServer());
        
        btnViewNutrient.addActionListener(ae -> {
            int row = tableMatrix.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Please select an experiment row from the log table.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = tableMatrix.convertRowIndexToModel(row);
            int expId = (int) modelMatrix.getValueAt(modelRow, 0);
            
            String response = client.sendRequest("FETCH_NUTRITION:" + expId);
            
            NutritionalFactSheetView factSheet = new NutritionalFactSheetView(this, response);
            factSheet.setVisible(true);
        });

        // 10 Layers Export Handler
        btnExport.addActionListener(ae -> {
            JPanel panel = new JPanel(new GridLayout(5, 2, 10, 5));
            JCheckBox[] boxes = new JCheckBox[10];
            String[] layerNames = {"Layer 1A", "Layer 1B", "Layer 2A", "Layer 2B", "Layer 2C", "Layer 3A", "Layer 3B", "Layer 3C", "Layer 4", "Layer 5"};
            String[] layerLabels = {"LAYER 1A", "LAYER 1B", "LAYER 2A", "LAYER 2B", "LAYER 2C", "LAYER 3A", "LAYER 3B", "LAYER 3C", "LAYER 4", "LAYER 5"};
            String[] layerFileNames = {
                "layer1a_exact_match.csv", 
                "layer1b_text_similarity.csv",
                "layer2a_numeric_quantity.csv", 
                "layer2b_numeric_nutrition.csv", 
                "layer2c_nutrition_totals.csv",
                "layer3a_json_validity.csv", 
                "layer3b_hallucination.csv", 
                "layer3c_ingredient_detection.csv",
                "layer4_human_evaluation.csv", 
                "layer5_condition_scores.csv"
            };

           

            for (int i = 0; i < 10; i++) {
                boxes[i] = new JCheckBox(layerNames[i]);
                panel.add(boxes[i]);
            }

            int option = JOptionPane.showConfirmDialog(this, panel, "Select Performance Evaluation Layers to Export", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (option == JOptionPane.OK_OPTION) {
                boolean selectedAny = false;
                for (int i = 0; i < 10; i++) {
                    if (boxes[i].isSelected()) {
                        selectedAny = true;
                        String label = layerLabels[i];
                        String defaultFileName = layerFileNames[i];
                        
                        String response = client.sendRequest("EXPORT_LAYER:" + label);
                        if (response != null && response.startsWith("SUCCESS:")) {
                            String csvContent = response.substring(8);
                            saveCSVFile(defaultFileName, csvContent);
                        } else {
                            JOptionPane.showMessageDialog(this, "Failed to export " + label + ": " + response, "Export Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
                if (!selectedAny) {
                    JOptionPane.showMessageDialog(this, "No layers were selected for export.", "Info", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });
    }

    private void saveCSVFile(String defaultName, String content) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new java.io.File(defaultName));
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(fileChooser.getSelectedFile())) {
                writer.print(content);
                JOptionPane.showMessageDialog(this, "File saved successfully: " + fileChooser.getSelectedFile().getName(), "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- Helper Methods ---

    private String getSelectedModelTag() {
        if (rbLlama.isSelected()) return "llama3.2:3b";
        if (rbPhi.isSelected()) return "phi4-mini:latest"; // Ditambah :latest
        if (rbQwen.isSelected()) return "qwen2.5:3b";
        if (rbSealion.isSelected()) return "aisingapore/Gemma-SEA-LION-v4-4B-VL:latest"; // Ditambah :latest
        return "medgemma:4b"; // Ditukar dari medgemma ke medgemma:4b
    }

    private List<String> getSelectedTechniqueNames() {
        List<String> activeTechs = new ArrayList<>();
        if (cbZero.isSelected()) activeTechs.add("Zero-Shot");
        if (cbFew.isSelected()) activeTechs.add("Few-Shot");
        if (cbCot.isSelected()) activeTechs.add("Chain-of-Thought");
        if (cbStructured.isSelected()) activeTechs.add("Structured-Output");
        return activeTechs;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DashboardView().setVisible(true));
    }
}