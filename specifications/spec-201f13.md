# PROJECT AGENT INSTRUCTIONS

## Mission

Build a **Java-native Android application** with three cleanly separated layers:

1. **Lean core engine** — a ported, Android-compatible version of the Lean Java Agentic Core (ReAct loop, tool registry, context compaction, JSON, LLM client abstraction). Pure Java, zero third-party dependencies.
2. **Secure interface module** — an approval-interlock boundary that gates every tool call, logs an audit trail, and exposes a small SPI surface to the GUI.
3. **Feature-rich GUI** — a Jetpack Compose chat application with multi-screen navigation and a **runtime-configurable provider system** (base URL, API key, model).

The end product must run on an Android phone (API 26+) as a single APK, with the GUI never importing the core engine directly.

**North star:** every phase must produce a **working, installable app**. Do not break the build between phases. Do not start a phase until the previous phase's "Definition of Done" passes.

---

## Source Repositories

> ⚠️ **First task: clone and verify every repository below before writing code.** Several URLs in the original brief were settings pages (paths ending in `/settings`) rather than repositories. Strip `/settings` to get the actual clone URL. If a repo does not exist, is empty, or is licensed incompatibly, **stop and report** — do not substitute your own invention.

| Purpose | Clone URL | What to Take | What to Ignore |
| :--- | :--- | :--- | :--- |
| **GUI foundation (primary)** | `https://github.com/jcham2024-arch/Google-Ai-repo` | Compose navigation, screen structure (`ui/chat`, `ui/agents`, `ui/workspace`, `ui/settings`, `ui/navigation`), `AIGateway` seam, `MainActivity` | The doc claims about MCP, swarms, Perchance — those are not implemented. Do not attempt to build them. `FileGovernanceEngine` is a stub — leave it. |
| **Core engine** | `https://github.com/jcham2024-arch/Leanarch2` | Entire `com.leanllm` Java source tree: ReAct loop, `ToolRegistry`, `ContextCompactor`, hand-rolled JSON, `LlmClient` interface | Desktop-only APIs (see porting table below) |
| **Security interface** | `https://github.com/jcham2024-arch/Interlock-Relay` (verify exact name) | The `interlock-relay-core` Android library module and its seven SPIs | Sample host app — you will write your own host |
| **GUI reference (secondary)** | `https://github.com/jcham2024-arch/NexusAgent` | Agent-task screens to later port as an *additional* navigation destination | The locked task-specific chat flow — the primary chat must remain general-purpose |
| **Reference only — DO NOT VENDOR** | `https://github.com/jcham2024-arch/Gemtest2` | Nothing (design reference for tool-call card UX only) | **Everything.** Spring Boot + LangChain4j is incompatible with an Android APK. |
| **Reference only — DO NOT VENDOR** | `https://github.com/jcham2024-arch/Agenticplatformsetup` | Nothing (reference for sandboxing concepts) | gVisor and TypeScript runners are not portable to standard Android |
| **DO NOT USE** | `https://github.com/jcham2024-arch/LeanJavallm` | — | Exact duplicate of Leanarch2 |
| **DO NOT USE** | `https://github.com/jcham2024-arch/ApexAgentManagementChat` | — | Earlier iteration of Gemtest2 |
| **DO NOT USE** | `https://github.com/jcham2024-arch/Abstractpojo1` | — | Scaffold only |
| **DO NOT USE** | `https://github.com/jcham2024-arch/Aegis` | — | Unverified / unrelated |
| **Reference (external, verify)** | Pocket Agent (search GitHub) | Native-Java-on-Android architecture pattern, APK size baseline (~36 MB) | Do not copy code unless license permits |

---

## Target Repository Structure

Create the following Gradle multi-module project. Use `Google-Ai-repo` as the base for `:app`, then add the two library modules.

