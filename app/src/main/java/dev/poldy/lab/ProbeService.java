package dev.poldy.lab;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.hardware.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import org.json.*;
import java.io.*;
import java.util.Locale;

/** Measures real events; does not change the device state, display power, or other apps. */
public final class ProbeService extends Service implements SensorEventListener, DisplayManager.DisplayListener {
    public static final String STOP = "dev.poldy.lab.STOP";
    public static volatile boolean running;
    public static volatile String status = "측정을 시작하면 각도와 화면 상태가 여기에 표시됩니다.";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SensorManager sensors;
    private DisplayManager displays;
    private Sensor hinge;
    private BufferedWriter writer;
    private float angle = Float.NaN;
    private int direction;
    private long lastEmit;
    private long angleSampleElapsed;
    private String lastDisplays = "";
    private boolean registered;
    private String session;
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            emit("heartbeat");
            handler.postDelayed(this, 500);
        }
    };
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            emit(intent.getAction());
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        sensors = getSystemService(SensorManager.class);
        displays = getSystemService(DisplayManager.class);
        hinge = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel("probe", "접힘 측정", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        PendingIntent stop = PendingIntent.getService(this, 1,
            new Intent(this, ProbeService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open = PendingIntent.getActivity(this, 2,
            new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "probe")
            .setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("Poldy · 접힘 측정 중")
            .setContentText("각도와 화면 상태만 기록합니다. 5분 후 자동 종료됩니다.")
            .setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "측정 종료", stop).build()).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);
        try {
            File dir = new File(getFilesDir(), "sessions");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create sessions directory");
            session = Long.toString(System.currentTimeMillis());
            writer = new BufferedWriter(new FileWriter(new File(dir, session + ".jsonl")));
            running = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            displays.registerDisplayListener(this, handler);
            registered = true;
            JSONObject metadata = new JSONObject();
            metadata.put("event", "session_start").put("session", session)
                .put("epoch_ms", System.currentTimeMillis()).put("elapsed_ms", SystemClock.elapsedRealtime())
                .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                .put("sdk", Build.VERSION.SDK_INT).put("build", Build.DISPLAY)
                .put("hinge_available", hinge != null);
            if (hinge != null) metadata.put("hinge_name", hinge.getName())
                .put("hinge_vendor", hinge.getVendor()).put("hinge_max", hinge.getMaximumRange());
            log(metadata);
            if (hinge != null && !sensors.registerListener(this, hinge, 20_000, handler)) {
                status = "힌지 센서 등록 실패";
                emit("hinge_registration_failed");
            }
            if(intent.getBooleanExtra("motion",false)) {
                for(int type:new int[]{Sensor.TYPE_GYROSCOPE,Sensor.TYPE_GRAVITY}){
                    Sensor motion=sensors.getDefaultSensor(type);
                    boolean ok=motion!=null&&sensors.registerListener(this,motion,20_000,handler);
                    log(new JSONObject().put("event","motion_sensor").put("type",type)
                        .put("name",motion==null?"missing":motion.getName()).put("registered",ok));
                }
            }
            handler.post(heartbeat);
            handler.postDelayed(this::stopSelf, 300_000);
        } catch (IOException | JSONException | RuntimeException e) {
            status = "측정 시작 실패: " + e.getClass().getSimpleName();
            Log.e("PoldyProbe", status);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.values.length == 0) return;
        if(event.sensor.getType()!=Sensor.TYPE_HINGE_ANGLE){
            if(event.values.length>=3)try {
                log(new JSONObject().put("event","motion").put("type",event.sensor.getType())
                    .put("timestamp_ns",event.timestamp).put("elapsed_ms",SystemClock.elapsedRealtime())
                    .put("x",event.values[0]).put("y",event.values[1]).put("z",event.values[2]));
            }catch(JSONException ignored){}
            return;
        }
        float next = event.values[0];
        if (!Float.isFinite(next) || next < 0 || next > 360) return;
        if (Float.isFinite(angle) && Math.abs(next - angle) > .2f) direction = next > angle ? 1 : -1;
        angle = next;
        long now = SystemClock.elapsedRealtime();
        // Sensor timestamps use the elapsedRealtimeNanos time base; include delivery latency.
        angleSampleElapsed = event.timestamp / 1_000_000L;
        if (now - lastEmit >= 20) { lastEmit = now; emit("hinge"); }
    }

    private void emit(String event) {
        if (!running) return;
        try {
            JSONArray list = new JSONArray();
            StringBuilder readable = new StringBuilder();
            for (Display display : displays.getDisplays()) {
                Display.Mode mode = display.getMode();
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                list.put(new JSONObject().put("id", display.getDisplayId()).put("name", display.getName())
                    .put("state", display.getState()).put("rotation", display.getRotation())
                    .put("logical_width", metrics.widthPixels).put("logical_height", metrics.heightPixels)
                    .put("mode_width", mode.getPhysicalWidth()).put("mode_height", mode.getPhysicalHeight())
                    .put("flags", display.getFlags()));
                readable.append("화면 ").append(display.getDisplayId()).append(" · ")
                    .append(display.getState() == Display.STATE_ON ? "ON" : "state=" + display.getState())
                    .append(" · ").append(metrics.widthPixels).append(" × ").append(metrics.heightPixels).append('\n');
            }
            String displayJson = list.toString();
            boolean changed = !displayJson.equals(lastDisplays);
            lastDisplays = displayJson;
            JSONObject data = new JSONObject().put("event", event).put("session", session)
                .put("epoch_ms", System.currentTimeMillis()).put("elapsed_ms", SystemClock.elapsedRealtime())
                .put("angle_deg", Float.isFinite(angle) ? angle : JSONObject.NULL)
                .put("angle_age_ms", Float.isFinite(angle) ? SystemClock.elapsedRealtime() - angleSampleElapsed : JSONObject.NULL)
                .put("direction", direction).put("display_changed", changed)
                .put("interactive", getSystemService(PowerManager.class).isInteractive())
                .put("locked", getSystemService(KeyguardManager.class).isKeyguardLocked())
                .put("cross_window_blur", getSystemService(WindowManager.class).isCrossWindowBlurEnabled())
                .put("displays", list);
            status = (Float.isFinite(angle) ? String.format(Locale.ROOT, "%.1f°", angle) : "힌지 각도 없음")
                + "  ·  " + (direction > 0 ? "펼치는 중" : direction < 0 ? "접는 중" : "대기")
                + "\n\n" + readable + "\n화면 변화와 각도를 함께 기록 중";
            log(data);
        } catch (JSONException | RuntimeException e) { Log.e("PoldyProbe", "sample_failed: " + e.getClass().getSimpleName()); }
    }

    private void log(JSONObject data) {
        String line = data.toString();
        Log.i("PoldyProbe", line);
        if (writer != null) {
            try { writer.write(line); writer.newLine(); writer.flush(); }
            catch (IOException e) { Log.e("PoldyProbe", "write_failed"); }
        }
    }
    @Override public void onDisplayAdded(int id) { emit("display_added:" + id); }
    @Override public void onDisplayRemoved(int id) { emit("display_removed:" + id); }
    @Override public void onDisplayChanged(int id) { emit("display_changed:" + id); }
    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); emit("configuration"); }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        emit("session_stop");
        running = false;
        handler.removeCallbacksAndMessages(null);
        sensors.unregisterListener(this);
        if (registered) { displays.unregisterDisplayListener(this); unregisterReceiver(screenReceiver); }
        if (writer != null) { try { writer.close(); } catch (IOException ignored) {} }
        stopForeground(STOP_FOREGROUND_REMOVE);
        status = "측정 종료 · 기록은 기기에 저장됐습니다.";
        super.onDestroy();
    }
}
