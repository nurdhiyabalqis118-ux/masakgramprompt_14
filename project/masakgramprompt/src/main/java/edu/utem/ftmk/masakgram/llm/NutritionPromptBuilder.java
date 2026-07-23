package edu.utem.ftmk.masakgram.llm;

public final class NutritionPromptBuilder {

    private NutritionPromptBuilder() {
    }

    public static String build(
            String technique,
            String transcript
    ) {
        // FIX 1: Strictly forbade the use of null and forced realistic numeric estimation
        String task =
                "Analyse the cooking transcript and estimate " +
                "its ingredients and nutritional values.\n" +
                "Return ONLY valid JSON. Do not use markdown.\n" +
                "CRITICAL: Do NOT use null for numerical nutritional fields. " +
                "If exact weights or values are missing from the transcript, you MUST use " +
                "your internal knowledge to estimate realistic numerical values based on " +
                "standard portion sizes (e.g., 100g flour = 364 kcal).\n\n";

        String schema =
                "Required JSON structure:\n" +
                "{\n" +
                "  \"recipe_name\": \"string\",\n" +
                "  \"servings_estimated\": 1,\n" +
                "  \"per_serving\": {\n" +
                "    \"calories\": 0,\n" +
                "    \"total_fat_g\": 0,\n" +
                "    \"saturated_fat_g\": 0,\n" +
                "    \"sodium_mg\": 0,\n" +
                "    \"carbohydrate_g\": 0,\n" +
                "    \"fiber_g\": 0,\n" +
                "    \"sugars_g\": 0,\n" +
                "    \"protein_g\": 0\n" +
                "  },\n" +
                "  \"total_recipe\": {\n" +
                "    \"calories\": 0,\n" +
                "    \"total_fat_g\": 0,\n" +
                "    \"carbohydrate_g\": 0,\n" +
                "    \"protein_g\": 0\n" +
                "  },\n" +
                "  \"ingredients\": [\n" +
                "    {\n" +
                "      \"name_original\": \"string\",\n" +
                "      \"name_en\": \"string\",\n" +
                "      \"quantity_value\": 0,\n" +
                "      \"unit_original\": \"string\",\n" +
                "      \"unit_en\": \"string\",\n" +
                "      \"estimated_weight_g\": 0,\n" +
                "      \"calories\": 0,\n" +
                "      \"total_fat_g\": 0,\n" +
                "      \"carbohydrate_g\": 0,\n" +
                "      \"protein_g\": 0\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n";

        String transcriptSection =
                "Transcript:\n" +
                transcript;

        if ("Few-Shot".equals(technique)) {
            return buildFewShot(
                    task,
                    schema,
                    transcriptSection
            );
        }

        if ("Chain-of-Thought".equals(
                technique
        )) {
            return buildReasoningPrompt(
                    task,
                    schema,
                    transcriptSection
            );
        }

        if ("Structured-Output".equals(
                technique
        )) {
            return buildStructuredPrompt(
                    task,
                    schema,
                    transcriptSection
            );
        }

        return buildZeroShot(
                task,
                schema,
                transcriptSection
        );
    }

    private static String buildZeroShot(
            String task,
            String schema,
            String transcript
    ) {
        return "ZERO-SHOT TASK\n\n" +
                task +
                schema +
                transcript;
    }

    private static String buildFewShot(
            String task,
            String schema,
            String transcript
    ) {
        String example =
                "Example transcript:\n" +
                "\"Masukkan 2 biji telur dan " +
                "1 sudu minyak. Masak untuk " +
                "dua orang.\"\n\n" +

                "Example output:\n" +
                "{\n" +
                "  \"recipe_name\": " +
                "\"Telur Goreng\",\n" +
                "  \"servings_estimated\": 2,\n" +
                "  \"per_serving\": {\n" +
                "    \"calories\": 115,\n" +
                "    \"total_fat_g\": 9,\n" +
                "    \"saturated_fat_g\": 2.5,\n" +
                "    \"sodium_mg\": 70,\n" +
                "    \"carbohydrate_g\": 0.5,\n" +
                "    \"fiber_g\": 0,\n" +
                "    \"sugars_g\": 0.2,\n" +
                "    \"protein_g\": 6\n" +
                "  },\n" +
                "  \"total_recipe\": {\n" +
                "    \"calories\": 230,\n" +
                "    \"total_fat_g\": 18,\n" +
                "    \"carbohydrate_g\": 1,\n" +
                "    \"protein_g\": 12\n" +
                "  },\n" +
                "  \"ingredients\": [\n" +
                "    {\n" +
                "      \"name_original\": " +
                "\"telur\",\n" +
                "      \"name_en\": \"egg\",\n" +
                "      \"quantity_value\": 2,\n" +
                "      \"unit_original\": \"biji\",\n" +
                "      \"unit_en\": \"pieces\",\n" +
                "      \"estimated_weight_g\": 100,\n" +
                "      \"calories\": 144,\n" +
                "      \"total_fat_g\": 10,\n" +
                "      \"carbohydrate_g\": 1,\n" +
                "      \"protein_g\": 12\n" +
                "    },\n" +
                "    {\n" +
                "      \"name_original\": " +
                "\"minyak\",\n" +
                "      \"name_en\": \"cooking oil\",\n" +
                "      \"quantity_value\": 1,\n" +
                "      \"unit_original\": \"sudu\",\n" +
                "      \"unit_en\": \"tablespoon\",\n" +
                "      \"estimated_weight_g\": 14,\n" +
                "      \"calories\": 120,\n" +
                "      \"total_fat_g\": 14,\n" +
                "      \"carbohydrate_g\": 0,\n" +
                "      \"protein_g\": 0\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n";

        return "FEW-SHOT TASK\n\n" +
                task +
                schema +
                example +
                "Now analyse the new transcript.\n\n" +
                transcript;
    }

    private static String buildReasoningPrompt(
            String task,
            String schema,
            String transcript
    ) {
        return "REASONING-ASSISTED TASK\n\n" +
                task +
                "Internally perform these steps:\n" +
                "1. Identify every ingredient.\n" +
                "2. Standardise quantities and units.\n" +
                "3. Estimate ingredient weights.\n" +
                "4. Estimate ingredient nutrients.\n" +
                "5. Calculate recipe totals.\n" +
                "6. Divide totals by servings.\n" +
                "7. Check that ingredient totals " +
                "are consistent with recipe totals.\n\n" +
                "Do not reveal the reasoning steps. " +
                "Return only the final JSON.\n\n" +
                schema +
                transcript;
    }

    private static String buildStructuredPrompt(
            String task,
            String schema,
            String transcript
    ) {
        return "STRICT STRUCTURED-OUTPUT TASK\n\n" +
                task +
                "Output requirements:\n" +
                "- The first character must be {.\n" +
                "- The final character must be }.\n" +
                "- Do not add unknown fields.\n" +
                // FIX 2: Banned nulls in the structured output rule
                "- All nutrient values must be estimated numbers, NEVER null.\n" + 
                "- ingredients must always be an array.\n" +
                "- Use an empty array if no ingredient " +
                "can be identified.\n" +
                "- Do not include comments.\n" +
                "- Do not include trailing commas.\n\n" +
                schema +
                transcript;
    }
}