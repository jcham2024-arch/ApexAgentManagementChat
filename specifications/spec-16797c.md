# Next Steps: Agent Swarm & Tool Execution Pipeline

## Objective
With API connectivity and editable model IDs now functional, the next critical milestone is transforming the app from a standard chat interface into a functional "Agentic" platform. This involves implementing the **Agent Swarm** (custom sub-agents) and the **Tool Execution Pipeline** with security interlocks.

Do not worry about maxTokens or token limit handling in this phase.

## Step 1: Complete the Rebranding Cleanup
Before adding new features, eliminate all residual "Narrator" references in the networking and UI layers.

- **Action**: Open `AIGateway.kt`.
- **Task**: Search for and replace the following strings:
  - `"Apex Narrator AI Gateway [Offline / Unconfigured Mode]"` -> `"Apex Agent AI Gateway [Offline / Unconfigured Mode]"`
  - `"Apex Narrator Gateway Error"` -> `"Apex Agent Gateway Error"`
  - Any other user-facing log or error messages containing "Narrator".
- **Action**: Scan `strings.xml` and Compose UI files to ensure all labels reflect "Agent" rather than "Narrator".

## Step 2: Implement the Agent Swarm Registry
Based on the UI mockup, the user needs to create and manage specialized sub-agents. 

### 2.1 Data Layer
- Create a data class for the sub-agent configuration:
  ```kotlin
  data class AgentCard(
      val id: String = UUID.randomUUID().toString(),
      val name: String,
      val systemPrompt: String,
      val modelOverride: String? = null // Optional: overrides provider default
  )
  ```
- Implement a Room Entity/DAO or DataStore serialization to persist a `List<AgentCard>`.

### 2.2 UI Layer (`AgentSwarmScreen.kt`)
- Build a list view displaying saved Agent Cards.
- Add a FAB (`+`) that opens a dialog to "Create Specialized Agent Card".
- The dialog must include `TextField`s for **Agent Name** and **System Prompt Instructions**, and a "Save Agent Card" button.
- Add a mechanism to set one agent as the "Active" agent for the chat session.

### 2.3 Orchestrator Integration (`AgentOrchestrator.kt`)
- Update the orchestrator to accept the `activeAgentCard` as a parameter.
- When constructing the payload for `AIGateway`, if an `AgentCard` is active, inject its `systemPrompt` as the System message at the beginning of the context window.

## Step 3: Implement the Tool Execution Pipeline
The agent must be able to call tools (e.g., reading a file, making an HTTP GET request).

### 3.1 Tool Registry & Basic Workers
- Create a `ToolRegistry` interface and a concrete implementation.
- Implement at least two basic `ToolWorker` classes:
  - `HttpGetTool`: Makes a network request to a provided URL and returns the response body.
  - `ReadFileTool`: Reads a file from the app's local Workspace directory.
- Define risk levels for these tools (e.g., `HttpGet` = L2 Low Risk, `ReadFile` = L1 Safe).

### 3.2 Orchestrator & Security Interlock
- Update `AgentOrchestrator` to handle LLM responses that request a tool call (using standard OpenAI function calling / tool use JSON schema).
- Before executing a tool, pass the request to the `SecurityInterlock`.
- **L1 Tools**: Execute immediately.
- **L2/L3 Tools**: Pause execution and emit a `StateFlow` event requiring user approval in the UI.

## Step 4: Chat UI Tool Trace & Approval
Update `ChatScreen.kt` to support the new agentic workflow.

### 4.1 Tool Call Trace
- Update the chat message rendering logic. If the agent emits a tool call (e.g., "Calling HttpGetTool with url: https://api.github.com"), render this in the chat as a system/trace message (e.g., italicized text or a distinct gray bubble) so the user sees what the agent is doing.

### 4.2 Approval Dialog
- Observe the `SecurityInterlock` approval `StateFlow` in `ChatViewModel`.
- If an approval request is pending, display an AlertDialog in the UI:
  - **Title**: "Agent Action Requires Approval"
  - **Body**: Show the tool name and the parameters (e.g., "Agent wants to execute: HttpGet on url...").
  - **Buttons**: "Deny" and "Approve".
- On "Approve", resume the orchestrator execution.

## Step 5: Validation & Testing
1.  **Agent Swarm Test**: Create a new Agent Card (e.g., "Coder" with system prompt "You are an expert Kotlin developer"). Activate it and ask a coding question. Verify the response style matches the system prompt.
2.  **Tool Execution Test**: Ask the active agent a question that requires a tool (e.g., "Fetch the latest status from https://api.github.com"). Verify the tool call is displayed in the UI.
3.  **Approval Interlock Test**: Ensure that if a tool is configured as L2/L3, the execution pauses and displays the approval dialog before continuing.