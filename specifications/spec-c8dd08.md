# Build Instructions: Fixing Provider Configuration, Token Limits, and Cleanup

## Overview
The latest APK build is functional, but requires immediate updates to allow dynamic model testing and to resolve API token limit errors. Additionally, residual architectural cleanup is needed to fully align with the "Agent" rebranding and remove the obsolete Perchance generator.

## Step 1: Make the Model ID Fully Editable

Based on the current architecture (`ProviderConfigurationManager` using DataStore and `ProviderPresets.kt`), the model field is currently read-only. Implement the following changes:

### 1.1 Data Layer
*   Extend `ProviderConfigurationManager` to include an `updateModelId(providerId: String, newModelId: String)` method.
*   Ensure the DataStore schema allows writing a custom string to the `defaultModel` field instead of falling back to presets.

### 1.2 UI Layer (`SettingsScreen.kt`)
Replace the static model text with a Compose `OutlinedTextField` (or `TextField`) bound to a `mutableStateOf` variable. Add an inline "Save" or "Check" icon next to the field to trigger the DataStore update and refresh the `ProviderRegistry` `StateFlow` on the fly.

```kotlin
// Example UI snippet for SettingsScreen.kt
var editableModel by remember { mutableStateOf(provider.defaultModel) }

Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(
        value = editableModel,
        onValueChange = { editableModel = it },
        label = { Text("Model ID") },
        singleLine = true,
        modifier = Modifier.weight(1f)
    )
    IconButton(onClick = { 
        viewModel.updateProviderModel(provider.id, editableModel) 
    }) {
        Icon(Icons.Default.Check, contentDescription = "Save Model ID")
    }
}
```

## Step 2: Fix the Token Limit Request Error

The `max_tokens` / `max_output_tokens` parameter is almost certainly either missing or configured too high for the newly selected model (many OpenAI-compatible endpoints default to 4096 or 8192, and models like `gemini-2.5-flash` cap at 65,536).

### 2.1 Centralize Token Limits
Add a `maxTokens: Int` field to `ProviderPresets.kt` and your provider data model. Default values should be:
*   Standard models: `4096`
*   `deepseek-chat`: `8192`
*   `gemini-2.5-flash`: `65536`

### 2.2 Payload Injection
In `AIGateway.kt` / `OpenAiCompatibleClient.java`, parse this field and explicitly include `"max_tokens": <value>` (or `"max_completion_tokens"` for newer OpenAI specs) in the JSON payload sent to `/chat/completions`.

### 2.3 Error Handling
Catch `HTTP 400` responses containing token-limit messages and surface a user-facing error on the Chat screen indicating that the prompt is too long or the output limit needs to be reduced.

## Step 3: Clean Up Residual Navigation & File Names

The README still lists `PerchanceBrowserScreen.kt` in the `:app` navigation tree. Since the Perchance generator scope was officially removed:
*   Delete `PerchanceBrowserScreen.kt` and remove its route from `AppNavigation.kt`.
*   Rename `NarratorOrchestrator.kt` to `AgentOrchestrator.kt` across the domain layer and update `ChatScreen.kt` references.

## Step 4: Architectural Verification

Once these changes are applied, run the following validation steps:

1.  **Run DataStore Migration Test**: Verify that changing a model ID in Settings survives an app restart (DataStore persistence).
2.  **Live API Ping**: Use the "Test Connection" feature in Settings to confirm the newly entered model ID returns a valid response without hitting the token cap.
3.  **Audit Trail Check**: Ensure model-switching events do not break the SHA-256 hash chain in `AuditLogManager`.