package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FarmerNavigationActiveParametersTest {
    @Test
    void ordinaryNavigationAppliesFullParametersAfterTargetCreation() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/vn/heomc/livingnpc/FarmerRuntime.java"),
                StandardCharsets.UTF_8);
        int start = source.indexOf("private boolean navigate(");
        int end = source.indexOf("private boolean startWaypointRoute", start);
        String method = source.substring(start, end);

        int target = method.indexOf("MovementService.startSimpleNavigation(");
        int active = method.indexOf("activeParametersAfterTarget", target);
        int navigationConfig = method.indexOf("LivingNavigation.allowDoors(activeParameters)", active);
        assertTrue(target >= 0 && active > target && navigationConfig > active,
                "ordinary navigation must configure active parameters after navigation start");
    }

    @Test
    void gateNavigationConfiguresActiveParameterCloneAfterTarget() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/vn/heomc/livingnpc/FarmerRuntime.java"),
                StandardCharsets.UTF_8);

        int gateStart = source.indexOf("private boolean startGateRoute(");
        int gateEnd = source.indexOf("private NavigationResult navigationResult(", gateStart);
        assertTrue(gateStart >= 0 && gateEnd > gateStart);
        String gateSource = source.substring(gateStart, gateEnd);
        int active = gateSource.indexOf("activeParameters");
        assertTrue(active >= 0);
        String activeBlock = gateSource.substring(active,
                Math.min(gateSource.length(), active + 700));
        assertTrue(activeBlock.contains(".distanceMargin(0.0)"));
        assertTrue(activeBlock.contains(".pathDistanceMargin(0.0)"));
    }
}

