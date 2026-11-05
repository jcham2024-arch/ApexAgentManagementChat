# Build Spec: The Self-Modification Pipeline & Code Extraction

## Objective
Now that the agent can read and write files via the ReAct loop, the next phase is to make this process safe, visible, and useful. Since an Android APK cannot natively run `gradle assembleDebug`, the "Build Takeover" must function by having the agent write the code, preview the diff, and extract the workspace for external compilation.

## Step 1: Live Workspace UI Sync
When the agent writes a file in the background, the user must see it immediately.

- **Action**: Update `WorkspaceManager` or `FileGovernanceEngine` to emit a `StateFlow` event (e.g., `WorkspaceChangedEvent(path: String)`) whenever a file is created, modified, or deleted.
- **UI Update**: In `WorkspaceScreen.kt`, observe this flow. If the currently viewed file is modified, reload its contents. If a new file is added, refresh the file tree.
- **Acceptance**: Ask the agent to "Create a new file called test.txt with the word 'Hello'". Approve the L3 action. The Workspace UI file tree should immediately show `test.txt` without needing a manual refresh.

## Step 2: Implement the Diff Viewer (Pre-Approval)
Currently, when the agent calls `write_file`, the L3 approval dialog probably just says "Agent wants to write to MainActivity.kt". The user needs to see *what* is changing before approving.

- **Action**: Modify the L3 approval prompt for `write_file`. 
- **Logic**: Before showing the approval dialog, the orchestrator should read the existing file (if it exists) and compare it to the proposed `content` argument.
- **UI**: Update the approval dialog to show a basic Diff (lines added vs. lines removed) or a side-by-side text comparison.
- **Acceptance**: Tell the agent to "Change the primary color in the theme to red." The approval dialog should show exactly which lines of the XML or Compose theme file are being altered.

## Step 3: Context Window Management (Truncation)
If you ask the agent to "Read build.gradle.kts", that file might be 300 lines long. If it then reads 3 more files, the context window will blow up, and the API will fail.

- **Action**: Implement a `ToolResultTruncator` in `AgentOrchestrator`.
- **Logic**: If a `read_file` tool result exceeds a certain character count (e.g., 10,000 characters), truncate the output and append a warning: `[... TRUNCATED. File too large. Use read_file_range tool to read specific lines ...]`.
- **New Tool**: Implement a `read_file_range(path, start_line, end_line)` tool so the agent can intelligently navigate large files without blowing up the context window.
- **Acceptance**: Ask the agent to read a massive file. Verify in the logs that the tool result is truncated, and the agent doesn't crash.

## Step 4: Workspace Export (The "Build" Handoff)
Since the agent cannot build the APK on the device, the user needs a way to extract the modified source code.

- **Action**: Add a "Compile/Export" button to the `WorkspaceScreen`.
- **Logic**: Create a `WorkspaceExporter` class that zips the entire `filesDir/workspace` directory into a `.zip` file.
- **UI**: Use Android's `Intent.ACTION_CREATE_DOCUMENT` or `FileProvider` to allow the user to save the `.zip` file to their device's Downloads folder or share it directly to a PC.
- **Acceptance**: Tap "Export Workspace". A `workspace.zip` file is generated containing the modified files. The user can extract this on their PC and run `gradle assembleDebug` to see the agent's changes come to life.

## Step 5: The "Builder" Agent Prompt Refinement
Update the Master Agent system prompt to enforce safe, self-correcting behavior.

- **Prompt Injection**:
  > "You are an expert Android developer. You have access to the workspace files. When making code changes, ALWAYS follow this loop:
  > 1. Use `read_file` to inspect the target file.
  > 2. Formulate your changes.
  > 3. Use `write_file` to apply the exact, complete code.
  > 4. Use `read_file` again to verify the file was saved correctly.
  > Do not provide partial snippets. Provide complete, compilable blocks."

## ⚠️ Constraints & Guardrails
- **Path Traversal**: Ensure `WorkspaceExporter` and `WorkspaceManager` strictly resolve canonical paths and throw an error if the agent tries to read/write outside the `/workspace` directory (e.g., `../../shared_prefs`).
- **L3 Confirmations**: Never auto-approve `write_file`. The diff viewer must always be presented to the user.