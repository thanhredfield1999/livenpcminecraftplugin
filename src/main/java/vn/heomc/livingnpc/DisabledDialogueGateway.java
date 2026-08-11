package vn.heomc.livingnpc;

import java.util.concurrent.CompletableFuture;

final class DisabledDialogueGateway implements DialogueGateway {
    @Override
    public boolean available() {
        return false;
    }

    @Override
    public CompletableFuture<DialogueDecision> decide(DialogueContext context) {
        return CompletableFuture.completedFuture(new DialogueDecision(
                GeminiIntent.WORK,
                "Tôi còn công việc phải hoàn thành trước khi hết ca.",
                "Tiếp tục công việc đã được giao."));
    }
}
