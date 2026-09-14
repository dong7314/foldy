package dev.poldy.lab;

/** Retains the last usable output through DPC's transient OFF, but never through HDR. */
final class PanelLuminanceHistory {
    private final long physical;
    private long latest;
    private PanelLuminance visible;
    private PanelLuminance output;
    PanelLuminanceHistory(long physical){this.physical=physical;}
    void observe(PanelLuminance sample){
        if(sample==null||sample.physicalId()!=physical||sample.wallMillis()<latest)return;
        latest=sample.wallMillis();
        output=sample;
        if(sample.sdr())visible=sample;
        // OFF is precisely the outgoing-panel transient that the hold repairs. Keep the
        // previous measured SDR output for current-nits validation, as in 0.18.6.
        else if(sample.backlight()>0)visible=null;
    }
    PanelLuminance snapshot(long now,float currentNits){
        return visible!=null&&visible.matches(physical,now,currentNits)?visible:null;
    }
    PanelLuminance output(){return output;}
}
