# Contributing

When contributing to this repository, please first discuss the change you wish to make via issue, email, or any other method with the owners of this repository before making a change.

## Pull Request Checklist
1) Create your branch from the main branch
2) Use [Semantic Commit Messages](https://gist.github.com/joshbuchea/6f47e86d2510bce28f8e7f42ae84c716)
3) Increase the version by using [Semantic Versioning](https://semver.org)
4) Ensure your changes are covered by tests
5) Follow the rules of [Clean Code](https://gist.github.com/wojteklu/73c6914cc446146b8b533c0988cf8d29) while coding
6) No reflection is used in the code

## Coding Rules

- **Immutability**: Use `final` wherever possible. Avoid mutable state unless there’s no other option.
- **No Reflection, No Magic**: Proxies, DI containers, or runtime trickery are forbidden. Code must be GraalVM-friendly.
- **Functional Pipelines**: Prefer flat, streaming, fluent, static code. Early exits over deep nesting.
- **Threading**: IO-bound work uses virtual threads (`Executors.newVirtualThreadPerTaskExecutor()` aka `GLOBAL_THREAD_POOL`). No custom thread pools without clear justification.
- **HTTP Semantics**: Calm APIs. Reads that find nothing return `200` with an empty list. Deletes on non-existing entities return `200`.
- **Error Handling**: Use [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807) JSON problem details with stable error codes.
- **Testing**:
    - Use embedded databases/brokers for integration tests.
    - Do not use Testcontainers.
    - No Mocks if possible.
    - Assertions must be exact (AssertJ style).
    - HttpTestCalls should be RestAssured or Java's internal HttpClient.
- **Packaging**: Group by domain, not by classic layers. Every class earns its oxygen—single, reusable purpose only.  
