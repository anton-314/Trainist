package dev.antonlammers.macrotrac.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "workout_templates", indices = [Index(value = ["stableId"], unique = true)])
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stableId: String,
    val name: String,
    /** Manual drag-to-reorder position in the templates list — lower sorts first. */
    val position: Int = 0,
)
