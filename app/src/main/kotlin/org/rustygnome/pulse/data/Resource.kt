package org.rustygnome.pulse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resources")
data class Resource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val pulseType: String? = null, // e.g. "KAFKA", "WEBSOCKET", "FILE"
    val type: ResourceType = ResourceType.PULSE,
    // Pulse specific
    val pulseId: String,
    val configContent: String, // Store raw config.json to preserve all fields and placeholders
    val scriptContent: String,
    // Extracted configuration for quick access/display
    val bootstrapServers: String? = null,
    val topic: String? = null,
    val apiKey: String? = null,
    val apiSecret: String? = null,
    val webSocketUrl: String? = null,
    val webSocketPayload: String? = null,
    val eventFile: String? = null,
    val eventSounds: List<String> = emptyList(),
    val acousticStyle: String,
    val timestampProperty: String? = null,
    val position: Int = 0
)

enum class ResourceType {
    PULSE, KAFKA, FILE, WEBSOCKET
}

enum class FileFormat {
    JSON, CSV
}
