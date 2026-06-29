# api-using-bq-gda-agent

A Spring Boot application that calls **BigQuery Gemini Data Agents (BQ GDA)** using the Vertex AI **Conversation Analytic API** (Reasoning Engine) and the **Vertex AI Session and Memory Engine** for multi-turn conversation management.

## Architecture

```
Client
  │
  ▼
ConversationController  (REST API – Spring MVC)
  │
  ├──► BigQueryAgentService   ──► VertexAiClient ──► Vertex AI Reasoning Engine (BQ GDA)
  │                                                   (Conversation Analytic API)
  │
  └──► VertexSessionService   ──► VertexAiClient ──► Vertex AI Session & Memory Engine
```

### Components

| Component | Responsibility |
|-----------|---------------|
| `ConversationController` | Exposes REST endpoints for querying the BQ GDA and managing sessions |
| `BigQueryAgentService` | Sends natural language queries to the Reasoning Engine; extracts the answer from the API response |
| `VertexSessionService` | CRUD operations on Vertex AI sessions; appends conversation turns to the Session and Memory Engine |
| `VertexAiClient` | Authenticated HTTP client (via Google Application Default Credentials) for all Vertex AI REST calls |

## Prerequisites

- Java 17+
- Maven 3.9+
- A GCP project with the following APIs enabled:
  - Vertex AI API
  - BigQuery API
- A deployed **Vertex AI Reasoning Engine** that serves as the BQ GDA
- Application Default Credentials configured (`gcloud auth application-default login` or a service account)

## Configuration

Set the following environment variables (or update `src/main/resources/application.properties`):

| Variable | Description | Default |
|----------|-------------|---------|
| `GCP_PROJECT_ID` | Your GCP project ID | `your-gcp-project-id` |
| `GCP_LOCATION` | GCP region | `us-central1` |
| `VERTEX_REASONING_ENGINE_ID` | ID of the deployed Reasoning Engine | `your-reasoning-engine-id` |

## Running the Application

```bash
export GCP_PROJECT_ID=my-project
export GCP_LOCATION=us-central1
export VERTEX_REASONING_ENGINE_ID=123456789

mvn spring-boot:run
```

The server starts on port `8080`.

## API Reference

### Query the BigQuery Gemini Data Agent

**POST** `/api/v1/query`

Send a natural language question to the BQ GDA. Optionally supply a `sessionId` to continue an
existing multi-turn conversation. If no `sessionId` is provided, a new session is created
automatically via the Session and Memory Engine.

```bash
# New conversation
curl -X POST http://localhost:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"query": "What were the top 5 products by revenue last quarter?"}'

# Continue existing conversation
curl -X POST http://localhost:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"query": "Break that down by region.", "sessionId": "abc123"}'
```

**Response**
```json
{
  "sessionId": "abc123",
  "answer": "The top 5 products by revenue last quarter were...",
  "rawResponse": { ... }
}
```

---

### Session Management

#### Create a session
**POST** `/api/v1/sessions`

```bash
curl -X POST http://localhost:8080/api/v1/sessions \
  -H "Content-Type: application/json" \
  -d '{"userId": "user-42", "displayName": "Sales Analysis Session"}'
```

#### List sessions
**GET** `/api/v1/sessions`

```bash
curl http://localhost:8080/api/v1/sessions
```

#### Get a session
**GET** `/api/v1/sessions/{sessionId}`

```bash
curl http://localhost:8080/api/v1/sessions/abc123
```

#### Delete a session
**DELETE** `/api/v1/sessions/{sessionId}`

```bash
curl -X DELETE http://localhost:8080/api/v1/sessions/abc123
```

## Running Tests

```bash
mvn test
```

## Building

```bash
mvn package
java -jar target/bq-gda-agent-0.0.1-SNAPSHOT.jar
```
