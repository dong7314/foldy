package dev.poldy.lab;

import java.util.IdentityHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** A grace period alone is insufficient: either physical panel may still use an old hold. */
final class FrameRetirement<T> {
    private final IdentityHashMap<T,Long> retired=new IdentityHashMap<>();
    void retire(T frame,long now){if(frame!=null)retired.putIfAbsent(frame,now);}
    void collect(long now,Predicate<T> referenced,Consumer<T> release){
        var iterator=retired.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();
            if(referenced.test(entry.getKey())){entry.setValue(now);continue;}
            if(now-entry.getValue()>=200){
                T frame=entry.getKey();iterator.remove();release.accept(frame);
            }
        }
    }
    void close(Consumer<T> release){retired.keySet().forEach(release);retired.clear();}
}
