package edu.utem.ftmk.masakgram.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.json.JSONObject;

public class LLMService {

    // Kita guna nama model yang paling "selamat" untuk Ollama API
    public static final String LLAMA = "llama3.2:3b";
    public static final String PHI = "phi4-mini";              
    public static final String QWEN = "qwen2.5:3b";
    public static final String SEALION = "aisingapore/Gemma-SEA-LION-v4-4B-VL"; 
    public static final String MEDGEMMA = "medgemma:4b";
    
    private final HttpClient httpClient;
    private static final String OLLAMA_ENDPOINT = "http://localhost:11434/api/generate";

    public LLMService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public String prompt(String modelName, String combinedPrompt) throws Exception {
        // Pembersihan total: Buang segala karakter yang boleh rosakkan JSON
        String cleanPrompt = combinedPrompt.replace("\\", "\\\\")
                                           .replace("\"", "\\\"")
                                           .replace("\n", " ")
                                           .replace("\r", " ");
        
        // Guna org.json untuk bina JSON yang confirm valid
     // Inside your prompt method in LLMService.java
        JSONObject jsonPayload = new JSONObject();
        jsonPayload.put("model", modelName);
        jsonPayload.put("prompt", cleanPrompt);
        jsonPayload.put("stream", false);

        // --- ADD THIS OPTIONS BLOCK ---
        JSONObject options = new JSONObject();
        options.put("num_predict", 2048); // Increase this to 2048 or 4096 to prevent cutoff
        jsonPayload.put("options", options);
        // ------------------------------

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_ENDPOINT))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload.toString()))
                .timeout(Duration.ofSeconds(120)) 
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            // Kita print body respon sebab Ollama selalunya bagi tahu kenapa dia reject (400)
            throw new RuntimeException("Ollama error " + response.statusCode() + ": " + response.body());
        }
    }

    public String extractResponseText(String rawJsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(rawJsonResponse);
            return jsonObject.getString("response");
        } catch (Exception e) {
            return "Parsing Error: " + e.getMessage();
        }
    }
}