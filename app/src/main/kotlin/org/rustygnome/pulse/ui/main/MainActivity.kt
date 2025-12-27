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
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rustygnome.pulse.R
import org.rustygnome.pulse.data.AppDatabase
import org.rustygnome.pulse.data.Resource
import org.rustygnome.pulse.audio.player.*
import org.rustygnome.pulse.ui.settings.SettingsActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import java.util.Collections

class MainActivity : AppCompatActivity() {
    private var currentlyPlayingId: Long = -1L
    private var pendingResourceId: Long = -1L

    private lateinit var pulseRecyclerView: RecyclerView
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var adapter: PulsePlaybackAdapter
    private lateinit var db: AppDatabase
    private lateinit var emptyView: TextView
    private var resourceList: MutableList<Resource> = mutableListOf()
    private lateinit var itemTouchHelper: ItemTouchHelper
    
    private var visualizerView: PulseVisualizerView? = null
    private var visualizerMode: PulseVisualizerView.Mode = PulseVisualizerView.Mode.RIPPLE

    private val playerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val stoppedId = intent?.getLongExtra("resource_id", -1L) ?: -1L
            
            when (intent?.action) {
                PlayerService.ACTION_PULSE_FIRE -> {
                    val vol = intent.getFloatExtra(PlayerService.EXTRA_VOLUME, 1.0f)
                    val pitch = intent.getFloatExtra(PlayerService.EXTRA_PITCH, 1.0f)
                    visualizerView?.onPulse(vol, pitch)
                }
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
                RhythmicPlayerService.ACTION_PLAYER_STOPPED -> {
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
        
        visualizerView = findViewById(R.id.pulseVisualizer)
        appBarLayout = findViewById(R.id.appBarLayout)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)

        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "pulse-db-v5")
            .fallbackToDestructiveMigration()
            .build()

        pulseRecyclerView = findViewById(R.id.pulseRecyclerView)
        emptyView = findViewById(R.id.emptyView)

        adapter = PulsePlaybackAdapter(
            onPlayPauseClick = { resource, shouldPlay ->
                if (shouldPlay) {
                    startPlayback(resource)
                } else {
                    stopPlayback()
                }
            },
            onInfoClick = { showPulseDescription(it) },
            onStartDrag = { viewHolder -> itemTouchHelper.startDrag(viewHolder) }
        )
        pulseRecyclerView.adapter = adapter

        setupItemTouchHelper()
        loadResources()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (currentlyPlayingId != -1L && visualizerMode != PulseVisualizerView.Mode.NONE) {
            if (ev?.action == MotionEvent.ACTION_DOWN) {
                stopPlayback()
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
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

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                saveNewOrder()
            }
        })
        itemTouchHelper.attachToRecyclerView(pulseRecyclerView)
    }

    private fun saveNewOrder() {
        val updatedList = resourceList.mapIndexed { index, resource ->
            resource.copy(position = index)
        }
        CoroutineScope(Dispatchers.IO).launch {
            db.resourceDao().updatePositions(updatedList)
        }
    }

    private fun showPulseDescription(resource: Resource) {
        AlertDialog.Builder(this)
            .setTitle(resource.name)
            .setMessage(resource.description ?: "No description provided for this pulse.")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(PlayerService.ACTION_PULSE_FIRE)
            addAction(KafkaPlayerService.ACTION_PLAYER_ERROR)
            addAction(KafkaPlayerService.ACTION_PLAYER_STOPPED)
            addAction(FilePlayerService.ACTION_PLAYER_STOPPED)
            addAction(WebSocketPlayerService.ACTION_PLAYER_STOPPED)
            addAction(RandomPlayerService.ACTION_PLAYER_STOPPED)
            addAction(RhythmicPlayerService.ACTION_PLAYER_STOPPED)
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
        visualizerView?.setPulseName(null)
        updateUIVisibility(false)
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
        
        val prefs = getSharedPreferences("pulse_prefs", Context.MODE_PRIVATE)
        val modeStr = prefs.getString("visualizer_mode", "RIPPLE") ?: "RIPPLE"
        visualizerMode = try {
            PulseVisualizerView.Mode.valueOf(modeStr)
        } catch (e: Exception) {
            PulseVisualizerView.Mode.RIPPLE
        }
        
        visualizerView?.setMode(visualizerMode)
        visualizerView?.visibility = if (visualizerMode == PulseVisualizerView.Mode.NONE) View.GONE else View.VISIBLE
        
        if (currentlyPlayingId != -1L) {
            val res = resourceList.find { it.id == currentlyPlayingId }
            visualizerView?.setPulseName(res?.name)
        }
        
        updateUIVisibility(currentlyPlayingId != -1L)
        
        loadResources()
    }

    private fun loadResources() {
        CoroutineScope(Dispatchers.IO).launch {
            val list = db.resourceDao().getAll()
            withContext(Dispatchers.Main) {
                resourceList = list.toMutableList()
                adapter.submitList(resourceList.toList())
                emptyView.visibility = if (resourceList.isEmpty()) View.VISIBLE else View.GONE
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
        visualizerView?.setPulseName(resource.name)
        updateUIVisibility(true)
        
        Log.i(TAG, "Starting playback for source: ${resource.name}")
        startSelectedService(resource)
    }

    private fun stopPlayback() {
        Log.i(TAG, "Stopping all playback")
        currentlyPlayingId = -1L
        adapter.clearPlayingResource()
        visualizerView?.setPulseName(null)
        updateUIVisibility(false)
        
        stopService(Intent(this, KafkaPlayerService::class.java))
        stopService(Intent(this, FilePlayerService::class.java))
        stopService(Intent(this, WebSocketPlayerService::class.java))
        stopService(Intent(this, RandomPlayerService::class.java))
        stopService(Intent(this, RhythmicPlayerService::class.java))
    }

    private fun updateUIVisibility(isPlaying: Boolean) {
        val hideUI = isPlaying && visualizerMode != PulseVisualizerView.Mode.NONE
        val duration = 400L
        
        if (hideUI) {
            pulseRecyclerView.animate().alpha(0f).setDuration(duration).start()
            appBarLayout.animate().translationY(-appBarLayout.height.toFloat()).setDuration(duration).start()
        } else {
            pulseRecyclerView.animate().alpha(1f).setDuration(duration).start()
            appBarLayout.animate().translationY(0f).setDuration(duration).start()
        }
    }

    private fun startSelectedService(resource: Resource) {
        Log.i(TAG, "Starting service for resource: ${resource.name}")
        
        val serviceIntent = when {
            resource.pulseType == "KAFKA" -> Intent(this, KafkaPlayerService::class.java)
            resource.pulseType == "WEBSOCKET" -> Intent(this, WebSocketPlayerService::class.java)
            resource.pulseType == "SIMULATION" -> Intent(this, RandomPlayerService::class.java)
            resource.pulseType == "RHYTHM" -> Intent(this, RhythmicPlayerService::class.java)
            else -> {
                when {
                    resource.webSocketUrl != null -> Intent(this, WebSocketPlayerService::class.java)
                    resource.topic != null -> Intent(this, KafkaPlayerService::class.java)
                    else -> Intent(this, FilePlayerService::class.java)
                }
            }
        }
        
        serviceIntent.apply {
            putExtra("resource_id", resource.id)
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
        startService(serviceIntent)
    }
}
