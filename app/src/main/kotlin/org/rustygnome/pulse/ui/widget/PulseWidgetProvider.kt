package org.rustygnome.pulse.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import org.rustygnome.pulse.R
import org.rustygnome.pulse.audio.player.*
import org.rustygnome.pulse.data.AppDatabase
import org.rustygnome.pulse.data.Resource
import org.rustygnome.pulse.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PulseWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PREV = "org.rustygnome.pulse.WIDGET_PREV"
        const val ACTION_NEXT = "org.rustygnome.pulse.WIDGET_NEXT"
        const val ACTION_PLAY_STOP = "org.rustygnome.pulse.WIDGET_PLAY_STOP"
        const val ACTION_INFO = "org.rustygnome.pulse.WIDGET_INFO"
        
        private const val PREFS_NAME = "pulse_widget_prefs"
        private const val KEY_INDEX = "selected_resource_index"
        
        private var cachedResources: List<Resource> = emptyList()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshResources(context) {
            appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_PREV -> {
                refreshResources(context) {
                    if (cachedResources.isNotEmpty()) {
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        var index = prefs.getInt(KEY_INDEX, 0)
                        index = (index - 1 + cachedResources.size) % cachedResources.size
                        prefs.edit().putInt(KEY_INDEX, index).apply()
                        updateAllWidgets(context)
                    }
                }
            }
            ACTION_NEXT -> {
                refreshResources(context) {
                    if (cachedResources.isNotEmpty()) {
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        var index = prefs.getInt(KEY_INDEX, 0)
                        index = (index + 1) % cachedResources.size
                        prefs.edit().putInt(KEY_INDEX, index).apply()
                        updateAllWidgets(context)
                    }
                }
            }
            ACTION_PLAY_STOP -> {
                refreshResources(context) {
                    handlePlayStop(context)
                }
            }
            ACTION_INFO -> {
                refreshResources(context) {
                    showInfo(context)
                }
            }
            PlayerService.ACTION_PLAYER_STARTED -> {
                val resourceId = intent.getLongExtra("resource_id", -1L)
                refreshResources(context) {
                    if (resourceId != -1L) {
                        val index = cachedResources.indexOfFirst { it.id == resourceId }
                        if (index != -1) {
                            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            prefs.edit().putInt(KEY_INDEX, index).apply()
                        }
                    }
                    updateAllWidgets(context)
                }
            }
            KafkaPlayerService.ACTION_PLAYER_STOPPED,
            FilePlayerService.ACTION_PLAYER_STOPPED,
            WebSocketPlayerService.ACTION_PLAYER_STOPPED,
            RandomPlayerService.ACTION_PLAYER_STOPPED,
            RhythmicPlayerService.ACTION_PLAYER_STOPPED,
            PlayerNotificationHelper.ACTION_STOP -> {
                refreshResources(context) {
                    updateAllWidgets(context)
                }
            }
        }
    }

    private fun showInfo(context: Context) {
        val isPlaying = AbstractPlayerService.runningServiceId != -1L
        val playingId = AbstractPlayerService.runningServiceId
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val index = prefs.getInt(KEY_INDEX, 0).coerceIn(0, cachedResources.size - 1)
        
        val resource = if (isPlaying) {
            cachedResources.find { it.id == playingId } ?: if (cachedResources.isNotEmpty()) cachedResources[index] else null
        } else {
            if (cachedResources.isNotEmpty()) cachedResources[index] else null
        }

        resource?.let {
            val intent = Intent(context, WidgetInfoActivity::class.java).apply {
                putExtra("title", it.name)
                putExtra("message", it.description ?: "No description provided.")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            context.startActivity(intent)
        }
    }

    private fun handlePlayStop(context: Context) {
        val playingId = AbstractPlayerService.runningServiceId
        
        if (playingId != -1L) {
            context.sendBroadcast(Intent(PlayerNotificationHelper.ACTION_STOP).setPackage(context.packageName))
        } else {
            if (cachedResources.isNotEmpty()) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val index = prefs.getInt(KEY_INDEX, 0).coerceIn(0, cachedResources.size - 1)
                val resource = cachedResources[index]
                startPlayback(context, resource)
            }
        }
    }

    private fun startPlayback(context: Context, resource: Resource) {
        val serviceIntent = when {
            resource.pulseType == "KAFKA" -> Intent(context, KafkaPlayerService::class.java)
            resource.pulseType == "WEBSOCKET" -> Intent(context, WebSocketPlayerService::class.java)
            resource.pulseType == "SIMULATION" -> Intent(context, RandomPlayerService::class.java)
            resource.pulseType == "RHYTHM" -> Intent(context, RhythmicPlayerService::class.java)
            else -> {
                when {
                    resource.webSocketUrl != null -> Intent(context, WebSocketPlayerService::class.java)
                    resource.topic != null -> Intent(context, KafkaPlayerService::class.java)
                    else -> Intent(context, FilePlayerService::class.java)
                }
            }
        }
        
        serviceIntent.apply {
            putExtra("resource_id", resource.id)
            putExtra("pulse_name", resource.name)
            putExtra("pulse_id", resource.pulseId)
            putExtra("config_content", resource.configContent)
            putExtra("script_content", resource.scriptContent)
            putStringArrayListExtra("event_sounds", ArrayList(resource.eventSounds))
            putExtra("acoustic_style", resource.acousticStyle)
            putExtra("ws_url", resource.webSocketUrl)
            putExtra("ws_payload", resource.webSocketPayload)
            putExtra("bootstrap_servers", resource.bootstrapServers)
            putExtra("topic", resource.topic)
            putExtra("api_key", resource.apiKey)
            putExtra("api_secret", resource.apiSecret)
            putExtra("event_file_uri", resource.eventFile)
            putExtra("file_format", "JSON")
            putExtra("timestamp_property", resource.timestampProperty)
        }
        context.startForegroundService(serviceIntent)
    }

    private fun refreshResources(context: Context, onDone: () -> Unit) {
        val db = AppDatabase.getDatabase(context)
        CoroutineScope(Dispatchers.IO).launch {
            cachedResources = db.resourceDao().getAll()
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, PulseWidgetProvider::class.java))
        ids.forEach { updateWidget(context, appWidgetManager, it) }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_pulse_control)
        
        val isPlaying = AbstractPlayerService.runningServiceId != -1L
        val playingId = AbstractPlayerService.runningServiceId
        
        if (cachedResources.isNotEmpty()) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val index = prefs.getInt(KEY_INDEX, 0).coerceIn(0, cachedResources.size - 1)
            
            val displayResource = if (isPlaying) {
                cachedResources.find { it.id == playingId } ?: cachedResources[index]
            } else {
                cachedResources[index]
            }
            
            views.setTextViewText(R.id.widgetPulseName, displayResource.name)
            views.setTextViewText(R.id.widgetPulseDescription, displayResource.description ?: "No description.")
            views.setImageViewResource(R.id.widgetBtnPlayStop, if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        } else {
            views.setTextViewText(R.id.widgetPulseName, "No Pulses Loaded")
            views.setTextViewText(R.id.widgetPulseDescription, "")
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widgetPulseName, openAppPendingIntent)

        views.setOnClickPendingIntent(R.id.widgetBtnPrev, getActionPendingIntent(context, ACTION_PREV))
        views.setOnClickPendingIntent(R.id.widgetBtnNext, getActionPendingIntent(context, ACTION_NEXT))
        views.setOnClickPendingIntent(R.id.widgetBtnPlayStop, getActionPendingIntent(context, ACTION_PLAY_STOP))
        views.setOnClickPendingIntent(R.id.widgetBtnInfo, getActionPendingIntent(context, ACTION_INFO))

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    private fun getActionPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, PulseWidgetProvider::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
