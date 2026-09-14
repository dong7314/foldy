package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;

public class LogcatRestartTest {
    @Test public void normalExpiryRenewsIndefinitely(){
        LogcatRestart p=new LogcatRestart();
        for(int i=0;i<100;i++)assertEquals(0,p.delay(false,300_010,300_000,false));
    }
    @Test public void crashLoopHasABoundedBudget(){
        LogcatRestart p=new LogcatRestart();
        assertEquals(250,p.delay(false,10,300_000,true));
        assertEquals(500,p.delay(false,10,300_000,true));
        assertEquals(750,p.delay(false,10,300_000,false));
        assertEquals(-1,p.delay(false,10,300_000,false));
    }
    @Test public void stopAlwaysWinsOverRenewalAndRetry(){
        LogcatRestart p=new LogcatRestart();
        assertEquals(-1,p.delay(true,300_100,300_000,false));
        assertEquals(-1,p.delay(true,10,300_000,true));
    }
    @Test public void healthyLeaseResetsTheCrashBudget(){
        LogcatRestart p=new LogcatRestart();
        p.delay(false,10,300_000,true);p.delay(false,10,300_000,true);
        assertEquals(0,p.delay(false,300_000,300_000,false));
        assertEquals(250,p.delay(false,10,300_000,true));
    }
}
