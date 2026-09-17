package com.thedesitadka.core.security

import java.io.File

object IntegrityChecker {

    data class IntegrityState(
        val isDebuggable: Boolean,
        val isRooted: Boolean,
        val isEmulator: Boolean,
        val isTampered: Boolean
    )

    private val ROOT_PATHS = listOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su"
    )

    /**
     * Checks basic device and application integrity indicators.
     * Implements non-destructive defense-in-depth: reports risk signals without destructive action.
     */
    fun performCheck(isDebugBuild: Boolean = false): IntegrityState {
        val rootDetected = checkRootFiles()
        val emulatorDetected = checkEmulatorSignals()

        val state = IntegrityState(
            isDebuggable = isDebugBuild,
            isRooted = rootDetected,
            isEmulator = emulatorDetected,
            isTampered = rootDetected && !isDebugBuild
        )

        if (state.isTampered) {
            StreamHubLogger.w("IntegrityChecker", "Integrity warning: device environment signals modification risk")
        }

        return state
    }

    private fun checkRootFiles(): Boolean {
        return try {
            ROOT_PATHS.any { path -> File(path).exists() }
        } catch (e: Exception) {
            false
        }
    }

    private fun checkEmulatorSignals(): Boolean {
        val qemu = System.getProperty("ro.kernel.qemu")
        val hardware = System.getProperty("ro.hardware")
        return (qemu != null && qemu == "1") || (hardware != null && hardware.contains("goldfish"))
    }
}
