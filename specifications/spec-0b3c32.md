# Project Handoff: Apex Agentic — Next Phase (Dynamic Tool Execution & Self-Modification)

## 📋 Current Project Status & Recent Changes

The repository has evolved from a static blueprint into a partially functional Android application with a robust security foundation. Recent commits (e.g., `feat(config): add max token control`, `feat: add Master Agent support`) have added the necessary primitives for agentic behavior.

### ✅ What is Implemented and Working
- **Dynamic Provider Configuration**: Full CRUD for providers via DataStore, including editable `baseUrl`, `modelId`, and `maxTokens`. API keys are securely stored in Android Keystore.
- **Token Limit Handling**: The `AIGateway` now injects `max_tokens` (or `maxOutputTokens`) into requests and catches HTTP 400/429 errors, returning a user-friendly token limit message.
- **Tool Workers (Primitives)**: Basic `ToolWorker` implementations exist for `ReadFileToolWorker`, `WriteFileToolWorker`, `HttpGetToolWorker`, `HttpPostToolWorker`, and `RunCommandToolWorker`.
- **Security Interlock**: The `SecurityInterlock` enforces risk levels (L0-L4), requires user approval for high-risk actions, and logs SHA-256 hash-chained audit records.
- **Agent Swarm UI**: The `AgentListScreen` allows creation and selection of specialized `AgentCard` instances with custom system prompts.
- **Workspace UI**: The `WorkspaceScreen` displays files from a Room database, allowing users to view file contents.

### ⚠️ Critical Gaps (The "Prime Push" Blockers)
The goal is for the agent to autonomously read and write files in its workspace. While the tools exist, the **orchestration logic** does not yet use them dynamically based on LLM output.

1.  **Hardcoded Tool Invocation**: The `AgentOrchestrator` currently uses a simplistic `prompt.contains("http_get")` check to trigger a tool call. It does **not** parse the standard LLM `tool_calls` JSON response to decide when to invoke `read_file` or `write_file`.
2.  **Unused File Tools**: The `ReadFileToolWorker` and `WriteFileToolWorker` are implemented but are **not registered** with the `ToolRegistry` or used by the `AgentOrchestrator`.
3.  **Missing Tool Definition in Payload**: The `AIGateway` does not include the `tools` array (JSON schema definitions for `read_file`, `write_file`, etc.) in the payload sent to the LLM. Without these definitions, the LLM cannot request these tools.
4.  **Disconnected File Governance**: The `FileGovernanceEngine` (which safely writes files to `context.filesDir` and Room) is a separate class and is not integrated as a `ToolWorker` for the agent to call.

## 🎯 Next Steps: Implement Dynamic Tool Execution & Self-Modification

The new agent must bridge the gap between the LLM's decisions and the existing tool primitives.

### Step 1: Define Tool Schemas and Update `AIGateway`
- **Action**: In `AIGateway.kt`, when constructing the JSON payload for both Gemini and OpenAI-compatible endpoints, include a `tools` array containing the schema definitions for `read_file` and `write_file`.
- **Schema Example (OpenAI Format)**:
  ```json
  {
    "type": "function",
    "function": {
      "name": "read_file",
      "description": "Reads the contents of a file in the workspace.",
      "parameters": {
        "type": "object",
        "properties": {
          "path": { "type": "string", "description": "Relative path to the file" }
        },
        "required": ["path"]
      }
    }
  }
  ```
- **Tool Choice**: Ensure the `tool_choice` is set to `"auto"` so the LLM can decide when to call a tool.

### Step 2: Update `AgentOrchestrator` to Parse LLM Tool Calls
- **Current State**: The orchestrator has a hardcoded `http_get` check.
- **Required State**: Implement a full ReAct-style loop.
    1.  Send the user prompt and tool schemas to the LLM.
    2.  Parse the `AIResponse` for a `tool_calls` array (OpenAI) or `functionCall` (Gemini).
    3.  If a tool call is present, extract the tool name and arguments.
    4.  Pass the tool call to the `SecurityInterlock` (which will enforce risk policies).
    5.  If approved, execute the tool via the `ToolRegistry`.
    6.  Append the tool's result as a `role: "tool"` message to the conversation history.
    7.  Re-send the updated history to the LLM to get the final response.
- **Note**: The `SecurityInterlock` already supports `ToolCall` objects; the orchestrator just needs to route them correctly.

### Step 3: Register File Tools and Integrate Governance
- **Action**: In the `ChatViewModel` or wherever the `ToolRegistry` is initialized, register the `ReadFileToolWorker` and `WriteFileToolWorker`.
- **Integration**: For `write_file`, wrap the `FileGovernanceEngine.createFile` or `applyApprovedSelfEdit` method inside a new `ToolWorker` (or modify `WriteFileToolWorker` to delegate to `FileGovernanceEngine`). This ensures writes are persisted to both the physical sandbox and the Room database for UI visibility.
- **Risk Level**: Set `read_file` to L2 (auto-approved) and `write_file` to L3 (requires user approval via `AndroidApprovalPort`).

### Step 4: Validate the Self-Modification Loop
- **Test**: Ask the agent to "Read the contents of `build.gradle.kts` and add a new dependency."
- **Expected Flow**:
    1.  LLM calls `read_file(path="build.gradle.kts")`.
    2.  Orchestrator executes tool, returns file content.
    3.  LLM calls `write_file(path="build.gradle.kts", content="...modified code...")`.
    4.  UI displays approval dialog (L3).
    5.  User approves; file is written.
    6.  Verify the file is updated in the `WorkspaceScreen`.

## 🛠️ Key Files to Modify
| File | Required Change |
| :--- | :--- |
| `app/src/main/java/com/example/domain/gateway/AIGateway.kt` | Add `tools` array to JSON payload for Gemini and OpenAI. |
| `app/src/main/java/com/example/domain/engine/AgentOrchestrator.kt` | Replace hardcoded tool check with a dynamic `tool_calls` parsing loop. |
| `app/src/main/java/com/example/ui/chat/ChatViewModel.kt` | Register `ReadFileToolWorker` and `WriteFileToolWorker` in the `ToolRegistry`. |
| `app/src/main/java/com/example/domain/tools/WriteFileToolWorker.kt` | Delegate file writes to `FileGovernanceEngine` for Room + disk persistence. |

## ⚠️ Constraints & Guardrails
- **Zero Third-Party Deps in `:core-engine`**: Do not add OkHttp or JSON libraries to the core engine. Use the existing `MiniJson` and `OpenAiCompatibleClient`.
- **Security Interlock**: All L3+ tools must trigger the `AndroidApprovalPort`. Do not bypass this for file writes.
- **Java 11 Compatibility**: The core engine must remain Java 11 compatible (no records, no virtual threads).