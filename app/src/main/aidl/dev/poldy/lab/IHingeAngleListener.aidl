package dev.poldy.lab;

oneway interface IHingeAngleListener {
    void onAngle(long sensorNanos, float angle, String source);
    void onStopped(String reason);
}
