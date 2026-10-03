package com.healthify.app.data.db

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.healthify.app.BuildConfig
import com.healthify.app.HealthifyApp
import java.io.File

/**
 * Copies the Room database files aside once per app update, *before* Room
 * opens (and possibly migrates) them. The on-device DB is the only copy of
 * a user's history — Firestore is push-only and the DB is excluded from
 * Auto Backup — so if a migration ever ships broken, a hotfix can restore
 * from `noBackupFilesDir/db_snapshot/`. Nothing reads the snapshot today.
 *
 * Keyed on versionCode rather than the DB's user_version: in WAL mode the
 * header of the main file can lag behind the WAL, so the header isn't a
 * reliable "is this an upgrade?" signal. The files are copied together
 * (main + -wal + -shm) while closed, which keeps the copy consistent.
 */
object DbBackup {

    private const val TAG = "DbBackup"
    private const val DIR = "db_snapshot"
    private const val KEY_SNAPSHOT_VERSION_CODE = "db_snapshot_version_code"

    fun snapshotOnAppUpdate(context: Context, dbName: String) {
        val prefs = context.getSharedPreferences(HealthifyApp.PREFS_FILE, Context.MODE_PRIVATE)
        val current = BuildConfig.VERSION_CODE
        if (prefs.getInt(KEY_SNAPSHOT_VERSION_CODE, 0) >= current) return
        try {
            val db = context.getDatabasePath(dbName)
            if (db.exists()) {
                val dir = File(context.noBackupFilesDir, DIR)
                dir.deleteRecursively()          // keep only the latest snapshot
                dir.mkdirs()
                for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                    val src = File(db.path + suffix)
                    if (src.exists()) src.copyTo(File(dir, src.name), overwrite = true)
                }
                Log.i(TAG, "Snapshot of $dbName taken before opening with versionCode $current")
            }
            prefs.edit().putInt(KEY_SNAPSHOT_VERSION_CODE, current).apply()
        } catch (e: Exception) {
            // Never block the app on the safety copy.
            Log.w(TAG, "DB snapshot failed: ${e.message}")
            Firebase.crashlytics.recordException(e)
        }
    }
}
