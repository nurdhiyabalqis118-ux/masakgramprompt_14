package edu.utem.ftmk.masakgram.llm;

public class LLMServiceTest {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   MASAKGRAMPROMPT: ADVANCED ENGINE MULTI-TEST    ");
        System.out.println("==================================================");

        LLMService service = new LLMService();

        String mockTranscript = "Resipi Sambal Tumis: Guna 2 ulas bawang besar, 10 tangkai cili kering, minyak masak, dan sedikit belacan.";
        String mockSystemPrompt = "You are a professional chef's assistant. Extract a simple bulleted list of ingredients from this text: " 
                                + mockTranscript;

        // An array of the model constants you will be evaluating from image_d51161.png
        // We will test the ones you have downloaded, and catch errors gracefully for the rest
        String[] modelsToTest = { 
            LLMService.LLAMA, 
            LLMService.QWEN 
        };

        for (String model : modelsToTest) {
            System.out.println("\n[EXECUTION] Deploying Task Payload to: " + model);
            System.out.println("--------------------------------------------------");

            try {
                long startTime = System.currentTimeMillis();
                
                // 1. Get raw JSON string block
                String rawJson = service.prompt(model, mockSystemPrompt);
                
                // 2. Parse out only the pure clean answer text
                String cleanText = service.extractResponseText(rawJson);
                
                long totalDuration = System.currentTimeMillis() - startTime;

                System.out.println("[CLEAN RESPONSE]:");
                System.out.println(cleanText.trim());
                System.out.println("\n[METRICS] Performance Processing Speed: " + totalDuration + " ms");

            } catch (Exception e) {
                System.out.println("[NOTICE] Could not run " + model + " yet. (Likely not pulled via Ollama terminal command line).");
            }
            System.out.println("==================================================");
        }
    }
}