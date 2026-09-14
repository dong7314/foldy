package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;

public class FoldViewMotionTest {
    @Test public void endpointsAndInactiveEnvelopeRemainPixelAligned(){
        FoldViewMotion m=new FoldViewMotion();m.attitude(.6f,-.6f,.6f);m.orientation(.8f,.1f,.8f);
        for(boolean inner:new boolean[]{false,true})for(int angle:new int[]{0,180}){
            FoldViewMotion.Pose p=m.at(FoldOptics.at(inner,angle),1);
            assertEquals(0,p.dx(),0);assertEquals(0,p.dy(),0);assertEquals(1,p.scale(),0);
        }
        assertEquals(1,m.at(FoldOptics.at(true,90),0).scale(),0);
    }
    @Test public void gyroAndGravityBothChangeTheMidFoldMaterial(){
        FoldViewMotion m=new FoldViewMotion();FoldOptics.Pose o=FoldOptics.at(false,90);
        FoldViewMotion.Pose neutral=m.at(o,1);
        m.attitude(.4f,.3f,.2f);assertNotEquals(neutral,m.at(o,1));
        m.attitude(0,0,0);m.orientation(.6f,.4f,.7f);assertNotEquals(neutral,m.at(o,1));
    }
    @Test public void parallaxAlwaysCoversThePanelAndStaysSubtle(){
        FoldViewMotion m=new FoldViewMotion();
        for(int tilt=-1;tilt<=1;tilt++)for(int angle=0;angle<=180;angle++){
            m.attitude(tilt*10,tilt*10,tilt*10);m.orientation(tilt*10,0,1);
            FoldViewMotion.Pose p=m.at(FoldOptics.at(true,angle),1);
            assertTrue(Math.abs(p.dx())<=.004f);assertTrue(Math.abs(p.dy())<=.005f);
            assertTrue((p.scale()-1)/2+1e-6f>=Math.abs(p.dx()));
            assertTrue((p.scale()-1)/2+1e-6f>=Math.abs(p.dy()));
        }
    }
    @Test public void heldAngleAndReturnedPoseHaveNoTimeBasedDrift(){
        FoldViewMotion m=new FoldViewMotion();m.attitude(.2f,-.1f,.3f);m.orientation(.3f,.8f,.4f);
        FoldViewMotion.Pose held=m.at(FoldOptics.at(true,125),1);
        for(int i=0;i<180;i++)m.at(FoldOptics.at(true,i),1);
        assertEquals(held,m.at(FoldOptics.at(true,125),1));
        m.attitude(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY);
        assertTrue(Float.isFinite(m.at(FoldOptics.at(true,90),1).scale()));
    }
}
