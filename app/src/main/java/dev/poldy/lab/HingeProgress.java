package dev.poldy.lab;

/** HAL drives optical motion; coarse posture only proposes a terminal endpoint. */
final class HingeProgress {
    enum Change { NONE, START_OPEN, START_CLOSE, FINISH_OPEN, FINISH_CLOSE }
    private float angle,restAngle;
    private boolean sampled,active;
    private int endpoint=-1;
    private long lastSample,candidateAt;
    boolean active(){return active;}
    float angle(){return angle;}
    Change sample(float next,long now){
        float previous=sampled?angle:restAngle;
        angle=next;sampled=true;lastSample=now;
        if(!active){
            // A final record still approaching the endpoint cannot start the next gesture.
            boolean opening=restAngle==0&&next>3&&next>previous;
            boolean closing=restAngle==180&&next<177&&next<previous;
            if(opening||closing){active=true;endpoint=-1;return opening?Change.START_OPEN:Change.START_CLOSE;}
            return Change.NONE;
        }
        if(endpoint>=0&&Math.abs(next-endpoint)<=3)return finish();
        return Change.NONE;
    }
    void begin(){active=true;endpoint=-1;}
    Change posture(boolean inner,long now){
        endpoint=inner?180:0;candidateAt=now;
        if(!active)return finish();
        if(sampled&&now-lastSample<=500&&Math.abs(angle-endpoint)<=3)return finish();
        return Change.NONE;
    }
    Change tick(long now){
        // These HAL logs omit small changes. Only bridge their <=12 degree terminal
        // deadband after both the physical posture and last angle have stayed quiet.
        if(active&&endpoint>=0&&sampled&&Math.abs(angle-endpoint)<=12
            &&now-Math.max(lastSample,candidateAt)>=350)return finish();
        return Change.NONE;
    }
    private Change finish(){
        restAngle=endpoint;active=false;endpoint=-1;
        return restAngle==180?Change.FINISH_OPEN:Change.FINISH_CLOSE;
    }
}
