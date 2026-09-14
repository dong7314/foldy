package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;

public class WindowPrivacyPolicyTest {
    @Test public void onlyVerifiedSystemPowerSnapshotsAreExempt(){
        assertTrue(WindowPrivacyPolicy.systemPowerFade(1000,0,0,0x40040001,"ColorFade BLAST_d1#27711"));
        assertTrue(WindowPrivacyPolicy.systemPowerFade(1000,0,0,0x40040009,"ColorFade_d0_child-surface"));
        assertFalse(WindowPrivacyPolicy.systemPowerFade(10577,0,0,1,"ColorFade BLAST_d1"));
        assertFalse(WindowPrivacyPolicy.systemPowerFade(1000,1,0x2000,1,"ColorFade BLAST_d1"));
        assertFalse(WindowPrivacyPolicy.systemPowerFade(1000,0,0,1,"Authentication window"));
    }
    @Test public void onlyOwnRawBufferIsExcludedFromSecureWindowChecks(){
        assertTrue(WindowPrivacyPolicy.ownRenderSurface(10574,10574,0));
        assertFalse(WindowPrivacyPolicy.ownRenderSurface(10574,10574,1));
        assertFalse(WindowPrivacyPolicy.ownRenderSurface(10577,10574,0));
    }
    @Test public void secureFlagChangeWithinSameActivityIsProtected(){
        assertFalse(WindowPrivacyPolicy.protectedContent(0x81810120,0));
        assertTrue(WindowPrivacyPolicy.protectedContent(0x81812120,0));
        assertTrue(WindowPrivacyPolicy.protectedContent(0x81810120,0x40000));
    }
    @Test public void hiddenWindowsAndPrivateMirrorsAreIgnored(){
        assertTrue(WindowPrivacyPolicy.visible(0,0,1,false));
        assertTrue(WindowPrivacyPolicy.visible(1,8,1,false));
        assertFalse(WindowPrivacyPolicy.visible(0,2,1,false));
        assertFalse(WindowPrivacyPolicy.visible(0,0x10000,1,false));
        assertFalse(WindowPrivacyPolicy.visible(2147483000,0,1,false));
        assertFalse(WindowPrivacyPolicy.visible(0,0,0,false));
        assertFalse(WindowPrivacyPolicy.visible(0,0,1,true));
    }
}
