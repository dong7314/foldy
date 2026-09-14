package dev.poldy.lab;

/** Rejects stale asynchronous work and waits for frames from the requested logical viewport. */
final class TransferGate {
    enum Phase {READY, COVERING, WAITING_FRAME}
    long generation;
    boolean inner;
    Phase phase=Phase.READY;
    private int freshFrames;
    void invalidateFrames(){freshFrames=0;if(phase==Phase.READY)phase=Phase.WAITING_FRAME;}
    long begin(boolean inner){this.inner=inner;freshFrames=0;phase=Phase.COVERING;return ++generation;}
    /** Snapshot preparation owns the target buffer, including a same-panel reversal. */
    boolean canAnimateOutgoing(boolean sourceInner){return phase==Phase.COVERING&&sourceInner!=inner;}
    boolean covered(long token){
        if(token!=generation||phase!=Phase.COVERING)return false;
        phase=Phase.WAITING_FRAME;return true;
    }
    boolean frame(long token,boolean fromInner){
        if(token!=generation||phase!=Phase.WAITING_FRAME||fromInner!=inner)return false;
        if(++freshFrames<2)return false;
        phase=Phase.READY;return true;
    }
}
