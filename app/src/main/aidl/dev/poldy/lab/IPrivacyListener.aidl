package dev.poldy.lab;
oneway interface IPrivacyListener {
    void onPrivacyChanged(boolean blocked, long epoch);
}
