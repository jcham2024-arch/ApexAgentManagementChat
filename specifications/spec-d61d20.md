# Prime Push: Workspace File I/O & Self-Modification Pipeline

## Objective
The immediate goal is to enable the Agent to read and write files within its own sandboxed workspace. By providing the LLM with `read_file` and `write_file` tools, the agent can inspect its own source code, generate patches, and write them back to the workspace. While a standard Android APK cannot natively compile itself (due to OS sandboxing), this capability allows the agent to generate the modified source files that can then be extracted and rebuilt, effectively taking over the build process.

## Step 1: Build the Workspace Manager
Create a robust file management layer that safely handles I/O operations within the app's sandbox.

### 1.1 Implementation
- Create `WorkspaceManager.kt` in the `:app` or `:core-engine` module.
- Use `Context.filesDir` (or `getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)`) as the root directory to ensure the app has read/write permissions without needing `MANAGE_EXTERNAL_STORAGE`.
- Implement methods:
  - `fun listFiles(path: String): List<File>`
  - `fun readFile(path: String): String`
  - `fun writeFile(path: String, content: String): Boolean`
  - `fun deleteFile(path: String): Boolean`
- **Security**: Ensure the `WorkspaceManager` uses canonical path validation to prevent Path Traversal attacks (e.g., blocking `../../etc/passwd`).

## Step 2: Define Tool Schemas for the LLM
The LLM needs explicit JSON schemas to understand how to call these new tools.

### 2.1 Tool Registry Update
- Define the schemas in your `ToolRegistry` or within the `AIGateway` payload construction:
  ```json
  [
    {
      "type": "function",
      "function": {
        "name": "read_file",
        "description": "Reads the contents of a file in the workspace.",
        "parameters": {
          "type": "object",
          "properties": {
            "filepath": {
              "type": "string",
              "description": "The relative path to the file."
            }
          },
          "required": ["filepath"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "write_file",
        "description": "Writes or overwrites a file in the workspace.",
        "parameters": {
          "type": "object",
          "properties": {
            "filepath": {
              "type": "string",
              "description": "The relative path to the file."
            },
            "content": {
              "type": "string",
              "description": "The full content to write to the file."
            }
          },
          "required": ["filepath", "content"]
        }
      }
    }
  ]
  ```
- Update `AIGateway.kt` to include this `tools` array in the POST payload to the OpenAI-compatible endpoint.

## Step 3: Implement the Tool Workers
Create the concrete classes that execute the tool calls when the LLM requests them.

### 3.1 File Tool Workers
- Create `ReadFileTool.kt` and `WriteFileTool.kt` implementing the `ToolWorker` interface.
- **ReadFileTool**: Parses the `filepath` argument, calls `WorkspaceManager.readFile()`, and returns the string content as the tool output.
- **WriteFileTool**: Parses `filepath` and `content`, calls `WorkspaceManager.writeFile()`, and returns a success message (e.g., "Successfully wrote 400 bytes to MainActivity.kt").
- **Risk Levels**:
  - `ReadFileTool`: **L2** (Low Risk - auto-approve or prompt once).
  - `WriteFileTool`: **L3** (High Risk - **Must** trigger a UI approval dialog before executing).

## Step 4: Orchestrator Execution Loop
Update `AgentOrchestrator.kt` to handle the multi-step agentic loop.

### 4.1 The Loop
1. User sends a prompt: "Read build.gradle and add a dependency."
2. Orchestrator sends prompt to LLM with tools.
3. LLM responds with a `tool_calls` object (calling `read_file`).
4. Orchestrator executes `read_file` via `ToolRegistry`.
5. Orchestrator appends the tool result as a `role: "tool"` message to the conversation history.
6. Orchestrator sends the new history back to the LLM.
7. LLM responds with a `tool_calls` object (calling `write_file` with the modified Gradle code).
8. Orchestrator pauses, triggers the `SecurityInterlock` for L3 approval.
9. User clicks "Approve" in the UI.
10. Orchestrator executes `write_file`.
11. LLM summarizes the changes to the user.

## Step 5: Workspace UI Integration
The user needs to see the files being manipulated.

### 5.1 File Tree & Editor (`WorkspaceScreen.kt`)
- Wire the left-hand file list to `WorkspaceManager.listFiles("")`.
- Clicking a file should call `WorkspaceManager.readFile(path)` and display the text in the right-hand pane.
- Add a "Refresh" button to the UI to re-read the directory list after the Agent writes a new file.

## Step 6: The "Build Takeover" System Prompt
To make the Agent act as a coding agent, inject a specialized `AgentCard` system prompt.

### 6.1 Prompt Engineering
Create a default Agent Card called "Builder" with the following system prompt:
> "You are an expert Android developer with direct access to the workspace via the read_file and write_file tools. When asked to modify the codebase, first use read_file to inspect the existing code, then use write_file to apply your changes. Always provide complete, compilable code blocks when overwriting files. Do not use placeholders like '// existing code here'."

## Validation Test
1.  Open the Workspace tab. Manually create a file (or use a tool to pre-seed one like `Test.kt`).
2.  Go to Chat. Ask the agent: "Read the contents of Test.kt."
3.  Verify the agent calls the tool and replies with the contents.
4.  Ask the agent: "Overwrite Test.kt with a simple Kotlin Hello World main function."
5.  Verify the UI prompts you for L3 approval to write the file.
6.  Approve it. Check the Workspace tab to confirm the file changed on disk.