package org.rustygnome.pulse.ui.main

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rustygnome.pulse.R
import org.rustygnome.pulse.data.AppDatabase
import org.rustygnome.pulse.data.Resource
import org.rustygnome.pulse.audio.player.FilePlayerService
import org.rustygnome.pulse.audio.player.KafkaPlayerService
import org.rustygnome.pulse.audio.player.WebSocketPlayerService
import org.rustygnome.pulse.audio.player.RandomPlayerService
import org.rustygnome.pulse.audio.player.RhythmicChaosService
import org.rustygnome.pulse.ui.settings.SettingsActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import java.util.Collections

class MainActivity : AppCompatActivity() {
    private var currentlyPlayingId: Long = -1L
    private var pendingResourceId: Long = -1L

    private lateinit var pluginRecyclerView: RecyclerView
    private lateinit var adapter: PluginPlaybackAdapter
    private lateinit var db: AppDatabase
    private lateinit var emptyView: TextView
    private var resourceList: MutableList<Resource> = mutableListOf()
    private lateinit var itemTouchHelper: ItemTouchHelper

    private val playerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val stoppedId = intent?.getLongExtra("resource_id", -1L) ?: -1L
            
            when (intent?.action) {
                KafkaPlayerService.ACTION_PLAYER_ERROR -> {
                    val errorMsg = intent.getStringExtra(KafkaPlayerService.EXTRA_ERROR_MESSAGE)
                    if (errorMsg != null) {
                        showErrorSnackbar(errorMsg)
                        if (stoppedId == currentlyPlayingId || stoppedId == -1L) {
                            onPlaybackStopped()
                        }
                    }
                }
                KafkaPlayerService.ACTION_PLAYER_STOPPED, 
                FilePlayerService.ACTION_PLAYER_STOPPED,
                WebSocketPlayerService.ACTION_PLAYER_STOPPED,
                RandomPlayerService.ACTION_PLAYER_STOPPED,
                RhythmicChaosService.ACTION_PLAYER_STOPPED -> {
                    if (stoppedId == currentlyPlayingId || stoppedId == -1L) {
                        onPlaybackStopped()
                    }
                }
            }
        }
    }

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resourceId = result.data?.getLongExtra("selected_resource_id", -1L) ?: -1L
            if (resourceId != -1L) {
                pendingResourceId = resourceId
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate: Activity created")

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)

        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "pulse-db-v5")
            .fallbackToDestructiveMigration()
            .build()

        pluginRecyclerView = findViewById(R.id.pluginRecyclerView)
        emptyView = findViewById(R.id.emptyView)

        adapter = PluginPlaybackAdapter(
            onPlayPauseClick = { resource, shouldPlay ->
                if (shouldPlay) {
                    startPlayback(resource)
                } else {
                    stopPlayback()
                }
            },
            onInfoClick = { showPluginDescription(it) },
            onStartDrag = { viewHolder -> itemTouchHelper.startDrag(viewHolder) }
        )
        pluginRecyclerView.adapter = adapter

        setupItemTouchHelper()
        loadResources()
    }

    private fun setupItemTouchHelper() {
        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                    Collections.swap(resourceList, fromPos, toPos)
                    adapter.notifyItemMoved(fromPos, toPos)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No-op
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                saveNewOrder()
            }
        })
        itemTouchHelper.attachToRecyclerView(pluginRecyclerView)
    }

    private fun saveNewOrder() {
        val updatedList = resourceList.mapIndexed { index, resource ->
            resource.copy(position = index)
        }
        CoroutineScope(Dispatchers.IO).launch {
            db.resourceDao().updatePositions(updatedList)
        }
    }

    private fun showPluginDescription(resource: Resource) {
        AlertDialog.Builder(this)
            .setTitle(resource.name)
            .setMessage(resource.description ?: "No description provided for this plugin.")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(KafkaPlayerService.ACTION_PLAYER_ERROR)
            addAction(KafkaPlayerService.ACTION_PLAYER_STOPPED)
            addAction(FilePlayerService.ACTION_PLAYER_STOPPED)
            addAction(WebSocketPlayerService.ACTION_PLAYER_STOPPED)
            addAction(RandomPlayerService.ACTION_PLAYER_STOPPED)
            addAction(RhythmicChaosService.ACTION_PLAYER_STOPPED)
        }
        registerReceiver(playerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(playerReceiver)
    }

    private fun showErrorSnackbar(message: String) {
        val rootLayout = findViewById<View>(R.id.mainLayout)
        Snackbar.make(rootLayout, message, Snackbar.LENGTH_LONG)
            .setAction("OK") { }
            .show()
    }

    private fun onPlaybackStopped() {
        currentlyPlayingId = -1L
        adapter.clearPlayingResource()
    }

    private fun playResourceById(resourceId: Long) {
        val resource = resourceList.find { it.id == resourceId }
        resource?.let {
            startPlayback(it)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        loadResources()
    }

    private fun loadResources() {
        CoroutineScope(Dispatchers.IO).launch {
            val list = db.resourceDao().getAll()
            withContext(Dispatchers.Main) {
                resourceList = list.toMutableList()
                adapter.submitList(resourceList.toList())
                
                emptyView.visibility = if (resourceList.isEmpty()) View.VISIBLE else View.GONE
                
                // Handle pending playback request from Settings
                if (pendingResourceId != -1L) {
                    playResourceById(pendingResourceId)
                    pendingResourceId = -1L
                }
            }
        }
    }

    private fun startPlayback(resource: Resource) {
        if (currentlyPlayingId != -1L) {
            stopPlayback()
        }

        currentlyPlayingId = resource.id
        adapter.setPlayingResource(resource.id)
        
        Log.i(TAG, "Starting playback for source: ${resource.name}")
        startSelectedService(resource)
    }

    private fun stopPlayback() {
        Log.i(TAG, "Stopping all playback")
        currentlyPlayingId = -1L
        adapter.clearPlayingResource()
        
        stopService(Intent(this, KafkaPlayerService::class.java))
        stopService(Intent(this, FilePlayerService::class.java))
        stopService(Intent(this, WebSocketPlayerService::class.java))
        stopService(Intent(this, RandomPlayerService::class.java))
        stopService(Intent(this, RhythmicChaosService::class.java))
    }

    private fun startSelectedService(resource: Resource) {
        Log.i(TAG, "Starting service for resource: ${resource.name}")
        
        val serviceIntent = when {
            resource.pluginType == "KAFKA" -> Intent(this, KafkaPlayerService::class.java)
            resource.pluginType == "WEBSOCKET" -> Intent(this, WebSocketPlayerService::class.java)
            resource.pluginType == "SIMULATION" -> Intent(this, RandomPlayerService::class.java)
            resource.pluginType == "RHYTHM" -> Intent(this, RhythmicChaosService::class.java)
            else -> {
                // Legacy detection or fallback
                when {
                    resource.webSocketUrl != null -> Intent(this, WebSocketPlayerService::class.java)
                    resource.topic != null -> Intent(this, KafkaPlayerService::class.java)
                    else -> Intent(this, FilePlayerService::class.java)
                }
            }
        }
        
        serviceIntent.apply {
            putExtra("resource_id", resource.id)
            putExtra("plugin_id", resource.pluginId)
            putExtra("config_content", resource.configContent) // Pass the full config for placeholder resolution
            putExtra("script_content", resource.scriptContent)
            putStringArrayListExtra("event_sounds", ArrayList(resource.eventSounds))
            putExtra("acoustic_style", resource.acousticStyle)
            
            // Legacy/Display fields (still passed for convenience or until services are fully refactored)
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
        startService(serviceIntent)
    }
}
