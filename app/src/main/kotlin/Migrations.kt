package com.healthify.app.data.db

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec

// ═══════════════════════════════════════════════════════════════════════════
// AUTO-MIGRATION SPECS
// One spec per version step that needs more than added tables/columns.
// Each step is covered by MigrationTest (src/androidTest).
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 1 → 2 (app 1.1.0)
 * - users: + unitSystem (added by Room from the entity's column default)
 * - check_ins: − heartRateAvg (heart-rate permission dropped in 1.0.15,
 *   Play "Minimum Scope"; this also deletes values stored by ≤1.0.14)
 * - check_ins: − aiInsight (never written)
 */
@DeleteColumn.Entries(
    DeleteColumn(tableName = "check_ins", columnName = "heartRateAvg"),
    DeleteColumn(tableName = "check_ins", columnName = "aiInsight"),
)
class Migration1To2Spec : AutoMigrationSpec
