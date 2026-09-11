package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;
import static dev.poldy.lab.HingeProgress.Change.*;

public class HingeProgressTest {
    @Test public void startsFromMeasuredDepartureBeforeLatePublicPosture(){
        HingeProgress p=new HingeProgress();p.posture(false,0);
        assertEquals(START_OPEN,p.sample(6,100));assertTrue(p.active());
        p.begin();p.sample(157,1000);
        assertEquals(NONE,p.posture(true,1100));assertTrue(p.active());
        assertEquals(NONE,p.sample(167,1130));assertEquals(NONE,p.sample(169,1170));
        assertEquals(FINISH_OPEN,p.sample(177,1330));
        assertEquals(NONE,p.sample(180,1500));
        assertEquals(START_CLOSE,p.sample(169,2200));
    }
    @Test public void aCoarseEndpointDoesNotSweepTheRemainingTwentyDegrees(){
        HingeProgress p=new HingeProgress();p.begin();p.sample(22,100);
        assertEquals(NONE,p.posture(false,200));
        assertEquals(NONE,p.tick(5000));assertTrue(p.active());assertEquals(22,p.angle(),0);
        p.sample(11,5100);
        assertEquals(NONE,p.tick(5449));assertEquals(FINISH_CLOSE,p.tick(5450));
    }
    @Test public void aPauseOrReversalHasNoInventedAngleTrajectory(){
        HingeProgress p=new HingeProgress();p.sample(6,10);p.sample(90,100);
        assertEquals(NONE,p.tick(6000));assertEquals(90,p.angle(),0);assertTrue(p.active());
        assertEquals(NONE,p.sample(78,6100));assertEquals(NONE,p.sample(101,6200));
        assertEquals(101,p.angle(),0);assertTrue(p.active());
    }
    @Test public void endpointCandidateIsCancelledByNewPhysicalDeparture(){
        HingeProgress p=new HingeProgress();p.begin();p.sample(170,100);p.posture(true,110);
        p.begin();assertEquals(NONE,p.tick(2000));assertTrue(p.active());
    }
    @Test public void trailingEndpointLogsCannotRefrostACompletedPanel(){
        HingeProgress p=new HingeProgress();p.begin();p.sample(11,100);p.posture(false,200);
        assertEquals(FINISH_CLOSE,p.tick(600));
        assertEquals(NONE,p.sample(8,650));assertEquals(NONE,p.sample(1,700));
        assertEquals(START_OPEN,p.sample(6,1200));
    }
    @Test public void fallbackRequiresBothFreshPostureAndAngleToBecomeQuiet(){
        HingeProgress p=new HingeProgress();p.begin();p.sample(174,100);
        assertEquals(NONE,p.tick(1000));p.posture(true,1100);
        assertEquals(NONE,p.tick(1400));p.sample(175,1400);
        assertEquals(NONE,p.tick(1749));assertEquals(FINISH_OPEN,p.tick(1750));
    }
}
