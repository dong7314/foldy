package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;
public class FoldPlaneTest {
    @Test public void bothFacesKeepTheirHingeFixedWhileFreeEdgesContract(){
        for(boolean inner:new boolean[]{false,true}){
            FoldPlane.Shape s=FoldPlane.at(FoldOptics.at(inner,90),0,1,0,0,0,0);
            float[] q=new float[8];FoldPlane.quad(inner,s,1000,1000,1,q);
            if(inner){assertEquals(500,q[2],0);assertEquals(0,q[3],0);assertEquals(500,q[4],0);assertEquals(1000,q[5],0);assertTrue(q[1]>0);assertTrue(q[7]<1000);}
            else {assertEquals(0,q[0],0);assertEquals(0,q[1],0);assertEquals(0,q[6],0);assertEquals(1000,q[7],0);assertTrue(q[3]>0);assertTrue(q[5]<1000);}
        }
    }
    @Test public void pitchAndRollChangeTheWedgesAtTheSameFoldProgress(){
        FoldOptics.Pose p=FoldOptics.at(true,110);
        FoldPlane.Shape a=FoldPlane.at(p,0,0,1,-.5f,0,0),b=FoldPlane.at(p,0,0,1,.5f,0,0);
        assertTrue(a.top()<b.top());assertTrue(a.bottom()>b.bottom());
        assertNotEquals(b,FoldPlane.at(p,0,0,1,.5f,0,.5f));
        assertNotEquals(b,FoldPlane.at(p,0,0,1,.5f,.5f,0));
    }
    @Test public void allSampledPlanesStayInsideTheirPanelWithoutTurningInsideOut(){
        for(boolean inner:new boolean[]{false,true})for(int angle=0;angle<=180;angle+=3)
            for(int tilt=-1;tilt<=1;tilt++){
                FoldPlane.Shape s=FoldPlane.at(FoldOptics.at(inner,angle),tilt,tilt,Math.abs(tilt),tilt*.65f,tilt*.65f,tilt*.65f);
                float[] q=new float[8];FoldPlane.quad(inner,s,1000,1000,1,q);
                for(float v:q)assertTrue(Float.isFinite(v)&&v>=0&&v<=1000);
                assertTrue(q[0]<q[2]);assertTrue(q[1]<q[7]);assertTrue(q[3]<q[5]);
            }
    }
}
