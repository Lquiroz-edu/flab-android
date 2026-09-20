package com.lquiroz.flab.system

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.lquiroz.flab.FLabApplication
import com.lquiroz.flab.MainActivity
import com.lquiroz.flab.R
import com.lquiroz.flab.core.ModuleId
import com.lquiroz.flab.motion.EvidenceSource
import com.lquiroz.flab.motion.FoldEvidence
import com.lquiroz.flab.motion.FoldMotionEngine
import com.lquiroz.flab.motion.MotionChannels
import com.lquiroz.flab.profiles.AppProfile
import com.lquiroz.flab.profiles.DefaultAppProfiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

/**
 * Hosts the system-wide fold effect (see [FoldOverlayWindow]).
 *
 * ### Why a foreground service
 *
 * The effect has to react while F/LAB is not the app on screen — that is the entire point of the
 * feature. Android only keeps a process reliably alive for that with a foreground service, and a
 * foreground service must post a visible notification. That notification is a feature, not a tax:
 * something that can draw over other apps should be impossible to forget is running, and the
 * notification carries a one-tap route to switching it off.
 *
 * ### Cost
 *
 * Still event-driven. No timer, no wake lock. The frame loop is the same `while (needsFrames)` as
 * the in-app renderer: it starts on hinge evidence and stops the moment motion settles, at which
 * point the overlay is removed and its surface released. A Fold sitting still costs one idle
 * coroutine and nothing else.
 */
class FLabOverlayService : Service() {

    private val scope = CoroutineScope(
        SupervisorJob() + Handler(Looper.getMainLooper()).asCoroutineDispatcher(),
    )
    private val engine = FoldMotionEngine()
    private lateinit var overlay: FoldOverlayWindow
    private var loopJob: Job? = null
    private var previewJob: Job? = null

    /** Wakes the frame loop. Conflated: many samples, one wake-up. */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Latest per-app overrides, kept off the frame path. See [observe]. */
    private var appOverrides: List<AppProfile> = emptyList()

    private val keyguardManager by lazy {
        getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }

    private val app get() = application as FLabApplication

