package io.github.alessiocpt.powerremapper.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.alessiocpt.powerremapper.R

// The repository is a proper noun, not translatable copy, so it lives in code; every project link
// derives from it so the fork is repointed in one place.
const val REPOSITORY_DISPLAY_NAME = "alessiocpt/Nothing-Power-Remapper"
const val REPOSITORY_URL = "https://github.com/$REPOSITORY_DISPLAY_NAME"

const val PRIVACY_POLICY_URL = "$REPOSITORY_URL/blob/main/PRIVACY.md"

const val ISSUES_URL = "$REPOSITORY_URL/issues"

/** The projects this app is forked from, credited in Settings. Both are MIT licensed. */
const val UPSTREAM_ESSENTIAL_KEY_TOOLS_URL = "https://github.com/KoukeNeko/EssentialKeyTools"
const val UPSTREAM_NOTHING_BUTTON_REMAPPER_URL =
    "https://github.com/THE-TECH-CHANGER/NothingButtonRemapper"

private const val PLAY_STORE_PACKAGE = "com.android.vending"
private const val PRODUCTION_APPLICATION_ID = "io.github.alessiocpt.powerremapper"
private const val PLAY_STORE_WEB_URL =
    "https://play.google.com/store/apps/details?id=$PRODUCTION_APPLICATION_ID"

/** Opens a public project page in the user's browser without crashing if no handler is installed. */
fun openExternalUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (error: android.content.ActivityNotFoundException) {
        Toast.makeText(context, R.string.external_link_open_failed, Toast.LENGTH_LONG).show()
    }
}

/** Opens the production listing in Google Play, with the web listing as a safe fallback. */
fun openPlayStoreListing(context: Context) {
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$PRODUCTION_APPLICATION_ID")
    ).apply {
        setPackage(PLAY_STORE_PACKAGE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(marketIntent)
    } catch (error: android.content.ActivityNotFoundException) {
        openExternalUrl(context, PLAY_STORE_WEB_URL)
    }
}
