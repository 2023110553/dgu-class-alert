package kr.ac.dongguk.classalert.data

import android.content.Context
import org.json.JSONObject

object CourseCatalog {
    private val courseCode = Regex("^[A-Z]{2,}[A-Z0-9]*\\d{4}$")
    @Volatile private var cached: Map<String, String>? = null

    fun extractCode(category: String?): String? = category
        ?.split('_')
        ?.lastOrNull { courseCode.matches(it) }

    fun label(context: Context, category: String?): String? {
        val code = extractCode(category) ?: return null
        return courses(context)[code] ?: code
    }

    private fun courses(context: Context): Map<String, String> = cached ?: synchronized(this) {
        cached ?: context.assets.open("dgu_courses_2026.json").bufferedReader().use { reader ->
            val json = JSONObject(reader.readText()).getJSONObject("courses")
            buildMap {
                json.keys().forEach { code -> put(code, json.getString(code)) }
            }
        }.also { cached = it }
    }
}
