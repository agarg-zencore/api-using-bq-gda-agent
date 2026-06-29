# BigQuery Conversational Agent Chatbot API

Spring Boot REST API for chat with a pre-created BigQuery **data agent** via Google Cloud's [Conversational Analytics API](https://cloud.google.com/gemini/docs/conversational-analytics-api/overview) (**Gemini Data Agent**, or **GDA**).

Each chat turn calls GDA in **stateless** mode, but the API replays full message history from **Vertex AI Sessions** on every request — giving you stateful multi-turn chat without GDA conversation references. **Vertex AI Memory Bank** adds long-term user context across separate conversations.

Authenticate with Application Default Credentials (`GOOGLE_APPLICATION_CREDENTIALS` or a Cloud Run service account).

## Sessions vs Memory Bank

Both are **Vertex AI Agent Engine** resources, but they solve different problems.

| | **Sessions** | **Memory Bank** |
|---|---|---|
| **What it stores** | Full turn-by-turn chat log (user messages + agent replies) | Distilled facts about a user (preferences, context) |
| **Scoped by** | `conversationId` (one chat thread) | `userId` (one person, across all threads) |
| **Lifespan** | One conversation until deleted or TTL expires | Persists across many conversations for the same user |
| **When the API uses it** | **Before** chat — load history and replay to GDA | **Before** chat — retrieve relevant memories; **after** chat — generate new memories |
| **API mapping** | `POST /conversations` → create session | `GET /users/{userId}/memories` |
| **Example** | "User asked about Toyota, then asked for average price" | "User prefers Toyota and Honda brands" |

### How they work together

```
1. POST /conversations          → Create a Session (conversationId + userId)
2. POST /conversations/{id}/messages
       │
       ├─ Sessions:  load all prior events for this conversationId
       ├─ Memory Bank: retrieve memories for this userId
       ├─ GDA:        stateless :chat with history + memories + new message
       └─ After reply:
            ├─ Sessions:     append user message + agent reply
            └─ Memory Bank:  generate long-term memories (async)
```

**Sessions** answer: *"What did we say in this conversation?"*  
**Memory Bank** answers: *"What do we know about this user from past conversations?"*

You only need to pass `userId` when **creating** a conversation. Follow-up messages need only `conversationId` — the API reads `userId` from the stored session.

## Create Sessions and Memory Bank instances

You need **two Agent Engine instances** (empty — no agent code deployment required):

| Instance | Config property | Purpose |
|----------|-----------------|---------|
| Sessions | `app.sessions-engine-id` | Stores conversation events |
| Memory Bank | `app.memory-bank-engine-id` | Stores long-term user memories |

### Prerequisites

1. **Enable APIs** in your GCP project:

   ```bash
   gcloud config set project YOUR_PROJECT_ID
   gcloud services enable aiplatform.googleapis.com
   ```

2. **Grant IAM** to your runtime service account:

   ```bash
   gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
     --member="serviceAccount:YOUR_SA@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
     --role="roles/aiplatform.user"
   ```

3. **Install the Vertex AI Python SDK** (one-time setup tool — the Spring Boot app does not use Python):

   ```bash
   cd /path/to/zencore-ai-using-bqagent
   python3 -m venv .venv
   source .venv/bin/activate
   pip install "google-cloud-aiplatform>=1.111.0"
   ```

4. **Authenticate**:

   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/sa-key.json
   ```

### Run the setup script

Edit `scripts/create_agent_engines.py` if needed (`PROJECT_ID`, `LOCATION`), then:

```bash
source .venv/bin/activate
python scripts/create_agent_engines.py
```

Example output:

```
SESSIONS_ENGINE_ID=5090605245940105216
Full name: projects/.../locations/us-central1/reasoningEngines/5090605245940105216

MEMORY_BANK_ENGINE_ID=4174122721770209280
Full name: projects/.../locations/us-central1/reasoningEngines/4174122721770209280
```

Copy the IDs into `src/main/resources/application.yml` or export them as env vars:

```bash
export VERTEX_LOCATION=us-central1
export VERTEX_SESSIONS_ENGINE_ID=5090605245940105216
export VERTEX_MEMORY_BANK_ENGINE_ID=4174122721770209280
```

`VERTEX_LOCATION` must match the region where you created the instances. First-time Agent Engine use may take 1–2 minutes; later instances are usually faster.

## Quick start

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/sa-bq-agent-key.json
export VERTEX_LOCATION=us-central1
export VERTEX_SESSIONS_ENGINE_ID=your-sessions-engine-id
export VERTEX_MEMORY_BANK_ENGINE_ID=your-memory-bank-engine-id

mvn spring-boot:run
```

```bash
curl http://localhost:8080/health

# 1) Create a conversation (creates a Vertex Session; pass userId once)
curl -s -X POST http://localhost:8080/api/v1/conversations \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-123" \
  -d '{"conversationId":"my-conv-1"}'

# 2) Send a message (history loaded from Session; only message + conversationId needed)
curl -s -X POST http://localhost:8080/api/v1/conversations/my-conv-1/messages \
  -H "Content-Type: application/json" \
  -d '{"message":"What data can you help me with?"}'

# 3) Stream a follow-up (same session history + Memory Bank context)
curl -N -X POST http://localhost:8080/api/v1/conversations/my-conv-1/messages/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"Show me Toyota cars under $20k"}'

# 4) List conversation history (from Session)
curl -s http://localhost:8080/api/v1/conversations/my-conv-1/messages

# 5) List long-term memories for a user (from Memory Bank)
curl -s http://localhost:8080/api/v1/users/user-123/memories

# 6) Submit feedback on a conversation (stored in BigQuery)
curl -s -X POST http://localhost:8080/api/v1/conversations/my-conv-1/feedback \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-123" \
  -d '{
    "positive": true,
    "feedbackMessage": "Great SQL explanation",
    "categories": ["Something worked well"],
    "invocationId": "optional-turn-id-from-session-messages"
  }'

# 7) Delete conversation (deletes the Session)
curl -X DELETE http://localhost:8080/api/v1/conversations/my-conv-1

# One-off stateless chat (no Session, no Memory Bank)
curl -s -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"What data can you help me with?"}'
```

