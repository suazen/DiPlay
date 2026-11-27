package com.shilapi.xcertplay.hud

import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * The head unit's own day/night state.
 *
 * BYD does not drive Android's UI night mode from the car setting, so `Configuration.uiMode` can
 * stay at night all day. The stock apps read instrument feature `0x40C0B02C` and treat `2` as
 * night (`NightModeGlobal.setNightExtra(value == 2)`). The feature is not readable through the
 * autoservice `getInt` call, but the shell can read it through the device API the stock app uses,
 * so [BydDayNightTool] runs under `app_process` and prints the raw value.
 */
object BydDayNight {
    private const val TAG = "DiPlay-DayNight"
    private const val FEATURE = 0x40C0B02C
    private const val MIN_INTERVAL_MILLIS = 30_000L

    private val shell = BydAdbShell(TAG)
    private var lastAttempt = 0L

    @Volatile
    var night: Boolean? = null
        private set

    /** The car reported a new state. Called on the thread that ran [refresh]. */
    @Volatile
    var listener: ((Boolean) -> Unit)? = null

    /** Read the car's state, at most once per 30 s. Blocking: call from a worker thread. */
    fun refresh(context: Context): Boolean? {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAttempt < MIN_INTERVAL_MILLIS) return night
        lastAttempt = now
        val app = context.applicationContext
        val output = shell.run(
            app,
            "CLASSPATH=${app.packageCodePath} app_process /system/bin ${BydDayNightTool::class.java.name}",
        )
        val parsed = parse(output) ?: return night
        if (parsed != night) {
            night = parsed
            Log.i(TAG, "car day/night=${if (parsed) "night" else "day"}")
            listener?.invoke(parsed)
        }
        return parsed
    }

    /** `daynight=2` is night; any other readable value is day (the stock apps only test == 2). */
    internal fun parse(output: String?): Boolean? {
        val value = output?.lineSequence()?.firstNotNullOfOrNull { line ->
            line.trim().takeIf { it.startsWith("daynight=") }?.substringAfter('=')?.toIntOrNull()
        } ?: return null
        return value == 2
    }
}

/** Runs under the head unit's adb shell through app_process; reads one instrument feature. */
internal object BydDayNightTool {
    private const val FEATURE = 0x40C0B02C

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            runCatching { android.os.Looper.prepareMainLooper() }
            val thread = Class.forName("android.app.ActivityThread")
            val main = thread.getMethod("systemMain").invoke(null)
            val context = thread.getMethod("getSystemContext").invoke(main) as Context
            val cls = Class.forName("android.hardware.bydauto.instrument.BYDAutoInstrumentDevice")
            val device = runCatching {
                cls.getMethod("getInstance", Context::class.java).invoke(null, context)
            }.getOrElse {
                cls.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
                    .newInstance(context)
            }
            val event = cls.getMethod("get", IntArray::class.java, Class::class.java)
                .invoke(device, intArrayOf(FEATURE), Integer.TYPE)
            val value = event?.javaClass?.getField("intValue")?.getInt(event)
            println("daynight=$value")
        } catch (error: Throwable) {
            println("daynightERR=${error.javaClass.simpleName}")
        } finally {
            System.exit(0)
        }
    }
}
