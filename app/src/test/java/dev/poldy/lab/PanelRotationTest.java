package dev.poldy.lab;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PanelRotationTest {
    @Test public void swapsPanelPreferencesWithLogicalDisplayIds() {
        PanelRotation rotation = new PanelRotation();
        rotation.capture(false, "lock 0", "free");
        assertEquals("lock 0", rotation.mode(false, 0));
        assertEquals("free", rotation.mode(false, 1));
        assertEquals("free", rotation.mode(true, 0));
        assertEquals("lock 0", rotation.mode(true, 1));
        assertEquals("free", rotation.panelMode(true));
        assertEquals("lock 0", rotation.panelMode(false));
    }

    @Test public void observingOnePanelDoesNotOverwriteTheOther() {
        PanelRotation rotation = new PanelRotation();
        rotation.capture(false, "lock 0", "free");
        rotation.observeActive(true, "lock 1");
        assertEquals("lock 1", rotation.mode(true, 0));
        assertEquals("lock 0", rotation.mode(true, 1));
        assertEquals("lock 1", rotation.panelMode(true));
        assertEquals("lock 0", rotation.panelMode(false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCommandOutputOutsideRotationGrammar() {
        PanelRotation.normalize("free; settings put system x 1");
    }
}
