package org.rustygnome.pulse.ui.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.rustygnome.pulse.R
import org.rustygnome.pulse.data.AppDatabase
import org.rustygnome.pulse.data.Resource
import org.rustygnome.pulse.data.ResourceType
import org.rustygnome.pulse.data.SecurityHelper
import org.rustygnome.pulse.pulses.GitHubPulseService
import org.rustygnome.pulse.pulses.PulseManager
import java.io.IOException
import java.util.Collections
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var adapter: PulseSettingsAdapter
    private lateinit var securityHelper: SecurityHelper
    private lateinit var pulseManager: PulseManager
    private lateinit var githubService: GitHubPulseService
    private var resourceList: MutableList<Resource> = mutableListOf()
    private lateinit var itemTouchHelper: ItemTouchHelper

    private var txtSelectedPulse: TextView? = null
    private var currentPulseData: PulseManager.PulseData? = null
    private var activeEditDialog: AlertDialog? = null

    // Preview fields
    private var previewContainer: View? = null
    private var previewTitle: TextView? = null
    private var previewDescription: TextView? = null
    private var previewType: TextView? = null
    private var previewDetails: TextView? = null
    
    // Credentials fields
    private var credentialsContainer: LinearLayout? = null
    private var txtCredentialsHeader: TextView? = null
    private val credentialInputs = mutableMapOf<String, EditText>()

    private val pulsePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            currentPulseData = pulseManager.unpackPulse(uri)
            if (currentPulseData != null) {
                txtSelectedPulse?.text = uri.lastPathSegment ?: "Pulse unpacked"
                showPulsePreview(currentPulseData!!, null, false)
            } else {
                Toast.makeText(this, "Invalid Pulse ZIP", Toast.LENGTH_SHORT).show()
                previewContainer?.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: MaterialToolbar = findViewById(R.id.settingsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "pulse-db-v5")
            .fallbackToDestructiveMigration()
            .build()
        securityHelper = SecurityHelper(this)
        pulseManager = PulseManager(this)
        githubService = GitHubPulseService()

        val recyclerView: RecyclerView = findViewById(R.id.resourceRecyclerView)
        adapter = PulseSettingsAdapter(
            onEdit = { showEditDialog(it) },
            onDelete = { deleteResource(it) },
            onPlay = { selectAndPlay(it) },
            onStartDrag = { viewHolder -> itemTouchHelper.startDrag(viewHolder) }
        )
        recyclerView.adapter = adapter

        setupItemTouchHelper(recyclerView)

        findViewById<FloatingActionButton>(R.id.addResourceFab).setOnClickListener {
            showEditDialog(null)
        }

        loadResources()
    }

    private fun setupItemTouchHelper(recyclerView: RecyclerView) {
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
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun saveNewOrder() {
        val updatedList = resourceList.mapIndexed { index, resource ->
            resource.copy(position = index)
        }
        CoroutineScope(Dispatchers.IO).launch {
            db.resourceDao().updatePositions(updatedList)
        }
    }

    private fun loadResources() {
        CoroutineScope(Dispatchers.IO).launch {
            val list = db.resourceDao().getAll()
            withContext(Dispatchers.Main) {
                resourceList = list.toMutableList()
                adapter.submitList(resourceList.toList())
            }
        }
    }

    private fun selectAndPlay(resource: Resource) {
        val intent = Intent().apply {
            putExtra("selected_resource_id", resource.id)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun showEditDialog(resource: Resource?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_pulse, null)
        val switchEnableLocal = dialogView.findViewById<SwitchMaterial>(R.id.switchEnableLocal)
        val btnSelectPulse = dialogView.findViewById<Button>(R.id.btnSelectPulse)
        val btnDownloadPulse = dialogView.findViewById<Button>(R.id.btnDownloadPulse)
        txtSelectedPulse = dialogView.findViewById(R.id.txtSelectedPulse)

        previewContainer = dialogView.findViewById(R.id.previewContainer)
        previewTitle = dialogView.findViewById(R.id.previewTitle)
        previewDescription = dialogView.findViewById(R.id.previewDescription)
        previewType = dialogView.findViewById(R.id.previewType)
        previewDetails = dialogView.findViewById(R.id.previewDetails)
        
        credentialsContainer = dialogView.findViewById(R.id.credentialsContainer)
        txtCredentialsHeader = dialogView.findViewById(R.id.txtCredentialsHeader)

        switchEnableLocal.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                btnSelectPulse.visibility = View.VISIBLE
                btnDownloadPulse.visibility = View.GONE
            } else {
                btnSelectPulse.visibility = View.GONE
                btnDownloadPulse.visibility = View.VISIBLE
            }
        }

        btnSelectPulse.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
            pulsePickerLauncher.launch(intent)
        }

        btnDownloadPulse.setOnClickListener {
            showGitHubPulsesDialog()
        }

        val alertDialog = AlertDialog.Builder(this)
            .setTitle(if (resource == null) "Add Pulse" else "Edit Pulse")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        
        activeEditDialog = alertDialog

        alertDialog.setOnShowListener {
            val saveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                if (resource == null && currentPulseData == null) {
                    Toast.makeText(this, "Please select a .pulse file", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                var pulseName = resource?.name ?: "Unnamed Pulse"
                var pulseDescription = resource?.description
                var pulseType = resource?.pulseType
                var bootstrap: String? = resource?.bootstrapServers
                var topic: String? = resource?.topic
                var apiKey: String? = resource?.apiKey
                var apiSecret: String? = resource?.apiSecret
                var wsUrl: String? = resource?.webSocketUrl
                var wsPayload: String? = resource?.webSocketPayload
                var eventFile: String? = resource?.eventFile
                var acousticStyle = resource?.acousticStyle ?: "Default"
                var eventSounds = resource?.eventSounds ?: emptyList()
                var timestampProperty: String? = resource?.timestampProperty
                var configContent = resource?.configContent ?: ""

                currentPulseData?.let { data ->
                    configContent = data.config
                    try {
                        val configJson = JSONObject(data.config)
                        pulseName = configJson.optString("name", pulseName)
                        pulseDescription = configJson.optString("description", pulseDescription)
                        pulseType = configJson.optString("type", null)
                        bootstrap = configJson.optString("bootstrapServers", null)
                        topic = configJson.optString("topic", null)
                        apiKey = configJson.optString("apiKey", null)
                        apiSecret = configJson.optString("apiSecret", null)
                        wsUrl = configJson.optString("webSocketUrl", null)
                        wsPayload = configJson.optString("webSocketPayload", null)
                        eventFile = configJson.optString("eventFile", null)
                        acousticStyle = configJson.optString("acousticStyle", "Default")
                        timestampProperty = configJson.optString("timestampProperty", null)

                        val soundsArray = configJson.optJSONArray("eventSounds")
                        if (soundsArray != null) {
                            val list = mutableListOf<String>()
                            for (i in 0 until soundsArray.length()) list.add(soundsArray.getString(i))
                            eventSounds = list
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsActivity", "Error parsing pulse config", e)
                    }
                }

                // Mandatory field validation for ALL displayed credential inputs
                for ((key, input) in credentialInputs) {
                    if (input.text.toString().trim().isEmpty()) {
                        val label = when(key) {
                            "apiKey" -> "Kafka API Key"
                            "apiSecret" -> "Kafka API Secret"
                            else -> key.replace("_", " ").lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
                        }
                        Toast.makeText(this, "$label is mandatory!", Toast.LENGTH_LONG).show()
                        input.requestFocus()
                        return@setOnClickListener
                    }
                }

                // Encrypt entered credentials
                val encryptedApiKey = securityHelper.encrypt(credentialInputs["apiKey"]?.text?.toString() ?: securityHelper.decrypt(apiKey))
                val encryptedApiSecret = securityHelper.encrypt(credentialInputs["apiSecret"]?.text?.toString() ?: securityHelper.decrypt(apiSecret))

                val newResource = Resource(
                    id = resource?.id ?: 0,
                    name = pulseName,
                    description = pulseDescription,
                    pulseType = pulseType,
                    type = ResourceType.PULSE,
                    pulseId = currentPulseData?.id ?: resource?.pulseId ?: "",
                    configContent = configContent,
                    scriptContent = currentPulseData?.script ?: resource?.scriptContent ?: "",
                    bootstrapServers = bootstrap,
                    topic = topic,
                    apiKey = encryptedApiKey,
                    apiSecret = encryptedApiSecret,
                    webSocketUrl = wsUrl,
                    webSocketPayload = wsPayload,
                    eventFile = eventFile,
                    eventSounds = eventSounds,
                    acousticStyle = acousticStyle,
                    timestampProperty = timestampProperty,
                    position = resource?.position ?: (resourceList.maxOfOrNull { it.position }?.plus(1) ?: 0)
                )

                CoroutineScope(Dispatchers.IO).launch {
                    val existing = db.resourceDao().getAll().find { it.name == pulseName && it.id != resource?.id }
                    withContext(Dispatchers.Main) {
                        if (existing != null) {
                            AlertDialog.Builder(this@SettingsActivity)
                                .setTitle("Pulse Conflict")
                                .setMessage("A pulse named '$pulseName' already exists. Do you want to replace it or load both?")
                                .setPositiveButton("Replace") { _, _ ->
                                    savePulseWithCredentials(newResource.copy(id = existing.id, position = existing.position), alertDialog)
                                }
                                .setNeutralButton("Load Both") { _, _ ->
                                    savePulseWithCredentials(newResource.copy(id = 0, position = (resourceList.maxOfOrNull { it.position }?.plus(1) ?: 0)), alertDialog)
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else {
                            savePulseWithCredentials(newResource, alertDialog)
                        }
                    }
                }
            }

            resource?.let {
                txtSelectedPulse?.text = "Pulse: ${it.pulseId}"
                val mockData = PulseManager.PulseData(
                    it.pulseId,
                    it.scriptContent,
                    it.configContent,
                    ""
                )
                showPulsePreview(mockData, it, false)
            }
        }

        alertDialog.show()
    }

    private fun showGitHubPulsesDialog() {
        val progressDialog = AlertDialog.Builder(this)
            .setMessage("Fetching available pulses...")
            .setCancelable(false)
            .show()

        githubService.fetchAvailablePulses(
            onSuccess = { pulses ->
                runOnUiThread {
                    progressDialog.dismiss()
                    if (pulses.isEmpty()) {
                        Toast.makeText(this, "No pulses found online", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    val adapter = object : ArrayAdapter<GitHubPulseService.GitHubPulse>(
                        this, R.layout.item_github_pulse, pulses
                    ) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_github_pulse, parent, false)
                            val pulse = getItem(position)!!
                            view.findViewById<TextView>(R.id.pulseName).text = pulse.name
                            val descView = view.findViewById<TextView>(R.id.pulseDescription)

                            if (pulse.description.isNullOrBlank()) {
                                descView.visibility = View.GONE
                            } else {
                                descView.visibility = View.VISIBLE
                                descView.text = pulse.description
                            }
                            return view
                        }
                    }

                    AlertDialog.Builder(this)
                        .setTitle("Select Pulse to Download")
                        .setAdapter(adapter) { _, which ->
                            downloadAndUnpackPulse(pulses[which])
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            },
            onError = { e ->
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Error fetching pulses: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun downloadAndUnpackPulse(pulse: GitHubPulseService.GitHubPulse) {
        val progressDialog = AlertDialog.Builder(this)
            .setMessage("Downloading ${pulse.name}...")
            .setCancelable(false)
            .show()

        val client = OkHttpClient()
        val request = Request.Builder().url(pulse.downloadUrl).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this@SettingsActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this@SettingsActivity, "Download failed: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                val body = response.body
                if (body == null) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this@SettingsActivity, "Empty response from server", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                val unpackedData = pulseManager.unpackPulse(body.byteStream())
                runOnUiThread {
                    progressDialog.dismiss()
                    if (unpackedData != null) {
                        currentPulseData = unpackedData
                        txtSelectedPulse?.text = pulse.name
                        showPulsePreview(unpackedData, null, true)
                    } else {
                        Toast.makeText(this@SettingsActivity, "Failed to unpack downloaded pulse", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun savePulseWithCredentials(resource: Resource, mainDialog: AlertDialog) {
        val credentials = credentialInputs.filterKeys { it != "apiKey" && it != "apiSecret" }
            .mapValues { it.value.text.toString() }
        
        CoroutineScope(Dispatchers.IO).launch {
            val id = if (resource.id == 0L) {
                db.resourceDao().insert(resource)
            } else {
                db.resourceDao().update(resource)
                resource.id
            }
            
            // Save personal tokens securely
            if (credentials.isNotEmpty()) {
                securityHelper.saveCredentials(id, credentials)
            }
            
            loadResources()
            withContext(Dispatchers.Main) {
                mainDialog.dismiss()
            }
        }
    }

    private fun showPulsePreview(data: PulseManager.PulseData, resource: Resource?, isDownload: Boolean) {
        try {
            val config = JSONObject(data.config)
            previewContainer?.visibility = View.VISIBLE
            previewTitle?.text = config.optString("name", "Unnamed Pulse")
            
            val desc = config.optString("description", resource?.description ?: "")
            previewDescription?.text = desc
            previewDescription?.visibility = if (desc.isNotEmpty()) View.VISIBLE else View.GONE

            val type = config.optString("type", resource?.pulseType ?: "")
            previewType?.text = "Type: $type"

            val details = StringBuilder()
            when (type) {
                "WEBSOCKET" -> details.append("URL: ${config.optString("webSocketUrl", "")}")
                "KAFKA" -> {
                    details.append("Server: ${config.optString("bootstrapServers", "N/A")}\n")
                    details.append("Topic: ${config.optString("topic", "")}")
                }
                "FILE" -> {
                    details.append("File: ${config.optString("eventFile", "")}")
                    if (config.has("timestampProperty")) {
                        details.append("\nTimed: ${config.getString("timestampProperty")}")
                    }
                }
                else -> details.append("Custom configuration")
            }
            details.append("\nStyle: ${config.optString("acousticStyle", "Default")}")
            previewDetails?.text = details.toString()
            
            // Scan for placeholders in entire config JSON
            val placeholders = findPlaceholders(data.config)
            
            // Force add apiKey and apiSecret for KAFKA
            val requiredFields = placeholders.toMutableSet()
            if (type == "KAFKA" || config.has("topic") || resource?.pulseType == "KAFKA") {
                requiredFields.add("apiKey")
                requiredFields.add("apiSecret")
            }
            
            updateCredentialsUI(requiredFields, resource)

            if (isDownload) {
                activeEditDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                    text = "Back to List"
                    setOnClickListener {
                        previewContainer?.visibility = View.GONE
                        currentPulseData = null
                        txtSelectedPulse?.text = "No pulse selected"
                        updateCredentialsUI(emptySet(), null)
                        showGitHubPulsesDialog()
                    }
                }
            } else {
                activeEditDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                    text = "Cancel"
                    setOnClickListener {
                        activeEditDialog?.dismiss()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error displaying preview", e)
            previewContainer?.visibility = View.GONE
        }
    }

    private fun findPlaceholders(text: String): Set<String> {
        val regex = Regex("\\\$\\{([^}]+)\\}")
        return regex.findAll(text).map { it.groupValues[1] }.toSet()
    }

    private fun updateCredentialsUI(fields: Set<String>, resource: Resource?) {
        val container = credentialsContainer ?: return
        container.removeAllViews()
        credentialInputs.clear()
        
        if (fields.isEmpty()) {
            txtCredentialsHeader?.visibility = View.GONE
            container.visibility = View.GONE
            return
        }

        txtCredentialsHeader?.visibility = View.VISIBLE
        container.visibility = View.VISIBLE
        
        // Load existing values if editing
        val existingPlaceholders = resource?.let { 
            securityHelper.getCredentials(it.id, fields) 
        } ?: emptyMap()

        val existingApiKey = resource?.let { securityHelper.decrypt(it.apiKey) }
        val existingApiSecret = resource?.let { securityHelper.decrypt(it.apiSecret) }

        fields.forEach { key ->
            val layout = TextInputLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 8
                }
                val label = when(key) {
                    "apiKey" -> "Kafka Key"
                    "apiSecret" -> "Kafka Secret"
                    else -> key.replace("_", " ").lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
                }
                hint = label
                endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            }
            val editText = TextInputEditText(this).apply {
                val value = when(key) {
                    "apiKey" -> existingApiKey
                    "apiSecret" -> existingApiSecret
                    else -> existingPlaceholders[key]
                }
                setText(value ?: "")
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            layout.addView(editText)
            container.addView(layout)
            credentialInputs[key] = editText
        }
    }

    private fun deleteResource(resource: Resource) {
        AlertDialog.Builder(this)
            .setTitle("Delete Pulse")
            .setMessage("Are you sure you want to delete the pulse '${resource.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    db.resourceDao().delete(resource)
                    if (resource.pulseId != null) {
                        pulseManager.deletePulse(resource.pulseId)
                    }
                    securityHelper.deleteCredentials(resource.id)
                    loadResources()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
