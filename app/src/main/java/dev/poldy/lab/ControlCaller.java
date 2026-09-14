package dev.poldy.lab;

/** Checked before AIDL unmarshals any privileged operation or native handle. */
final class ControlCaller {
    static final int DESTROY_TRANSACTION=16777115;
    static boolean allowed(int caller,int owner,int controller,int transaction) {
        return owner>=0&&caller==owner||caller==controller&&transaction==DESTROY_TRANSACTION;
    }
}
