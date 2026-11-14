package io.github.alessiocpt.powerremapper.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.alessiocpt.powerremapper.R
import io.github.alessiocpt.powerremapper.actions.KeyAction
import io.github.alessiocpt.powerremapper.core.KeyGesture
import io.github.alessiocpt.powerremapper.diagnostics.CrashReportStore
import io.github.alessiocpt.powerremapper.diagnostics.buildCrashIssueUrl
import io.github.alessiocpt.powerremapper.diagnostics.ProcessExit
import io.github.alessiocpt.powerremapper.diagnostics.formatProcessExit
import io.github.alessiocpt.powerremapper.diagnostics.readRecentProcessExits
import io.github.alessiocpt.powerremapper.settings.Fallback
import io.github.alessiocpt.powerremapper.settings.GestureSlot
import io.github.alessiocpt.powerremapper.settings.Profile
import io.github.alessiocpt.powerremapper.settings.SettingsRepository
import io.github.alessiocpt.powerremapper.ui.ISSUES_URL
import io.github.alessiocpt.powerremapper.ui.components.NothingButton
import io.github.alessiocpt.powerremapper.ui.components.NothingCard
import io.github.alessiocpt.powerremapper.ui.components.NothingSectionLabel
import io.github.alessiocpt.powerremapper.ui.openExternalUrl
import io.github.alessiocpt.powerremapper.ui.screenContentPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SCREEN_PADDING = 24.dp
private val TITLE_TO_CONTENT_GAP = 32.dp
private val CARD_GAP = 16.dp
private val LABEL_GAP = 12.dp
private val BODY_GAP = 16.dp
private val BUTTON_GAP = 12.dp
private val EXIT_ROW_GAP = 8.dp

private const val CLIP_LABEL = "Nothing Poweruser Remapper crash report"

// Temporary until the Profile screens exist (Task 8): a fixed Profile to try on a debug build.
private const val TEST_PROFILE_PACKAGE = "com.nothing.camera"

/**
 * Turns "it crashed" into something reportable. The last crash this app caught, plus the exits
 * Android itself recorded, rendered so the user can copy or share them into a bug report.
 *
 * Nothing here leaves the device on its own: every way out of this screen is a deliberate user
 * action, which is what keeps the app free of any data-collection disclosure.
 */
@Composable
fun DiagnosticsScreen(
    systemBarsPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val store = remember(context) { CrashReportStore.create(context) }
    var crashReport by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(store) {
        crashReport = withContext(Dispatchers.IO) { store.read() }
    }
    val processExits by produceState(initialValue = emptyList<ProcessExit>(), context) {
        value = withContext(Dispatchers.IO) { readRecentProcessExits(context) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(screenContentPadding(systemBarsPadding, SCREEN_PADDING))
    ) {
        Text(
            text = stringResource(R.string.diagnostics_title),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(TITLE_TO_CONTENT_GAP))

        CrashReportCard(
            report = crashReport,
            onCopy = { report -> coroutineScope.launch { copyReport(clipboard, report) } },
            onShare = { shareReport(context, it) },
            onReport = { report ->
                // The trace is far too long for a query string, so it travels on the clipboard while
                // the issue form opens prefilled with the title and the short device facts.
                coroutineScope.launch {
                    copyReport(clipboard, report)
                    openExternalUrl(context, buildCrashIssueUrl(ISSUES_URL, report))
                }
            },
            onClear = {
                coroutineScope.launch {
                    withContext(Dispatchers.IO) { store.clear() }
                    crashReport = null
                }
            }
        )
        Spacer(modifier = Modifier.height(CARD_GAP))
        ProcessExitsCard(exits = processExits)
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Spacer(modifier = Modifier.height(CARD_GAP))
            TestProfileCard()
        }
    }
}

/**
 * Debug builds only, until the Profile screens exist: adds or removes a fixed Isolated Profile for
 * Nothing Camera that maps long press to a screenshot, so Profile switching can be tried on a phone.
 */
