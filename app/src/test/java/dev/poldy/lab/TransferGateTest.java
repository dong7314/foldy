package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;
public class TransferGateTest {
    @Test public void changedOwnerOrInvalidProfileRequiresTwoFreshFramesAgain(){
        TransferGate g=new TransferGate();long token=g.begin(true);g.covered(token);g.frame(token,true);
        g.invalidateFrames();assertFalse(g.frame(token,true));assertTrue(g.frame(token,true));
        g.invalidateFrames();assertEquals(TransferGate.Phase.WAITING_FRAME,g.phase);
        assertFalse(g.frame(token,true));assertTrue(g.frame(token,true));
    }
    @Test public void framesCannotRevealBeforeTheCurtainIsReady(){
        TransferGate g=new TransferGate();long t=g.begin(true);
        assertFalse(g.frame(t,true));assertEquals(TransferGate.Phase.COVERING,g.phase);
        assertTrue(g.covered(t));assertFalse(g.frame(t,true));assertTrue(g.frame(t,true));
    }
    @Test public void outgoingFramesDoNotCountAsDestinationFrames(){
        TransferGate g=new TransferGate();long t=g.begin(true);g.covered(t);
        for(int i=0;i<10;i++)assertFalse(g.frame(t,false));
        assertEquals(TransferGate.Phase.WAITING_FRAME,g.phase);
    }
    @Test public void reversalInvalidatesOldCallbacksAndFrames(){
        TransferGate g=new TransferGate();long open=g.begin(true);long close=g.begin(false);
        assertFalse(g.covered(open));assertTrue(g.covered(close));assertFalse(g.frame(open,true));
        assertFalse(g.frame(close,true));assertFalse(g.frame(close,false));assertTrue(g.frame(close,false));assertFalse(g.inner);
    }
    @Test public void completedRevealDoesNotStartAgain(){
        TransferGate g=new TransferGate();long t=g.begin(false);g.covered(t);g.frame(t,false);
        assertTrue(g.frame(t,false));assertFalse(g.frame(t,false));
    }
    @Test public void delayedSnapshotKeepsSourceMovingWithoutRevealingDestination(){
        for(boolean destinationInner:new boolean[]{true,false}){
            TransferGate g=new TransferGate();AngleSmoother visual=new AngleSmoother();
            visual.target(destinationInner?0:180,false);
            for(long now=1000;now<=4000;now+=16)visual.step(now);
            float before=visual.value();
            long token=g.begin(destinationInner);
            visual.target(destinationInner?30:150,false);
            for(long now=4016;now<=4192;now+=16){
                assertTrue(g.canAnimateOutgoing(!destinationInner));
                assertFalse(g.canAnimateOutgoing(destinationInner));
                visual.step(now);
                assertFalse(g.frame(token,destinationInner));
                assertEquals(TransferGate.Phase.COVERING,g.phase);
            }
            assertTrue(Math.abs(visual.value()-before)>5);
            assertTrue(g.covered(token));
            assertFalse(g.canAnimateOutgoing(!destinationInner));
            assertFalse(g.frame(token,destinationInner));
            assertTrue(g.frame(token,destinationInner));
        }
    }
    @Test public void reversalBackToCurrentPanelProtectsItsSnapshot(){
        TransferGate g=new TransferGate();long open=g.begin(true);
        assertTrue(g.canAnimateOutgoing(false));
        long reverse=g.begin(false);
        // The cover is now both the current panel and the snapshot target.
        assertFalse(g.canAnimateOutgoing(false));
        assertFalse(g.covered(open));assertFalse(g.frame(open,true));
        assertFalse(g.frame(reverse,false));
        assertTrue(g.covered(reverse));
        assertFalse(g.frame(reverse,false));assertTrue(g.frame(reverse,false));
    }
}
