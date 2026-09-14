package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;

public class SetupStageTest {
    @Test public void protectedWindowHasADistinctPausedState(){
        assertEquals(SetupStage.PAUSED,SetupStage.resolve(true,true,true,false,true));
        assertEquals(SetupStage.READY,SetupStage.resolve(true,true,false,false,true));
    }
    @Test public void previewCannotPretendToGrantScreenControl(){
        assertEquals(SetupStage.EXPERIENCE,SetupStage.resolve(false,false,false,false));
        assertEquals(SetupStage.CONNECT,SetupStage.resolve(true,false,false,false));
    }
    @Test public void aRunningServiceIsNotSuccessUntilItHasPreparedFrames(){
        assertEquals(SetupStage.STARTING,SetupStage.resolve(true,true,true,false));
        assertEquals(SetupStage.ACTIVE,SetupStage.resolve(true,true,true,true));
    }
    @Test public void lostPermissionAndStoppedServiceReturnToTheCorrectAction(){
        assertEquals(SetupStage.CONNECT,SetupStage.resolve(true,false,false,true));
        assertEquals(SetupStage.READY,SetupStage.resolve(true,true,false,true));
        assertEquals(SetupStage.ACTIVE,SetupStage.resolve(false,true,true,true));
    }
    @Test public void disconnectedServiceCannotContinueToClaimSuccess(){
        assertEquals(SetupStage.CONNECT,SetupStage.resolve(true,false,true,true));
    }
}
