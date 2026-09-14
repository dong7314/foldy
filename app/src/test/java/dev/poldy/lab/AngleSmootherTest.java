package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;
public class AngleSmootherTest {
    @org.junit.Test public void resumeOnOpenPanelDoesNotSweepFromClosed(){
        AngleSmoother smoother=new AngleSmoother();smoother.reset(180);
        org.junit.Assert.assertEquals(180,smoother.step(1000),0);
        org.junit.Assert.assertTrue(smoother.settled());
        smoother.measuredTarget(170,1_000_000_000L);
        float next=smoother.step(1016);
        org.junit.Assert.assertTrue(next<180&&next>179);
    }
    @Test public void endpointDoesNotDisappearInASingleFrame(){
        AngleSmoother s=new AngleSmoother();s.step(1000);s.target(180,true);
        assertTrue(s.step(1016)<40);assertFalse(s.settled());
        for(int t=1032;t<3300;t+=16)s.step(t);
        assertTrue(s.settled());
    }
    @Test public void reversalContinuesFromDisplayedValue(){
        AngleSmoother s=new AngleSmoother();s.step(1000);s.target(100,false);
        for(int t=1016;t<1400;t+=16)s.step(t);
        float visible=s.value();s.target(20,false);assertEquals(visible,s.value(),0);
        assertTrue(visible>20);
        for(int t=1400;t<1600;t+=16)s.step(t);
        assertTrue(s.value()<visible);
    }
    @Test public void largeEndpointCorrectionHasBoundedSpeedInBothDirections(){
        AngleSmoother s=new AngleSmoother();s.step(1000);
        for(float target:new float[]{180,0}){
            s.target(target,true);
            for(int i=1;i<=150;i++){
                float previous=s.value();s.step(1000+(target==0?2400:0)+i*16);
                assertTrue("Endpoint correction exceeded 140 degrees/second",Math.abs(s.value()-previous)<=2.241f);
            }
            assertEquals(target,s.value(),.6f);
        }
    }
    @Test public void irregularRenderingDoesNotProduceAOneFrameCatchUp(){
        AngleSmoother s=new AngleSmoother();s.step(1000);s.target(180,true);
        for(int t=1016;t<=1176;t+=16)s.step(t);
        float before=s.value();s.step(1500);
        assertTrue(s.value()-before<=8.401f);
        assertFalse(s.settled());
    }
    @Test public void pausedTargetConvergesWithoutGoingPastIt(){
        AngleSmoother s=new AngleSmoother();s.step(1000);s.target(73,false);
        for(int t=1016;t<3000;t+=16){assertTrue(s.step(t)<=73);}
        assertEquals(73,s.value(),.001f);
    }
    @Test public void sparseTwelveDegreeSamplesKeepMovingBetweenUpdates(){
        AngleSmoother s=new AngleSmoother();s.step(1000);
        for(int sample=1;sample<=10;sample++){
            long start=1000+(sample-1)*240;
            s.measuredTarget(sample*12,start*1_000_000L);
            float before=0,after=0;
            for(int dt=8;dt<=232;dt+=8){before=s.value();after=s.step(start+dt);}
            assertTrue("Reached the sample too soon and stopped",after-before>.04f);
            assertTrue(after<=sample*12);
        }
    }
    @Test public void verySparseSamplesUseTheIntervalWithoutFrameJumps(){
        AngleSmoother s=new AngleSmoother();s.step(1000);
        for(int sample=1;sample<=6;sample++){
            long start=1000+(sample-1)*400;
            s.measuredTarget(sample*10,start*1_000_000L);
            float previous=s.value(),lateMotion=0;
            for(int dt=8;dt<=400;dt+=8){
                float value=s.step(start+dt);
                assertTrue("Sparse input created a visible frame jump",value-previous<.65f);
                if(dt>360)lateMotion+=value-previous;
                previous=value;
            }
            // The first target has no preceding interval to learn from.
            if(sample>1)assertTrue("Sparse input stopped well before its next sample",lateMotion>.25f);
        }
    }
    @Test public void sparseTargetStopsAndReversesWithoutResettingTheDisplayedAngle(){
        AngleSmoother s=new AngleSmoother();s.step(1000);
        s.measuredTarget(90,1_000_000_000L);
        for(int t=1008;t<=2000;t+=8)s.step(t);
        float visible=s.value();s.measuredTarget(78,2_000_000_000L);
        assertEquals(visible,s.value(),0);
        for(int t=2008;t<4000;t+=8){s.step(t);assertTrue(s.value()<=90);assertTrue(s.value()>=78);}
        assertEquals(78,s.value(),.025f);
    }
}
