package dev.poldy.lab;

/** Restart budget for bounded logcat children; an intentional stop never renews a lease. */
final class LogcatRestart {
    private int failures;
    long delay(boolean closed,long elapsedMillis,long leaseMillis,boolean readFailed) {
        if(closed)return -1;
        if(!readFailed&&elapsedMillis>=leaseMillis-250){failures=0;return 0;}
        if(elapsedMillis>=30_000)failures=0;
        return ++failures<=3?250L*failures:-1;
    }
}
