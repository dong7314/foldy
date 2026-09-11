#!/system/bin/sh
[ "$(getprop ro.product.model)" = SM-F971N ] || exit 2
poldy_before="$(dumpsys device_state)"
printf '%s\n' "$poldy_before" | grep -q 'mOverrideState=Optional.empty' || exit 2
printf '%s\n' "$poldy_before" | grep -q 'mBaseState=Optional\[DeviceState{identifier=0,' || exit 2
poldy_size_before="$(wm size -d 0)"
printf '%s\n' "$poldy_size_before" | grep -q 'Override size:' && exit 2
recover_size_trial() {
    if wm size -d 0 | grep -q 'Override size: 2448x1848'; then wm size reset -d 0; fi
    poldy_current_owner="$(dumpsys device_state | sed -n 's/.*Request: mPid=\([0-9]*\),.*/\1/p')"
    [ "$poldy_current_owner" = "$poldy_owner_pid" ] && cmd device_state state reset
}
cmd device_state state 5 || exit 2
poldy_owner_pid="$(dumpsys device_state | sed -n 's/.*Request: mPid=\([0-9]*\),.*/\1/p')"
(sleep 9; recover_size_trial) &
poldy_size_guard=$!
sleep 1
CLASSPATH=/data/local/tmp/poldy-surface-probe.dex app_process /system/bin LogicalSizeTrial "$1"
poldy_size_result=$?
recover_size_trial
wait "$poldy_size_guard"
exit "$poldy_size_result"
