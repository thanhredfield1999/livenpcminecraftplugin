package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.astar.pathfinder.DoorExaminer;
import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.api.npc.NPC;
import org.junit.jupiter.api.Test;

class FarmerManagerNavigationConfigTest {
    @Test
    void managedNpcDisablesCitizensDoorExaminerAndInstallsGuardedReplacement() {
        NPC npc = mock(NPC.class);
        MetadataStore data = mock(MetadataStore.class);
        Navigator navigator = mock(Navigator.class);
        NavigatorParameters parameters = new NavigatorParameters().examiner(new DoorExaminer());
        when(npc.data()).thenReturn(data);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.getDefaultParameters()).thenReturn(parameters);

        FarmerManager.configureWorkerNpc(npc);

        verify(data).set("pathfinder-open-doors", false);
        verify(data).remove("reset-pitch-on-tick");
        assertFalse(parameters.hasExaminer(DoorExaminer.class));
        assertTrue(parameters.hasExaminer(LivingDoorExaminer.class));
    }
}