package vn.heomc.livingnpc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.junit.jupiter.api.Test;

class MerchantRuntimeTest {
    @Test
    void releaseForSleepCancelsActiveNavigation() {
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        when(npc.getNavigator()).thenReturn(navigator);
        when(npc.isSpawned()).thenReturn(true);
        when(navigator.isNavigating()).thenReturn(true);

        MerchantRuntime runtime = new MerchantRuntime(npc, mock(FarmerDefinition.class));
        runtime.releaseForSleep();

        verify(navigator).cancelNavigation();
    }
}
