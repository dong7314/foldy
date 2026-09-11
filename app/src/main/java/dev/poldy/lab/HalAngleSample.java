package dev.poldy.lab;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses only the two angle records verified on this phone's Samsung HAL. */
final class HalAngleSample {
    static final long MAX_AGE_NANOS=500_000_000L;
    private static final Pattern RECORD=Pattern.compile(
        "^handle_sns_client_event:\\d+, \\[0\\](folding_angle|lid_angle_fusion) ts=(\\d+) ns value \\[([^\\]]+)\\].*$");
    final long sensorNanos;
    final float angle;
    final String source;
    private HalAngleSample(long stamp,float angle,String source){this.sensorNanos=stamp;this.angle=angle;this.source=source;}
    static HalAngleSample parse(String line,long now,long previous,long started){
        if(line==null||line.length()>2048)return null;
        Matcher m=RECORD.matcher(line);
        if(!m.matches())return null;
        try {
            long stamp=Long.parseLong(m.group(2));
            if(stamp<started||stamp<=previous||stamp>now||now-stamp>MAX_AGE_NANOS)return null;
            String source=m.group(1);String[] fields=m.group(3).split("/",-1);
            int index=source.equals("folding_angle")?0:1;
            if(fields.length!=(index==0?2:3))return null;
            float angle=Float.parseFloat(fields[index].trim());
            if(!Float.isFinite(angle)||angle<0||angle>180)return null;
            return new HalAngleSample(stamp,angle,source);
        }catch(NumberFormatException e){return null;}
    }
}
