package io.github.alessiocpt.powerremapper.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.alessiocpt.powerremapper.actions.KeyAction
import io.github.alessiocpt.powerremapper.core.KeyGesture
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun onboardingStepPersistsUntilOnboardingCompletes() = runBlocking {
        val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStoreFile = File(temporaryFolder.root, "settings.preferences_pb")
        val repository = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { dataStoreFile }
            )
        )

        try {
            assertEquals(
                OnboardingState(completed = false, step = OnboardingStep.LANGUAGE),
                repository.onboardingState.first()
            )

            repository.setOnboardingStep(OnboardingStep.ACCESSIBILITY)
            assertEquals(
                OnboardingState(completed = false, step = OnboardingStep.ACCESSIBILITY),
                repository.onboardingState.first()
            )

            repository.setOnboardingCompleted()
            assertEquals(
                OnboardingState(completed = true, step = OnboardingStep.LANGUAGE),
                repository.onboardingState.first()
            )
        } finally {
            dataStoreScope.cancel()
        }
    }

    @Test
    fun hapticStrengthDefaultsOffAndPersists() = runBlocking {
        val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStoreFile = File(temporaryFolder.root, "settings.preferences_pb")
        val repository = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { dataStoreFile }
            )
        )

        try {
            assertEquals(HapticStrength.OFF, repository.hapticStrength.first())
            assertEquals(false, repository.hapticsOnActionOnly.first())

            repository.setHapticStrength(HapticStrength.STRONG)
            repository.setHapticsOnActionOnly(true)
            assertEquals(HapticStrength.STRONG, repository.hapticStrength.first())
            assertEquals(true, repository.hapticsOnActionOnly.first())
        } finally {
            dataStoreScope.cancel()
        }
    }

    @Test
    fun actionsOnlyWhenUnlockedDefaultsOffAndPersists() = runBlocking {
        val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStoreFile = File(temporaryFolder.root, "settings.preferences_pb")
        val repository = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { dataStoreFile }
            )
        )

        try {
            assertEquals(false, repository.actionsOnlyWhenUnlocked.first())

            repository.setActionsOnlyWhenUnlocked(true)
            assertEquals(true, repository.actionsOnlyWhenUnlocked.first())

            repository.setActionsOnlyWhenUnlocked(false)
            assertEquals(false, repository.actionsOnlyWhenUnlocked.first())
        } finally {
            dataStoreScope.cancel()
        }
    }

    @Test
    fun customHapticPatternDefaultsAndPersistsWithinRange() = runBlocking {
        val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStoreFile = File(temporaryFolder.root, "settings.preferences_pb")
        val repository = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { dataStoreFile }
            )
        )

        try {
            assertEquals(HapticPattern.DEFAULT, repository.customHapticPattern.first())

            val pattern = HapticPattern(
                pulseCount = 3,
                pulseMillis = 30,
                frequencyHz = 8,
                amplitudePercent = 60
            )
            repository.setCustomHapticPattern(pattern)
            assertEquals(pattern, repository.customHapticPattern.first())

            repository.setCustomHapticPattern(
                HapticPattern(pulseCount = 99, pulseMillis = 1, frequencyHz = 0, amplitudePercent = 500)
            )
            assertEquals(
                HapticPattern(
                    pulseCount = HapticPattern.PULSE_COUNT_RANGE.last,
                    pulseMillis = HapticPattern.PULSE_MILLIS_RANGE.first,
                    frequencyHz = HapticPattern.FREQUENCY_HZ_RANGE.first,
                    amplitudePercent = HapticPattern.AMPLITUDE_PERCENT_RANGE.last
                ),
                repository.customHapticPattern.first()
            )
        } finally {
            dataStoreScope.cancel()
        }
    }

    @Test
    fun themeStyleDefaultsToNothingAndPersists() = runBlocking {
        val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStoreFile = File(temporaryFolder.root, "settings.preferences_pb")
        val repository = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { dataStoreFile }
            )
        )

        try {
            assertEquals(ThemeStyle.NOTHING, repository.themeStyle.first())

            repository.setThemeStyle(ThemeStyle.MATERIAL)
            assertEquals(ThemeStyle.MATERIAL, repository.themeStyle.first())
        } finally {
            dataStoreScope.cancel()
        }
    }

    @Test
    fun profileSetCombinesTheDefaultProfileWithPersistedAppProfiles() = runBlocking {
        val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStoreFile = File(temporaryFolder.root, "settings.preferences_pb")
        val repository = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { dataStoreFile }
            )
        )
        val camera = "com.nothing.camera"

        try {
            assertNothingConfigured(repository.profileSet.first())

            repository.setActionFor(KeyGesture.DOUBLE_PRESS, KeyAction.ToggleFlashlight)
            repository.upsertProfile(Profile.newFor(camera))
            repository.setProfileSlot(
                camera,
                KeyGesture.SINGLE_PRESS,
                GestureSlot.Assigned(KeyAction.MediaPlayPause)
            )
            repository.setProfileFallback(camera, Fallback.ISOLATED)

            val profiles = repository.profileSet.first()
            assertEquals(KeyAction.ToggleFlashlight, profiles.resolve(null, KeyGesture.DOUBLE_PRESS))
            assertEquals(KeyAction.MediaPlayPause, profiles.resolve(camera, KeyGesture.SINGLE_PRESS))
            assertEquals(KeyAction.DoNothing, profiles.resolve(camera, KeyGesture.DOUBLE_PRESS))

            repository.setProfileSlot(camera, KeyGesture.SINGLE_PRESS, GestureSlot.Unassigned)
            assertEquals(
                GestureSlot.Unassigned,
                repository.profileSet.first().profileFor(camera)?.slotFor(KeyGesture.SINGLE_PRESS)
            )

            repository.deleteProfile(camera)
            assertNull(repository.profileSet.first().profileFor(camera))
        } finally {
            dataStoreScope.cancel()
        }
    }

    @Test
    fun editingAProfileThatDoesNotExistChangesNothing() = runBlocking {
        val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStoreFile = File(temporaryFolder.root, "settings.preferences_pb")
        val repository = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { dataStoreFile }
            )
        )

        try {
            repository.setProfileFallback("com.example.missing", Fallback.ISOLATED)
            repository.setProfileSlot(
                "com.example.missing",
                KeyGesture.LONG_PRESS,
                GestureSlot.Assigned(KeyAction.LockScreen)
            )

            assertNothingConfigured(repository.profileSet.first())
        } finally {
            dataStoreScope.cancel()
        }
    }

    // The Default Profile reads back as an explicit Do Nothing per gesture, which behaves like an
    // empty map without being equal to one, so this checks behaviour rather than equality.
    private fun assertNothingConfigured(profiles: ProfileSet) {
        assertEquals(emptyList<Profile>(), profiles.appProfiles)
        assertEquals(emptySet<KeyGesture>(), profiles.activeGestures(null))
    }
}