@Composable
private fun TestProfileCard() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember(context) { SettingsRepository.getInstance(context) }
    val profiles by repository.profileSet.collectAsState(initial = null)
    val hasTestProfile = profiles?.profileFor(TEST_PROFILE_PACKAGE) != null
    NothingCard(modifier = Modifier.fillMaxWidth()) {
        NothingSectionLabel(text = stringResource(R.string.diagnostics_test_profile_title))
        Spacer(modifier = Modifier.height(LABEL_GAP))
        Text(
            text = stringResource(R.string.diagnostics_test_profile_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(BODY_GAP))
        NothingButton(
            text = stringResource(
                if (hasTestProfile) {
                    R.string.diagnostics_test_profile_remove
                } else {
                    R.string.diagnostics_test_profile_add
                }
            ),
            onClick = {
                coroutineScope.launch {
                    if (hasTestProfile) {
                        repository.deleteProfile(TEST_PROFILE_PACKAGE)
                    } else {
                        repository.upsertProfile(
                            Profile.newFor(TEST_PROFILE_PACKAGE)
                                .withFallback(Fallback.ISOLATED)
                                .withSlot(
                                    KeyGesture.LONG_PRESS,
                                    GestureSlot.Assigned(KeyAction.TakeScreenshot)
                                )
                        )
                    }
                }
            },
            enabled = profiles != null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CrashReportCard(
    report: String?,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onReport: (String) -> Unit,
    onClear: () -> Unit
) {
    NothingCard(modifier = Modifier.fillMaxWidth()) {
        NothingSectionLabel(text = stringResource(R.string.diagnostics_crash_label))
        Spacer(modifier = Modifier.height(LABEL_GAP))
        if (report == null) {
            Text(
                text = stringResource(R.string.diagnostics_crash_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@NothingCard
        }

        Text(
            text = stringResource(R.string.diagnostics_crash_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(BODY_GAP))
        // A stack trace is wide: let it scroll sideways inside the card instead of wrapping into
        // an unreadable block or pushing the page out of the viewport.
        Text(
            text = report,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        )
        Spacer(modifier = Modifier.height(BODY_GAP))
        Column(verticalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
            NothingButton(
                text = stringResource(R.string.diagnostics_action_copy),
                onClick = { onCopy(report) },
                modifier = Modifier.fillMaxWidth()
            )
            NothingButton(
                text = stringResource(R.string.diagnostics_action_share),
                onClick = { onShare(report) },
                outlined = true,
                modifier = Modifier.fillMaxWidth()
            )
            NothingButton(
                text = stringResource(R.string.diagnostics_action_report),
                onClick = { onReport(report) },
                outlined = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.diagnostics_report_caption),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NothingButton(
                text = stringResource(R.string.diagnostics_action_clear),
                onClick = onClear,
                outlined = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ProcessExitsCard(exits: List<ProcessExit>) {
    NothingCard(modifier = Modifier.fillMaxWidth()) {
        NothingSectionLabel(text = stringResource(R.string.diagnostics_exits_label))
        Spacer(modifier = Modifier.height(LABEL_GAP))
        Text(
            text = stringResource(R.string.diagnostics_exits_caption),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(BODY_GAP))
        if (exits.isEmpty()) {
            Text(
                text = stringResource(R.string.diagnostics_exits_empty),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@NothingCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(EXIT_ROW_GAP)) {
            for (exit in exits) {
                Text(
                    text = formatProcessExit(exit),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

/**
 * Android 13+ shows its own copy confirmation, so the app adds no message of its own. The clip is
 * marked sensitive so the system preview does not put a stack trace — which can carry file paths
 * and URIs — on screen for anyone standing nearby.
 */
private suspend fun copyReport(clipboard: androidx.compose.ui.platform.Clipboard, report: String) {
    val clip = ClipData.newPlainText(CLIP_LABEL, report)
    clip.description.extras = PersistableBundle().apply {
        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
    clipboard.setClipEntry(ClipEntry(clip))
}

private fun shareReport(context: android.content.Context, report: String) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, report)
    }
    try {
        context.startActivity(Intent.createChooser(share, null))
    } catch (error: ActivityNotFoundException) {
        Toast.makeText(context, R.string.diagnostics_share_failed, Toast.LENGTH_LONG).show()
    }
}
