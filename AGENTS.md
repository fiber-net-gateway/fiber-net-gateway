# Repository Guidelines

## Project Structure & Module Organization
This is a Maven multi-module workspace anchored by `pom.xml`. Core runtime logic lives in `fiber-gateway-server` (Netty HTTP server), `fiber-gateway-httpclient` (async outbound client), `fiber-gateway-proxy` (routing, filters), and `fiber-gateway-script` (interpreter and AOT pipeline). Shared primitives sit in `fiber-gateway-common` and resiliency helpers in `fiber-gateway-support`. `fiber-gateway-example` produces the runnable JAR plus sample scripts, while `fiber-gateway-test` hosts IO-thread test runners and fixtures. Refer to `doc/` for design notes and `confs/` for baseline YAML/JSON configs.

## Build, Test, and Development Commands
- `mvn clean package -DskipTests` builds every module and stages jars under each `target/`.
- `mvn test` executes the full JUnit test suite across modules; run with `-pl <module> -am` to scope changes.
- `mvn -pl fiber-gateway-example -am package` assembles the distributable gateway demo.
- `java -jar fiber-gateway-example/target/fiber-gateway-example-1.0-SNAPSHOT.jar scripts` boots the example against a directory of `.js` scripts (see `README.md` for creating `scripts/fiber-net.js`).

## Coding Style & Naming Conventions
Use Java 8 semantics with 4-space indentation and UTF-8 source files. Favor small, immutable helpers in `io.fiber.net.*` packages, `UpperCamelCase` for classes, `lowerCamelCase` for methods/fields, and `UPPER_SNAKE_CASE` for constants. Netty callbacks must stay non-blocking; defer heavy work to async utilities in `fiber-gateway-common`. This gateway framework is performance-first: minimize memory copies and allocations, avoid redundant defensive checks such as unnecessary `!= null` branches, and keep code compact when clarity is not harmed. Keep script files kebab-cased (e.g., `sum-array.js`) to match loader expectations.

## Testing Guidelines
Unit tests live beside their modules under `src/test/java` and rely on JUnit 4 plus the custom `io.fiber.net.test.IOThreadRunner` when Netty IO threads are required. Mirror production package names so reflective lookups stay consistent. Favor descriptive method names such as `shouldBalanceAcrossConnections`. Run `mvn test -pl fiber-gateway-<module>` locally before pushing; integration-heavy changes should also exercise the runnable example against `curl` smoke tests documented in `README.md`.

## Commit & Pull Request Guidelines
Recent history favors short, imperative subject lines (e.g., `add route ver`, `refactor engine class`). Follow the same pattern, keeping body text for rationale or perf numbers. Each pull request should include: concise summary, affected modules, reproduction steps or scripts, and references to tracking issues. Attach curl traces, screenshots, or benchmark tables when changing behavior visible to end users, and mention any doc updates (e.g., `doc/dev.md`) you touched.

## Security & Configuration Tips
Never commit secrets from `confs/` or `logs/`; treat those directories as templates only. Validate that any new script-built features gate external calls through allowlists defined in `fiber-gateway-proxy`. When adding configuration keys, document defaults in `doc/user.md` and surface safe fallbacks so misconfiguration does not expose the embedded HTTP client.