## Architecture

```
Client → REST API
           ├─ Vertex AI Sessions      (create / list / append / delete events)
           ├─ Vertex AI Memory Bank   (retrieve before chat, generate after chat)
           ├─ BigQuery                (agent feedback from users)
           └─ GDA stateless :chat     (full session history replayed each turn)
```

## Configuration

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `app.gcp-project-id` | `GCP_PROJECT_ID` | `bq-agent-poc-500313` | GCP project |
| `app.gcp-location` | `GCP_LOCATION` | `us` | GDA region |
| `app.data-agent-id` | `DATA_AGENT_ID` | *(see application.yml)* | Pre-created data agent ID |
| `app.vertex-location` | `VERTEX_LOCATION` | `us-central1` | Region for Sessions & Memory Bank |
| `app.sessions-engine-id` | `VERTEX_SESSIONS_ENGINE_ID` | *(required for conversations)* | Sessions Agent Engine ID |
| `app.memory-bank-engine-id` | `VERTEX_MEMORY_BANK_ENGINE_ID` | *(required for memories)* | Memory Bank Agent Engine ID |
| `app.default-user-id` | `DEFAULT_USER_ID` | `default-user` | Fallback when no userId provided |
| `app.chat-timeout-seconds` | `CHAT_TIMEOUT_SECONDS` | `300` | SSE stream timeout |
| `app.feedback-dataset` | `FEEDBACK_DATASET` | `agent_feedback` | BigQuery dataset for user feedback |
| `app.feedback-table` | `FEEDBACK_TABLE` | `agent_feedback` | BigQuery table for user feedback |
| `app.feedback-auto-create-table` | `FEEDBACK_AUTO_CREATE_TABLE` | `true` | Create dataset/table on first feedback if missing |

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Health check |
| `POST` | `/api/v1/conversations` | Create conversation (Vertex Session) |
| `DELETE` | `/api/v1/conversations/{id}` | Delete conversation (Vertex Session) |
| `GET` | `/api/v1/conversations/{id}/messages` | List session events |
| `GET` | `/api/v1/users/{userId}/memories` | List Memory Bank facts for a user |
| `POST` | `/api/v1/conversations/{id}/feedback` | Submit user feedback (stored in BigQuery) |
| `POST` | `/api/v1/conversations/{id}/messages` | Chat with history — returns JSON when complete |
| `POST` | `/api/v1/conversations/{id}/messages/stream` | Chat with history — SSE stream (chunks as they arrive) |
| `POST` | `/api/v1/chat` | One-off stateless chat (JSON) |
| `POST` | `/api/v1/chat/stream` | One-off stateless chat (SSE) |

### `/messages` vs `/messages/stream`

Both use the same backend logic (Session history + Memory Bank + GDA). The difference is response delivery:

- **`/messages`** — waits for the full agent response, returns a JSON array.
- **`/messages/stream`** — streams each chunk over SSE as GDA produces it; ends with `[DONE]`. Use `curl -N`.

### Agent feedback

`POST /api/v1/conversations/{id}/feedback` stores end-user ratings in BigQuery (similar to Snowflake Cortex Agent feedback observability events). Request body:

| Field | Required | Description |
|-------|----------|-------------|
| `positive` | Yes | `true` = thumbs up, `false` = thumbs down |
| `feedbackMessage` | No | Free-text comment |
| `categories` | No | List of category strings |
| `invocationId` | No | Turn ID from session messages (omit for conversation-level feedback) |
| `userId` | No | Overrides `X-User-Id` header / session user |

Default BigQuery location: dataset `agent_feedback`, table `agent_feedback`.

Create the table manually (optional — the API can auto-create on first feedback when `FEEDBACK_AUTO_CREATE_TABLE=true`):

```bash
export GCP_PROJECT_ID=your-project-id
bq query --project_id="$GCP_PROJECT_ID" --use_legacy_sql=false < scripts/create_agent_feedback_table.sql
```

Query feedback:

```sql
SELECT *
FROM `agent_feedback.agent_feedback`
WHERE conversation_id = 'my-conv-1'
ORDER BY created_at DESC;
```

## IAM

Grant the runtime identity (the account in `GOOGLE_APPLICATION_CREDENTIALS`):

**GDA (Gemini Data Agent) / BigQuery**
- `roles/geminidataanalytics.dataAgentUser`
- `roles/geminidataanalytics.dataAgentStatelessUser`
- `roles/cloudaicompanion.user`
- `roles/bigquery.jobUser` + dataset `dataViewer`

**Vertex AI Sessions & Memory Bank**
- `roles/aiplatform.user`

**Agent feedback (BigQuery writes)**
- `roles/bigquery.dataEditor` on the feedback dataset (or `bigquery.admin` for auto-create dataset/table)

## Build

```bash
mvn clean package -DskipTests
java -jar target/bq-agent-api-1.0.0-SNAPSHOT.jar
```

## Further reading

- [Agent Platform Sessions](https://cloud.google.com/gemini/docs/agent-platform/sessions/manage-with-adk)
- [Memory Bank setup](https://cloud.google.com/gemini/docs/agent-platform/memory-bank/setup)
- [Memory Bank API quickstart](https://cloud.google.com/gemini/docs/agent-platform/memory-bank/api-quickstart)
