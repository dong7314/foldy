import java.lang.reflect.*;
public class TaskApi { public static void main(String[] args)throws Exception {for(Method m:Class.forName("android.app.IActivityTaskManager").getMethods())if(m.getName().equals("getTasks"))System.out.println(m);}}
