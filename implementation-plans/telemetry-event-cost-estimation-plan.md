# TelemetryEvent and CostEstimation Implementation Plan

Defines the `TelemetryEvent` POJO in `apex-chat-core`, extending `ChatLifecycleEvent` and composing `GenerationTelemetry` alongside a new `CostEstimation` POJO to enable reactive, audit-ready performance and cost monitoring across LLM invocations.

## User Review & Critical Decisions

> [!IMPORTANT]
> - **EventSystem Integration**: `TelemetryEvent` directly extends `ChatLifecycleEvent` with `ChatEventType.TELEMETRY_RECORDED`, making it instantly compatible with `ChatEventRegistry` and reactive listeners attached to `chat.getEventSystem()`.
> - **Compositional Cleanliness**: Rather than duplicating token usage and latency fields, `TelemetryEvent` composes the existing `GenerationTelemetry` (input/output tokens, cached tokens, reasoning tokens, TTFT, total latency, tokens/sec) and composes a dedicated `CostEstimation` POJO (input cost, output cost, reasoning cost, total cost, currency code, and pricing tier).

- **Confirmed Decision**: Extend `ChatLifecycleEvent` while composing `GenerationTelemetry` and `CostEstimation`.

---

## 1. Component Architecture & Data Model

```
┌───────────────────────────────────────────────────────────────────────────┐
│              ChatLifecycleEvent (from com.apex.chat.core.event)           │
│  ├── eventId: String                                                      │
│  ├── eventType: ChatEventType.TELEMETRY_RECORDED                          │
│  ├── timestamp: Instant                                                   │
│  └── chatId: String                                                       │
└─────────────────────────────────────▲─────────────────────────────────────┘
                                      │ extends
┌─────────────────────────────────────┴─────────────────────────────────────┐
│                              TelemetryEvent                               │
│  ├── modelIdentifier: String (e.g. "gemini-2.5-flash")                    │
│  ├── turnIndex: Integer                                                   │
│  ├── stepIndex: Integer                                                   │
│  │                                                                        │
│  ├── telemetry: GenerationTelemetry                                       │
│  │     ├── promptTokens: int                                              │
│  │     ├── completionTokens: int                                          │
│  │     ├── totalTokens: int                                               │
│  │     ├── cachedTokens: Integer                                          │
│  │     ├── reasoningTokens: Integer                                       │
│  │     ├── timeToFirstTokenMs: long                                       │
│  │     └── totalLatencyMs: long                                           │
│  │                                                                        │
│  └── costEstimation: CostEstimation                                       │
│        ├── inputCostUsd: Double                                           │
│        ├── outputCostUsd: Double                                          │
│        ├── reasoningCostUsd: Double                                       │
│        ├── totalCostUsd: Double                                           │
│        ├── currencyCode: String ("USD")                                   │
│        └── pricingTierIdentifier: String                                  │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Granular Class Specifications

### Package: `com.apex.chat.core.telemetry` (or `com.apex.chat.core.event`)
1. **`CostEstimation`**:
   - `inputCostUsd` (Double)
   - `outputCostUsd` (Double)
   - `reasoningCostUsd` (Double)
   - `totalCostUsd` (Double)
   - `currencyCode` (String, default "USD")
   - `pricingTierIdentifier` (String)
   - Fluent chainable setters, constructors, equality, and hash code.

2. **`TelemetryEvent extends ChatLifecycleEvent`**:
   - `modelIdentifier` (String)
   - `turnIndex` (Integer)
   - `stepIndex` (Integer)
   - `telemetry` (GenerationTelemetry)
   - `costEstimation` (CostEstimation)
   - Static factory helper: `of(String chatId, String model, GenerationTelemetry telemetry, CostEstimation cost)`.

3. **`ChatEventType` Update**:
   - Add `TELEMETRY_RECORDED` to the enumeration.

---

## 3. Unit Verification Plan
- Extend `ChatCoreTest.java` to verify:
  - `TelemetryEvent` instantiation and composition of `GenerationTelemetry` + `CostEstimation`.
  - Publishing `TelemetryEvent` through `ChatEventSystem` and catching it in a type-specific `ChatEventListener`.
  - Correct event inheritance, properties, and getters/setters.