```
apex-android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── app/                                # ← from Google-Ai-repo (Kotlin + Compose)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/apex/agent/
│           ├── MainActivity.kt
│           ├── ui/
│           │   ├── navigation/         # ← keep from Google-Ai-repo
│           │   ├── chat/               # ← keep, extend
│           │   ├── agents/             # ← keep, extend (NexusAgent screens go here later)
│           │   ├── workspace/          # ← keep
│           │   └── settings/           # ← EXTEND heavily (provider config lives here)
│           ├── data/
│           │   ├── provider/
│           │   │   ├── ProviderConfig.kt
│           │   │   ├── ProviderRepository.kt      # DataStore + Keystore
│           │   │   ├── ProviderPresets.kt
│           │   │   └── ProviderTestConnection.kt
│           │   └── gateway/
│           │       └── AIGateway.kt              # refactored in Phase A, replaced in Phase C
│           └── security/
│               └── HostRelayService.kt           # Phase D: implements RelaySurfaces SPI
│
├── core-engine/                        # ← from Leanarch2, PORTED (plain Java)
│   ├── build.gradle.kts                # java-library plugin, Java 11
│   └── src/main/java/com/leanllm/
│       ├── agent/                      # ReAct loop
│       ├── client/                     # LlmClient interface + OpenAiCompatibleClient
│       ├── json/                       # hand-rolled JSON — KEEP, it's an asset
│       ├── domain/                     # POJOs/records
│       └── scripting/                  # REVIEW — subprocess logic must be redesigned
│
└── security-interface/                 # ← vendored Interlock Relay (Android library)
    ├── build.gradle.kts                # com.android.library
    └── src/main/java/.../relay/        # approval gate, audit log, SPI interfaces
```

**Dependency direction is strictly one-way and must never be violated:**

```
:app  →  :security-interface  →  :core-engine
```

- `:core-engine` imports **only** `java.*` (post-desugaring). No Android, no Kotlin, no third-party.
- `:security-interface` may import Android APIs and `:core-engine`.
- `:app` imports both but **must not import `com.leanllm.*` directly** — all engine access goes through `:security-interface`.

---

## Non-Negotiable Constraints

1. **`:core-engine` compiles at Java 11 source/target.** Do not raise it. Android's ART does not support Java 21 bytecode.
2. **`:core-engine` has zero third-party dependencies.** No Gson, no Jackson, no OkHttp, no Retrofit. Use `java.net.HttpURLConnection` and the existing hand-rolled JSON.
3. **API keys live only in Android Keystore.** `BuildConfig` may seed a default on first launch, but the active key must come from Keystore. Never log keys. Never write keys to DataStore, Room, or files.
4. **One HTTP client for all providers.** All supported providers expose an OpenAI-compatible `/v1/chat/completions` endpoint. Build **one** `OpenAiCompatibleClient` with a configurable `baseUrl`, not per-provider clients.
5. **The GUI never imports the core engine directly.** Preserve the one-way dependency chain.
6. **No WebView.** Do not embed the React frontends from Gemtest2 or Leanarch2. The GUI is native Compose.
7. **No Spring Boot, no LangChain4j, no reflection-heavy frameworks** anywhere in the APK.
8. **Do not implement features the docs claim but the code does not contain** (MCP swarms, Perchance hub, self-improving CAS). Scope is defined by the phases below.

---

## Porting Table — Leanarch2 → Android

Before copying Leanarch2 into `:core-engine`, run this audit and apply every replacement:

```bash
cd Leanarch2/java
grep -rn "Thread.ofVirtual\|java.net.http\|ProcessBuilder\|ProcessHandle\|java.nio.file.Path.of" .
```

| Desktop API found | Android replacement | Notes |
| :--- | :--- | :--- |
| `Thread.ofVirtual()` | `Executors.newFixedThreadPool(n)` or `newCachedThreadPool()` | Android has no Loom. Keep thread pool creation behind a single factory class. |
| `java.net.http.HttpClient` | `java.net.HttpURLConnection` | Wrap in a `HttpTransport` interface so tests can mock it. |
| `ProcessBuilder` (tool execution) | **In-process `ToolWorker` interface** | Android forbids arbitrary fork/exec. Tools become Java classes invoked on the thread pool. |
| `ProcessHandle.descendants()` | Remove entirely | Replace cleanup logic with cooperative cancellation (`Future.cancel(true)`, `InterruptedException` handling). |
| `Path.of(...)` with absolute desktop paths | `File getFilesDir()` / `getCacheDir()` passed in via config | Never hardcode paths. Accept a `File storageRoot` in the engine constructor. |

**Definition of Done for the port:** `./gradlew :core-engine:compileJava` succeeds, and `grep -rn "Thread.ofVirtual\|java.net.http\|ProcessHandle" core-engine/src` returns nothing.

---

## Phases

### Phase A — Provider Configuration System (do this first; it's the only missing user-facing feature)

**Goal:** a user can add a provider, enter a key, pick a model, test the connection, and chat through it. Delivers a working multi-provider app on top of the existing Google-Ai-repo GUI.

