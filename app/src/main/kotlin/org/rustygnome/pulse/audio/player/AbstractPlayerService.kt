package org.rustygnome.pulse.audio.player

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import org.rustygnome.pulse.audio.Synthesizer
import org.rustygnome.pulse.pulses.ScriptEvaluator

abstract class AbstractPlayerService : Service(), PlayerService {

    protected lateinit var synthesizer: Synthesizer
    protected lateinit var notificationHelper: PlayerNotificationHelper
    protected var wakeLock: PowerManager.WakeLock? = null
    
    protected var scriptEvaluator: ScriptEvaluator? = null
    protected var isRunning = false
    protected var resourceId: Long = -1L
    protected var pulseName: String = "Pulse"

    abstract val tag: String
    abstract val actionStopped: String

    companion object {
        var runningServiceId: Long = -1L
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PlayerNotificationHelper.ACTION_STOP) {
                Log.i(tag, "Received stop action from notification")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "onCreate: Service creating.")
        synthesizer = Synthesizer(this)
        notificationHelper = PlayerNotificationHelper(this)
        
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Pulse:${tag}WakeLock")

        val filter = IntentFilter(PlayerNotificationHelper.ACTION_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        resourceId = intent?.getLongExtra("resource_id", -1L) ?: -1L
        pulseName = intent?.getStringExtra("pulse_name") ?: "Pulse"
        
        runningServiceId = resourceId
        
        startForeground(PlayerNotificationHelper.NOTIFICATION_ID, notificationHelper.buildNotification(pulseName))
        wakeLock?.acquire(10*60*1000L)

        val eventSounds = intent?.getStringArrayListExtra("event_sounds")
        val acousticStyle = intent?.getStringExtra("acoustic_style")
        val pulseId = intent?.getStringExtra("pulse_id")
        val scriptContent = intent?.getStringExtra("script_content")

        if (eventSounds != null && acousticStyle != null && scriptContent != null) {
            if (!isRunning) {
                isRunning = true
                synthesizer.loadStyle(acousticStyle, eventSounds, pulseId)
                scriptEvaluator = ScriptEvaluator(scriptContent)
                onStartPlayback(intent)
                sendStartedBroadcast()
            }
        } else {
            Log.w(tag, "Missing required intent extras, stopping service.")
            stopSelf()
        }
        
        return START_STICKY
    }

    protected abstract fun onStartPlayback(intent: Intent)

    private fun sendStartedBroadcast() {
        val intent = Intent(PlayerService.ACTION_PLAYER_STARTED).apply {
            putExtra("resource_id", resourceId)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    protected fun sendStoppedBroadcast() {
        val intent = Intent(actionStopped).apply {
            putExtra("resource_id", resourceId)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        Log.i(tag, "onDestroy: Service destroying.")
        isRunning = false
        runningServiceId = -1L
        unregisterReceiver(stopReceiver)
        scriptEvaluator?.release()
        synthesizer.release()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        sendStoppedBroadcast()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null
}
