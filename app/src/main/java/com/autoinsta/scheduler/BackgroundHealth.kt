package com.autoinsta.scheduler

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Whether Android will actually let this app wake up on time.
 *
 * ## Why this exists
 * On 2026-09-09 a post did not publish. The alarm code was correct — it uses
 * `setExactAndAllowWhileIdle`, the one variant that pierces Doze — and it still did not
 * fire for thirteen hours. `dumpsys alarm` gave the reason:
 *
 * ```
 * policyWhenElapsed: … app_standby=-2d8h44m21s …
 * am get-standby-bucket com.autoinsta  →  40  (RARE)
 * ```
 *
 * An app in the **RARE** bucket has even its exact alarms throttled to roughly one per
 * day, and this app is idle by design: it exists to do one thing a week without being
 * opened. That is precisely the usage pattern Android penalises.
 *
 * The only real fix is the battery-optimisation exemption, and only the owner can grant
 * it. So this reports the state, and the UI makes it impossible to miss.
 */
object BackgroundHealth {

    /**
     * True when Android has been told to leave this app alone.
     *
     * Without it, an app that is opened rarely drifts into a restrictive standby bucket
     * and its alarms stop being honoured on time — silently, with no error anywhere.
     */
    fun isIgnoringBatteryOptimisations(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * The App Standby bucket, or -1 if it cannot be read.
     *
     * 10 ACTIVE · 20 WORKING_SET · 30 FREQUENT · 40 RARE · 45 RESTRICTED.
     * From RARE down, exact alarms are throttled hard.
     *
     * Buckets arrived in API 28 and `minSdk` here is 26, so this is diagnostic only —
     * never a gate on behaviour. -1 means "could not tell", which is different from
     * "unrestricted" and is reported as such.
     */
    fun standbyBucket(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return UNKNOWN_BUCKET
        return runCatching {
            (context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager)
                .appStandbyBucket
        }.getOrDefault(UNKNOWN_BUCKET)
    }

    /** True when the bucket is one that defers alarms. False when it cannot be read. */
    fun isThrottledBucket(context: Context): Boolean =
        standbyBucket(context).let {
            it != UNKNOWN_BUCKET && it >= UsageStatsManager.STANDBY_BUCKET_RARE
        }

    const val UNKNOWN_BUCKET = -1

    /**
     * The system dialogue that grants the exemption.
     *
     * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` shows a one-tap prompt. It is
     * Play-restricted, which does not apply here: this app is not published, the same
     * basis on which its API keys ship inside the APK.
     */
    fun requestExemption(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            // Some OEM builds hide it. The general battery screen is the fallback.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