**Tasks:**
1. Create `ProviderConfig` data class in `app/data/provider/`.
2. Create `ProviderRepository`:
   - Non-secret fields (`id`, `displayName`, `baseUrl`, `model`, `isActive`) → **DataStore (Preferences)**.
   - API key → **Android Keystore**, stored under an alias referenced by `apiKeyAlias`. Encrypt with an AES key generated in the Keystore.
   - CRUD operations + `observeActiveProvider(): Flow<ProviderConfig>`.
3. Create `ProviderPresets.kt` with these base URLs:
   ```
   OpenAI            https://api.openai.com/v1
   OpenRouter        https://openrouter.ai/api/v1
   Groq              https://api.groq.com/openai/v1
   Gemini (compat)   https://generativelanguage.googleapis.com/v1beta/openai
   NVIDIA NIM        https://integrate.api.nvidia.com/v1
   Ollama (local)    http://localhost:11434/v1
   LM Studio (local) http://localhost:1234/v1
   Custom            (blank)
   ```
   Presets auto-fill `baseUrl` but the field stays editable.
4. Build Compose screens in `ui/settings/`:
   - **Provider list:** active provider highlighted, add/edit/delete.
   - **Provider editor:** preset dropdown, editable `baseUrl`, masked API key field, model field, **Test Connection** button.
   - **Test connection:** `GET {baseUrl}/models`; on success, populate a model picker. Fall back to a `POST /chat/completions` with `max_tokens: 1` if `/models` is unavailable.
5. Refactor `AIGateway` to read `ProviderConfig` from `ProviderRepository` instead of `BuildConfig`. Keep Retrofit/Moshi for this phase — swap them in Phase C.
6. Remove `BuildConfig.GEMINI_API_KEY` as the runtime source of truth. It may remain as a first-launch seed only.

**Definition of Done:**
- User adds an OpenRouter provider, enters a key, selects a model, test passes, and a chat message round-trips through OpenRouter.
- User switches to Ollama (local), test passes (if Ollama is running), chat round-trips.
- API key is verifiable in Keystore and absent from all logs, files, and DataStore.

---

### Phase B — Port the Lean Core Engine

**Goal:** a headless `:core-engine` module that compiles at Java 11 and performs one streaming chat turn against a configured OpenAI-compatible endpoint.

**Tasks:**
1. Add `:core-engine` to `settings.gradle.kts`. Create `core-engine/build.gradle.kts` using the `java-library` plugin with `sourceCompatibility = JavaVersion.VERSION_11`.
2. Copy Leanarch2's `com.leanllm` source into `core-engine/src/main/java/`.
3. Run the porting audit (grep command above). Apply every replacement in the porting table.
4. Implement `OpenAiCompatibleClient implements LlmClient`:
   - Constructor takes `baseUrl`, `apiKey`, `model`, `HttpTransport`.
   - Use `HttpURLConnection` for `POST /chat/completions` with `stream: true`.
   - Parse SSE lines (`data: {...}`) with the hand-rolled JSON parser.
   - Expose `Flow`-like callback or `Iterator<String>` of deltas — **no Kotlin types in the core**.
5. Redesign tool execution: replace `ProcessBuilder` with a `ToolWorker` interface. Each tool is a Java class implementing `ToolWorker`; the registry invokes them on a thread pool.
6. Write a JUnit test that runs one streaming turn against a mock `HttpTransport`. (This is the only place JUnit is allowed — test scope only, not runtime.)

**Definition of Done:**
- `./gradlew :core-engine:compileJava` succeeds.
- `grep -rn "Thread.ofVirtual\|java.net.http\|ProcessHandle\|ProcessBuilder" core-engine/src` returns nothing.
- JUnit test passes.
- The core JAR contains no transitive dependencies (`./gradlew :core-engine:dependencies` shows only `java.base`).

---

### Phase C — Swap the Transport Under the GUI

**Goal:** the same settings screen and chat UI now run through the lean core. Retrofit and Moshi are removed.

**Tasks:**
1. Write a Kotlin adapter in `app/data/gateway/` that wraps `OpenAiCompatibleClient` in a coroutine `Flow` for Compose consumption.
2. `AIGateway` now delegates to the core client. It reads `ProviderConfig` from `ProviderRepository` and passes `baseUrl`, `apiKey` (fetched from Keystore at call time), and `model` into the core client.
3. Remove Retrofit and Moshi from `app/build.gradle.kts` dependencies. Delete `AIGateway`'s Retrofit interfaces.
4. **Do not change any Compose UI in this phase.**

