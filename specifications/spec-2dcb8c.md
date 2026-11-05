# Missing Features Build List and Brief Build Specs

## Priority 0 — Make the project compile and run

### 1. App-layer orchestration
**Build spec**
- Add `:app` Kotlin classes: `ChatViewModel`, `SettingsViewModel`, `ChatRepository`, `SettingsRepository`, and use cases such as `SendMessageUseCase`, `ApproveToolUseCase`, `LoadSettingsUseCase`.
- Wire Compose screens to ViewModels via `collectAsStateWithLifecycle`.
- Define UI state models: `ChatUiState`, `SettingsUiState`, `ToolApprovalUiState`.
- Acceptance: `gradle assembleDebug` succeeds; app launches to `ChatScreen`; a user message updates UI state without crashing.

### 2. Build and architectural guardrail verification
**Build spec**
- Ensure all modules (`:app`, `:security-interface`, `:core-engine`) have complete source sets and Gradle configs.
- Add CI checks: `gradle assembleDebug`, `gradle :core-engine:dependencies` must show zero third-party deps, and `grep` checks must fail on forbidden APIs in `:core-engine`.
- Acceptance: clean build from fresh checkout; guardrail checks pass; no unresolved references.

## Priority 1 — Core security and orchestration

### 3. Security primitives implementation
**Build spec**
- Implement `AuditLogManager` with SHA-256 hash chain over tool invocations; append-only storage; tamper detection method.
- Implement `AndroidKeyStoreManager` using AES-256-GCM; API keys encrypted at rest; no plaintext secrets in logs or prefs.
- Implement `SecurityInterlock` enforcing L1/L2/L3 risk policies and approval flow.
- Add unit/integration tests for hash-chain integrity, tamper detection, and keystore encrypt/decrypt.
- Acceptance: tests prove audit log cannot be silently modified; secrets round-trip through keystore; interlock blocks unapproved L3 actions.

### 4. NarratorOrchestrator and ProviderRegistry wiring
**Build spec**
- Implement `NarratorOrchestrator` to accept user intent, plan tasks, call `AIGateway`, invoke tools through `ToolRegistry`, and return results.
- Implement `ProviderRegistry` to read settings and inject selected provider into orchestrator via `StateFlow`.
- Acceptance: orchestrator can execute a simple plan (e.g., `http_get`) end-to-end in a test harness.

## Priority 2 — Provider and tool functionality

### 5. AI provider adapters
**Build spec**
- Implement concrete `ChatModelPort` adapters for Gemini, OpenAI, Groq, and Ollama/local.
- Each adapter: request/response mapping, streaming if supported, error normalization, timeout handling.
- Acceptance: adapter tests use mock HTTP servers; switching provider in settings changes active adapter.

### 6. Tool executors and risk registry
**Build spec**
- Implement `ToolWorker` for `read_file`, `write_file`, `http_get`, `http_post`, `run_command`.
- Register each tool in `ToolRegistry` with risk level in `ToolRiskRegistry` (e.g., `http_get` = L2, `run_command` = L3).
- Add sandbox/path validation for file tools and command allowlist/denylist for `run_command`.
- Acceptance: each tool executes in isolation; risk levels trigger correct interlock behavior.

## Priority 3 — Product completeness

### 7. Settings persistence and injection
**Build spec**
- Implement DataStore or encrypted preferences for non-secret settings.
- Store API keys only via `AndroidKeyStoreManager`.
- Expose settings as `StateFlow` consumed by `ProviderRegistry` and orchestrator.
- Acceptance: settings survive app restart; API keys never appear in plaintext storage.

### 8. Chat UI state handling and error surfaces
**Build spec**
- Add loading, streaming, error, and tool-approval states to `ChatScreen`.
- Display audit/tool activity summaries and approval prompts for L3 actions.
- Acceptance: user can send message, see streamed response, approve/deny tool call, and view error messages without crash.

### 9. End-to-end integration test harness
**Build spec**
- Create instrumented or Robolectric test that: sends chat message → orchestrator plans → tool invoked → interlock approves/denies → audit record written → UI state updated.
- Use fake provider and fake tool worker for deterministic tests.
- Acceptance: test passes in CI and covers at least one L2 auto-approved tool and one L3 approval-required tool.

### 10. Offline-first queue and sync (if in scope)
**Build spec**
- Implement local queue for pending tool actions/messages when offline.
- Define sync policy and conflict handling; ensure audit log remains append-only.
- Acceptance: actions queue offline and replay on reconnect; audit chain remains valid.