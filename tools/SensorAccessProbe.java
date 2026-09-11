import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** Bounded shell diagnostic. Uses normal permission checks; no identity or policy changes. */
public final class SensorAccessProbe {
    private static Throwable cause(Throwable error) {
        while (error instanceof java.lang.reflect.InvocationTargetException && error.getCause() != null)
            error = error.getCause();
        return error;
    }

    public static void main(String[] args) throws Exception {
        if (Process.myUid() != 2000) throw new IllegalStateException("Run as ADB shell only");
        Looper.prepareMainLooper();
        Object thread = Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        Context system = (Context) thread.getClass().getMethod("getSystemContext").invoke(thread);
        Context context = system.createPackageContext("com.android.shell", 0);
        SensorManager manager = context.getSystemService(SensorManager.class);
        Handler handler = new Handler(Looper.getMainLooper());
        JSONObject report = new JSONObject();
        report.put("uid", Process.myUid()).put("package", context.getPackageName());
        report.put("SSENSOR_granted", context.checkSelfPermission("com.samsung.permission.SSENSOR") == PackageManager.PERMISSION_GRANTED);
        JSONArray results = new JSONArray();
        report.put("sensors", results);
        ArrayList<SensorEventListener> registered = new ArrayList<>();
        // Public hinge is the positive control. The other types were discovered in this device's inventory.
        for (int type : new int[]{Sensor.TYPE_HINGE_ANGLE, 65686, 65687, 65689}) {
            JSONObject item = new JSONObject().put("type", type);
            results.put(item);
            Sensor sensor = manager.getDefaultSensor(type);
            item.put("present", sensor != null);
            if (sensor == null) continue;
            item.put("name", sensor.getName()).put("max_range", sensor.getMaximumRange());
            item.put("resolution", sensor.getResolution()).put("reporting_mode", sensor.getReportingMode());
            JSONArray samples = new JSONArray();
            item.put("samples", samples);
            SensorEventListener listener = new SensorEventListener() {
                public void onAccuracyChanged(Sensor s, int accuracy) {}
                public void onSensorChanged(SensorEvent event) {
                    // Keep the report small; only sensors expressly selected above are registered.
                    if (samples.length() >= 8) return;
                    try {
                        JSONArray values = new JSONArray();
                        for (float value : event.values) values.put(value);
                        samples.put(new JSONObject().put("timestamp_ns", event.timestamp).put("values", values));
                    } catch (Exception e) { throw new IllegalStateException(e); }
                }
            };
            try {
                boolean accepted = manager.registerListener(listener, sensor, 20_000, handler);
                item.put("registered", accepted);
                if (accepted) registered.add(listener);
            } catch (Throwable error) {
                item.put("registered", false).put("error", cause(error).toString());
            }
        }
        // Read only. Never call the setOemUnlock* methods or reboot to a flashing mode.
        JSONObject oem = new JSONObject();
        report.put("oem_lock_read_only", oem);
        try {
            IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "oem_lock");
            oem.put("service_present", binder != null);
            if (binder != null) {
                Object service = Class.forName("android.service.oemlock.IOemLockService$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, binder);
                Class<?> api = Class.forName("android.service.oemlock.IOemLockService");
                for (String name : new String[]{"isDeviceOemUnlocked", "isOemUnlockAllowed", "isOemUnlockAllowedByUser", "isOemUnlockAllowedByCarrier"}) {
                    try { oem.put(name, api.getMethod(name).invoke(service)); }
                    catch (Throwable error) { oem.put(name, cause(error).toString()); }
                }
            }
        } catch (Throwable error) { oem.put("error", cause(error).toString()); }
        handler.postDelayed(() -> {
            for (SensorEventListener listener : registered) manager.unregisterListener(listener);
            try {
                report.put("all_probe_listeners_unregistered", true);
                System.out.println("POLDY_SENSOR_ACCESS_RESULT=" + report);
            } catch (Exception error) { throw new IllegalStateException(error); }
            System.out.flush();
            System.exit(0);
        }, 2_000);
        Looper.loop();
    }
}
