package edu.utem.ftmk.masakgram.llm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NutritionResultSaver {

    private NutritionResultSaver() {
    }
    
    private static String autoCloseJson(String json) {
        int openBraces = 0;
        int openBrackets = 0;
        for (char c : json.toCharArray()) {
            if (c == '{') openBraces++;
            if (c == '}') openBraces--;
            if (c == '[') openBrackets++;
            if (c == ']') openBrackets--;
        }
        
        while (openBrackets > 0) {
            json += "]";
            openBrackets--;
        }
        while (openBraces > 0) {
            json += "}";
            openBraces--;
        }
        return json;
    }

    public static boolean save(
            int experimentId,
            String rawResponse
    ) throws SQLException {

        System.out.println("====== [LLM RAW RESPONSE FOR EXP ID: " + experimentId + "] ======");
        System.out.println(rawResponse);
        System.out.println("===============================================================");

        String cleanedJson = extractJson(rawResponse);
        
        cleanedJson = advancedJsonRepair(cleanedJson);
        cleanedJson = autoCloseJson(cleanedJson);

        JSONObject root;
        try {
            if (cleanedJson.startsWith("[")) {
                root = new JSONObject();
                root.put("ingredients", new JSONArray(cleanedJson));
            } else {
                root = new JSONObject(cleanedJson);
            }
        } catch (Exception exception) {
            System.err.println("❌ JSON Parsing Failed for Exp ID " + experimentId + ": " + exception.getMessage());
            System.err.println("Cleaned JSON attempted: " + cleanedJson);
            saveInvalidResult(experimentId, rawResponse);
            return false;
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                int resultId = insertNutritionResult(
                            connection,
                            experimentId,
                            root,
                            rawResponse
                        );

                JSONArray ingredientsArray = null;
                if (root.has("ingredients") && !root.isNull("ingredients")) {
                    ingredientsArray = root.optJSONArray("ingredients");
                }

                if (ingredientsArray != null) {
                    insertIngredients(
                            connection,
                            experimentId,
                            resultId,
                            ingredientsArray
                    );
                }

                connection.commit();
                System.out.println("✅ Successfully saved nutritional results to DB for Exp ID: " + experimentId);
                return true;

            } catch (Exception exception) {
                connection.rollback();
                
                System.err.println("❌ SQL Database Insert Failed: " + exception.getMessage());
                exception.printStackTrace();
                
                try {
                    saveInvalidResult(experimentId, rawResponse + " | SQL Error: " + exception.getMessage());
                } catch (Exception ignored) {}
                
                return false;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }
    
    private static String advancedJsonRepair(String json) {
        if (json == null || json.isBlank()) {
            return "{}";
        }
        json = json.replaceFirst("^\\{\\s*\\.", "{").replaceFirst("^\\{\\s*,", "{");
        json = json.replaceAll("//.*|/\\*(?:.|[\\n\\r])*?\\*/", "");

        // --- NEW: Clean up Chain-of-Thought inline math expressions and comma numbers ---
        json = cleanChainOfThoughtMath(json);

        Pattern fractionPattern = Pattern.compile(":\\s*(?:([0-9]+)\\s+)?([0-9]+)\\s*/\\s*([0-9]+)");
        Matcher fractionMatcher = fractionPattern.matcher(json);
        StringBuilder sb = new StringBuilder();
        while (fractionMatcher.find()) {
            try {
                double whole = fractionMatcher.group(1) != null ? Double.parseDouble(fractionMatcher.group(1)) : 0.0;
                double num = Double.parseDouble(fractionMatcher.group(2));
                double den = Double.parseDouble(fractionMatcher.group(3));
                double val = whole + (den != 0 ? (num / den) : 0.0);
                fractionMatcher.appendReplacement(sb, Matcher.quoteReplacement(": " + val));
            } catch (Exception e) {
                fractionMatcher.appendReplacement(sb, Matcher.quoteReplacement(fractionMatcher.group(0)));
            }
        }
        fractionMatcher.appendTail(sb);
        json = sb.toString();

        json = json.replaceAll(":\\s*(?i)nan[_a-zA-Z]*(?=\\s*[,\\}\\]])", ": null");

        Pattern nakedValuePattern = Pattern.compile(":\\s*(?![\"'{_\\[\\-\\d])(?!true\\b)(?!false\\b)(?!null\\b)([a-zA-Z][a-zA-Z0-9\\s_\\-\\./]*)(?=\\s*[,\\}\\]])");
        Matcher nakedValueMatcher = nakedValuePattern.matcher(json);
        json = nakedValueMatcher.replaceAll(": \"$1\"");

        Pattern nakedArrayPattern = Pattern.compile("(?<=\\[|,)\\s*(?![\"'{_\\[\\-\\d])(?!true\\b)(?!false\\b)(?!null\\b)([a-zA-Z][a-zA-Z0-9\\s_\\-\\./]*)(?=\\s*[,\\]])");
        Matcher nakedArrayMatcher = nakedArrayPattern.matcher(json);
        json = nakedArrayMatcher.replaceAll("\"$1\"");

        json = json.replaceAll(",\\s*\\}", "}");
        json = json.replaceAll(",\\s*\\]", "]");

        return json.trim();
    }

    private static String cleanChainOfThoughtMath(String json) {
        // Matches values containing math symbols like '+' or thousands separators like '87,500' inside JSON fields
        Pattern mathValPattern = Pattern.compile("(:\\s*)([0-9][0-9,\\.\\+\\s\\-*\\/]+)(?=\\s*[,\\}\\]])");
        Matcher matcher = mathValPattern.matcher(json);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String expression = matcher.group(2).trim();
            try {
                // If it contains a plus sign or complex math, fallback to extracting the first valid number or evaluating simply
                if (expression.contains("+") || expression.contains("-") || expression.contains("*") || expression.contains("/")) {
                    // Extract the first clean number found in the expression string
                    String cleanNum = expression.replaceAll("[^0-9.]", "");
                    if (cleanNum.isEmpty()) cleanNum = "0";
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + cleanNum));
                } else if (expression.contains(",")) {
                    // Remove thousands separator commas (e.g., 87,500 -> 87500)
                    String fixedNum = expression.replace(",", "");
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + fixedNum));
                } else {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                }
            } catch (Exception e) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static int insertNutritionResult(
            Connection connection,
            int experimentId,
            JSONObject root,
            String rawResponse
    ) throws SQLException {

        JSONObject serving = null;
        if (root.has("per_serving") && !root.isNull("per_serving")) {
            serving = root.optJSONObject("per_serving");
        }
        if (serving == null) {
            serving = new JSONObject();
        }

        JSONObject total = null;
        if (root.has("total_recipe") && !root.isNull("total_recipe")) {
            total = root.optJSONObject("total_recipe");
        }
        if (total == null) {
            total = new JSONObject();
        }

        String sql =
                "INSERT INTO nutrition_result (" +
                "experiment_id, recipe_name, servings_estimated, " +
                "serving_calories, serving_total_fat_g, serving_saturated_fat_g, serving_cholesterol_mg, " +
                "serving_sodium_mg, serving_carbohydrate_g, serving_fiber_g, serving_sugars_g, " +
                "serving_protein_g, serving_vitamin_d_mcg, serving_calcium_mg, serving_iron_mg, serving_potassium_mg, " +
                "total_calories, total_fat_g, total_saturated_fat_g, total_cholesterol_mg, " +
                "total_sodium_mg, total_carbohydrate_g, total_fiber_g, total_sugars_g, " +
                "total_protein_g, total_vitamin_d_mcg, total_calcium_mg, total_iron_mg, total_potassium_mg, " +
                "raw_json_output, json_valid" +
                ") VALUES (" +
                "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1" +
                ")";

        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int index = 1;
            statement.setInt(index++, experimentId);

            if (root.has("recipe_name") && !root.isNull("recipe_name")) {
                String name = root.optString("recipe_name", null);
                if (name != null && name.length() > 200) {
                    name = name.substring(0, 197) + "...";
                }
                setNullableString(statement, index++, name);
            } else {
                statement.setNull(index++, Types.VARCHAR);
            }

            if (root.has("servings_estimated") && !root.isNull("servings_estimated")) {
                statement.setInt(index++, root.optInt("servings_estimated", 1));
            } else {
                statement.setNull(index++, Types.INTEGER);
            }

            setNullableNumber(statement, index++, serving, "calories");
            setNullableNumber(statement, index++, serving, "total_fat_g");
            setNullableNumber(statement, index++, serving, "saturated_fat_g");
            setNullableNumber(statement, index++, serving, "cholesterol_mg");
            setNullableNumber(statement, index++, serving, "sodium_mg");
            setNullableNumber(statement, index++, serving, "carbohydrate_g");
            setNullableNumber(statement, index++, serving, "fiber_g");
            setNullableNumber(statement, index++, serving, "sugars_g");
            setNullableNumber(statement, index++, serving, "protein_g");
            setNullableNumber(statement, index++, serving, "vitamin_d_mcg");
            setNullableNumber(statement, index++, serving, "calcium_mg");
            setNullableNumber(statement, index++, serving, "iron_mg");
            setNullableNumber(statement, index++, serving, "potassium_mg");

            setNullableNumber(statement, index++, total, "calories");
            setNullableNumber(statement, index++, total, "total_fat_g");
            setNullableNumber(statement, index++, total, "saturated_fat_g");
            setNullableNumber(statement, index++, total, "cholesterol_mg");
            setNullableNumber(statement, index++, total, "sodium_mg");
            setNullableNumber(statement, index++, total, "carbohydrate_g");
            setNullableNumber(statement, index++, total, "fiber_g");
            setNullableNumber(statement, index++, total, "sugars_g");
            setNullableNumber(statement, index++, total, "protein_g");
            setNullableNumber(statement, index++, total, "vitamin_d_mcg");
            setNullableNumber(statement, index++, total, "calcium_mg");
            setNullableNumber(statement, index++, total, "iron_mg");
            setNullableNumber(statement, index++, total, "potassium_mg");

            statement.setString(index, rawResponse);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("nutrition_result ID was not generated.");
                }
                return keys.getInt(1);
            }
        }
    }

    private static void insertIngredients(
            Connection connection,
            int experimentId,
            int resultId,
            JSONArray ingredients
    ) throws SQLException {

        if (ingredients == null || ingredients.length() == 0) {
            return;
        }

        String sql =
                "INSERT INTO ingredient_result (" +
                "result_id, name_original, name_en, quantity_value, unit_original, unit_en, " +
                "estimated_weight_g, calories, total_fat_g, total_carbohydrate_g, protein_g, hallucination_flag" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String matchGroundTruthSql = 
                "SELECT COUNT(*) FROM ground_truth_ingredient gti " +
                "JOIN ground_truth_reel gtr ON gti.gt_reel_id = gtr.gt_reel_id " +
                "JOIN experiment e ON gtr.transcript_id = e.transcript_id " +
                "WHERE e.experiment_id = ? AND (LOWER(gti.name_original) = LOWER(?) OR LOWER(gti.name_en) = LOWER(?))";

        try (PreparedStatement statement = connection.prepareStatement(sql);
             PreparedStatement checkStatement = connection.prepareStatement(matchGroundTruthSql)) {
             
            for (int i = 0; i < ingredients.length(); i++) {
                JSONObject ingredient = ingredients.optJSONObject(i);
                if (ingredient == null || ingredient.equals(JSONObject.NULL)) {
                    continue;
                }

                String nameOrig = ingredient.isNull("name_original") ? null : ingredient.optString("name_original", null);
                if (nameOrig != null && nameOrig.length() > 200) nameOrig = nameOrig.substring(0, 197) + "...";
                
                String nameEn = ingredient.isNull("name_en") ? null : ingredient.optString("name_en", null);
                if (nameEn != null && nameEn.length() > 200) nameEn = nameEn.substring(0, 197) + "...";

                int hallucinationFlag = 0; 
                if (nameEn != null || nameOrig != null) {
                    String lookupName = (nameEn != null) ? nameEn : nameOrig;
                    checkStatement.setInt(1, experimentId);
                    checkStatement.setString(2, lookupName);
                    checkStatement.setString(3, lookupName);
                    
                    try (ResultSet rs = checkStatement.executeQuery()) {
                        if (rs.next() && rs.getInt(1) == 0) {
                            hallucinationFlag = 1; 
                        }
                    }
                }

                int index = 1;
                statement.setInt(index++, resultId);
                setNullableString(statement, index++, nameOrig);
                setNullableString(statement, index++, nameEn);
                setNullableNumber(statement, index++, ingredient, "quantity_value");
                setNullableString(statement, index++, ingredient.isNull("unit_original") ? null : ingredient.optString("unit_original", null));
                setNullableString(statement, index++, ingredient.isNull("unit_en") ? null : ingredient.optString("unit_en", null));
                setNullableNumber(statement, index++, ingredient, "estimated_weight_g");
                setNullableNumber(statement, index++, ingredient, "calories");
                setNullableNumber(statement, index++, ingredient, "total_fat_g");
                setNullableNumber(statement, index++, ingredient, "total_carbohydrate_g");
                setNullableNumber(statement, index++, ingredient, "protein_g");
                
                statement.setInt(index, hallucinationFlag); 

                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void saveInvalidResult(int experimentId, String rawResponse) throws SQLException {
        String sql = "INSERT INTO nutrition_result (experiment_id, raw_json_output, json_valid) VALUES (?, ?, 0)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, experimentId);
            statement.setString(2, rawResponse);
            statement.executeUpdate();
        }
    }

    private static String extractJson(String response) {
        if (response == null || response.isBlank()) {
            return "{}";
        }
        String cleaned = response.trim();
        int mdStart = cleaned.toLowerCase().indexOf("```json");
        if (mdStart >= 0) {
            int blockStart = mdStart + 7;
            int mdEnd = cleaned.indexOf("```", blockStart);
            if (mdEnd > blockStart) {
                cleaned = cleaned.substring(blockStart, mdEnd).trim();
            }
        } else {
            cleaned = cleaned.replace("```", "").trim();
        }

        int firstBrace = cleaned.indexOf('{');
        int firstBracket = cleaned.indexOf('[');
        int startPoint = -1;
        int endPoint = -1;
        
        if (firstBrace >= 0 && (firstBracket == -1 || firstBrace < firstBracket)) {
            startPoint = firstBrace;
            endPoint = cleaned.lastIndexOf('}');
        } else if (firstBracket >= 0) {
            startPoint = firstBracket;
            endPoint = cleaned.lastIndexOf(']');
        }

        if (startPoint >= 0 && endPoint > startPoint) {
            return cleaned.substring(startPoint, endPoint + 1);
        }
        return cleaned;
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setNullableNumber(
            PreparedStatement statement,
            int index,
            JSONObject object,
            String key
    ) throws SQLException {

        if (object == null || !object.has(key) || object.isNull(key)) {
            statement.setNull(index, Types.FLOAT);
            return;
        }

        Object value = object.opt(key);
        if (value == null || value.equals(JSONObject.NULL)) {
            statement.setNull(index, Types.FLOAT);
            return;
        }

        if (value instanceof Number) {
            statement.setDouble(index, ((Number) value).doubleValue());
            return;
        }

        Double parsedValue = extractNumericValue(value.toString());
        if (parsedValue != null) {
            statement.setDouble(index, parsedValue);
        } else {
            statement.setNull(index, Types.FLOAT);
        }
    }

    private static Double extractNumericValue(String text) {
        if (text == null || text.isBlank() || text.equalsIgnoreCase("null")) {
            return null;
        }
        String clean = text.trim();
        
        if (clean.contains("/")) {
            try {
                String[] parts = clean.split("/");
                double num = Double.parseDouble(parts[0].replaceAll("[^0-9.]", ""));
                double den = Double.parseDouble(parts[1].replaceAll("[^0-9.]", ""));
                if (den != 0) return num / den;
            } catch (Exception ignored) {}
        }

        Pattern p = Pattern.compile("(\\d+(?:\\.\\d+)?)");
        Matcher m = p.matcher(clean);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (Exception ignored) {}
        }
        return null;
    }
}