# Build Spec: Persistent, Editable Provider Configurations & Native Clipboard Support

## Context & Clarification
- Clipboard: The inability to paste in the AI Studio preview is a known limitation of the web-based preview environment (iframe restrictions). In a compiled Android APK, standard Compose TextField components automatically interface with the native ClipboardManager. Long-pressing a field will surface the native "Paste" dialog without requiring any custom bridge code.
- Provider List: The current UI (Screenshot 3) shows hardcoded providers (DeepSeek, OpenAI, NVIDIA NIM) with static Base URLs and Models. To meet the requirement of a "persistent, and editable providers list", this must be upgraded to a full CRUD (Create, Read, Update, Delete) configuration model.

## Priority 1 — Dynamic Provider Configuration (CRUD)

### 1. Data Model & Persistence Layer
**Build spec**
- Define a `ProviderConfig` data class to replace the hardcoded list. (Code shown below without nested backticks):
    
    data class ProviderConfig(
        val id: String = UUID.randomUUID().toString(),
        val displayName: String,     // e.g., "Custom Groq"
        val baseUrl: String,         // e.g., "https://api.groq.com/openai/v1"
        val modelId: String,         // e.g., "llama3-70b-8192"
        val isActive: Boolean = false
    )

- Implement persistence using Room Database (preferred for structured CRUD lists) or Proto DataStore.
- Create `ProviderRepository` with flows:
  - `getAllProviders(): Flow<List<ProviderConfig>>`
  - `addProvider(config: ProviderConfig)`
  - `updateProvider(config: ProviderConfig)`
  - `deleteProvider(id: String)`
  - `setActiveProvider(id: String)`

### 2. Secure API Key Management
**Build spec**
- Do not store the API key in the `ProviderConfig` database entity.
- Store keys securely in the `AndroidKeyStoreManager` or EncryptedSharedPreferences, keyed by the `ProviderConfig.id` (e.g., `key_provider_12345`).
- The repository should expose a method `getApiKey(id: String): String?` to fetch the decrypted key only when making a network request.
- Expose a `hasApiKey(id: String): Flow<Boolean>` so the UI can display "Key: Configured" vs "Key: Not Set" without exposing the actual key.

### 3. UI Implementation (Settings Screen)
**Build spec**
- List View: Update the Settings screen to observe `getAllProviders()` and render a dynamic list.
- Add/Edit Modal: Add an "Edit" (pencil) icon to each provider card, and a generic "Add Custom Provider" button. Tapping either opens a Dialog/BottomSheet with:
  - TextField: Display Name
  - TextField: Base URL
  - TextField: Model ID
  - TextField: API Key (Password VisualTransformation, standard Compose TextField to enable native clipboard paste)
- Actions: Implement "Save", "Cancel", and "Delete".
- Acceptance: User can add a brand new provider (e.g., "Groq"), edit the Base URL and Model ID, save it, restart the app, and see the custom provider persisted in the list.

## Priority 2 — AI Gateway Integration & Native APK Testing

### 4. Refactor AIGateway for Dynamic Providers
**Build spec**
- Remove hardcoded provider enums/classes.
- Update `AIGateway` to instantiate an HTTP client dynamically based on the `ProviderConfig.baseUrl` and the decrypted API key.
- Since most modern providers offer OpenAI-compatible endpoints (as seen in your screenshot for Gemini, DeepSeek, etc.), standardize the network request payload to the OpenAI schema (`/chat/completions`).
- Ensure the `AgentOrchestrator` pulls the active `ProviderConfig` and its corresponding key via StateFlow before triggering a chat.
- Acceptance: Changing the active provider in the UI immediately routes subsequent chat messages to the new Base URL with the new Model ID.

### 5. APK Build & Verification
**Build spec**
- Run `gradle assembleDebug` to generate the APK.
- Install on a physical device or emulator with Google Play Services.
- Clipboard Test: Long-press the API Key field in the Settings dialog. Verify the native Android clipboard text bubble ("Paste") appears, and tapping it pastes the key.
- End-to-End Test: Add a provider, paste a key, set it active, and send a message in the Chat screen. Verify a successful response returns from the live API.

## Implementation Notes for AI Studio Build Agent
- Clipboard: Explicitly rely on Jetpack Compose's `LocalClipboardManager` or standard `TextField` clipboard behavior. Do not attempt to write custom JavaScript bridges for clipboard access, as they will fail in the preview and are unnecessary for the compiled APK.
- Security Check: Ensure API keys are never logged to Logcat or stored in plain text in the Room database.