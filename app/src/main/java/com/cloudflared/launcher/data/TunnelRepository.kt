package com.cloudflared.launcher.data

import android.content.Context
import com.cloudflared.launcher.model.TunnelProfile
import com.cloudflared.launcher.model.TunnelStatus
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class TunnelRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getProfiles(): List<TunnelProfile> = synchronized(this) {
        val raw = prefs.getString(KEY_PROFILES, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                item.toTunnelProfile()?.let(::add)
            }
        }.sortedByDescending { it.updatedAt }
    }

    fun upsertProfile(
        id: String?,
        name: String,
        token: String,
        note: String
    ): TunnelProfile = synchronized(this) {
        val now = System.currentTimeMillis()
        val profiles = getProfiles().toMutableList()
        val existingIndex = profiles.indexOfFirst { it.id == id }
        val existing = profiles.getOrNull(existingIndex)
        val profile = TunnelProfile(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            token = token.trim(),
            note = note.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            lastUsedAt = existing?.lastUsedAt ?: 0L
        )

        if (existingIndex >= 0) {
            profiles[existingIndex] = profile
        } else {
            profiles += profile
        }
        saveProfiles(profiles)
        profile
    }

    fun deleteProfile(id: String) = synchronized(this) {
        saveProfiles(getProfiles().filterNot { it.id == id })
        val installed = installedIds().toMutableSet()
        installed.remove(id)
        prefs.edit()
            .putStringSet(KEY_INSTALLED, installed)
            .remove(logKey(id))
            .remove(statusKey(id))
            .apply()
    }

    fun markUsed(id: String) = synchronized(this) {
        val now = System.currentTimeMillis()
        saveProfiles(getProfiles().map { profile ->
            if (profile.id == id) profile.copy(lastUsedAt = now, updatedAt = now) else profile
        })
    }

    fun markInstalled(id: String, installed: Boolean) = synchronized(this) {
        val ids = installedIds().toMutableSet()
        if (installed) ids += id else ids -= id
        prefs.edit().putStringSet(KEY_INSTALLED, ids).apply()
    }

    fun isInstalled(id: String): Boolean = installedIds().contains(id)

    fun setStatus(id: String, status: TunnelStatus) {
        prefs.edit().putString(statusKey(id), status.name).apply()
    }

    fun getStatus(id: String): TunnelStatus {
        if (!isInstalled(id)) return TunnelStatus.NotInstalled
        val stored = prefs.getString(statusKey(id), null) ?: return TunnelStatus.Unknown
        return runCatching { TunnelStatus.valueOf(stored) }.getOrDefault(TunnelStatus.Unknown)
    }

    fun getLog(id: String): String = prefs.getString(logKey(id), "").orEmpty()

    fun appendLog(id: String, message: String) = synchronized(this) {
        val current = getLog(id)
        val next = (current + message).takeLast(MAX_LOG_CHARS)
        prefs.edit().putString(logKey(id), next).apply()
    }

    fun replaceLog(id: String, message: String) {
        prefs.edit().putString(logKey(id), message.takeLast(MAX_LOG_CHARS)).apply()
    }

    fun clearLog(id: String) {
        prefs.edit().remove(logKey(id)).apply()
    }

    fun hasSeenOnboarding(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingSeen() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    private fun installedIds(): Set<String> =
        prefs.getStringSet(KEY_INSTALLED, emptySet()).orEmpty().toSet()

    private fun saveProfiles(profiles: List<TunnelProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun TunnelProfile.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("token", token)
        .put("note", note)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("lastUsedAt", lastUsedAt)

    private fun JSONObject.toTunnelProfile(): TunnelProfile? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = optString("name").takeIf { it.isNotBlank() } ?: return null
        val token = optString("token")
        return TunnelProfile(
            id = id,
            name = name,
            token = token,
            note = optString("note"),
            createdAt = optLong("createdAt"),
            updatedAt = optLong("updatedAt"),
            lastUsedAt = optLong("lastUsedAt")
        )
    }

    private fun logKey(id: String) = "log_$id"
    private fun statusKey(id: String) = "status_$id"

    private companion object {
        const val PREFS_NAME = "cloudflared_launcher"
        const val KEY_PROFILES = "profiles"
        const val KEY_INSTALLED = "installed_profile_ids"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val MAX_LOG_CHARS = 40_000
    }
}
