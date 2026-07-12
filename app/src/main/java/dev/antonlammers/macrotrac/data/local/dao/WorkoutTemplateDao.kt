package dev.antonlammers.macrotrac.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.antonlammers.macrotrac.data.local.entity.TemplateExerciseEntity
import dev.antonlammers.macrotrac.data.local.entity.WorkoutTemplateEntity
import dev.antonlammers.macrotrac.data.local.relation.TemplateWithExercises
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutTemplateDao {
    @Transaction
    @Query("SELECT * FROM workout_templates ORDER BY position ASC")
    fun allTemplates(): Flow<List<TemplateWithExercises>>

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE id = :id LIMIT 1")
    fun templateById(id: Long): Flow<TemplateWithExercises?>

    /** Replace on id (or insert when id == 0); a replace cascade-clears the old exercise slots. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: WorkoutTemplateEntity): Long

    @Insert
    suspend fun insertTemplateExercises(exercises: List<TemplateExerciseEntity>)

    @Query("DELETE FROM template_exercises WHERE templateId = :templateId")
    suspend fun deleteTemplateExercises(templateId: Long)

    @Query("DELETE FROM workout_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    /** The stored manual-order position of an existing template, so an edit-save can preserve it. */
    @Query("SELECT position FROM workout_templates WHERE id = :id LIMIT 1")
    suspend fun positionOf(id: Long): Int?

    /** Append position for a newly created template — one past the current highest. */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM workout_templates")
    suspend fun nextPosition(): Int

    @Query("UPDATE workout_templates SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)
}
