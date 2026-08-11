package vn.heomc.livingnpc;

import java.util.concurrent.CompletableFuture;

interface DialogueGateway extends AutoCloseable {
    boolean available();

    CompletableFuture<DialogueDecision> decide(DialogueContext context);

    @Override
    default void close() {
    }
}
