package com.lquiroz.flab.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.lquiroz.flab.core.ModuleId
import com.lquiroz.flab.profiles.AppProfile
import com.lquiroz.flab.profiles.ProfileId
import com.lquiroz.flab.profiles.TreatmentMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Everything the user has chosen, in one value. */
data class FLabConfiguration(
    val enabled: Boolean = false,
    val onboardingComplete: Boolean = false,
    val profileId: ProfileId = ProfileId.Balanced,
    val experimentsEnabled: Boolean = false,
    val moduleEnabled: Map<ModuleId, Boolean> = ModuleId.entries.associateWith { true },
    val appOverrides: List<AppProfile> = emptyList(),
)

/**
 * Persistent configuration.
 *
 * Deliberately `SharedPreferences` and deliberately small. Two DoD items depend on this being
 * boring: DoD 19 (survive a reboot without reconfiguring) and DoD 49's "an update over a previous
 * version keeps its configuration". Both are satisfied by storing plain primitives under stable
 * keys and tolerating unknown values on read, which is why every enum lookup below falls back
 * rather than throwing.
 *
 * `allowBackup` is off in the manifest, so this never travels to another device where its
 * per-app decisions would be meaningless.
 */
class FLabSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun current(): FLabConfiguration = FLabConfiguration(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        onboardingComplete = prefs.getBoolean(KEY_ONBOARDING, false),
        profileId = readProfile(),
        experimentsEnabled = prefs.getBoolean(KEY_EXPERIMENTS, false),
        moduleEnabled = ModuleId.entries.associateWith {
            prefs.getBoolean(moduleKey(it), true)
        },
        appOverrides = readOverrides(),
    )

    /** Emits the configuration now and on every change. Conflated: only the latest matters. */
    fun observe(): Flow<FLabConfiguration> = callbackFlow {
        trySend(current())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(current())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun setEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_ENABLED, enabled) }

    fun setOnboardingComplete(complete: Boolean) =
        prefs.edit { putBoolean(KEY_ONBOARDING, complete) }

    fun setProfile(id: ProfileId) = prefs.edit { putString(KEY_PROFILE, id.name) }

    fun setExperimentsEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_EXPERIMENTS, enabled) }

    fun setModuleEnabled(module: ModuleId, enabled: Boolean) =
        prefs.edit { putBoolean(moduleKey(module), enabled) }

    fun setAppOverride(profile: AppProfile) {
        val updated = readOverrides().filterNot { it.packageName == profile.packageName } + profile
        prefs.edit { putStringSet(KEY_OVERRIDES, updated.map(::encode).toSet()) }
    }

    fun clearAppOverride(packageName: String) {
        val updated = readOverrides().filterNot { it.packageName == packageName }
        prefs.edit { putStringSet(KEY_OVERRIDES, updated.map(::encode).toSet()) }
    }

    /** Reset F/LAB (DoD 21): forget everything F/LAB stored. Nothing outside this file is touched. */
    fun reset() = prefs.edit { clear() }

    private fun readProfile(): ProfileId {
        val stored = prefs.getString(KEY_PROFILE, null) ?: return ProfileId.Balanced
        return ProfileId.entries.firstOrNull { it.name == stored } ?: ProfileId.Balanced
    }

    private fun readOverrides(): List<AppProfile> =
        prefs.getStringSet(KEY_OVERRIDES, emptySet())
            .orEmpty()
            .mapNotNull(::decode)
            .sortedBy { it.displayName.lowercase() }

    // A record is `package|display|immersive|continuity`. Three fields of enum names and one free
    // string, so a hand-rolled encoding is cheaper than pulling in a serialization plugin, and a
    // record that does not parse is dropped rather than failing the whole read.
    private fun encode(profile: AppProfile): String = listOf(
        profile.packageName,
        profile.displayName.replace(SEPARATOR, ' '),
        profile.immersive.name,
        profile.continuity.name,
    ).joinToString(SEPARATOR.toString())

    private fun decode(record: String): AppProfile? {
        val parts = record.split(SEPARATOR)
        if (parts.size != 4) return null
        val immersive = TreatmentMode.entries.firstOrNull { it.name == parts[2] } ?: return null
        val continuity = TreatmentMode.entries.firstOrNull { it.name == parts[3] } ?: return null
        return AppProfile(
            packageName = parts[0],
            displayName = parts[1],
            immersive = immersive,
            continuity = continuity,
        )
    }

    private fun moduleKey(module: ModuleId) = "$KEY_MODULE_PREFIX${module.name}"

    private companion object {
        const val FILE_NAME = "flab_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_ONBOARDING = "onboarding_complete"
        const val KEY_PROFILE = "profile"
        const val KEY_EXPERIMENTS = "experiments_enabled"
        const val KEY_OVERRIDES = "app_overrides"
        const val KEY_MODULE_PREFIX = "module_"
        const val SEPARATOR = '|'
    }
}
