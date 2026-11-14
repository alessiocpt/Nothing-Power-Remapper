package io.github.alessiocpt.powerremapper.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.alessiocpt.powerremapper.R
import io.github.alessiocpt.powerremapper.actions.KeyAction
import io.github.alessiocpt.powerremapper.core.KeyGesture
import io.github.alessiocpt.powerremapper.settings.Fallback
import io.github.alessiocpt.powerremapper.settings.GestureSlot
import io.github.alessiocpt.powerremapper.settings.SettingsRepository
import io.github.alessiocpt.powerremapper.ui.AppLabelResolver
import io.github.alessiocpt.powerremapper.ui.CameraApps
import io.github.alessiocpt.powerremapper.ui.UiLabels
import io.github.alessiocpt.powerremapper.ui.components.NothingCard
import io.github.alessiocpt.powerremapper.ui.components.NothingSectionLabel
import io.github.alessiocpt.powerremapper.ui.screenContentPadding
import kotlinx.coroutines.launch

private val SCREEN_PADDING = 24.dp
private val TITLE_TO_CONTENT_GAP = 32.dp
private val CARD_GAP = 16.dp
private val LABEL_GAP = 12.dp
private val ROW_VERTICAL_PADDING = 12.dp
private val ROW_LABEL_GAP = 16.dp

/**
 * Edits one app Profile: its Fallback, and each Gesture as Unassigned, Do Nothing or an Action.
 * An Unassigned Gesture shows, greyed, what it will actually do, so the effect of the Fallback and
 * of the Default Profile is visible without leaving the screen.
 */
@Composable
fun ProfileEditorScreen(
    packageName: String,
    onEditGesture: (KeyGesture) -> Unit,
    systemBarsPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { SettingsRepository.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val profileSet by repository.profileSet.collectAsState(initial = null)
    val profiles = profileSet ?: return
    val profile = profiles.profileFor(packageName) ?: return
    val cameraApp = remember(packageName) { CameraApps.isCameraApp(context, packageName) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(screenContentPadding(systemBarsPadding, SCREEN_PADDING))
    ) {
        Text(
            text = AppLabelResolver.labelFor(context, packageName).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = packageName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(TITLE_TO_CONTENT_GAP))

        NothingCard(modifier = Modifier.fillMaxWidth()) {
            NothingSectionLabel(text = stringResource(R.string.profile_editor_fallback))
            Spacer(modifier = Modifier.height(LABEL_GAP))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.profile_editor_isolated_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(
                            when (profile.fallback) {
                                Fallback.INHERIT -> R.string.profiles_fallback_inherit
                                Fallback.ISOLATED -> R.string.profiles_fallback_isolated
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(ROW_LABEL_GAP))
                Switch(
                    checked = profile.fallback == Fallback.ISOLATED,
                    onCheckedChange = { isolated ->
                        coroutineScope.launch {
                            repository.setProfileFallback(
                                packageName,
                                if (isolated) Fallback.ISOLATED else Fallback.INHERIT
                            )
                        }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(CARD_GAP))

        NothingCard(modifier = Modifier.fillMaxWidth()) {
            NothingSectionLabel(text = stringResource(R.string.profile_editor_gestures))
            Spacer(modifier = Modifier.height(LABEL_GAP))
            Column(verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
                for (gesture in KeyGesture.entries) {
                    val slot = profile.slotFor(gesture)
                    val resolved = profiles.resolve(packageName, gesture)
                    val unavailable = cameraApp && resolved == KeyAction.ToggleFlashlight
                    GestureSlotRow(
                        gesture = gesture,
                        label = if (unavailable) {
                            stringResource(R.string.profile_editor_unavailable_in_camera)
                        } else {
                            slotLabel(context, slot, resolved)
                        },
                        inherited = slot == GestureSlot.Unassigned || unavailable,
                        onClick = { onEditGesture(gesture) }
                    )
                }
            }
        }
    }
}

/**
 * An Assigned slot names its Action. An Unassigned one names what it resolves to and why: the
 * Default Profile's Action under Inherit, nothing under Isolated.
 */
@Composable
private fun slotLabel(context: Context, slot: GestureSlot, resolved: KeyAction): String {
    val resolvedLabel = actionLabel(context, resolved)
    return when (slot) {
        is GestureSlot.Assigned -> actionLabel(context, slot.action)
        GestureSlot.Unassigned -> if (resolved == KeyAction.DoNothing) {
            stringResource(R.string.profile_editor_unassigned_nothing)
        } else {
            stringResource(R.string.profile_editor_unassigned_default, resolvedLabel)
        }
    }
}

private fun actionLabel(context: Context, action: KeyAction): String = when (action) {
    is KeyAction.LaunchApp -> AppLabelResolver.labelFor(context, action.packageName)
    else -> context.getString(UiLabels.builtInActionLabelRes(action.id))
}

@Composable
private fun GestureSlotRow(
    gesture: KeyGesture,
    label: String,
    inherited: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = ROW_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(UiLabels.gestureLabelRes(gesture)),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(ROW_LABEL_GAP))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (inherited) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            textAlign = TextAlign.End,
            // Weighted too, so a long label like "Flashlight (from Default)" wraps on its own side
            // instead of squeezing the gesture name.
            modifier = Modifier.weight(1f)
        )
    }
}
