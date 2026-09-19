# Changelog

All notable changes to this project are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Client for the TypeSafe System One API: `POST /v1/systemone` and `GET /v1/models`.
- Android (API level 21 and newer) and JVM (Java 11 and newer) targets.
- `TypeSafeClient` for blocking calls, `TypeSafeClientAsync` for coroutines, and
  `TypeSafeFutureClient` for `CompletableFuture` on the JVM.
- Raw response access through `withRawResponse()`.
- Per-request options through `RequestOptions`, and modified client copies through
  `withOptions`.
- `NoulQuestion`, `ChoiceQuestion` (1 to 255 options), `ScoreQuestion` (1 to 10 levels), and
  `RawQuestion`.
- Typed answer maps `choices`, `scores`, and `nouls` on `SystemOneResult`.
- Configuration from the builder, `typesafe.*` system properties, and `TYPESAFE_*` environment
  variables.
- Automatic retries that follow the TypeSafe retry policy, configurable through `RetryStrategy`.
- Exceptions for each error status, with the status code, body, headers, endpoint, and request ID.
- Logging with redaction of credential headers.
- Support for OpenRouter and other services that implement the TypeSafe API.
