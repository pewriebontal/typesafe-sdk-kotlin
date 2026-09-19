package net.bontal.typesafesdk.examples;

import net.bontal.typesafesdk.ApiException;
import net.bontal.typesafesdk.ChoiceAnswer;
import net.bontal.typesafesdk.ChoiceQuestion;
import net.bontal.typesafesdk.NoulCriteria;
import net.bontal.typesafesdk.NoulQuestion;
import net.bontal.typesafesdk.RequestOptions;
import net.bontal.typesafesdk.SystemOneRequest;
import net.bontal.typesafesdk.SystemOneResult;
import net.bontal.typesafesdk.TypeSafeClient;
import net.bontal.typesafesdk.TypeSafeFutureClient;

import java.util.concurrent.CompletableFuture;

public final class JavaQuickstart {

    private JavaQuickstart() {}

    static SystemOneRequest routing(String message) {
        return SystemOneRequest.builder()
                .state(message)
                .putQuestion(
                        "team",
                        ChoiceQuestion.builder()
                                .instructions("Which team should handle this message")
                                .putCriterion("billing", "Payments, invoices, and refunds")
                                .putCriterion("technical", "Bugs and integration problems")
                                .putCriterion("sales", "Pricing and new purchases")
                                .build())
                .putQuestion(
                        "urgent",
                        NoulQuestion.builder()
                                .instructions("The message needs a reply today")
                                .criteria(
                                        NoulCriteria.builder()
                                                .yes(
                                                        "Something is broken, blocked, or"
                                                                + " time-sensitive")
                                                .no("It can wait for the normal queue")
                                                .build())
                                .build())
                .build();
    }

    public static void run(TypeSafeClient client) throws Exception {
        SystemOneResult sync =
                client.systemOne(
                        routing("Our checkout page returns a 500 error since this morning."),
                        RequestOptions.builder().timeoutMillis(10_000).build());
        ChoiceAnswer team = sync.getChoices().get("team");
        System.out.printf(
                "sync: %s (%.0f%% confident), urgent=%.2f%n",
                team.getChoice(),
                team.getConfidence() * 100,
                sync.getNouls().get("urgent").getNoul());

        try (TypeSafeFutureClient futures = TypeSafeFutureClient.of(client)) {
            CompletableFuture<SystemOneResult> first =
                    futures.systemOne(routing("Can I get a quote for 40 seats?"));
            CompletableFuture<SystemOneResult> second =
                    futures.systemOne(routing("I was charged twice for March."));
            String summary =
                    first.thenCombine(
                                    second,
                                    (a, b) ->
                                            a.getChoices().get("team").getChoice()
                                                    + " + "
                                                    + b.getChoices().get("team").getChoice())
                            .get();
            System.out.println("async: " + summary);
        }

        try {
            client.withOptions(options -> options.apiKey("not-a-real-key"))
                    .systemOne(routing("hello"));
        } catch (ApiException e) {
            String hint = e.getStatusCode() == 401 ? "check the API key" : "request failed";
            System.out.println(hint + ": " + e.getMessage());
        }
    }
}
