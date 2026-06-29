import vertexai

PROJECT_ID = "bq-agent-poc-500313"
LOCATION = "us-central1"  # must match VERTEX_LOCATION in your Spring app

client = vertexai.Client(project=PROJECT_ID, location=LOCATION)

# 1) Sessions store — no code deployment required
sessions_engine = client.agent_engines.create(
    config={"display_name": "bq-agent-sessions"}
)
sessions_id = sessions_engine.api_resource.name.split("/")[-1]
print(f"SESSIONS_ENGINE_ID={sessions_id}")
print(f"Full name: {sessions_engine.api_resource.name}")

# 2) Memory Bank instance — separate engine
memory_bank = client.agent_engines.create(
    config={"display_name": "bq-agent-memory-bank"}
)
memory_id = memory_bank.api_resource.name.split("/")[-1]
print(f"MEMORY_BANK_ENGINE_ID={memory_id}")
print(f"Full name: {memory_bank.api_resource.name}")