    override fun onCreate() {
        super.onCreate()
        overlay = FoldOverlayWindow(this)
        startForegroundCompat()
        observe()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PREVIEW -> runPreview()
        }
        // START_STICKY: after a fold cycle that killed the process, come back. Not
        // START_REDELIVER_INTENT — there is no work to redo, only state to re-read.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        previewJob?.cancel()
        loopJob?.cancel()
        // Releases the hinge listener the service asked to keep alive. Skipping this would leave
        // the sensor registered for the rest of the process after the service stopped.
        runCatching { app.core.releaseContinuousTracking(this) }
        overlay.release()
        scope.cancel()
        running.value = false
        super.onDestroy()
    }

    private fun observe() {
        running.value = true
        val core = app.core

        // Without this the feature simply would not fire. Hinge evidence normally flows only while
        // an Activity is attached and stops as soon as motion settles — but reacting to a fold
        // *while F/LAB is not on screen* is the entire point here, so the service asks the Core to
        // keep the listener registered for as long as it runs, and releases it in onDestroy.
        core.requestContinuousTracking(this)

        scope.launch {
            core.state.collect { state ->
                if (!state.isModuleRunning(ModuleId.FoldMotion)) overlay.hide()
            }
        }

        // Cached rather than read per frame: settings.current() parses a string set out of
        // SharedPreferences, and doing that at the panel's refresh rate is exactly the kind of cost
        // DoD 22 and 23 rule out.
        scope.launch {
            app.settings.observe().collect { appOverrides = it.appOverrides }
        }

        loopJob = scope.launch {
            launch {
                core.evidence.collect { evidence ->
                    engine.submit(evidence)
                    wake.trySend(Unit)
                }
            }
            for (unused in wake) {
                engine.updateTuning(core.effectiveMotionTuning)
                runLoop()
            }
        }
    }

    /**
     * A synthetic fold — closed, open, closed again over a couple of seconds — fed to the engine as
     * `Manual` evidence and drawn through exactly the same loop, policy and window as a real one.
     *
     * This exists because the real effect is, by design, only visible while the hinge is moving,
     * and a fold on a Galaxy Fold also switches displays under it; the honest way to let someone
     * confirm the layer works at all is to run it over the app they are already looking at. Nothing
     * is special-cased: if the policy would refuse (protected app, power saving, locked), the
     * preview refuses too, and Diagnostics says why.
     */
    private fun runPreview() {
        if (previewJob?.isActive == true) return
        previewJob = scope.launch {
            val start = System.nanoTime()
            while (true) {
                val now = System.nanoTime()
                val t = ((now - start).toFloat() / PREVIEW_DURATION_NANOS).coerceIn(0f, 1f)
                val progress = 0.5f - 0.5f * cos(t * 2.0 * PI).toFloat()
                engine.submit(FoldEvidence(progress, EvidenceSource.Manual, now))
                wake.trySend(Unit)
                if (t >= 1f) break
                delay(PREVIEW_SAMPLE_MILLIS)
            }
        }
    }

    /**
     * Drives frames while the device is moving, then takes the overlay down.
     *
     * The verdict is re-evaluated every frame rather than once at the start. The foreground app can
     * change mid-fold — opening the device from the launcher straight into a banking app is a real
     * sequence — and the answer has to follow it rather than being decided once and held.
     */
    private suspend fun runLoop() {
        while (engine.needsFrames) {
            awaitFrame()
            val channels = engine.advance(System.nanoTime())
            applyVerdict(channels)
        }
        // One more pass at rest, through the same policy rather than a hardcoded verdict. Motion
        // settling and the policy's reason for not showing are independent: if the engine is off,
        // an app is protected, or a permission is missing, that is still the true answer once the
        // device stops moving, and stamping NotMoving over it would hide the real cause from
        // Diagnostics and from Home.
        applyVerdict(MotionChannels.Neutral)
    }

    private fun applyVerdict(channels: MotionChannels) {
        val state = app.core.state.value
        val foreground = FLabAccessibilityService.currentForegroundPackage.value
        val profile = foreground?.let { DefaultAppProfiles.forPackage(it, appOverrides) }

        val verdict = SystemEffectPolicy.decide(
            state = state,
            foregroundPackage = foreground,
            profile = profile,
            energy = engine.frame.energy,
            hasOverlayPermission = overlay.hasPermission(),
            isDeviceLocked = keyguardManager?.isKeyguardLocked == true,
        )
        lastVerdict.value = verdict

        if (verdict == OverlayVerdict.Show) overlay.apply(channels) else overlay.hide()
    }

    private fun startForegroundCompat() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.overlay_channel_description)
            setShowBadge(false)
        }
        manager?.createNotificationChannel(channel)

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, FLabOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_flab_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, getString(R.string.overlay_notification_stop), stop)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "flab_system_effects"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.lquiroz.flab.STOP_OVERLAY"
        const val ACTION_PREVIEW = "com.lquiroz.flab.PREVIEW_OVERLAY"

        /** Slow enough to read as a fold, fast enough to carry real energy through the veil easing. */
        private const val PREVIEW_DURATION_NANOS = 2_400_000_000L
        private const val PREVIEW_SAMPLE_MILLIS = 16L

        private val running = MutableStateFlow(false)
        private val lastVerdict = MutableStateFlow(OverlayVerdict.ModuleOff)

        /** Whether the service is up. Drives the Home row and Diagnostics. */
        val isRunning: StateFlow<Boolean> = running.asStateFlow()

        /** The most recent decision, so Diagnostics can say why nothing is happening. */
        val currentVerdict: StateFlow<OverlayVerdict> = lastVerdict.asStateFlow()

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, FLabOverlayService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FLabOverlayService::class.java)) }
        }

        /** Runs [runPreview] on the live service. A no-op if the service is not allowed to start. */
        fun preview(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context, FLabOverlayService::class.java).setAction(ACTION_PREVIEW),
                )
            }
        }
    }
}
