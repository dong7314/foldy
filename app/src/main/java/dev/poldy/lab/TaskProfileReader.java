package dev.poldy.lab;

import android.content.ComponentName;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.IBinder;
import java.lang.reflect.Method;
import java.util.List;

/** Read-only API shape and Samsung config field verified by tools/TaskApi on SM-F971N. */
final class TaskProfileReader {
    record Profile(String owner,boolean inner,boolean ready) {}
    private final Object service;
    private final Method getTasks;
    TaskProfileReader()throws Exception {
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"activity_task");
        service=Class.forName("android.app.IActivityTaskManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        getTasks=Class.forName("android.app.IActivityTaskManager").getMethod("getTasks",int.class,boolean.class,boolean.class,int.class);
    }
    Profile read()throws Exception {
        List<?> tasks=(List<?>)getTasks.invoke(service,1,false,false,0);
        if(tasks.isEmpty())return new Profile(null,false,false);
        Object task=tasks.get(0);Class<?> type=task.getClass();
        ComponentName top=(ComponentName)type.getField("topActivity").get(task);
        if(top==null||!(boolean)type.getField("isVisible").get(task))return new Profile(null,false,false);
        String owner=type.getField("userId").get(task)+":"+type.getField("taskId").get(task)+":"+top.flattenToString();
        Configuration config=(Configuration)type.getField("configuration").get(task);
        int deviceType=(int)Configuration.class.getField("semDisplayDeviceType").get(config);
        Object window=Configuration.class.getField("windowConfiguration").get(config);
        Rect bounds=(Rect)window.getClass().getMethod("getBounds").invoke(window);
        int mode=(int)window.getClass().getMethod("getWindowingMode").invoke(window);
        boolean inner=deviceType==0;
        boolean ready=(int)type.getField("displayId").get(task)==0&&mode==1
            &&bounds.left==0&&bounds.top==0&&(deviceType==0||deviceType==5)
            &&nativeBounds(inner,bounds.width(),bounds.height());
        return new Profile(owner,inner,ready);
    }
    static boolean nativeBounds(boolean inner,int width,int height) {
        int naturalWidth=inner?2448:1248,naturalHeight=inner?1848:1972;
        return (width==naturalWidth&&height==naturalHeight)
            ||(width==naturalHeight&&height==naturalWidth);
    }
}
