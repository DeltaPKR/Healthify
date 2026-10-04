package com.healthify.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Where Health Connect's "privacy policy" link lands: from the permission
 * sheet on Android 13 and lower (ACTION_SHOW_PERMISSIONS_RATIONALE) and
 * from Settings on Android 14+ (the VIEW_PERMISSION_USAGE alias). Health
 * Connect requires it to show the same privacy policy as the Play
 * listing, so it opens that page and closes. Without this activity the
 * Android 13-and-lower Health Connect app won't show the permission sheet.
 */
class HealthPrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
        } catch (e: ActivityNotFoundException) {
            // No browser: open the app rather than nothing.
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    companion object {
        /** Also in the manifest's Health Connect meta-data and on the Play listing. */
        const val PRIVACY_POLICY_URL = "https://deltapkr.github.io/Healthify/privacy/"
    }
}
