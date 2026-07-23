package edu.utem.ftmk.masakgram.tcp;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class PromptManager {

    /**
     * Membaca fail system dan user dari resources, kemudian menggabungkannya secara dinamik.
     * @param techniqueId 1 = Zero-Shot, 2 = Few-Shot, 3 = Chain-of-Thought, 4 = Structured Output
     * @param transcriptContent Isi teks transkrip nasi lemak dari database
     */
    public static String buildPrompt(int techniqueId, String transcriptContent) {
        String systemFile = "";
        String userFile = "";

        // Padankan fail ikut ID teknik dan nama fail sebenar yang Dr. Emaliana bagi
        switch (techniqueId) {
            case 1:
                systemFile = "zero_shot_system.txt";
                userFile = "zero_shot_user.txt";
                break;
            case 2:
                systemFile = "few_shot_system.txt";
                userFile = "few_shot_user.txt";
                break;
            case 3:
                systemFile = "chain_of_thought_system.txt";
                userFile = "chain_of_thought_user.txt";
                break;
            case 4:
                systemFile = "structured_output_system.txt";
                userFile = "structured_output_user.txt";
                break;
            default:
                systemFile = "zero_shot_system.txt";
                userFile = "zero_shot_user.txt";
        }

        // 1. Baca isi kandungan fail SYSTEM dari folder src/main/resources/prompts/
        String systemContent = readResourceFile("/prompts/" + systemFile);
        
        // 2. Baca isi kandungan fail USER dari folder src/main/resources/prompts/
        String userContent = readResourceFile("/prompts/" + userFile);

        // 3. Suntik transkrip masakan ke dalam template USER
        if (userContent.contains("{transcript_data}")) {
            userContent = userContent.replace("{transcript_data}", transcriptContent);
        } else if (userContent.contains("{transcript}")) {
            userContent = userContent.replace("{transcript}", transcriptContent);
        } else {
            userContent = userContent + "\n" + transcriptContent;
        }

        // 4. Gabungkan SYSTEM prompt dan USER prompt untuk dihantar ke enjin LLM
        return "[SYSTEM]\n" + systemContent + "\n[USER]\n" + userContent;
    }

    // Fungsi utiliti File I/O untuk membaca fail di dalam folder src/main/resources
    private static String readResourceFile(String resourcePath) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = PromptManager.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("[MEMBER 4 ERROR] Fail tidak dijumpai dalam resources: " + resourcePath);
                return "";
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            System.err.println("[MEMBER 4 ERROR] Gagal membaca fail: " + resourcePath);
        }
        return sb.toString();
    }
}