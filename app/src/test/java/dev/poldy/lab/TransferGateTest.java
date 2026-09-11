package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;
public class TransferGateTest {
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
}
