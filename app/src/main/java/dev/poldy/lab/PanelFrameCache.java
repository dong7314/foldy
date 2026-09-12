package dev.poldy.lab;

/** Two native layouts for one observed task/component. Unknown ownership invalidates both. */
final class PanelFrameCache<T> {
    static final long MAX_AGE_MILLIS=15_000;
    private String owner;
    private T inner,outer;
    private long innerAt,outerAt;
    boolean owner(String next) {
        if(next!=null&&next.equals(owner))return false;
        boolean changed=owner!=null||inner!=null||outer!=null;
        owner=next;inner=null;outer=null;return changed;
    }
    void put(T frame,boolean inside,String task,long now) {
        owner(task);if(task==null)return;
        if(inside){inner=frame;innerAt=now;}else{outer=frame;outerAt=now;}
    }
    T get(boolean inside,long now) {
        if(inside){if(now-innerAt>MAX_AGE_MILLIS)inner=null;return inner;}
        if(now-outerAt>MAX_AGE_MILLIS)outer=null;return outer;
    }
    boolean references(T frame){return frame!=null&&(frame==inner||frame==outer);}
    void clear(){owner(null);}
}
