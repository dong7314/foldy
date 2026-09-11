#!/system/bin/sh
# Run only the matching short-lived test; recover its request if a native graphics crash occurs.
case "$1" in baseline|pin) ;; *) exit 2;; esac
recover_trial() {
    poldy_trial_dump="$(dumpsys device_state)"
    poldy_request_pid="$(printf '%s\n' "$poldy_trial_dump" | sed -n 's/.*Request: mPid=\([0-9]*\),.*/\1/p')"
    if [ "$poldy_request_pid" = "$poldy_trial_pid" ]; then
        poldy_base_id="$(printf '%s\n' "$poldy_trial_dump" | sed -n 's/.*mBaseState=Optional\[DeviceState{identifier=\([0-9]*\),.*/\1/p')"
        case "$poldy_base_id" in 0|1|2|3)
            cmd device_state state "$poldy_base_id"
            cmd device_state state reset
            echo RECOVERED_TEST_REQUEST
        ;; esac
    fi
}
CLASSPATH=/data/local/tmp/poldy-surface-probe.dex app_process /system/bin CompositorHoldTrial "$1" &
poldy_trial_pid=$!
(sleep 9; recover_trial) &
poldy_guard_pid=$!
wait "$poldy_trial_pid"
poldy_result=$?
recover_trial
wait "$poldy_guard_pid"
exit "$poldy_result"
