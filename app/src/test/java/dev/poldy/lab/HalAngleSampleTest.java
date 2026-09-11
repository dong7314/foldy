package dev.poldy.lab;
import org.junit.Test;
import static org.junit.Assert.*;

public class HalAngleSampleTest {
    private String line(String source,String stamp,String fields){
        return "handle_sns_client_event:107, [0]"+source+" ts="+stamp+" ns value ["+fields+"] ([0/0/2/2] [0.3/9.4/2.5])";
    }
    private HalAngleSample parse(String source,String fields){
        return HalAngleSample.parse(line(source,"1000000000",fields),1_004_000_000L,0,900_000_000L);
    }
    @Test public void readsTheCorrectFieldInBothVerifiedRecordFormats(){
        assertEquals(124,parse("folding_angle","124/0").angle,0);
        assertEquals(78,parse("lid_angle_fusion","1/ 78/1").angle,0);
        assertEquals(1_000_000_000L,parse("folding_angle","124/0").sensorNanos);
    }
    @Test public void ignoresHistoryFromBeforeReaderStarted(){
        assertNull(HalAngleSample.parse(line("folding_angle","1000000000","80/0"),1_004_000_000L,0,1_001_000_000L));
    }
    @Test public void rejectsStaleFutureDuplicateAndOutOfOrderRecords(){
        String l=line("folding_angle","1000000000","80/0");
        assertNull(HalAngleSample.parse(l,1_501_000_000L,0,0));
        assertNull(HalAngleSample.parse(l,999_999_999L,0,0));
        assertNull(HalAngleSample.parse(l,1_004_000_000L,1_000_000_000L,0));
        assertNull(HalAngleSample.parse(l,1_004_000_000L,1_001_000_000L,0));
    }
    @Test public void ignoresMalformedAndNonAngleLines(){
        for(String value:new String[]{"NaN/0","Infinity/0","-1/0","181/0","80","80/0/0","bad/0"})
            assertNull(value,parse("folding_angle",value));
        assertNull(parse("lid_angle_fusion","78/0"));
        assertNull(parse("not_an_angle","80/0"));
        assertNull(HalAngleSample.parse("another log says "+line("folding_angle","1000000000","80/0"),1_004_000_000L,0,0));
        assertNull(HalAngleSample.parse(line("folding_angle","99999999999999999999999","80/0"),1_004_000_000L,0,0));
    }
}
