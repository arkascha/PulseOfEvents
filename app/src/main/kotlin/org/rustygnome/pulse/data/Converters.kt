package org.rustygnome.pulse.data

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return JSONArray(value).toString()
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val jsonArray = JSONArray(value)
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }
}
