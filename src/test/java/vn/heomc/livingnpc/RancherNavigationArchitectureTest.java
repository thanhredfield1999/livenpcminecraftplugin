package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RancherNavigationArchitectureTest {
    @Test
    void gateLegSetsTargetOncePerNavigationStart() throws Exception {
        String source = source();
        int start = source.indexOf("public boolean start(Location legTarget, double margin)");
        int end = source.indexOf("public boolean navigating()", start);

        assertEquals(1, occurrences(
                source.substring(start, end), "MovementService.startSimpleNavigation("));
    }

    @Test
    void ordinaryNavigationStartsTargetOnce() throws Exception {
        String source = source();
        int start = source.indexOf("private boolean startNavigation(");
        int end = source.indexOf("static double distanceOutsideSquared", start);

        assertEquals(1, occurrences(
                source.substring(start, end), "MovementService.startSimpleNavigation("));
    }

    @Test
    void gateLegAppliesActiveParametersAfterTargetCreation() throws Exception {
        String source = source();
        int start = source.indexOf("public boolean start(Location legTarget, double margin)");
        int end = source.indexOf("public boolean navigating()", start);
        String method = source.substring(start, end);

        int target = method.indexOf("MovementService.startSimpleNavigation(");
        int active = method.indexOf("activeParametersAfterTarget", target);
        assertTrue(target >= 0 && active > target,
                "gate leg must configure active local parameters after setTarget");
        String activeBlock = method.substring(active,
                Math.min(method.length(), active + 500));
        assertTrue(activeBlock.contains(".distanceMargin(0.0)"),
                "gate leg must disable Citizens completion margin on active parameters");
        assertTrue(activeBlock.contains(".pathDistanceMargin(0.0)"),
                "gate leg must disable Citizens path cutoff on active parameters");
    }

    @Test
    void waypointLegAppliesActiveParametersAfterTargetCreation() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/vn/heomc/livingnpc/FarmerRuntime.java"),
                StandardCharsets.UTF_8);
        int start = source.indexOf("public boolean start(Location legTarget)");
        int end = source.indexOf("public boolean navigating()", start);
        String method = source.substring(start, end);

        int target = method.indexOf("MovementService.startSimpleNavigation(");
        int active = method.indexOf("activeParametersAfterTarget", target);
        assertTrue(target >= 0 && active > target,
                "waypoint leg must configure active local parameters after setTarget");
    }

    private static String source() throws Exception {
        return Files.readString(
                Path.of("src/main/java/vn/heomc/livingnpc/RancherRuntime.java"),
                StandardCharsets.UTF_8);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
