package dev.poldy.lab;

import org.junit.Test;
import static org.junit.Assert.*;

public class FoldOpticsTest {
    @Test public void endpointsAreSharpOnTheVisiblePanel(){
        assertEquals(0,FoldOptics.at(false,0).strength(),0);
        assertEquals(0,FoldOptics.at(true,180).strength(),0);
    }
    @Test public void openingSpreadsCoverEffectAndClearsInnerFace(){
        for(int a=1;a<=180;a++){
            assertTrue(FoldOptics.at(false,a).strength()>=FoldOptics.at(false,a-1).strength());
            assertTrue(FoldOptics.at(false,a).edge()<=FoldOptics.at(false,a-1).edge());
            assertTrue(FoldOptics.at(true,a).strength()<=FoldOptics.at(true,a-1).strength());
        }
    }
    @Test public void pauseAndReversalDoNotRestartATimeline(){
        FoldOptics.Pose held=FoldOptics.at(true,137.25f);
        for(int a=138;a<180;a++)FoldOptics.at(true,a);
        for(int a=179;a>137;a--)FoldOptics.at(true,a);
        assertEquals(held,FoldOptics.at(true,137.25f));
        assertEquals(held,FoldOptics.at(true,137.25f));
    }
    @Test public void stationaryRightHalfRemainsSharp(){
        for(int a=0;a<=180;a++)assertEquals(0,FoldOptics.spatial(FoldOptics.at(true,a),.66f),0);
        assertTrue(FoldOptics.spatial(FoldOptics.at(false,40),.95f)>FoldOptics.spatial(FoldOptics.at(false,40),.1f));
    }
    @Test public void distinctIntermediateAnglesProduceDistinctPoses(){
        assertNotEquals(FoldOptics.at(false,20),FoldOptics.at(false,40));
        assertNotEquals(FoldOptics.at(true,120),FoldOptics.at(true,150));
    }
    @Test public void neitherPanelHasAHalfGesturePlateau(){
        for(boolean inner:new boolean[]{false,true})for(int a=1;a<180;a++){
            assertNotEquals(FoldOptics.at(inner,a-1).strength(),FoldOptics.at(inner,a).strength(),0);
        }
    }
    @Test public void bothPanelsHaveVisibleFrostAsSoonAsTheyLeaveRest(){
        for(boolean inner:new boolean[]{false,true}){
            FoldOptics.Pose entered=FoldOptics.at(inner,inner?178:2);
            assertTrue(entered.strength()>.19f);
            // The near diffusion layer must already be visible at the moving edge.
            float local=entered.strength()*FoldOptics.spatial(entered,inner?0:1);
            assertTrue(FoldOptics.smooth(0,.48f,local)>.3f);
            assertEquals(0,FoldOptics.at(inner,inner?180:0).strength(),0);
        }
    }
    @Test public void coverDiffusionReachesTheLeftEdgeAtMidGesture(){
        assertEquals(1,FoldOptics.spatial(FoldOptics.at(false,90),0),0);
        assertEquals(1,FoldOptics.spatial(FoldOptics.at(false,180),0),0);
        assertTrue(FoldOptics.at(false,140).strength()>FoldOptics.at(false,100).strength());
        assertTrue(FoldOptics.spatial(FoldOptics.at(false,30),.95f)>FoldOptics.spatial(FoldOptics.at(false,30),0));
    }
    @Test(expected=IllegalArgumentException.class) public void missingAngleIsNotInvented(){FoldOptics.at(true,Float.NaN);}
    @Test public void layingThePhoneFlatAddsEdgeShade(){
        FoldOptics.Pose pose=FoldOptics.at(true,130);
        assertTrue(FoldPlane.at(pose,0,0,1,0,0,0).darkness()>FoldPlane.at(pose,0,1,0,0,0,0).darkness());
    }
    @Test public void visibleEndpointsHaveNoResidualDisplacementOrShade(){
        for(FoldOptics.Pose pose:new FoldOptics.Pose[]{FoldOptics.at(false,0),FoldOptics.at(true,180)}){
            FoldPlane.Shape g=FoldPlane.at(pose,1,1,1,.5f,.5f,.5f);
            assertEquals(0,g.top(),0);assertEquals(0,g.bottom(),0);assertEquals(0,g.side(),0);assertEquals(0,g.darkness(),0);
        }
    }
}
