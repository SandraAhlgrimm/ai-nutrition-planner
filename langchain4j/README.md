# LangChain4j module

This module contains the LangChain4j implementation of the nutrition planner.

## Beginner local flow

Use this path for a basic first run:

1. Start Ollama (or make sure an Ollama instance is already running on `http://localhost:11434`):
   ```bash
   docker compose --profile ollama up -d
   ```
2. Set local environment variables:
   ```bash
   export SPRING_PROFILES_ACTIVE=ollama
   export SPRING_SECURITY_USER_NAME=alice
   export SPRING_SECURITY_USER_PASSWORD=change-this-local-password
   # Optional overrides:
   # export OLLAMA_BASE_URL=http://localhost:11434
   # export OLLAMA_MODEL_NAME=qwen2.5
   ```
3. Run the module from the repository root:
   ```bash
   mvn spring-boot:run -pl langchain4j
   ```
4. Open `http://localhost:8080` and sign in with the configured security user.

If `SPRING_SECURITY_USER_PASSWORD` is not set, Spring Security generates a random password at startup and prints it in logs.

## Hosted providers (optional)

Use one of these profiles when you want hosted models:

- `openai`:
  - `OPENAI_API_KEY` (required)
  - `OPENAI_MODEL_NAME` (optional, default `gpt-4o`)
- `azure`:
  - `AZURE_OPENAI_ENDPOINT` (required)
  - `AZURE_OPENAI_API_KEY` (required)
  - `AZURE_OPENAI_DEPLOYMENT_NAME` (optional, default `gpt-4o`)

## Advanced features

The implementation already includes advanced agentic patterns (validation loops, tools, observability listeners, and multi-agent orchestration).  
For a first run, treat these as internal implementation details and focus on:

- choosing days/meals in the UI
- generating a nutrition plan
- seeing authenticated request flow end-to-end
