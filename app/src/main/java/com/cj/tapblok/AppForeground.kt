package com.cj.tapblok

/**
 * Tracks which TapBlok screens are currently visible so NfcHandlerActivity can tell a
 * foreground scan from a background one in strict mode. The NFC intent pauses the
 * underlying activity before the handler starts, so "paused a moment ago" still counts
 * as foreground.
 */
object AppForeground {

    private const val GRACE_MS = 5_000L

    @Volatile private var mainResumed = false
    @Volatile private var mainPausedAt = 0L
    @Volatile private var blockingResumed = false
    @Volatile private var blockingPausedAt = 0L

    /** Package shown on the block screen, null when none is known. */
    @Volatile var blockedPackage: String? = null
        private set

    fun onMainResumed() {
        mainResumed = true
    }

    fun onMainPaused() {
        mainResumed = false
        mainPausedAt = System.currentTimeMillis()
    }

    fun onBlockingResumed(packageName: String?) {
        blockingResumed = true
        blockedPackage = packageName
    }

    fun onBlockingPaused() {
        blockingResumed = false
        blockingPausedAt = System.currentTimeMillis()
    }

    fun isMainVisible(): Boolean =
        mainResumed || System.currentTimeMillis() - mainPausedAt < GRACE_MS

    fun isBlockingVisible(): Boolean =
        blockingResumed || System.currentTimeMillis() - blockingPausedAt < GRACE_MS
}
