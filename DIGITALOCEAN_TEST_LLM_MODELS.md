# DigitalOcean Test LLM Models

## Purpose

The shadow proxy needs two different LLMs for end-to-end testing:

- Primary model: serves the synchronous customer response.
- Candidate model: runs asynchronously from Kafka and is compared against the primary output.

For this test setup, we use DigitalOcean Serverless Inference rather than persistent GenAI agents. This keeps the setup lightweight and avoids paying for dedicated model infrastructure.

## Selected Models

### Primary

- Role: primary LLM
- DigitalOcean model name: Llama 3.3 70B Instruct
- Model ID: `llama3.3-70b-instruct`
- Owner in `/v1/models`: `digitalocean` (DigitalOcean-hosted open-weight model)
- Model UUID: not exposed by the Serverless Inference `/v1/models` response
- Endpoint: `https://inference.do-ai.run/v1/chat/completions`

### Candidate

- Role: candidate LLM
- DigitalOcean model name: OpenAI GPT-OSS 20B
- Model ID: `openai-gpt-oss-20b`
- Owner in `/v1/models`: `digitalocean` (DigitalOcean-hosted open-weight model)
- Model UUID: not exposed by the Serverless Inference `/v1/models` response
- Endpoint: `https://inference.do-ai.run/v1/chat/completions`

### Why not an OpenAI/Anthropic-branded model?

Models where `/v1/models` reports `"owned_by": "openai"` or `"owned_by": "anthropic"` (e.g. `openai-gpt-4o-mini`,
`anthropic-claude-...`) are proxied to the actual upstream provider and gated behind a higher DigitalOcean
subscription/billing tier. Calling them from a lower tier returns:

```json
{"error": {"message": "this model is not available for your subscription tier", "type": "forbidden_error"}, "status_code": 403}
```

This is a billing restriction, not an auth or code bug — the request otherwise succeeds (correct model ID, valid
token). Both models above are `owned_by: digitalocean` (open-weight, DO-hosted) and are not subject to this gate,
which is why they were chosen for this test setup instead.

## DigitalOcean Resources

- Project: `first-project`
- Project ID: `bd73dae5-3ab4-485d-88a5-ab3a35d3b5f7`
- GenAI workspace: `llm-shadow-test-workspace`
- Workspace UUID: `11f1778d-8f57-76ab-aee4-4e013e2ddde4`

## Required Secret

Create a DigitalOcean **Model Access Key** (Control Panel → **Inference** → **Manage** → **Model Access Keys** →
**Create model access key**) and expose it to both services:

```bash
export DO_MODEL_ACCESS_KEY="<model-access-key>"
```

When creating the key:

- **Foundation models**: select "All models" (or at least `llama3.3-70b-instruct` and `openai-gpt-oss-20b`).
- **VPC network**: choose **No VPC network** if calling from a laptop/local dev machine. A VPC-restricted key
  rejects every request that doesn't originate from inside that DigitalOcean private network with a generic
  `403 {"id":"Forbidden","message":"You are not authorized to perform this operation"}` — indistinguishable from
  an auth failure unless you know to check this.

DigitalOcean personal access tokens (PATs) are documented as an alternative credential for Serverless Inference
(as of the May 2026 release, "all scopes must be granted"), but in practice we saw a full-access PAT rejected with
the same `403 Forbidden` on both `GET /v1/models` and `POST /v1/chat/completions`, even after regenerating it. A
dedicated Model Access Key with no VPC restriction is the credential that reliably worked, so it's the recommended
approach for this project.

The selected model IDs were verified from the model access key's `/v1/models` response.

## Spring Configuration

The production profiles are configured in:

- `proxy-api/src/main/resources/application-prod.properties`
- `shadow-worker/src/main/resources/application-prod.properties`

Both profiles use:

```properties
app.primary-llm.base-url=https://inference.do-ai.run/v1/chat/completions
app.primary-llm.default-model=llama3.3-70b-instruct
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