**Definition of Done:**
- The Phase A acceptance tests still pass with Retrofit and Moshi removed.
- Streaming tokens render incrementally in the chat UI.
- Cancellation stops the stream and closes the connection.

---

### Phase D — Security Interface

**Goal:** every tool call pauses for user approval, and every decision is appended to an audit log.

**Tasks:**
1. Vendor Interlock Relay's `interlock-relay-core` as `:security-interface`. Verify its license permits this; if not, **stop and report** — do not reimplement it silently.
2. In `app/security/HostRelayService.kt`, start `RelayRuntime.start(applicationContext, RelayConfig())` on app launch and `stop()` on shutdown.
3. Override the `RelaySurfaces` SPI to render a Compose `ModalBottomSheet` (or dialog) for each approval request: tool name, arguments, approve/deny buttons.
4. Route all `ToolWorker` invocations from the core through the interlock. Denied calls must return a structured error to the ReAct loop, which must not crash.
5. Verify audit log persists to app-private storage (`getFilesDir()/audit/`).

**Definition of Done:**
- A chat message triggers a tool call → approval dialog appears → approving proceeds, denying halts execution gracefully.
- Audit log contains an entry for each decision, retrievable from a debug screen.
- Manifest requests only the permissions strictly needed by the selected backend. Shizuku and accessibility are **opt-in**, not declared by default.

---

### Phase E — Optional Agent Mode (NexusAgent)

**Goal:** add NexusAgent's task-specific agent screens as an *additional* navigation destination. General chat remains the default.

**Tasks:**
1. Port NexusAgent's `ui/agents` screens into `app/ui/agents/`. Wire them as a new nav destination reachable from the drawer.
2. These screens use the same `ProviderRepository` and core client — no new networking code.

**Definition of Done:**
- Both general chat and agent-mode chat are reachable and functional.
- Switching modes preserves the active provider configuration.

---

## Verification Checklist (run before declaring any phase done)

```bash
# Build the whole project
./gradlew clean assembleDebug

# Core engine must be pure Java 11, no third-party deps
./gradlew :core-engine:dependencies --configuration runtimeClasspath   # expect only java.base

# Core engine must not reference desktop-only APIs
grep -rn "Thread.ofVirtual\|java.net.http\|ProcessHandle\|ProcessBuilder" core-engine/src

# Core engine must not import Android
grep -rn "import android\." core-engine/src

# GUI must not import the core directly
grep -rn "import com.leanllm" app/src

# APK installs and launches on API 26+
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.apex.agent/.MainActivity
```

The last three greps must **all return empty**.

---

## Things That Will Bite You

1. **Subprocess-based tool execution in Leanarch2.** If the grep reveals `ProcessBuilder` threaded through `ToolRegistry` and `scripting/`, budget 15–30% of the core for redesign. Do not defer this to Phase D — it belongs in Phase B.
2. **`java.net.http` appearing in Leanarch2's `client/` module.** Replace before compilation, not after. The replacement is mechanical: `HttpURLConnection` + manual SSE line reader.
3. **Leaking keys into logs.** During Phase A, `adb logcat` and grep for the key pattern across all log levels. Keystore-backed keys must never appear.
4. **Breaking the dependency chain under pressure.** If you find yourself wanting to call `com.leanllm.*` from `:app`, add the method to the `:security-interface` SPI instead.
5. **Scope creep from Google-Ai-repo's docs.** MCP, swarms, Perchance hub, self-improving CAS — none of these are in scope. The phases above are the complete scope.
6. **License surprises.** Before vendoring any code from Leanarch2, Interlock Relay, or Google-Ai-repo, read the LICENSE file. If a license is incompatible with the distribution model, stop and report — do not silently reimplement.

---

## Reporting Format

After each phase, report:

1. **Phase name and status** (done / blocked).
2. **Build output** — paste the result of `./gradlew clean assembleDebug`.
3. **Verification results** — paste the output of the three grep checks and the dependency check.
4. **Deviations** — every place where the porting table's replacement did not apply cleanly, and what you did instead.
5. **Open questions** — anything that requires a decision before the next phase.

Do not proceed to the next phase until the current phase's Definition of Done passes and the report is filed.