package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;

public class LuminanceGuardTest {
    private PanelLuminance sdr(long at,float nits){return new PanelLuminance(1,at,.15f,nits,nits);}
    private PanelLuminance off(long at){return new PanelLuminance(1,at,-1,1,1);}
    @Test public void normalBrightnessNeverTriggersAReplay(){
        PanelLuminance initial=sdr(100,81);LuminanceGuard g=new LuminanceGuard(initial,initial);
        assertNull(g.takeRecovery(101));g.observe(sdr(120,278));assertNull(g.takeRecovery(121));
    }
    @Test public void offRecoversOnceUsingTheMostRecentPositiveOutput(){
        PanelLuminance initial=sdr(100,81);LuminanceGuard g=new LuminanceGuard(initial,initial);
        g.observe(off(110));assertEquals(81,g.takeRecovery(111).nits(),0);assertNull(g.takeRecovery(112));
        g.observe(sdr(120,278));g.observe(off(130));assertEquals(278,g.takeRecovery(131).nits(),0);
    }
    @Test public void positiveOutputCancelsPendingOffBeforeAWrite(){
        PanelLuminance initial=sdr(100,81);LuminanceGuard g=new LuminanceGuard(initial,initial);
        g.observe(off(110));g.observe(sdr(111,278));assertNull(g.takeRecovery(112));
    }
    @Test public void staleFutureWrongPanelAndClosedGuardsCannotWrite(){
        PanelLuminance initial=sdr(100,81);LuminanceGuard g=new LuminanceGuard(initial,initial);
        g.observe(new PanelLuminance(2,110,-1,1,1));assertNull(g.takeRecovery(111));
        g.observe(off(120));assertNull(g.takeRecovery(119));assertNull(g.takeRecovery(621));
        g.observe(off(630));g.close();assertNull(g.takeRecovery(631));
    }
    @Test public void hdrInvalidatesSdrRecoveryAndRenewedHistoryCannotUndoIt(){
        PanelLuminance initial=sdr(100,81);LuminanceGuard g=new LuminanceGuard(initial,initial);
        g.observe(new PanelLuminance(1,110,.25f,600,300));g.observe(initial);
        g.observe(off(120));assertNull(g.takeRecovery(121));
        g.observe(sdr(130,278));g.observe(off(140));assertEquals(278,g.takeRecovery(141).nits(),0);
    }
}
