package dev.poldy.lab;

import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;

public class FrameRetirementTest {
    @Test public void oldPanelHoldSurvivesMultipleRapidReversals(){
        FrameRetirement<Object> retired=new FrameRetirement<>();Object first=new Object(),second=new Object();
        ArrayList<Object> released=new ArrayList<>();retired.retire(first,0);retired.retire(second,160);
        retired.collect(380,frame->frame==first,released::add);
        assertFalse(released.contains(first));assertTrue(released.contains(second));
        retired.collect(1000,frame->false,released::add);assertTrue(released.contains(first));
    }
    @Test public void recentlySubmittedFrameKeepsItsGracePeriod(){
        FrameRetirement<Object> retired=new FrameRetirement<>();Object frame=new Object();ArrayList<Object> released=new ArrayList<>();
        retired.retire(frame,100);retired.collect(299,value->false,released::add);assertTrue(released.isEmpty());
        retired.collect(300,value->false,released::add);assertEquals(1,released.size());
    }
    @Test public void duplicateRetirementReleasesEachObjectOnce(){
        FrameRetirement<Object> retired=new FrameRetirement<>();Object frame=new Object();ArrayList<Object> released=new ArrayList<>();
        retired.retire(frame,0);retired.retire(frame,100);retired.collect(400,value->false,released::add);
        retired.close(released::add);assertEquals(1,released.size());
    }
    @Test public void gracePeriodExtendsUntilThePanelStopsUsingItsFrame(){
        FrameRetirement<Object> retired=new FrameRetirement<>();Object frame=new Object();ArrayList<Object> released=new ArrayList<>();
        retired.retire(frame,0);retired.collect(500,value->true,released::add);
        retired.collect(600,value->false,released::add);assertTrue(released.isEmpty());
        retired.collect(700,value->false,released::add);assertEquals(1,released.size());
    }
}
