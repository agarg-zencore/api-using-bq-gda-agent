-- Creates the BigQuery dataset and table used by POST /api/v1/conversations/{id}/feedback.
-- Schema matches AgentFeedbackService in the Spring Boot app.
--
-- Run with bq (set GCP project first):
--   export GCP_PROJECT_ID=your-project-id
--   bq query --project_id="$GCP_PROJECT_ID" --use_legacy_sql=false < scripts/create_agent_feedback_table.sql
--
-- Or in the BigQuery console: paste and run this script.

CREATE SCHEMA IF NOT EXISTS `agent_feedback`
OPTIONS (
  description = 'End-user feedback for BigQuery conversational agent'
);

CREATE TABLE IF NOT EXISTS `agent_feedback.agent_feedback` (
  feedback_id STRING NOT NULL,
  created_at TIMESTAMP NOT NULL,
  user_id STRING,
  conversation_id STRING,
  invocation_id STRING,
  data_agent_id STRING,
  positive BOOL NOT NULL,
  feedback_message STRING,
  categories ARRAY<STRING>
)
OPTIONS (
  description = 'Thumbs up/down and comments on agent responses'
);
