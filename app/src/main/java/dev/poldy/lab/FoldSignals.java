package dev.poldy.lab;

/** Samsung's public sensor emits posture values, not a continuous physical angle. */
final class FoldSignals {
    enum Action { NONE, OPEN, CLOSE, REST }
    private int previous = -1;
    private int previousBase = -1;
    Action accept(float angle) {
        int next = angle <= 2 ? 0 : angle >= 178 ? 180 : 90;
        Action result = Action.NONE;
        if (next != previous) {
            if (next == 90 && previous == 0) result = Action.OPEN;
            else if (next == 90 && previous == 180) result = Action.CLOSE;
            else if (next != 90) result = Action.REST;
        }
        previous = next;
        return result;
    }
    Action acceptBaseState(int state) {
        if(state<0)return Action.NONE;
        int old=previousBase;previousBase=state;
        if(old==0 && state!=0 && previous==0){previous=90;return Action.OPEN;}
        if(old>=0 && old!=0 && state==0 && previous!=0){previous=0;return Action.REST;}
        return Action.NONE;
    }
    void clear() { previous = -1; previousBase = -1; }
}
