package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;

public class MotionAngleEstimatorTest {
    @Test public void equalOutAndBackMotionDoesNotAccumulateDirectionalGainDrift(){
        for(boolean inner:new boolean[]{false,true}){
            MotionAngleEstimator e=new MotionAngleEstimator(5.2694f,3.3043f);e.anchor(inner);e.begin();
            long time=20_000_000L;e.gyro(time,0);
            float start=e.angle(),sign=inner?-1:1;
            for(int round=0;round<3;round++){
                for(int i=0;i<20;i++)e.gyro(time+=20_000_000L,sign*.3f);
                e.gyro(time+=20_000_000L,0);
                for(int i=0;i<20;i++)e.gyro(time+=20_000_000L,-sign*.3f);
                e.gyro(time+=20_000_000L,0);
                assertEquals(start,e.angle(),.001f);
            }
        }
    }
    @Test public void rotationAtAnEndpointDoesNotStartAnAnimation(){
        MotionAngleEstimator e=new MotionAngleEstimator(2,2);e.anchor(false);
        for(int i=1;i<100;i++)e.gyro(i*20_000_000L,1);
        assertEquals(0,e.angle(),0);assertFalse(e.active());
    }
    @Test public void holdingStillDoesNotAdvanceByTime(){
        MotionAngleEstimator e=new MotionAngleEstimator(2,2);e.begin();
        for(int i=1;i<=25;i++)e.gyro(i*20_000_000L,.3f);
        e.gyro(26*20_000_000L,0);float held=e.angle();
        for(int i=27;i<250;i++)e.gyro(i*20_000_000L,0);
        assertEquals(held,e.angle(),.001);
    }
    @Test public void reversalMovesBackFromTheCurrentEstimate(){
        MotionAngleEstimator e=new MotionAngleEstimator(2,2);e.begin();
        for(int i=1;i<=25;i++)e.gyro(i*20_000_000L,.4f);
        float peak=e.angle();for(int i=26;i<=40;i++)e.gyro(i*20_000_000L,-.4f);
        assertTrue(e.angle()<peak);assertTrue(e.angle()>2);
    }
    @Test public void endpointResetsDriftAndLongGapsAreNotIntegrated(){
        MotionAngleEstimator e=new MotionAngleEstimator(2,2);e.begin();e.gyro(20_000_000L,1);
        float before=e.angle();e.gyro(5_000_000_000L,1);assertEquals(before,e.angle(),0);
        e.anchor(true);assertEquals(180,e.angle(),0);assertFalse(e.active());
    }
}
