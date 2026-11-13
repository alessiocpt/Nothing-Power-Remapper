package io.github.alessiocpt.powerremapper.updates

import android.content.Context
import android.content.pm.PackageManager
import java.io.IOException

enum class UpdateSource {
    GITHUB_STABLE,
    GITHUB_PREVIEW
}

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(
        val versionName: String,
        val releaseUrl: String
    ) : UpdateCheckResult
}

interface AppUpdateChecker {
    val currentVersionName: String
    val source: UpdateSource

    suspend fun check(): Result<UpdateCheckResult>
}

/** The app ships on GitHub only: preview builds follow pre-releases, everything else stable ones. */
internal fun chooseUpdateSource(packageName: String): UpdateSource =
    if (packageName.endsWith(PREVIEW_APPLICATION_ID_SUFFIX)) {
        UpdateSource.GITHUB_PREVIEW
    } else {
        UpdateSource.GITHUB_STABLE
    }

object AppUpdateCheckerFactory {
    fun create(context: Context): AppUpdateChecker {
        val appContext = context.applicationContext
        val currentVersionName = readCurrentVersionName(appContext)

        return when (chooseUpdateSource(appContext.packageName)) {
            UpdateSource.GITHUB_STABLE -> GitHubAppUpdateChecker(
                currentVersionName = currentVersionName,
                source = UpdateSource.GITHUB_STABLE,
                channel = GitHubReleaseChannel.STABLE
            )
            UpdateSource.GITHUB_PREVIEW -> GitHubAppUpdateChecker(
                currentVersionName = currentVersionName,
                source = UpdateSource.GITHUB_PREVIEW,
                channel = GitHubReleaseChannel.PREVIEW
            )
        }
    }

    private fun readCurrentVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0)
        ).versionName
    }.getOrNull().orEmpty().ifEmpty { UNKNOWN_VERSION_NAME }
}

private class GitHubAppUpdateChecker(
    override val currentVersionName: String,
    override val source: UpdateSource,
    private val channel: GitHubReleaseChannel,
    private val service: GitHubReleasesService = GitHubReleasesService()
) : AppUpdateChecker {

    override suspend fun check(): Result<UpdateCheckResult> = service.fetchLatest(channel).mapCatching {
        latestRelease ->
        val currentVersion = AppVersion.parse(currentVersionName)
            ?: throw IOException("Unsupported installed version: $currentVersionName")

        if (latestRelease.version > currentVersion) {
            UpdateCheckResult.Available(
                versionName = latestRelease.versionName,
                releaseUrl = latestRelease.pageUrl
            )
        } else {
            UpdateCheckResult.UpToDate
        }
    }
}

private const val PREVIEW_APPLICATION_ID_SUFFIX = ".preview"
private const val UNKNOWN_VERSION_NAME = "—"
