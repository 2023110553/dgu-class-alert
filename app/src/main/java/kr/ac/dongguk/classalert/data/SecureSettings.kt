package kr.ac.dongguk.classalert.data

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSettings(context: Context) {
    private val prefs = context.getSharedPreferences("secure_settings", Context.MODE_PRIVATE)
    private val normal = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var calendarUrl: String?
        get() = decrypt(prefs.getString("calendar_url", null))
        set(value) { prefs.edit().putString("calendar_url", value?.let(::encrypt)).apply() }
    var alertOffsetsHours: List<Long>
        get() = normal.getString("offsets", "72,24,3")!!.split(',').mapNotNull(String::toLongOrNull).filter { it > 0 }.distinct()
        set(value) { normal.edit().putString("offsets", value.joinToString(",")).apply() }
    var assignmentAlerts: Boolean
        get() = normal.getBoolean("assignment_alerts", true)
        set(v) { normal.edit().putBoolean("assignment_alerts", v).apply() }
    var lectureAlerts: Boolean
        get() = normal.getBoolean("lecture_alerts", true)
        set(v) { normal.edit().putBoolean("lecture_alerts", v).apply() }
    var lastSuccess: Long?
        get() = normal.getLong("last_success", -1).takeIf { it >= 0 }
        set(v) { normal.edit().putLong("last_success", v ?: -1).apply() }
    var lastError: String?
        get() = normal.getString("last_error", null)
        set(v) { normal.edit().putString("last_error", v).apply() }

    fun courseAlias(code: String): String? = JSONObject(normal.getString("course_aliases", "{}") ?: "{}")
        .optString(code).trim().takeIf { it.isNotEmpty() }

    fun setCourseAlias(code: String, name: String) {
        val aliases = JSONObject(normal.getString("course_aliases", "{}") ?: "{}")
        if (name.isBlank()) aliases.remove(code) else aliases.put(code, name.trim())
        normal.edit().putString("course_aliases", aliases.toString()).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }
    private fun decrypt(value: String?): String? = value?.let { runCatching {
        val bytes = Base64.decode(it, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }.getOrNull() }
    companion object { private const val ALIAS = "class_alert_calendar_url" }
}

