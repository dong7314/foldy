package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;
public class FoldAttitudeTest {
    @Test public void allThreeGyroAxesDriveDistinctOrientationComponents(){
        for(int axis=0;axis<3;axis++){
            FoldAttitude a=new FoldAttitude();a.begin();
            for(int i=1;i<=26;i++)a.gyro(i*20_000_000L,axis==0?.5f:0,axis==1?.5f:0,axis==2?.5f:0);
            for(int i=0;i<100;i++)a.step(1000+i*16);
            float[] angles={a.pitch(),a.yaw(),a.roll()};
            assertEquals(.25f,angles[axis],.001f);
            for(int j=0;j<3;j++)if(j!=axis)assertEquals(0,angles[j],.001f);
        }
    }
    @Test public void aPauseKeepsItsPoseAndReverseRotationReturnsIt(){
        FoldAttitude a=new FoldAttitude();a.begin();long at=20_000_000L;a.gyro(at,0,0,0);
        for(int i=0;i<25;i++)a.gyro(at+=20_000_000L,.4f,0,0);
        for(int i=0;i<25;i++)a.gyro(at+=20_000_000L,0,0,0);
        for(int i=0;i<100;i++)a.step(1000+i*16);assertEquals(.2f,a.pitch(),.001f);
        for(int i=0;i<25;i++)a.gyro(at+=20_000_000L,-.4f,0,0);
        for(int i=0;i<100;i++)a.step(3000+i*16);assertEquals(0,a.pitch(),.001f);
    }
    @Test public void inactiveRotationAndLongSensorGapsAreNotAnimated(){
        FoldAttitude a=new FoldAttitude();a.gyro(20_000_000,1,1,1);a.gyro(40_000_000,1,1,1);a.step(1000);
        assertEquals(0,a.pitch(),0);a.begin();a.gyro(5_000_000_000L,1,1,1);a.step(2000);
        assertEquals(0,a.pitch(),0);assertEquals(0,a.yaw(),0);assertEquals(0,a.roll(),0);
    }
}
