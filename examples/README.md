# Examples

Runnable programs that use the SDK, based on the patterns in the
[TypeSafe documentation](https://docs.typesafe.ai/patterns). Each example is a single file in
[`src/main`](src/main).

## Running the examples

Set an API key, then run one or more examples by name:

```bash
read -rs TYPESAFE_API_KEY && export TYPESAFE_API_KEY
./gradlew :examples:run --args="support-triage"
```

`read -rs` reads the key without echoing it. Paste the key and press Enter.

To use OpenRouter instead of the TypeSafe API, set `OPENROUTER_API_KEY`. The examples then call
OpenRouter with the `typesafe/jev-1.13` model:

```bash
read -rs OPENROUTER_API_KEY && export OPENROUTER_API_KEY
./gradlew :examples:run --args="agent-guardrail batch-classification"
```

Without arguments, every example runs. Set `TYPESAFE_LOG_LEVEL=info` to log each request. Client
setup is in [`Clients.kt`](src/main/kotlin/net/bontal/typesafesdk/examples/Clients.kt).

## List of examples

| Name | Client | Question types |
|---|---|---|
| [`support-triage`](src/main/kotlin/net/bontal/typesafesdk/examples/SupportTriage.kt) | Blocking | Choice, Score |
| [`confidence-routing`](src/main/kotlin/net/bontal/typesafesdk/examples/ConfidenceRouting.kt) | Blocking | Choice |
| [`intent-routing`](src/main/kotlin/net/bontal/typesafesdk/examples/IntentRouting.kt) | Blocking | Choice, Score |
| [`error-handling`](src/main/kotlin/net/bontal/typesafesdk/examples/ErrorHandling.kt) | Blocking | Noul |
| [`java`](src/main/java/net/bontal/typesafesdk/examples/JavaQuickstart.java) | Blocking and future | Choice, Noul |
| [`agent-guardrail`](src/main/kotlin/net/bontal/typesafesdk/examples/AgentGuardrail.kt) | Coroutines | Noul |
| [`batch-classification`](src/main/kotlin/net/bontal/typesafesdk/examples/BatchClassification.kt) | Coroutines | Choice, Noul |
| [`composite-scoring`](src/main/kotlin/net/bontal/typesafesdk/examples/CompositeScoring.kt) | Coroutines | Score |

### support-triage

Classifies support tickets by category, severity, and customer frustration in a single request,
then routes each ticket in code. The severity answer is only used when the category is `bug`.
Because the API evaluates all questions in one call, asking extra questions adds input tokens but
no extra round trip.

```kotlin
val result = client.systemOne(triageRequest(ticket))
val queue = when (result.choices.getValue("category").choice) {
    "bug" -> if (result.scores.getValue("severity").score >= 1.5) "page the on-call engineer" else "engineering backlog"
    "billing" -> "billing team"
    else -> "help-center bot"
}
```

### confidence-routing

Interprets requests to a voice banking assistant and acts only when the answer's `confidence` is
above a threshold for that action: 0.6 to read a balance, 0.75 to freeze a card, and 0.85 to
transfer money. Between 0.6 and the action's threshold, the assistant asks the caller to confirm.
Below 0.6, it transfers the call to a person.

### intent-routing

Sends a JSON object as `state` and decides which handler should answer each customer message: a
FAQ lookup, an LLM agent, or a specialist. When the complexity score has low confidence, the
message goes to a person for triage. The example also reads the request ID from
`withRawResponse()`.

### error-handling

Shows per-request `RequestOptions`, a client copy created with `withOptions`, and `catch` blocks
ordered from the most specific exception (`RateLimitException`) to the most general
(`TypeSafeException`). It also triggers a validation error and an authentication error on
purpose.

### java

Uses the builders from Java, reads answers with `getChoices()` and `getNouls()`, runs two requests
concurrently with `TypeSafeFutureClient` and `thenCombine`, and handles an `ApiException`.

### agent-guardrail

Checks each tool call proposed by an AI agent against the user's original request. The checks run
concurrently with `async` and `awaitAll`. A call runs automatically at 80% or higher, is refused
below 20%, and otherwise waits for approval.

```kotlin
val reviews = proposedCalls.map { call -> async { review(client, call) } }.awaitAll()
```

### batch-classification

Classifies a list of product reviews by sentiment and checks whether each one reports a defect. A
`Semaphore` limits the number of concurrent requests to 4, each request has a 15 second timeout,
and the example prints the total number of input tokens used.

### composite-scoring

Scores job candidates on experience, system design, and leadership in one request per candidate.
Each score is divided by the number of levels minus one to give a value from 0 to 1, and the
candidates are ranked with a different set of weights for each of two roles.

## Writing your own

- Include every question a decision may need in one `SystemOneRequest`.
- Base decisions on `choice`, `score`, and `noul`, and check `confidence` before taking actions
  that are hard to undo. Choice and Score answers have a confidence value; Noul answers do not.
- Use `TypeSafeClientAsync` in coroutine code, `TypeSafeClient` in blocking code, and
  `TypeSafeFutureClient` from Java.
- Create one client per application and close it on shutdown.
- When using OpenRouter, give every question `instructions` and set both `yes` and `no` on
  `NoulCriteria`.
