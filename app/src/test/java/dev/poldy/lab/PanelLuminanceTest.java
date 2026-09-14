package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;

public class PanelLuminanceTest {
    private static final long PANEL=4630947123231501204L;
    private static String line(String backlight,String nits,String sdr) {
        return " 1789350156.589 2140 2140 I SurfaceFlinger: setDisplayBrightness("+PANEL+"): displayBrightness: "+backlight+", displayBrightnessNits: "+nits+", sdrWhitePointNits: "+sdr+" , dimmingRatio: 1.000000";
    }
    @Test public void measuredBacklightIsNotTheLogicalBrightnessPercentage() {
        PanelLuminance s=PanelLuminance.parse(line("0.147799","364.098297","364.098297"));
        assertNotNull(s);assertEquals(.147799f,s.backlight(),.000001f);
        assertTrue(s.matches(PANEL,s.wallMillis()+200,364.1f));
    }
    @Test public void offAndMinimumSentinelsCannotReplaceTheLastVisibleSample() {
        assertNull(PanelLuminance.parse(line("-1.000000","1.000000","1.000000")));
        assertNull(PanelLuminance.parse(line("0.000000","1.000000","1.000000")));
    }
    @Test public void hdrCannotBeReplayedAsSdr() {
        assertNull(PanelLuminance.parse(line("0.25","600","300")));
        assertNotNull(PanelLuminance.parse(line("0.15","372.517273","372.517212")));
    }
    @Test public void changedBrightnessOrPanelInvalidatesAStationaryHistorySample() {
        PanelLuminance s=PanelLuminance.parse(line("0.147799","364.098297","364.098297"));
        assertFalse(s.matches(PANEL,s.wallMillis()+100,100));
        assertFalse(s.matches(PANEL-1,s.wallMillis()+100,364));
        assertTrue(s.matches(PANEL,s.wallMillis()+3_600_000,364));
        assertFalse(s.matches(PANEL,s.wallMillis()-1,364));
    }
    @Test public void outputHistoryRetainsOffAndHdrToInvalidateAnOlderSdrRecord() {
        PanelLuminance off=PanelLuminance.parseOutput(line("-1","1","1"));
        PanelLuminance hdr=PanelLuminance.parseOutput(line("0.25","600","300"));
        assertNotNull(off);assertFalse(off.matches(PANEL,off.wallMillis()+1,364));
        assertNotNull(hdr);assertFalse(hdr.matches(PANEL,hdr.wallMillis()+1,600));
    }
    @Test public void unrelatedAndInvalidLogsAreIgnored() {
        assertNull(PanelLuminance.parse("I PoldyControl: brightness=0.8"));
        assertNull(PanelLuminance.parse(line("1.2","400","400")));
        assertNull(PanelLuminance.parse(line("NaN","400","400")));
    }
}
