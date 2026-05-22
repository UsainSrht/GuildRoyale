package dev.guildroyale.api.service;

import java.util.Objects;

/**
 * Represents the outcome of a service operation.
 *
 * <p>Use {@link #success()} or {@link #failure(String)} factory methods.
 * Pattern-match on subtypes or use {@link #isSuccess()} / {@link #isFailure()}.
 *
 * <pre>{@code
 * switch (result) {
 *     case ActionResult.Success s  -> player.sendMessage("Done!");
 *     case ActionResult.Failure f  -> player.sendMessage(f.reason());
 * }
 * }</pre>
 */
public sealed interface ActionResult permits ActionResult.Success, ActionResult.Failure {

    record Success() implements ActionResult {}

    record Failure(String reason) implements ActionResult {
        public Failure {
            Objects.requireNonNull(reason, "reason");
        }
    }

    static ActionResult success() { return new Success(); }
    static ActionResult failure(String reason) { return new Failure(reason); }

    default boolean isSuccess() { return this instanceof Success; }
    default boolean isFailure() { return this instanceof Failure; }

    default String failureReasonOrEmpty() {
        return switch (this) {
            case Failure f -> f.reason();
            case Success ignored -> "";
        };
    }
}
