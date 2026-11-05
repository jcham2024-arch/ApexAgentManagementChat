# Build Spec: Phase 3 — Multi-Agent Swarms, Persistent Memory, and Autonomy

## Objective
The app can currently modify its own codebase via single-agent interactions. The next step is to make the agents truly autonomous and collaborative. This involves allowing agents to delegate tasks to each other (Swarm), remembering past context (Memory), and executing complex tasks in the background.

## Step 1: Multi-Agent Delegation (The "Swarm" Protocol)
Currently, the "Agent Swarm" UI just lets you pick a system prompt. A true swarm means agents can call *each other*.

- **Action**: Create a new tool called `DelegateTaskTool`.
- **Logic**: 
  - Parameters: `target_agent_id` (String), `task_description` (String).
  - When the `AgentOrchestrator` parses this tool call, it pauses the current agent's execution, loads the target `AgentCard`, passes the `task_description` to the target agent, and waits for its final response.
  - The target agent's final response is then fed back to the original agent as the tool result.
- **UI Update**: When an agent delegates, show a distinct UI element in the chat (e.g., "Master Agent delegated task to Coder Agent..."). Add a visual indicator of the nested agent execution.
- **Acceptance**: Create a "Manager" agent and a "Writer" agent. Tell the Manager to "Write a poem using the Writer agent." The Manager should call `DelegateTaskTool`, the Writer executes, and the Manager returns the poem.

## Step 2: Persistent Long-Term Memory (RAG)
Agents currently forget everything when the app restarts or when a chat session gets too long.

- **Action**: Implement a local Vector Database using Room.
- **Database Schema**: Create a table `MemoryItem` (id, text_content, embedding_vector, timestamp, agent_id).
- **Embedding Generation**: Add a `getEmbeddings(text: String)` method to `AIGateway` (OpenAI uses `text-embedding-3-small`, Gemini uses `text-embedding-004`).
- **Memory Tools**: 
  - `save_memory(text: String)`: Generates an embedding and saves it to the Room DB.
  - `search_memory(query: String)`: Generates an embedding for the query, performs a cosine similarity search against the Room DB, and returns the top 3 most relevant memory strings.
- **Acceptance**: Tell the agent "Remember that my API base URL for DeepSeek is X". Verify it calls `save_memory`. Restart the app. Ask the agent "What is my DeepSeek base URL?" Verify it calls `search_memory` and answers correctly.

## Step 3: Background Task Execution (WorkManager)
If the LLM takes 3 minutes to plan and execute a 5-step coding task, the user shouldn't have to stare at the screen or keep the app in the foreground.

- **Action**: Integrate Android `WorkManager`.
- **Logic**: Create a `AgentTaskWorker` that runs the ReAct loop. 
  - The UI's "Send" button should enqueue a `OneTimeWorkRequest` with the prompt payload.
  - Emit progress updates via LiveData/StateFlow so the UI can render the steps even if the app is backgrounded.
- **Notifications**: Send a system notification when a long-running agent task (e.g., "Refactoring MainActivity") completes or requires L3 approval.
- **Acceptance**: Kick off a massive refactoring task, press the Home button to leave the app, and verify the task continues. Receive a notification when it finishes.

## Step 4: Dynamic Tool Creation (Self-Extension)
If the agent needs a tool that doesn't exist (e.g., `fetch_weather`), it should be able to write the tool itself.

- **Action**: Add a `CreateToolTool`.
- **Logic**: The agent writes a new Kotlin file implementing the `ToolWorker` interface into the workspace. The agent then updates `ChatViewModel` to load the new tool dynamically (using DexClassLoader or by restarting the orchestration loop with the new tool schema).
- **Acceptance**: Ask the agent to create a tool that calculates the Fibonacci sequence. It writes the file, and then uses the tool to answer a math question.

## Step 5: Multimodal Capabilities (Vision)
- **Action**: Add an image attachment button to `ChatScreen`.
- **Logic**: Pass images as Base64 encoded strings in the LLM payload (supported by both OpenAI and Gemini). Update the `read_file` tool to allow reading `.jpg`/`.png` files by returning base64 strings.
- **Acceptance**: Take a screenshot of a UI, attach it, and ask the agent to "Write the Compose code for this UI."

## ⚠️ Constraints & Guardrails
- **Memory Security**: Embeddings and memory strings must be encrypted at rest using the existing `AndroidKeyStoreManager`.
- **WorkManager Limits**: Ensure the `AgentTaskWorker` respects Android's 10-minute background execution limit by checkpointing state (saving the conversation history to Room so it can resume if killed).