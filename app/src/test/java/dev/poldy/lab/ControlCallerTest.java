package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;

public class ControlCallerTest {
    @Test public void foreignAppsAndShellCannotInvokeDisplayOperations(){
        for(int code=1;code<=13;code++){
            assertTrue(ControlCaller.allowed(10574,10574,2000,code));
            assertFalse(ControlCaller.allowed(10577,10574,2000,code));
            assertFalse(ControlCaller.allowed(2000,10574,2000,code));
        }
    }
    @Test public void shizukuCanDestroyItsWorkerButAnUnrelatedAppCannot(){
        assertTrue(ControlCaller.allowed(2000,10574,2000,ControlCaller.DESTROY_TRANSACTION));
        assertFalse(ControlCaller.allowed(10577,10574,2000,ControlCaller.DESTROY_TRANSACTION));
        assertFalse(ControlCaller.allowed(10574,-1,2000,1));
    }
}
