# Updated Missing Features Build List & Build Specs (Based on UI Emulation)
*Incorporating visual evidence from the Apex Agentic emulated UI screenshots.*

## Scope Changes
- Removed: The entire "Perchance generator" portion.
- Renamed: "Narrator" → "Agent". The orchestration layer is now `AgentOrchestrator`.
- **New UI Additions identified**: Agent Swarm (Sub-agents), Workspace/Code viewer, and specific Provider configurations (DeepSeek, NVIDIA NIM).

## Priority 0 — Make the project compile, run, and align terminology

### 1. Terminology & String Cleanup (Visual Evidence: Screenshot 3 & 4)
**Build spec**
- Rename all lingering "Narrator" references in the UI and underlying code.
- Screenshot 3: Change "Active Narrator Provider" to "Active Agent Provider".
- Screenshot 4: Change system response string "Apex Narrator AI Gateway" to "Apex Agent AI Gateway".
- Ensure the input placeholder "Ask Apex Agent..." (already correct in Screenshot 4) is consistent across the app.
- Remove all generator-related screens, routes, and string resources.
- Acceptance: `grep -ri "narrator\|perchance\|generator"` returns zero results in source and resources.

## Priority 1 — Core UI & Security Implementation (Visual Evidence: Screenshot 3 & 4)

### 2. Provider Settings & Key Management (Screenshot 3 & 4)
**Build spec**
- **UI Wiring**: The "Add API Key" buttons in Screenshot 3 must trigger a secure input dialog.
- **Key Storage**: Implement `AndroidKeyStoreManager` (AES-256-GCM) to encrypt keys. The UI shows "Key: Not Set"; wire the `StateFlow` to update this to "Key: Configured" once saved.
- **Provider List**: Implement concrete `ChatModelPort` adapters for the UI-listed providers:
  - Gemini (OpenAI Compatible) - `https://generativelanguage.googleapis.com/v1beta/openai`
  - DeepSeek - `https://api.deepseek.com/v1` (Model: `deepseek-chat`)
  - OpenAI - `https://api.openai.com/v1` (Model: `gpt-4o`)
  - NVIDIA NIM - `https://integrate.api.nvidia.com/v1` (Model: `meta/llama-3.3-70b-instruct`)
- **Offline Fallback**: Address Screenshot 4's system message. The `AIGateway` must gracefully catch missing keys and return a localized error state (or mock response) rather than just an offline system string.
- Acceptance: Saving a key in Settings updates the UI state and allows the Chat screen to make a real network request.

### 3. Agent Swarm & Sub-agent Registry (Screenshot 1)
**Build spec**
- **Data Model**: Create `AgentCard` entity (ID, Name, SystemPrompt, ModelOverride).
- **Persistence**: Implement DataStore or Room to save `AgentCard` instances.
- **UI Wiring**: Screenshot 1 shows a modal to "Create Specialized Agent Card". Wire this modal to the repository to save the card and render it in the list (e.g., "twat1").
- **Orchestrator Integration**: Update `AgentOrchestrator` to accept an `AgentCard`. If a card is active, prepend its `SystemPrompt` to the LLM call and override the model if specified.
- Acceptance: User can create, save, and view an agent card. Chatting while an agent is active uses that agent's specific system prompt.

## Priority 2 — Workspace & Tool Execution (Visual Evidence: Screenshot 2)

### 4. Workspace File Viewer & Editor (Screenshot 2)
**Build spec**
- **UI Implementation**: Build the file tree/selector. Clicking a file (e.g., `README.md`) must load its contents into the right-hand pane.
- **File I/O Tools**: Implement `ToolWorker` for `read_file`, `write_file`, `list_dir` restricted to the app's sandboxed workspace directory.
- **Syntax Highlighting**: Implement a basic code viewer for the right pane to display code or markdown.
- Acceptance: Clicking `README.md` displays its actual contents. An active agent can use `write_file` to modify a file in the workspace, and the UI updates.

### 5. Security Interlock & Audit Log
**Build spec**
- Implement `SecurityInterlock` enforcing L1/L2/L3 risk policies for tools.
- Implement `AuditLogManager` with SHA-256 hash chain for workspace tool usage.
- Acceptance: L3 actions (e.g., writing files or running commands) trigger an approval dialog in the UI before execution, and the action is logged.

## Priority 3 — Product Completeness

### 6. Chat UI State Handling (Screenshot 4)
**Build spec**
- Add loading, streaming, and error states to the Chat UI.
- Display agent plan steps (e.g., "Reading file...", "Thinking...").
- Render tool approval prompts inline in the chat.
- Acceptance: User can send message, see streamed response from the selected provider, and approve/deny tool calls.

### 7. End-to-End Integration Test Harness
**Build spec**
- Robolectric test simulating: Selecting an `AgentCard` → sending a message → provider request (mocked) → tool use (workspace read) → UI update.
- Acceptance: Test passes in CI, proving the Swarm, Orchestrator, and Workspace tools are correctly wired.