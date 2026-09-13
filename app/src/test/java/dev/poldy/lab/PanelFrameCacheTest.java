package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;

public class PanelFrameCacheTest {
    @Test public void missingNativeLayoutNeverReturnsOppositeContent(){
        PanelFrameCache<Object> c=new PanelFrameCache<>();Object outer=new Object();
        c.put(outer,false,"task:app",0);assertNull(c.get(true,1));assertSame(outer,c.get(false,1));
    }
    @Test public void sameTaskRetainsTwoDistinctLayouts(){
        PanelFrameCache<Object> c=new PanelFrameCache<>();Object inner=new Object(),outer=new Object();
        c.put(inner,true,"task:app",0);c.put(outer,false,"task:app",100);
        assertSame(inner,c.get(true,101));assertSame(outer,c.get(false,101));
    }
    @Test public void appOrTaskChangeInvalidatesBothPanels(){
        PanelFrameCache<Object> c=new PanelFrameCache<>();Object old=new Object(),next=new Object();
        c.put(old,true,"1:appA",0);c.put(old,false,"1:appA",0);
        c.put(next,false,"2:appB",1);assertNull(c.get(true,2));assertFalse(c.references(old));
        c.owner("3:appB");assertNull(c.get(false,3));
    }
    @Test public void transientUnknownOwnerDoesNotErasePresentedFrames(){
        PanelFrameCache<Object> c=new PanelFrameCache<>();Object old=new Object();
        c.put(old,true,"task",0);assertFalse(c.owner(null));assertTrue(c.references(old));
        c.put(old,false,null,1);assertNull(c.get(false,2));
    }
    @Test public void expirationDropsOnlyTheAgedPanel(){
        PanelFrameCache<Object> c=new PanelFrameCache<>();Object inner=new Object(),outer=new Object();
        c.put(inner,true,"task",0);c.put(outer,false,"task",10000);
        assertNull(c.get(true,15001));assertSame(outer,c.get(false,15001));assertFalse(c.references(inner));
    }
    @Test public void retirementWaitsUntilCacheReleasesTheFrame(){
        PanelFrameCache<Object> c=new PanelFrameCache<>();FrameRetirement<Object> r=new FrameRetirement<>();
        Object frame=new Object();ArrayList<Object> released=new ArrayList<>();
        c.put(frame,true,"task",0);r.retire(frame,1);r.collect(500,c::references,released::add);
        assertTrue(released.isEmpty());c.clear();r.collect(701,c::references,released::add);
        assertEquals(1,released.size());assertSame(frame,released.get(0));
    }
}
