# typesafe-sdk-kotlin

[![Maven Central](https://img.shields.io/maven-central/v/net.bontal/typesafe-sdk-kotlin.svg)](https://central.sonatype.com/artifact/net.bontal/typesafe-sdk-kotlin)
[![Build](https://github.com/pewriebontal/typesafe-sdk-kotlin/actions/workflows/build.yml/badge.svg)](https://github.com/pewriebontal/typesafe-sdk-kotlin/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A Kotlin and Java client for the [TypeSafe AI](https://docs.typesafe.ai) System One API
(`POST /v1/systemone` and `GET /v1/models`). It is a Kotlin Multiplatform library with Android and
JVM targets, and it can be called from Kotlin or Java.

This is an independent SDK maintained by Bontal LLC. It is not an official TypeSafe AI product.

## Requirements

- Java 11 or newer, or Android API level 21 or newer.
- Kotlin 2.2 or newer, when the SDK is used from Kotlin.
- A Ktor client engine, version 3.3.3 or newer.

## Installation

### Gradle

```kotlin
dependencies {
    implementation("net.bontal:typesafe-sdk-kotlin:0.1.0")
    implementation("io.ktor:ktor-client-okhttp:3.3.3")
}
```

The SDK depends on `ktor-client-core` and leaves the choice of HTTP engine to the application:

| Engine | Typical use |
|---|---|
| `io.ktor:ktor-client-okhttp` | Android and JVM |
| `io.ktor:ktor-client-cio` | JVM, with no dependencies beyond Kotlin coroutines |
| `io.ktor:ktor-client-apache5` | JVM applications that already use Apache HttpClient 5 |
| Any other `HttpClientEngine` | Pass it to `engine(...)` |

Gradle selects the Android or JVM variant of the SDK automatically.

### Maven

Maven does not read Gradle module metadata, so Maven projects depend on the JVM artifact and on
the `-jvm` variant of the Ktor engine:

```xml
<dependency>
  <groupId>net.bontal</groupId>
  <artifactId>typesafe-sdk-kotlin-jvm</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>io.ktor</groupId>
  <artifactId>ktor-client-okhttp-jvm</artifactId>
  <version>3.3.3</version>
</dependency>
```

## Usage

### Kotlin

```kotlin
val client = TypeSafeClientAsync { apiKey(System.getenv("TYPESAFE_API_KEY")) }

val result = client.systemOne(
    SystemOneRequest {
        state("I was charged twice. Please help.")
        putQuestion(
            "department",
            ChoiceQuestion {
                instructions("Which team should handle this")
                putCriterion("billing", "Payment or subscription issues")
                putCriterion("technical", "Bugs or integration problems")
            },
        )
        putQuestion(
            "urgency",
            ScoreQuestion {
                instructions("How urgent is this message")
                addCriterion("Can wait")
                addCriterion("Needs attention this week")
                addCriterion("Needs attention today")
            },
        )
        putQuestion("spam", NoulQuestion { instructions("Is this message spam") })
    },
)

println(result.choices.getValue("department").choice)
println(result.scores.getValue("urgency").score)
println(result.nouls.getValue("spam").noul)

client.close()
```

### Java

```java
TypeSafeClient client = TypeSafeClient.builder()
    .apiKey(System.getenv("TYPESAFE_API_KEY"))
    .build();

SystemOneRequest request = SystemOneRequest.builder()
    .state("I was charged twice. Please help.")
    .putQuestion("department", ChoiceQuestion.builder()
        .instructions("Which team should handle this")
        .putCriterion("billing", "Payment or subscription issues")
        .putCriterion("technical", "Bugs or integration problems")
        .build())
    .build();

SystemOneResult result = client.systemOne(request);
System.out.println(result.getChoices().get("department").getChoice());

client.close();
```

In Kotlin, every model is created with a `Type { ... }` function. In Java, every model has a
`Type.builder()`. All models are immutable and provide `toBuilder()`.

### Blocking, coroutine, and future clients

- `TypeSafeClientAsync` has `suspend` functions for coroutine code.
- `TypeSafeClient` blocks the calling thread. On Android it throws if called on the main thread.
- `TypeSafeFutureClient.of(client)` returns `CompletableFuture` results on the JVM. Cancelling a
  future cancels its request. Closing the future client cancels pending futures but does not close
  the underlying client.

`client.async()` and `client.sync()` switch between the blocking and coroutine clients and share
one connection pool. Create one client per application and close it on shutdown.

### Per-request options

```kotlin
client.systemOne(
    request,
    RequestOptions {
        timeout(5.seconds)
        retry(RetryStrategy.NONE)
        putHeader("X-Trace-Id", traceId)
    },
)
```

`RequestOptions` override the timeout, retry policy, and headers for a single call.
`withOptions` returns a client with a modified configuration:

```kotlin
val pinned = client.withOptions { it.defaultModel("jev-1.13.0") }
```

A client returned by `withOptions` shares the parent's connection pool. Closing the parent closes
the pool. Closing the copy has no effect unless the copy changed `engine` or `httpClientConfig`.

An engine passed to `engine(...)` belongs to the caller. Closing the client does not close it, so
close the engine yourself when it is no longer needed.

### Raw responses

```kotlin
val raw: HttpResponseFor<SystemOneResult> = client.withRawResponse().systemOne(request)
raw.statusCode
raw.headers["x-typesafe-request-id"]
raw.requestId
raw.body
```

Header lookups are case-insensitive.

### OpenRouter and other gateways

Any service that implements the [TypeSafe OpenAPI specification](https://api.typesafe.ai/openapi.json)
can be used by setting `baseUrl`, `apiKey`, and `defaultModel`:

```kotlin
val client = TypeSafeClient {
    apiKey(System.getenv("OPENROUTER_API_KEY"))
    baseUrl("https://openrouter.ai/api")
    defaultModel("typesafe/jev-1.13")
}
```

Differences to be aware of when using OpenRouter:

- Model names use OpenRouter's format, for example `typesafe/jev-1.13` or `~typesafe/jev-latest`.
- `models()` expects the TypeSafe model list and does not work with OpenRouter's catalog.
- Every question must have `instructions`, and `NoulCriteria` must set both `yes` and `no`.
- Error responses carry OpenRouter's `request-id` header, so `requestId` is `null`.
- Responses include `usage.cost` in USD, which can be read from the raw response body.

## Configuration

| Setting | System property | Environment variable | Default |
|---|---|---|---|
| `apiKey` | `typesafe.apiKey` | `TYPESAFE_API_KEY` | None, required |
| `baseUrl` | `typesafe.baseUrl` | `TYPESAFE_BASE_URL` | `https://api.typesafe.ai` |
| `defaultModel` | `typesafe.defaultModel` | `TYPESAFE_DEFAULT_MODEL` | `jev-latest` |
| `logLevel` | `typesafe.logLevel` | `TYPESAFE_LOG_LEVEL` | `WARN` |
| `timeout` | | | 10 seconds per attempt |
| `retry` | | | `RetryStrategy.DEFAULT` |
| `headers` | | | None |
| `engine`, `httpClientConfig`, `logger` | | | Ktor default engine, platform logger |

A value set on the builder takes precedence, followed by the system property, then the
environment variable, then the default. Blank values are ignored. `TypeSafeClient.fromEnv()`
builds a client from system properties and environment variables alone.

Android apps cannot read environment variables, so they must set `apiKey` on the builder. API keys
are trimmed, and keys that contain whitespace, control characters, or non-ASCII characters are
rejected before any request is sent.

### Retries

By default the client follows the retry policy in the TypeSafe documentation:

- Up to 2 retries after the first attempt.
- Retries on status 408, 429, and 500 to 599 (including 529), on connection errors, and on timeouts.
- Exponential backoff starting at 500 ms, capped at 5 seconds, with up to 25% jitter.
- `retry-after-ms` and `Retry-After` response headers are honored when they request 60 seconds
  or less.
- The whole call, including retries, has a budget of 30 seconds. Each attempt's timeout is
  shortened to fit the remaining budget, and no retry starts if its delay would exceed it.

Each setting can be changed with `RetryStrategy.builder()`. `RetryStrategy.NONE` disables retries
and the budget, so only the per-attempt `timeout` applies.
Retried requests carry an `X-TypeSafe-Retry-Count` header.

### Logging

At `INFO`, the client logs one line per request. At `DEBUG`, it also logs headers and bodies.
Headers that carry credentials, such as `Authorization`, API keys, cookies, tokens, and secrets,
are redacted. Request and response bodies are not redacted.

Logs go to `System.Logger` (name `net.bontal.typesafesdk`) on the JVM and to `android.util.Log`
(tag `TypeSafe`) on Android. Use `logger { level, message -> ... }` to send them elsewhere.

On the JVM, `DEBUG` messages are logged at `System.Logger.Level.DEBUG`. With the default
`java.util.logging` configuration, which prints `INFO` and above, they are not shown until the
`net.bontal.typesafesdk` logger and its handler are set to `FINE`, or until a logging bridge such
as SLF4J routes them.

## Error handling

All exceptions thrown by the client extend `TypeSafeException`.

| Exception | Thrown when |
|---|---|
| `BadRequestException` | The API returned 400. |
| `AuthenticationException` | The API returned 401. |
| `PermissionDeniedException` | The API returned 403. |
| `NotFoundException` | The API returned 404. |
| `UnprocessableEntityException` | The API returned 422. |
| `RateLimitException` | The API returned 429. `retryAfterMillis` holds the requested wait. |
| `InternalServerException` | The API returned a 5xx status. |
| `UnexpectedStatusCodeException` | The API returned any other non-2xx status. |
| `ApiResponseValidationException` | A 2xx response was missing required data. `fieldPath` names the field, for example `answers.tone.confidence`. |
| `ApiConnectionException` | The request failed without an HTTP response. |
| `ApiTimeoutException` | An attempt exceeded the timeout. Extends `ApiConnectionException`. |

The status exceptions extend `ApiException`, which provides `statusCode`, `body` (`null` for an
empty body), `headers`, `endpoint`, and `requestId`. `endpoint` contains the method and URL without
credentials, query string, or fragment. A typical message is:

```
401 Unauthorized from GET https://api.typesafe.ai/v1/models: Invalid API key (request id req_1)
```

Invalid arguments throw `IllegalArgumentException`. Building a model without a required field
throws `IllegalStateException`. Calls on a closed client throw `IllegalStateException` and are not
retried.

## Forward compatibility

- `SystemOneRequest.Builder.putAdditionalBodyProperty` sends request fields that the SDK does not
  model yet. They are added last and replace any field with the same name.
- `RawQuestion(jsonObject)` sends a question object unchanged, including question types that the
  SDK does not know about.
- Answers of an unknown type are skipped and logged as a warning. They remain available in the raw
  response body.
- Unknown fields in responses are ignored.

## Platform notes

### Android

Use `TypeSafeClientAsync` from a coroutine and add the `INTERNET` permission. Do not ship a
long-lived API key in an APK. Call the API from your own backend, or have your backend issue
short-lived keys. The SDK uses no reflection and needs no R8 or ProGuard keep rules.

### Spring Boot

Spring Boot's dependency management sets the versions of Kotlin, kotlinx-coroutines, and
kotlinx-serialization for the whole application, including libraries. A Ktor engine that requires
newer versions than Spring Boot provides fails at runtime with `NoSuchMethodError`.

| Spring Boot | Ktor engine | Required overrides |
|---|---|---|
| 4.0 and 4.1 | 3.3.x | None |
| 3.5 | 3.3.x | `kotlin.version` = 2.2.21, `kotlin-coroutines.version` = 1.10.2, `kotlin-serialization.version` = 1.9.0 |

For Spring Boot 3.5 with Gradle:

```kotlin
extra["kotlin.version"] = "2.2.21"
extra["kotlin-coroutines.version"] = "1.10.2"
extra["kotlin-serialization.version"] = "1.9.0"
```

With Maven, set the same three properties in `<properties>`.

Register the client as a singleton bean so that it is closed on shutdown:

```java
@Bean(destroyMethod = "close")
TypeSafeClient typeSafeClient() {
    return TypeSafeClient.builder().apiKey(apiKey).build();
}
```

### Tested versions

The SDK has been tested with Java 11, Kotlin 2.2.21, a Ktor 3.3.3 server application, Spring Boot
3.5.16, 4.0.8, and 4.1.1 (with Gradle and Maven), and an Android application with minimum API level
21 built with R8 enabled.

## Examples

The [`examples`](examples) directory contains runnable programs for support ticket triage,
confidence-based routing, intent routing, agent tool-call checks, batch classification, weighted
scoring, error handling, and Java usage. See [`examples/README.md`](examples/README.md).

## Development

```bash
./gradlew build                 # Compile and test all targets
./gradlew apiCheck              # Compare the public API with api/jvm/typesafe-sdk-kotlin.api
./gradlew apiDump               # Update the API file after an intended API change
./gradlew dokkaGenerate         # Generate API documentation
./gradlew publishToMavenLocal   # Install into ~/.m2 for local testing
```

Tests that call the real APIs are skipped unless `TYPESAFE_LIVE_TESTS=1` is set. With only that
variable set, they test error handling. Setting `TYPESAFE_API_KEY` or `OPENROUTER_API_KEY` as well
enables requests to the TypeSafe API or to OpenRouter.

```bash
TYPESAFE_LIVE_TESTS=1 TYPESAFE_API_KEY=... ./gradlew jvmTest --tests '*LiveApiTest*'
```

The public API is declared explicitly with `explicitApi()`. `apiCheck` runs in CI and fails the
build when a change would break binary compatibility.

## License

MIT. See [LICENSE](LICENSE).
