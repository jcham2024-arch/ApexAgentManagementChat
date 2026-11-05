# engine-json

**Tier:** 0 · **Purpose:** Minimal JSON parser shared by engine-tools-impl and engine-llm-http.

## Allowed dependencies
java.base + jspecify only

## Ports consumed
none

## Ports implemented
none — utility module

## Types
- `Json` — parseObject(String): Map<String, Object>
- `JsonException` — RuntimeException on malformed input

## Tests
`JsonTest`
