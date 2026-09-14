package dev.poldy.lab;

/** Recover OFF commands only. Positive system output always supersedes the old hold. */
final class LuminanceGuard implements AutoCloseable {
    private final long physical;
    private PanelLuminance visible;
    private long observed,offAt;
    private boolean pending,closed;
    LuminanceGuard(PanelLuminance visible,PanelLuminance latest){
        if(visible==null||!visible.sdr())throw new IllegalArgumentException("Measured SDR required");
        this.physical=visible.physicalId();this.visible=visible;observed=visible.wallMillis();
        observe(latest);
    }
    synchronized void observe(PanelLuminance output){
        if(closed||output==null||output.physicalId()!=physical||output.wallMillis()<observed)return;
        observed=output.wallMillis();
        if(output.sdr()){visible=output;pending=false;}
        else if(output.backlight()<=0){pending=true;offAt=output.wallMillis();}
        else {visible=null;pending=false;}
    }
    synchronized PanelLuminance takeRecovery(long now){
        if(closed||!pending||visible==null||now<offAt||now-offAt>500)return null;
        // At most one write for an observed OFF, even if its log echo arrives late.
        pending=false;return visible;
    }
    @Override public synchronized void close(){closed=true;pending=false;visible=null;}
}
