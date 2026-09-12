import java.lang.reflect.*;
/** Read-only firmware probe. Compile with javac --release 17 before D8. */
public class TaskApi {
    public static void main(String[] args)throws Exception {
        Class<?> api=Class.forName("android.app.IActivityTaskManager");
        for(Method m:api.getMethods())if(m.getName().equals("getTasks"))System.out.println(m);
        if(args.length==0)return;
        Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"activity_task");
        Object service=Class.forName("android.app.IActivityTaskManager$Stub")
            .getMethod("asInterface",Class.forName("android.os.IBinder")).invoke(null,binder);
        Object tasks=api.getMethod("getTasks",int.class,boolean.class,boolean.class,int.class).invoke(service,1,false,false,0);
        for(Object task:(java.util.List<?>)tasks) {
            for(String field:new String[]{"taskId","userId","displayId","topActivity","isVisible","configuration"})
                System.out.println(field+"="+task.getClass().getField(field).get(task));
            Object config=task.getClass().getField("configuration").get(task);
            System.out.println("semDisplayDeviceType="+config.getClass().getField("semDisplayDeviceType").get(config));
        }
    }
}
