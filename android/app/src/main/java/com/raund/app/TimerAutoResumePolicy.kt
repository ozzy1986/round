package com.raund.app

internal fun shouldResumeAfterScreenOn(
    running: Boolean,
    autoPausedByScreenOff: Boolean,
    keepRunningOnScreenOff: Boolean,
    keepRunningWhenLeavingApp: Boolean,
    keyguardLocked: Boolean
): Boolean {
    return running &&
        autoPausedByScreenOff &&
        !keepRunningOnScreenOff &&
        keepRunningWhenLeavingApp &&
        !keyguardLocked
}

internal fun shouldResumeAfterUnlock(
    running: Boolean,
    autoPausedByScreenOff: Boolean,
    keepRunningOnScreenOff: Boolean,
    keepRunningWhenLeavingApp: Boolean
): Boolean {
    return running &&
        autoPausedByScreenOff &&
        !keepRunningOnScreenOff &&
        keepRunningWhenLeavingApp
}
