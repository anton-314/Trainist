package dev.antonlammers.trainist.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One ordered exercise slot of a [WorkoutTemplateEntity]. Deleting the template cascade-deletes its
 * slots. The exercise itself is referenced by [exerciseStableId], not a row id.
 *
 * [setTypes] is the planned sets in order, newline-joined [dev.antonlammers.trainist.domain.model.SetType]
 * names (same list-field convention as [ExerciseEntity]'s muscles/instructions). [targetSets] is the
 * legacy plain set-count column, kept only so rows written before [setTypes] existed still read back
 * correctly — [setTypes] null/blank means "pre-migration row", and the mapper falls back to
 * [targetSets] NORMAL sets. New writes always populate both.
 *
 * [supersetGroupId] groups adjacent slots into a planned superset (null = the slot stands alone); it
 * is only meaningful within one template, which is why it is a plain Int and not a stable key.
 */
@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId")],
)
data class TemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseStableId: String,
    val position: Int,
    val targetSets: Int,
    val setTypes: String? = null,
    val supersetGroupId: Int? = null,
)
