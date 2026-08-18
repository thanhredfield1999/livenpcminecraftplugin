package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LivingNpcPluginStartupArchitectureTest {
    @Test
    void invalidConfigDisablesPluginInsteadOfReturningEnabledWithPartialState() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/vn/heomc/livingnpc/LivingNpcPlugin.java"),
                StandardCharsets.UTF_8);
        int guard = source.indexOf("configResult == ConfigSchemaMigration.Result.INVALID");
        assertTrue(guard >= 0);
        int end = source.indexOf("}", guard);
        String branch = source.substring(guard, Math.min(source.length(), end + 1));
        assertTrue(branch.contains("disablePlugin(this)"));
    }
}

