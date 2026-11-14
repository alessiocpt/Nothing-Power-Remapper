package io.github.alessiocpt.powerremapper.settings

import io.github.alessiocpt.powerremapper.actions.KeyAction
import io.github.alessiocpt.powerremapper.core.KeyGesture
import org.json.JSONException
import org.json.JSONObject

/**
 * Pure translation between the app Profiles and the single JSON string DataStore stores them in.
 * Free of DataStore types so the mapping is unit-testable on the JVM.
 *
 * Decoding never throws: corrupt JSON reads as no Profiles, a Profile without a package is
 * skipped, and unknown gestures, fallbacks or action ids degrade the way [KeyAction.fromPersisted]
 * does, so a settings change between versions can never crash the reader.
 *
 * ```
 * {"profiles":[{"package":"com.nothing.camera","fallback":"isolated",
 *   "slots":{"single_press":{"id":"launch_app","payload":"org.example"}}}]}
 * ```
 */
object ProfileSerialization {

    private const val PROFILES_FIELD = "profiles"
    private const val PACKAGE_FIELD = "package"
    private const val FALLBACK_FIELD = "fallback"
    private const val SLOTS_FIELD = "slots"
    private const val ACTION_ID_FIELD = "id"
    private const val ACTION_PAYLOAD_FIELD = "payload"

    fun encodeProfiles(profiles: List<Profile>): String {
        val encoded = profiles.map { profile ->
            val slots = JSONObject()
            for (gesture in KeyGesture.entries) {
                val slot = profile.slotFor(gesture) as? GestureSlot.Assigned ?: continue
                val persisted = SettingsSerialization.encodeAction(slot.action)
                slots.put(
                    gesture.name.lowercase(),
                    JSONObject()
                        .put(ACTION_ID_FIELD, persisted.id)
                        .putOpt(ACTION_PAYLOAD_FIELD, persisted.payload)
                )
            }
            JSONObject()
                .put(PACKAGE_FIELD, profile.packageName)
                .put(FALLBACK_FIELD, profile.fallback.name.lowercase())
                .put(SLOTS_FIELD, slots)
        }
        return JSONObject().put(PROFILES_FIELD, encoded).toString()
    }

    fun decodeProfiles(json: String?): List<Profile> {
        if (json.isNullOrEmpty()) return emptyList()
        val profiles = try {
            JSONObject(json).optJSONArray(PROFILES_FIELD)
        } catch (error: JSONException) {
            null
        } ?: return emptyList()
        return (0 until profiles.length()).mapNotNull { index ->
            profiles.optJSONObject(index)?.let(::decodeProfile)
        }
    }

    private fun decodeProfile(json: JSONObject): Profile? {
        val packageName = json.optString(PACKAGE_FIELD).ifEmpty { return null }
        val fallback = Fallback.entries.firstOrNull { fallback ->
            fallback.name.equals(json.optString(FALLBACK_FIELD), ignoreCase = true)
        } ?: Fallback.INHERIT
        val slots = json.optJSONObject(SLOTS_FIELD)
        var profile = Profile.newFor(packageName).withFallback(fallback)
        for (gesture in KeyGesture.entries) {
            val action = slots?.optJSONObject(gesture.name.lowercase()) ?: continue
            profile = profile.withSlot(
                gesture,
                GestureSlot.Assigned(
                    KeyAction.fromPersisted(
                        id = action.optString(ACTION_ID_FIELD),
                        payload = action.optString(ACTION_PAYLOAD_FIELD).ifEmpty { null }
                    )
                )
            )
        }
        return profile
    }
}
