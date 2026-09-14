package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProfileSwitchGateTest {
    @Test public void readyFramesCannotRemoveTheLateTraversalBarrier(){
        ProfileSwitchGate g=new ProfileSwitchGate();assertTrue(g.begin(1,true,100,true));
        g.observe(true,150);assertFalse(g.canFinish(1,749));assertTrue(g.canFinish(1,750));
    }
    @Test public void lateMappingChangeRestartsStabilityWindow(){
        ProfileSwitchGate g=new ProfileSwitchGate();g.begin(1,true,0,true);
        g.observe(true,20);g.observe(false,640);assertFalse(g.canFinish(1,800));
        g.observe(true,810);assertFalse(g.canFinish(1,889));assertTrue(g.canFinish(1,890));
    }
    @Test public void reversalRejectsAnOldCompletionEvenWhenTargetReturns(){
        ProfileSwitchGate g=new ProfileSwitchGate();g.begin(1,true,0,true);g.observe(true,10);
        g.begin(2,false,160,true);g.begin(3,true,380,true);g.observe(true,400);
        assertFalse(g.matches(1,true));assertFalse(g.canFinish(1,2000));
        assertFalse(g.canFinish(3,1029));assertTrue(g.canFinish(3,1030));
    }
    @Test public void staleRequestCannotChangeCurrentTarget(){
        ProfileSwitchGate g=new ProfileSwitchGate();g.begin(8,false,0,true);
        assertFalse(g.begin(7,true,100,true));assertFalse(g.begin(8,true,100,true));
        assertTrue(g.matches(8,false));
    }
    @Test public void sameProfileNeedsNoShieldDelay(){
        ProfileSwitchGate g=new ProfileSwitchGate();g.begin(0,false,0,false);
        assertTrue(g.canFinish(0,0));assertFalse(g.pending);
    }
    @Test public void resetInvalidatesTheOutstandingRelease(){
        ProfileSwitchGate g=new ProfileSwitchGate();g.begin(12,true,0,true);g.observe(true,10);g.reset();
        assertFalse(g.canFinish(12,1000));assertFalse(g.pending);
        assertTrue(g.begin(0,false,2000,false));
    }
    @Test public void firmwareCloseWaitsForCoveredReversalWithoutResettingTheGeneration(){
        ProfileSwitchGate g=new ProfileSwitchGate();g.begin(1,true,0,true);g.finished();
        assertFalse(g.canReassert(false));assertTrue(g.matches(1,true));
        assertTrue(g.begin(2,false,1000,true));
        assertFalse(g.matches(1,true));assertTrue(g.canReassert(false));
        g.observe(true,1100);
        assertFalse(g.canFinish(2,1649));assertTrue(g.canFinish(2,1650));
    }
    @Test public void canceledRequestMayReassertWithinItsExistingShield(){
        ProfileSwitchGate g=new ProfileSwitchGate();g.begin(1,true,0,true);
        assertTrue(g.canReassert(false));
        g.finished();assertTrue(g.canReassert(true));assertFalse(g.canReassert(false));
    }
    @Test public void restingCoverAndResetHaveDifferentRestorePolicies(){
        ProfileSwitchGate g=new ProfileSwitchGate();g.begin(1,false,0,false);
        assertTrue(g.canReassert(false));assertFalse(g.canReassert(true));
        g.reset();assertFalse(g.canReassert(false));assertFalse(g.canReassert(true));
    }
}
