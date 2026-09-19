package net.bontal.typesafesdk;

import io.ktor.client.engine.HttpClientEngine;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class JavaUsage {

    private JavaUsage() {}

    public static TypeSafeClient buildClient(HttpClientEngine engine) {
        return TypeSafeClient.builder()
                .apiKey("test-key")
                .timeoutMillis(10_000)
                .retry(RetryStrategy.builder().maxRetries(0).build())
                .logLevel(LogLevel.OFF)
                .engine(engine)
                .build();
    }

    public static SystemOneRequest buildRequest() {
        return SystemOneRequest.builder()
                .state("I was charged twice. Please help.")
                .putQuestion(
                        "department",
                        ChoiceQuestion.builder()
                                .instructions("Which team should handle this")
                                .putCriterion("billing", "Payment or subscription issues")
                                .putCriterion("technical", Entry.of("Bugs or integration problems"))
                                .build())
                .putQuestion(
                        "spam", NoulQuestion.builder().instructions("Is this message spam").build())
                .build();
    }

    public static String run(TypeSafeClient client) throws Exception {
        SystemOneRequest request = buildRequest();

        SystemOneResult result = client.systemOne(request);
        ChoiceAnswer department = result.getChoices().get("department");

        TypeSafeClient fast = client.withOptions(options -> options.timeoutMillis(5_000));
        SystemOneResult perCall =
                fast.systemOne(
                        request,
                        RequestOptions.builder()
                                .timeoutMillis(2_000)
                                .putHeader("X-Trace", "java")
                                .build());

        HttpResponseFor<List<ModelCard>> raw = client.withRawResponse().models();

        try (TypeSafeFutureClient futures = TypeSafeFutureClient.of(client)) {
            CompletableFuture<SystemOneResult> future = futures.systemOne(request);
            SystemOneResult async = future.get();
            return department.getChoice()
                    + "|"
                    + perCall.getModel()
                    + "|"
                    + raw.getStatusCode()
                    + "|"
                    + async.getModel();
        }
    }

    public static int modelCount(HttpClientEngine engine) {
        try (TypeSafeClient client = buildClient(engine)) {
            return client.models().size();
        }
    }

    public static String describe(TypeSafeException exception) {
        if (exception instanceof RateLimitException) {
            RateLimitException rateLimit = (RateLimitException) exception;
            return "rate-limited:" + rateLimit.getRetryAfterMillis();
        }
        if (exception instanceof ApiException) {
            ApiException api = (ApiException) exception;
            return api.getStatusCode() + ":" + api.getRequestId();
        }
        if (exception instanceof ApiTimeoutException) {
            return "timeout:" + ((ApiTimeoutException) exception).getTimeoutMillis();
        }
        return "connection";
    }
}
