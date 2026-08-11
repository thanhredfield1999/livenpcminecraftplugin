package vn.heomc.livingnpc;

import java.util.List;

record DialogueContext(
        String residentName,
        String title,
        String profession,
        String playerMessage,
        String currentPhase,
        long balanceMinor,
        List<String> recentMemories) {

    DialogueContext {
        recentMemories = List.copyOf(recentMemories);
    }
}
