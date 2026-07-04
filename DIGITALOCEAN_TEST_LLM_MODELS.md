# DigitalOcean Test LLM Models

## Purpose

The shadow proxy needs two different LLMs for end-to-end testing:

- Primary model: serves the synchronous customer response.
- Candidate model: runs asynchronously from Kafka and is compared against the primary output.

For this test setup, we use DigitalOcean Serverless Inference rather than persistent GenAI agents. This keeps the setup lightweight and avoids paying for dedicated model infrastructure.

## Selected Models

### Primary

- Role: primary LLM
- DigitalOcean model name: OpenAI GPT-4o mini
- Model ID: `openai-gpt-4o-mini`
- Model UUID: not exposed by the Serverless Inference `/v1/models` response
- Endpoint: `https://inference.do-ai.run/v1/chat/completions`

### Candidate

- Role: candidate LLM
- DigitalOcean model name: OpenAI GPT-OSS 20B
- Model ID: `openai-gpt-oss-20b`
- Model UUID: not exposed by the Serverless Inference `/v1/models` response
- Endpoint: `https://inference.do-ai.run/v1/chat/completions`

## DigitalOcean Resources

- Project: `first-project`
- Project ID: `bd73dae5-3ab4-485d-88a5-ab3a35d3b5f7`
- GenAI workspace: `llm-shadow-test-workspace`
- Workspace UUID: `11f1778d-8f57-76ab-aee4-4e013e2ddde4`

## Required Secret

Create a DigitalOcean model access key in the Inference UI and expose it to both services:

```bash
export DO_MODEL_ACCESS_KEY="<model-access-key>"
```

The DigitalOcean personal access token returned `403` against `https://inference.do-ai.run/v1/models`, so a dedicated model access key is required for live inference calls.

The selected model IDs were verified from the model access key's `/v1/models` response.

## Spring Configuration

The production profiles are configured in:

- `proxy-api/src/main/resources/application-prod.properties`
- `shadow-worker/src/main/resources/application-prod.properties`

Both profiles use:

```properties
app.primary-llm.base-url=https://inference.do-ai.run/v1/chat/completions
app.primary-llm.default-model=openai-gpt-4o-mini
app.candidate-llm.base-url=https://inference.do-ai.run/v1/chat/completions
app.candidate-llm.default-model=openai-gpt-oss-20b
```

The current LLM clients forward request payloads unchanged. For live testing against DigitalOcean Serverless Inference, the request payload should be OpenAI-compatible and include the intended `model` field.

These models were not created as dedicated resources. They are serverless foundation models already available through the DigitalOcean model access key.

## Agent Creation Attempt

I attempted to create persistent DigitalOcean GenAI agents for the two models. The DigitalOcean API and `doctl gradient agent create` both returned `404 failed to create agent`, even with:

- Valid project ID
- Valid GenAI workspace UUID
- Supported GenAI region: `tor1`
- Valid model UUIDs

Because the selected models are available as serverless inference models, the working test path is to call the shared Serverless Inference endpoint with the model IDs above.
