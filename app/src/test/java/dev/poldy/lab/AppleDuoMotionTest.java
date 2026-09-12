package dev.poldy.lab;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppleDuoMotionTest {
    @Test public void keepsAllAuthoredEndpointsAndRepresentativeFrames(){
        assertEquals(0,AppleDuoMotion.authoredRelease(0),0);
        assertEquals(-.014995f,AppleDuoMotion.authoredRelease(3),.000001f);
        assertEquals(.102637f,AppleDuoMotion.authoredRelease(10),.000001f);
        assertEquals(.499040f,AppleDuoMotion.authoredRelease(30),.000001f);
        assertEquals(1,AppleDuoMotion.authoredRelease(AppleDuoMotion.LAST_FRAME),0);
    }
    @Test public void mapsThreeDegreesToEachAuthoredFrame(){
        for(int frame=0;frame<=AppleDuoMotion.LAST_FRAME;frame++){
            float expected=FoldOptics.clamp(AppleDuoMotion.authoredRelease(frame),0,1);
            assertEquals(expected,AppleDuoMotion.release(frame*3),.000001f);
        }
    }
    @Test public void interpolatesBetweenFramesWithoutAClock(){
        float expected=(AppleDuoMotion.authoredRelease(20)+AppleDuoMotion.authoredRelease(21))/2;
        assertEquals(expected,AppleDuoMotion.release(61.5f),.000001f);
        assertEquals(AppleDuoMotion.release(61.5f),AppleDuoMotion.release(61.5f),0);
    }
    @Test public void followsTheThreeOfficialBrightnessStates(){
        assertEquals(.15f,AppleDuoMotion.innerScreenBrightness(0),.000001f);
        assertEquals(.25f,AppleDuoMotion.innerScreenBrightness(60),.000001f);
        assertEquals(1,AppleDuoMotion.innerScreenBrightness(180),.000001f);
    }
}
