-- LAYER 1A: Exact Match
-- name: LAYER 1A
SELECT 
    e.experiment_id,
    tr.transcript_id,
    m.model_name,
    pt.technique_name,
    COUNT(DISTINCT gir.gt_ingredient_id) AS total_ground_truth,
    COUNT(DISTINCT ir.ingredient_id) AS total_extracted,
    SUM(CASE WHEN LOWER(gir.name_en) = LOWER(ir.name_en) THEN 1 ELSE 0 END) AS exact_matches
FROM experiment e
LEFT JOIN transcript tr ON e.transcript_id = tr.transcript_id
LEFT JOIN llm_model m ON e.model_id = m.model_id
LEFT JOIN prompt_technique pt ON e.technique_id = pt.technique_id
LEFT JOIN ground_truth_reel gtr ON tr.transcript_id = gtr.transcript_id
LEFT JOIN ground_truth_ingredient gir ON gtr.gt_reel_id = gir.gt_reel_id
LEFT JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id
LEFT JOIN ingredient_result ir ON nr.result_id = ir.result_id
GROUP BY e.experiment_id, tr.transcript_id, m.model_name, pt.technique_name;

-- LAYER 1B: Text Similarity
-- name: LAYER 1B
SELECT 
    e.experiment_id,
    ir.ingredient_id,
    ir.name_original AS extracted_name,
    gir.name_original AS ground_truth_name,
    ir.name_en AS extracted_name_en,
    gir.name_en AS ground_truth_name_en
FROM experiment e
LEFT JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id
LEFT JOIN ingredient_result ir ON nr.result_id = ir.result_id
LEFT JOIN transcript tr ON e.transcript_id = tr.transcript_id
LEFT JOIN ground_truth_reel gtr ON tr.transcript_id = gtr.transcript_id
LEFT JOIN ground_truth_ingredient gir ON gtr.gt_reel_id = gir.gt_reel_id;

-- LAYER 2A: Numeric Quantity
-- name: LAYER 2A
SELECT 
    e.experiment_id,
    ir.ingredient_id,
    ir.quantity_value AS extracted_quantity,
    gir.quantity_value_culinary AS ground_truth_quantity,
    ir.unit_en AS extracted_unit,
    gir.quantity_unit_culinary AS ground_truth_unit
FROM experiment e
LEFT JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id
LEFT JOIN ingredient_result ir ON nr.result_id = ir.result_id
LEFT JOIN transcript tr ON e.transcript_id = tr.transcript_id
LEFT JOIN ground_truth_reel gtr ON tr.transcript_id = gtr.transcript_id
LEFT JOIN ground_truth_ingredient gir ON gtr.gt_reel_id = gir.gt_reel_id;

-- LAYER 2B: Numeric Nutrition
-- name: LAYER 2B
SELECT 
    e.experiment_id,
    ir.ingredient_id,
    ir.calories AS extracted_calories,
    gir.calories AS ground_truth_calories,
    ir.protein_g AS extracted_protein,
    gir.protein_g AS ground_truth_protein,
    ir.total_fat_g AS extracted_fat,
    gir.total_fat_g AS ground_truth_fat,
    ir.total_carbohydrate_g AS extracted_carbs,
    gir.total_carbohydrate_g AS ground_truth_carbs
FROM experiment e
LEFT JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id
LEFT JOIN ingredient_result ir ON nr.result_id = ir.result_id
LEFT JOIN transcript tr ON e.transcript_id = tr.transcript_id
LEFT JOIN ground_truth_reel gtr ON tr.transcript_id = gtr.transcript_id
LEFT JOIN ground_truth_ingredient gir ON gtr.gt_reel_id = gir.gt_reel_id;

-- LAYER 2C: Nutrition Totals
-- name: LAYER 2C
SELECT 
    e.experiment_id,
    nr.total_calories AS extracted_total_calories,
    nr.total_protein_g AS extracted_total_protein,
    nr.total_fat_g AS extracted_total_fat,
    nr.total_carbohydrate_g AS extracted_total_carbs
FROM experiment e
LEFT JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id;

-- LAYER 3A: JSON Validity
-- name: LAYER 3A
SELECT 
    e.experiment_id,
    nr.json_valid,
    SUBSTRING(nr.raw_json_output, 1, 200) AS json_preview
FROM experiment e
LEFT JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id;

-- LAYER 3B: Hallucination
-- name: LAYER 3B
SELECT 
    e.experiment_id,
    ir.ingredient_id,
    ir.name_en,
    ir.estimated_weight_g
FROM experiment e
LEFT JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id
LEFT JOIN ingredient_result ir ON nr.result_id = ir.result_id;

-- LAYER 3C: Ingredient Detection
-- name: LAYER 3C
SELECT 
    e.experiment_id,
    COUNT(ir.ingredient_id) AS detected_ingredient_count
FROM experiment e
LEFT JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id
LEFT JOIN ingredient_result ir ON nr.result_id = ir.result_id
GROUP BY e.experiment_id;

-- LAYER 4: Human Evaluation
-- name: LAYER 4
SELECT 
    e.experiment_id,
    tr.transcript_id,
    gtr.annotator_name
FROM experiment e
LEFT JOIN transcript tr ON e.transcript_id = tr.transcript_id
LEFT JOIN ground_truth_reel gtr ON tr.transcript_id = gtr.transcript_id;

-- LAYER 5: Condition Scores
-- name: LAYER 5
SELECT 
    m.model_name,
    pt.technique_name,
    COUNT(e.experiment_id) AS total_runs,
    SUM(CASE WHEN nr.json_valid = 1 THEN 1 ELSE 0 END) AS valid_json_count
FROM experiment e
LEFT JOIN llm_model m ON e.model_id = m.model_id
LEFT JOIN prompt_technique pt ON e.technique_id = pt.technique_id
LEFT JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id
GROUP BY m.model_id, m.model_name, pt.technique_id, pt.technique_name;