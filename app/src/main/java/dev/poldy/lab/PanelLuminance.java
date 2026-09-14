package dev.poldy.lab;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A measured SurfaceFlinger SDR output, never a Settings brightness percentage. */
record PanelLuminance(long physicalId,long wallMillis,float backlight,float nits,float sdrNits) {
    private static final Pattern LINE=Pattern.compile("^\\s*([0-9]+\\.[0-9]+).*?SurfaceFlinger: setDisplayBrightness\\(([0-9]+)\\): displayBrightness: ([-0-9.]+), displayBrightnessNits: ([-0-9.]+), sdrWhitePointNits: ([-0-9.]+)");
    static PanelLuminance parse(String line) {
        PanelLuminance sample=parseOutput(line);
        return sample!=null&&sample.sdr()?sample:null;
    }
    static PanelLuminance parseOutput(String line) {
        if(line==null||line.length()>2048)return null;
        Matcher m=LINE.matcher(line);if(!m.find())return null;
        try {
            PanelLuminance sample=new PanelLuminance(Long.parseLong(m.group(2)),
                Math.round(Double.parseDouble(m.group(1))*1000),Float.parseFloat(m.group(3)),
                Float.parseFloat(m.group(4)),Float.parseFloat(m.group(5)));
            return sample;
        }catch(NumberFormatException e){return null;}
    }
    boolean sdr() {
        return Float.isFinite(backlight)&&backlight>0&&backlight<=1
            &&Float.isFinite(nits)&&nits>1&&nits<=3000&&Float.isFinite(sdrNits)
            &&Math.abs(nits-sdrNits)<=Math.max(.05f,nits*.002f);
    }
    boolean matches(long physical,long now,float currentNits) {
        // Stationary output may not emit a new log for hours. Revalidate its current nits;
        // the live reader invalidates SDR history on HDR changes.
        return sdr()&&physicalId==physical&&now>=wallMillis
            &&Float.isFinite(currentNits)&&currentNits>1
            &&Math.abs(nits-currentNits)<=Math.max(1,currentNits*.015f);
    }
}
