package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;

public class PanelLuminanceHistoryTest {
    @Test public void renewalsCannotReplayOldSdrOverHdr(){
        PanelLuminanceHistory h=new PanelLuminanceHistory(1);
        h.observe(new PanelLuminance(1,100,.15f,360,360));
        h.observe(new PanelLuminance(1,200,.25f,600,300));
        h.observe(new PanelLuminance(1,100,.15f,360,360));
        assertNull(h.snapshot(300,360));
    }
    @Test public void transientOffRetainsTheOutgoingHoldOnlyIfCurrentNitsStillMatch(){
        PanelLuminanceHistory h=new PanelLuminanceHistory(1);
        h.observe(new PanelLuminance(1,100,.15f,360,360));
        h.observe(new PanelLuminance(1,200,-1,1,1));
        assertNotNull(h.snapshot(4_000_000,360));
        assertNull(h.snapshot(4_000_000,100));
        h.observe(new PanelLuminance(2,300,.15f,360,360));
        assertNotNull(h.snapshot(4_000_000,360));
    }
}
