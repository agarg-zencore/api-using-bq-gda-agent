-- Source dataset and sample verified-query SQL for the BigQuery Data Agent (POC).
-- The data agent itself is created in BigQuery → Agents (Agent Catalog), not via DDL.
-- Attach inventory.used_cars as a knowledge source and register the verified queries
-- from Section 9 of docs/BQ-Agent-Technical-Design.docx (or README).
--
-- Run:
--   export GCP_PROJECT_ID=your-project-id
--   bq query --project_id="$GCP_PROJECT_ID" --use_legacy_sql=false < scripts/create_data_agent_source.sql

CREATE SCHEMA IF NOT EXISTS `inventory`
OPTIONS (
  description = 'Used vehicle inventory knowledge source for conversational data agent'
);

CREATE TABLE IF NOT EXISTS `inventory.used_cars` (
  brand STRING,
  model STRING,
  model_year INT64,
  milage STRING,
  fuel_type STRING,
  transmission STRING,
  ext_col STRING,
  int_col STRING,
  accident STRING,
  clean_title BOOL,
  price INT64
);

-- ---------------------------------------------------------------------------
-- Verified Query 1
-- Question: "Show me Toyota cars under $20k"
-- Run in BigQuery Studio to validate before adding to the agent.
-- ---------------------------------------------------------------------------
-- SELECT
--   brand, model, model_year, milage, fuel_type, transmission,
--   ext_col, int_col, accident, clean_title, price
-- FROM `inventory.used_cars`
-- WHERE LOWER(brand) = 'toyota'
--   AND price < 20000
-- ORDER BY price ASC;

-- ---------------------------------------------------------------------------
-- Verified Query 2
-- Question: "What is the average price by brand?"
-- ---------------------------------------------------------------------------
-- SELECT
--   brand,
--   COUNT(*) AS vehicle_count,
--   ROUND(AVG(price), 2) AS avg_price,
--   MIN(price) AS min_price,
--   MAX(price) AS max_price
-- FROM `inventory.used_cars`
-- GROUP BY brand
-- ORDER BY avg_price DESC;

-- ---------------------------------------------------------------------------
-- Verified Query 3
-- Question: "Are there any Toyota hybrids with a clean title?"
-- ---------------------------------------------------------------------------
-- SELECT
--   brand, model, model_year, fuel_type, price, accident, clean_title
-- FROM `inventory.used_cars`
-- WHERE LOWER(brand) = 'toyota'
--   AND LOWER(fuel_type) LIKE '%hybrid%'
--   AND clean_title = TRUE
-- ORDER BY price ASC;

-- ---------------------------------------------------------------------------
-- Verified Query 4 (parameterized pattern in Agent Catalog)
-- Question: "Show me {brand} cars with less than {miles} miles"
-- ---------------------------------------------------------------------------
-- SELECT brand, model, model_year, milage, price
-- FROM `inventory.used_cars`
-- WHERE LOWER(brand) = LOWER(@brand)
--   AND SAFE_CAST(REGEXP_REPLACE(milage, r'[^0-9]', '') AS INT64) < @max_miles
-- ORDER BY price ASC;
