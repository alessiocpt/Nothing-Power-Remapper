package io.github.alessiocpt.powerremapper.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.alessiocpt.powerremapper.R
import io.github.alessiocpt.powerremapper.settings.Fallback
import io.github.alessiocpt.powerremapper.settings.Profile
import io.github.alessiocpt.powerremapper.settings.SettingsRepository
import io.github.alessiocpt.powerremapper.ui.AppLabelResolver
import io.github.alessiocpt.powerremapper.ui.InstalledApps
import io.github.alessiocpt.powerremapper.ui.LaunchableApp
import io.github.alessiocpt.powerremapper.ui.components.NothingButton
import io.github.alessiocpt.powerremapper.ui.components.NothingCard
import io.github.alessiocpt.powerremapper.ui.components.NothingSectionLabel
import io.github.alessiocpt.powerremapper.ui.screenContentPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SCREEN_PADDING = 24.dp
private val TITLE_TO_CONTENT_GAP = 32.dp
private val CARD_GAP = 16.dp
private val LABEL_GAP = 12.dp
private val ROW_GAP = 4.dp
private val ROW_VERTICAL_PADDING = 12.dp
private val ROW_ACTION_GAP = 12.dp
private val ICON_SIZE = 32.dp

/**
 * Lists the Profiles: the Default Profile, then one row per app Profile. Apps that have since been
 * uninstalled keep their Profile and say so, so reinstalling the app brings it back.
 */
@Composable
fun ProfilesScreen(
    onEditDefaultProfile: () -> Unit,
    onEditProfile: (packageName: String) -> Unit,
    onAddProfile: () -> Unit,
    systemBarsPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { SettingsRepository.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val profileSet by repository.profileSet.collectAsState(initial = null)
    var profilePendingDeletion by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = screenContentPadding(systemBarsPadding, SCREEN_PADDING)
    ) {
        item {
            Text(
                text = stringResource(R.string.profiles_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(TITLE_TO_CONTENT_GAP))
            NothingCard(modifier = Modifier.fillMaxWidth()) {
                ProfileRow(
                    title = stringResource(R.string.profiles_default_title),
                    caption = stringResource(R.string.profiles_default_caption),
                    onClick = onEditDefaultProfile
                )
            }
            Spacer(modifier = Modifier.height(CARD_GAP))
        }

        item {
            NothingCard(modifier = Modifier.fillMaxWidth()) {
                NothingSectionLabel(text = stringResource(R.string.profiles_section_apps))
                Spacer(modifier = Modifier.height(LABEL_GAP))
                val appProfiles = profileSet?.appProfiles.orEmpty()
                if (profileSet != null && appProfiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profiles_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                    for (profile in appProfiles) {
                        AppProfileRow(
                            profile = profile,
                            onClick = { onEditProfile(profile.packageName) },
                            onDelete = { profilePendingDeletion = profile.packageName }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(LABEL_GAP))
                NothingButton(
                    text = stringResource(R.string.profiles_add),
                    onClick = onAddProfile,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    val pendingDeletion = profilePendingDeletion
    if (pendingDeletion != null) {
        AlertDialog(
            onDismissRequest = { profilePendingDeletion = null },
            title = {
                Text(
                    stringResource(
                        R.string.profiles_delete_title,
                        AppLabelResolver.labelFor(context, pendingDeletion)
                    )
                )
            },
            text = { Text(stringResource(R.string.profiles_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        profilePendingDeletion = null
                        coroutineScope.launch { repository.deleteProfile(pendingDeletion) }
                    }
                ) { Text(stringResource(R.string.profiles_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { profilePendingDeletion = null }) {
                    Text(stringResource(R.string.profiles_delete_cancel))
                }
            }
        )
    }
}

@Composable
private fun AppProfileRow(profile: Profile, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val installed = remember(profile.packageName) { isInstalled(context, profile.packageName) }
    val fallback = stringResource(
        when (profile.fallback) {
            Fallback.INHERIT -> R.string.profiles_fallback_inherit
            Fallback.ISOLATED -> R.string.profiles_fallback_isolated
        }
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProfileRow(
            title = AppLabelResolver.labelFor(context, profile.packageName),
            caption = if (installed) {
                fallback
            } else {
                stringResource(R.string.profiles_not_installed, fallback)
            },
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(ROW_ACTION_GAP))
        TextButton(onClick = onDelete) {
            Text(
                text = stringResource(R.string.profiles_delete),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileRow(
    title: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = ROW_VERTICAL_PADDING)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun isInstalled(context: Context, packageName: String): Boolean = try {
    context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
    true
} catch (error: PackageManager.NameNotFoundException) {
    false
}

/**
 * Picks the app for a new Profile from the launchable apps, leaving out those that already have
 * one. Picking an app creates its Profile (Inherit, every Gesture Unassigned) and reports it.
 */
@Composable
fun ProfileAppPickerScreen(
    onProfileCreated: (packageName: String) -> Unit,
    systemBarsPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { SettingsRepository.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val profileSet by repository.profileSet.collectAsState(initial = null)

    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var appsLoaded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val iconSizePx = with(LocalDensity.current) { ICON_SIZE.roundToPx() }
    LaunchedEffect(iconSizePx) {
        apps = withContext(Dispatchers.Default) {
            InstalledApps.loadLaunchable(context, iconSizePx)
        }
        appsLoaded = true
    }

    val existing = profileSet?.appProfiles.orEmpty().map(Profile::packageName).toSet()
    val candidates = filterApps(apps, query).filter { app ->
        app.packageName !in existing && app.packageName != context.packageName
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = screenContentPadding(systemBarsPadding, SCREEN_PADDING)
    ) {
        item {
            Text(
                text = stringResource(R.string.profiles_add_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(TITLE_TO_CONTENT_GAP))
            SearchField(query = query, onQueryChange = { query = it })
            Spacer(modifier = Modifier.height(CARD_GAP))
            if (!appsLoaded) {
                Text(
                    text = stringResource(R.string.picker_apps_loading),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(candidates, key = { app -> app.packageName }) { app ->
            AppRow(
                app = app,
                onClick = {
                    coroutineScope.launch {
                        repository.upsertProfile(Profile.newFor(app.packageName))
                        onProfileCreated(app.packageName)
                    }
                }
            )
        }
    }
}
