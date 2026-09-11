import android.os.SystemClock;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads existing HAL log output as shell. No sensor subscription or logging setting changes. */
public final class HalAngleProbe {
    private static final Pattern SAMPLE = Pattern.compile("\\[0\\](folding_angle|lid_angle_fusion) ts=(\\d+) ns value \\[([^\\]]+)\\]");
    public static void main(String[] args) throws Exception {
        if (android.os.Process.myUid() != 2000) throw new IllegalStateException("Run as ADB shell only");
        int seconds = args.length == 0 ? 45 : Integer.parseInt(args[0]);
        if (seconds < 1 || seconds > 90) throw new IllegalArgumentException("1..90 seconds only");
        // Let a separate OS process enforce the read deadline. Closing a Process pipe from
        // another Java thread can wait for the BufferedReader's blocking read lock.
        Process log = new ProcessBuilder("/system/bin/timeout", seconds + "s", "/system/bin/logcat", "-b", "main", "-v", "raw", "-T", "1", "-s", "sensors-hal:I", "*:S")
            .redirectErrorStream(true).start();
        System.out.println(new JSONObject().put("event", "started").put("duration_seconds", seconds));
        System.out.flush();
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() { public void run() { System.exit(2); } }, (seconds + 3) * 1000L);
        long previous = 0;
        int accepted = 0, rejectedOld = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(log.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher match = SAMPLE.matcher(line);
                if (!match.find()) continue;
                long stamp = Long.parseLong(match.group(2)), now = SystemClock.elapsedRealtimeNanos();
                String source = match.group(1);
                String[] fields = match.group(3).split("/");
                int index = source.equals("folding_angle") ? 0 : 1;
                if (fields.length <= index) continue;
                float angle = Float.parseFloat(fields[index].trim());
                if (!Float.isFinite(angle) || angle < 0 || angle > 180) continue;
                // Ignore buffered history and cross-source events that arrive out of order.
                if (now - stamp > 2_000_000_000L || now < stamp || stamp <= previous) { rejectedOld++; continue; }
                previous = stamp; accepted++;
                System.out.println(new JSONObject().put("source", source).put("sensor_ns", stamp)
                    .put("received_ns", now).put("angle", angle).put("age_ms", (now - stamp) / 1e6));
                System.out.flush();
            }
        } finally {
            timer.cancel(); log.destroy();
        }
        System.out.println(new JSONObject().put("event", "finished").put("accepted", accepted).put("rejected_old_or_out_of_order", rejectedOld));
        System.out.flush();
    }
}
