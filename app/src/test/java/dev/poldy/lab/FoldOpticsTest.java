package dev.poldy.lab;

import org.junit.Test;
import static org.junit.Assert.*;

public class FoldOpticsTest {
    @Test public void visibleEndpointsAreSharpAndFullyLit(){
        FoldOptics.Pose cover=FoldOptics.at(false,0),inner=FoldOptics.at(true,180);
        assertEquals(0,cover.wipeAmount(),0);assertEquals(0,inner.wipeAmount(),0);
        assertEquals(1,FoldOptics.emissiveBrightness(cover),0);
        assertEquals(1,FoldOptics.emissiveBrightness(inner),0);
        for(float x:new float[]{0,.25f,.5f,.75f,1}){
            assertEquals(0,FoldOptics.blurArea(cover,x),0);
            assertEquals(0,FoldOptics.blurArea(inner,x),0);
        }
    }
    @Test public void portraitCoverUsesOfficialMidFoldPulse(){
        assertEquals(0,FoldOptics.at(false,0).wipeAmount(),0);
        assertEquals(.25f,FoldOptics.at(false,45).wipeAmount(),.000001f);
        assertEquals(.5f,FoldOptics.at(false,90).wipeAmount(),.000001f);
        assertEquals(.25f,FoldOptics.at(false,135).wipeAmount(),.000001f);
        assertEquals(0,FoldOptics.at(false,180).wipeAmount(),0);
    }
    @Test public void landscapeInnerUsesOfficialNegativeWipeFactor(){
        assertEquals(1,FoldOptics.at(true,0).wipeAmount(),0);
        assertEquals(1,FoldOptics.at(true,30).wipeAmount(),0);
        assertEquals(.8f,FoldOptics.at(true,60).wipeAmount(),.000001f);
        assertEquals(.6f,FoldOptics.at(true,90).wipeAmount(),.000001f);
        assertEquals(0,FoldOptics.at(true,180).wipeAmount(),0);
    }
    @Test public void blurBoundsMatchBothOfficialMaterials(){
        FoldOptics.Pose inner=FoldOptics.at(true,60),cover=FoldOptics.at(false,90);
        assertEquals(0,FoldOptics.blurArea(inner,.55f),.000001f);
        assertTrue(FoldOptics.blurArea(inner,0)>FoldOptics.blurArea(inner,.5f));
        assertEquals(0,FoldOptics.blurArea(cover,0),0);
        assertTrue(FoldOptics.blurArea(cover,1)>FoldOptics.blurArea(cover,.5f));
        assertTrue(FoldOptics.blurArea(cover,1)>1);
    }
    @Test public void earlyInnerDisplayUsesOfficialNonlinearEmissiveCurve(){
        assertTrue(FoldOptics.emissiveBrightness(FoldOptics.at(true,0))<.01f);
        assertTrue(FoldOptics.emissiveBrightness(FoldOptics.at(true,60))<.08f);
        assertTrue(FoldOptics.emissiveBrightness(FoldOptics.at(true,120))>.5f);
    }
    @Test public void shadeMovesFromFreeEdgeTowardTheHinge(){
        FoldOptics.Pose inner=FoldOptics.at(true,60),cover=FoldOptics.at(false,90);
        assertTrue(FoldOptics.shadeWipe(inner,0)<FoldOptics.shadeWipe(inner,.5f));
        assertTrue(FoldOptics.shadeWipe(cover,1)<FoldOptics.shadeWipe(cover,0));
    }
    @Test public void pauseAndReversalHaveNoIndependentClock(){
        FoldOptics.Pose held=FoldOptics.at(true,137.25f);
        for(int a=138;a<180;a++)FoldOptics.at(true,a);
        for(int a=179;a>137;a--)FoldOptics.at(true,a);
        assertEquals(held,FoldOptics.at(true,137.25f));
    }
    @Test(expected=IllegalArgumentException.class)
    public void missingAngleIsNotInvented(){FoldOptics.at(true,Float.NaN);}
}
