# AI & Automation Guidance

Nano expects automation agents to follow the same invariants we ask humans to keep. Internalise the checklist below before generating code.

## Core Principles
- **Functional style** – keep business logic in static methods, use records/final fields, avoid shared state.
- **Event-driven** – interact through channels, never by calling services directly.
- **Infrastructure-only services** – subclasses of `Service` provide connectors (HTTP, DB, file, metrics) and stay free of business logic.
- **TypeMap everywhere** – lean on Nano’s converters; no reflection or custom DTO forests.
- **No `null` returns** – use Optionals/empty collections instead.
- **UTC timestamps + deterministic inputs** – inject `Clock`, seed randomness, avoid environment leaks.

## Canonical Channels
| Channel | Purpose |
|---------|---------|
| `EVENT_HTTP_REQUEST` / `EVENT_HTTP_REQUEST_UNHANDLED` | Incoming HTTP pipeline + fallback |
| `EVENT_SEND_HTTP` | Outbound HTTP via `HttpClient` |
| `EVENT_METRIC_UPDATE` | Counters, gauges, timers |
| `EVENT_CONFIG_CHANGE` | Broadcast config overlays (never acknowledge) |
| `EVENT_FILE_WATCH` / `EVENT_FILE_CHANGE` | FileWatcher registrations and notifications |
| `EVENT_APP_ERROR` | Global error interception |

## Service Checklist
1. Pull config from `Context` in `start()` or `configure(changes, merged)`.
2. Listen for events in `onEvent`.
3. Use `context.newEvent(...).send()` for cross-service coordination.
4. Guard config updates with `Objects.equals` to avoid no-op reconfigures.
5. Shutdown cleanly in `stop()`.

## Testing Expectations
- Prefer component tests (real HTTP, embedded stores) over mocks.
- Run tests concurrently unless there is a deterministic reason not to.
- Inject deterministic clocks/seeds and clean up temp resources.

## Tooling Tips
- Target Java LTS (21+) and depend on `org.nanonative:nano:2025.1.3`.
- Read the [Quick Start](../quickstart/README.md) and service-specific guides before coding.
- Use `TypeMap`/`HttpObject` helpers instead of rolling custom transport types.

Follow these guardrails and both humans and AI tools produce idiomatic Nano code. 
