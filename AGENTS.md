# Repository Guidelines

Nano favors static, event-driven Java code with zero reflection and minimal dependencies. Treat this guide as the fastest path to productive contributions without violating the framework’s immutability and functional constraints.

## Project Structure & Module Organization
- Core source lives under `src/main/java/org/nanonative/nano`, grouped by domain (`core`, `services`, `helper`, etc.) instead of classic MVC layers.
- Tests and runnable examples sit in `src/test/java`, while fixtures (PNG logos, JSON payloads) are under `src/test/resources`.
- Long-form docs live in `docs/` (concept, context, services), and build artifacts land in `target/`. Keep generated files out of version control.

## Build, Test, and Development Commands
- `./mvnw clean compile` – compile all sources with Java 21 toolchain.
- `./mvnw test` – execute the JUnit 5 suite with AssertJ assertions.
- `./mvnw clean verify` – run tests plus Jacoco coverage (`target/site/jacoco/index.html`).
- `./mvnw clean package` – produce the distributable JAR in `target/`.
- `./mvnw clean deploy -P release` – semantic-release build (requires configured GPG and credentials).

## Coding Style & Naming Conventions
- Favor static methods, fluent chains, and `final` fields; never introduce shared mutable state or `null` returns.
- Event constants use screaming snake case (`EVENT_HTTP_RECEIVE`), packages remain `org.nanonative.nano.<domain>`.
- Indentation is 4 spaces; rely on `spotless:apply` if present in your toolchain but keep formatting deterministic.
- Zero reflection, no parallel streams, and only virtual threads (`Executors.newVirtualThreadPerTaskExecutor`) for concurrency.

## Testing Guidelines
- Write component tests in `src/test/java`, naming files `*Test` and methods with `should...` to express behavior.
- Use embedded resources (Flapdoodle Mongo, in-memory HTTP) instead of mocks; prefer real HTTP calls or WireMock stubs.
- Keep tests deterministic by injecting `Clock` or seeds, and assert exact payloads. Failing tests must reproduce locally with `./mvnw test -DfailIfNoTests=false`.

## Commit & Pull Request Guidelines
- Follow semantic commit messages (`feat: add metric cache eviction`) and bump versions per SemVer when shipping artifacts.
- Branch from `main`, describe intent, link issues, and attach logs/screenshots for behavioral changes or UI assets.
- PRs must list test evidence (`./mvnw clean verify` output) and restate any config toggles touched (e.g., `CONFIG_ENV_PROD`).

## Security & Configuration Tips
- Avoid storing secrets; reference environment variables in docs and sample configs only.
- Use the provided `docs/context` references to wire services (HTTP, metrics, logging) and prefer atomic Mongo updates via `mongoTemplate.findAndModify`.
- When targeting GraalVM, stick to the `native-image` profile in `pom.xml` and confirm compatibility before requesting review.
