package dev.poldy.lab;

/** Shell-side generation and traversal barrier. All calls are under DisplayControl's lock. */
final class ProfileSwitchGate {
    static final long MIN_SHIELD_MILLIS=650, STABLE_MILLIS=80;
    long generation=-1;
    boolean inner, pending;
    private long started, stableSince=-1;
    boolean begin(long token,boolean target,long now,boolean shielded) {
        if(token<=generation)return false;
        generation=token;inner=target;started=now;stableSince=-1;
        pending=shielded;return true;
    }
    boolean matches(long token,boolean target){return token==generation&&target==inner;}
    boolean canReassert(boolean physicalInner){return generation>=0&&(pending||physicalInner==inner);}
    void observe(boolean correct,long now){if(!correct)stableSince=-1;else if(stableSince<0)stableSince=now;}
    boolean canFinish(long token,long now) {
        return token==generation&&(!pending||(now-started>=MIN_SHIELD_MILLIS
            &&stableSince>=0&&now-stableSince>=STABLE_MILLIS));
    }
    void finished(){pending=false;}
    void reset(){generation=-1;pending=false;stableSince=-1;}
}
