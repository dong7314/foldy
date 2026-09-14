package dev.poldy.lab;

import android.util.Log;

/** Read-only history, renewed while the controller is active. */
final class PanelLuminanceReader implements AutoCloseable {
    private final RenewingLogcat reader;
    private final PanelLuminanceHistory inner=new PanelLuminanceHistory(PhysicalPanels.INNER);
    private final PanelLuminanceHistory outer=new PanelLuminanceHistory(PhysicalPanels.OUTER);
    private LuminanceGuard guard;
    PanelLuminanceReader(){
        reader=new RenewingLogcat(RenewingLogcat.Stream.LUMINANCE,new RenewingLogcat.Listener(){
            public void line(String line){observe(PanelLuminance.parseOutput(line));}
            public void stopped(String reason){Log.w("PoldyControl","luminance_reader_unavailable:"+reason);}
        });
        reader.start();
    }
    private synchronized void observe(PanelLuminance sample){
        inner.observe(sample);outer.observe(sample);
        if(guard!=null)guard.observe(sample);
    }
    synchronized PanelLuminance snapshot(boolean isInner,float currentNits){
        return reader.running()?(isInner?inner:outer).snapshot(System.currentTimeMillis(),currentNits):null;
    }
    synchronized LuminanceGuard beginHold(boolean isInner,float currentNits){
        if(guard!=null)guard.close();guard=null;
        PanelLuminance sample=snapshot(isInner,currentNits);
        if(sample!=null)guard=new LuminanceGuard(sample,(isInner?inner:outer).output());
        return guard;
    }
    @Override public synchronized void close(){reader.close();if(guard!=null){guard.close();guard=null;}}
}
