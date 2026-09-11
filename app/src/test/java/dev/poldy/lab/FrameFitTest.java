package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrameFitTest {
    @Test public void innerOnCoverIncludesBothSides() {
        FrameFit fit=new FrameFit(2448,1848,1248,1972);
        assertEquals(1248,2448*fit.scale,.01);assertEquals(0,fit.x,.01);assertTrue(fit.y>0);
        assertTrue(fit.y+1848*fit.scale<=1972);
    }
    @Test public void coverOnInnerKeepsNativeAspect() {
        FrameFit fit=new FrameFit(1248,1972,2448,1848);
        assertEquals(1848,1972*fit.scale,.01);assertEquals(0,fit.y,.01);assertTrue(fit.x>0);
    }
}
