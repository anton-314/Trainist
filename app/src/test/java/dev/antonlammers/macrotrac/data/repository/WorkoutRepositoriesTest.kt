package dev.antonlammers.macrotrac.data.repository

import app.cash.turbine.test
import dev.antonlammers.macrotrac.data.local.dao.ExerciseDao
import dev.antonlammers.macrotrac.data.local.dao.WorkoutSessionDao
import dev.antonlammers.macrotrac.data.local.dao.WorkoutTemplateDao
import dev.antonlammers.macrotrac.data.local.entity.ExerciseEntity
import dev.antonlammers.macrotrac.data.local.entity.SessionExerciseEntity
import dev.antonlammers.macrotrac.data.local.entity.SetEntryEntity
import dev.antonlammers.macrotrac.data.local.entity.TemplateExerciseEntity
import dev.antonlammers.macrotrac.data.local.entity.WorkoutSessionEntity
import dev.antonlammers.macrotrac.data.local.entity.WorkoutTemplateEntity
import dev.antonlammers.macrotrac.data.local.relation.SessionExerciseWithSets
import dev.antonlammers.macrotrac.data.local.relation.SessionWithExercises
import dev.antonlammers.macrotrac.data.local.relation.TemplateWithExercises
import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.SessionExercise
import dev.antonlammers.macrotrac.domain.model.SetEntry
import dev.antonlammers.macrotrac.domain.model.SetType
import dev.antonlammers.macrotrac.domain.model.TemplateExercise
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import dev.antonlammers.macrotrac.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkoutRepositoriesTest {

    // --- Exercise catalog ---

    @Test
    fun `exercises are mapped, sorted by name, upserted by stableId and deletable`() = runTest {
        val repo = ExerciseCatalogRepositoryImpl(FakeExerciseDao())

        repo.exercises().test {
            assertEquals(emptyList<Exercise>(), awaitItem())

            repo.upsertAll(listOf(exercise("z", "Zucchini"), exercise("a", "Apfel")))
            assertEquals(listOf("Apfel", "Zucchini"), awaitItem().map { it.name })

            // Same stableId replaces rather than adds a second row.
            repo.upsertAll(listOf(exercise("a", "Apfel (neu)")))
            val updated = awaitItem()
            assertEquals(2, updated.size)
            assertEquals("Apfel (neu)", updated.first { it.stableId == "a" }.name)

            repo.delete("a")
            assertEquals(listOf("Zucchini"), awaitItem().map { it.name })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exercise by stableId returns the mapped exercise or null`() = runTest {
        val repo = ExerciseCatalogRepositoryImpl(FakeExerciseDao())
        repo.upsertAll(listOf(exercise("a", "Apfel")))

        assertEquals("Apfel", repo.exercise("a").first()?.name)
        assertNull(repo.exercise("missing").first())
    }

    // --- Templates ---

    @Test
    fun `saving a template persists ordered exercises and reports its id`() = runTest {
        val repo = WorkoutTemplateRepositoryImpl(FakeWorkoutTemplateDao(), ImmediateTransactionRunner())

        val id = repo.save(
            WorkoutTemplate(
                stableId = "tpl", name = "Push Day",
                exercises = listOf(
                    TemplateExercise("bench", position = 0, targetSets = 4),
                    TemplateExercise("ohp", position = 0, targetSets = 3),
                ),
            ),
        )
        assertTrue(id > 0)

        val saved = repo.templates().first().single()
        assertEquals(id, saved.id)
        assertEquals(listOf("bench", "ohp"), saved.exercises.map { it.exerciseStableId })
        assertEquals(listOf(0, 1), saved.exercises.map { it.position })
    }

    @Test
    fun `re-saving a template rewrites its exercise list instead of appending`() = runTest {
        val repo = WorkoutTemplateRepositoryImpl(FakeWorkoutTemplateDao(), ImmediateTransactionRunner())
        val id = repo.save(
            WorkoutTemplate(stableId = "tpl", name = "Push", exercises = listOf(TemplateExercise("bench", 0, 4))),
        )

        repo.save(
            WorkoutTemplate(id = id, stableId = "tpl", name = "Push v2", exercises = listOf(TemplateExercise("dips", 0, 3))),
        )

        val saved = repo.templates().first().single()
        assertEquals("Push v2", saved.name)
        assertEquals(listOf("dips"), saved.exercises.map { it.exerciseStableId })
    }

    @Test
    fun `re-saving a template with id 0 and a known stableId replaces it instead of duplicating`() = runTest {
        // Mirrors a backup re-import: the reassembled template always carries id = 0, so only the
        // stableId identifies it as the same template already on the target device.
        val repo = WorkoutTemplateRepositoryImpl(FakeWorkoutTemplateDao(), ImmediateTransactionRunner())
        repo.save(
            WorkoutTemplate(stableId = "tpl", name = "Push", exercises = listOf(TemplateExercise("bench", 0, 4))),
        )

        repo.save(
            WorkoutTemplate(stableId = "tpl", name = "Push v2", exercises = listOf(TemplateExercise("dips", 0, 3))),
        )

        val all = repo.templates().first()
        assertEquals(1, all.size)
        assertEquals("Push v2", all.single().name)
        assertEquals(listOf("dips"), all.single().exercises.map { it.exerciseStableId })
    }

    @Test
    fun `deleting a template removes it and its exercises`() = runTest {
        val repo = WorkoutTemplateRepositoryImpl(FakeWorkoutTemplateDao(), ImmediateTransactionRunner())
        val id = repo.save(
            WorkoutTemplate(stableId = "tpl", name = "Push", exercises = listOf(TemplateExercise("bench", 0, 4))),
        )

        repo.delete(id)

        assertEquals(emptyList<WorkoutTemplate>(), repo.templates().first())
        assertNull(repo.template(id).first())
    }

    @Test
    fun `new templates are appended in creation order and an edit preserves its position`() = runTest {
        val repo = WorkoutTemplateRepositoryImpl(FakeWorkoutTemplateDao(), ImmediateTransactionRunner())
        repo.save(WorkoutTemplate(stableId = "a", name = "Push"))
        val bId = repo.save(WorkoutTemplate(stableId = "b", name = "Pull"))
        repo.save(WorkoutTemplate(stableId = "c", name = "Legs"))

        assertEquals(listOf("a", "b", "c"), repo.templates().first().map { it.stableId })

        // Editing the middle template (rename) must not move it in the list.
        repo.save(WorkoutTemplate(id = bId, stableId = "b", name = "Pull v2"))
        assertEquals(listOf("a", "b", "c"), repo.templates().first().map { it.stableId })
        assertEquals("Pull v2", repo.templates().first()[1].name)
    }

    @Test
    fun `reorder applies the new manual order and persists it`() = runTest {
        val repo = WorkoutTemplateRepositoryImpl(FakeWorkoutTemplateDao(), ImmediateTransactionRunner())
        val aId = repo.save(WorkoutTemplate(stableId = "a", name = "Push"))
        val bId = repo.save(WorkoutTemplate(stableId = "b", name = "Pull"))
        val cId = repo.save(WorkoutTemplate(stableId = "c", name = "Legs"))

        repo.reorder(listOf(cId, aId, bId))

        assertEquals(listOf("c", "a", "b"), repo.templates().first().map { it.stableId })
    }

    // --- Sessions ---

    @Test
    fun `saving a session persists the template it was started from`() = runTest {
        val repo = WorkoutSessionRepositoryImpl(FakeWorkoutSessionDao(), ImmediateTransactionRunner())

        val id = repo.save(
            WorkoutSession(
                stableId = "s1", date = LocalDate.of(2026, 7, 10), isActive = false,
                startedAtMs = 1_000, endedAtMs = 2_000, templateStableId = "tpl-push",
            ),
        )

        assertEquals("tpl-push", repo.session(id).first()?.templateStableId)
    }

    @Test
    fun `saving a session persists the full exercise and set graph`() = runTest {
        val repo = WorkoutSessionRepositoryImpl(FakeWorkoutSessionDao(), ImmediateTransactionRunner())

        val id = repo.save(
            WorkoutSession(
                stableId = "s1", date = LocalDate.of(2026, 7, 10), isActive = true, startedAtMs = 1_000,
                exercises = listOf(
                    SessionExercise(
                        exerciseStableId = "squat", position = 0,
                        sets = listOf(
                            SetEntry(position = 0, weightKg = 60.0, reps = 8, type = SetType.WARMUP),
                            SetEntry(position = 1, weightKg = 100.0, reps = 5, completed = true),
                        ),
                    ),
                ),
            ),
        )

        val saved = repo.session(id).first()!!
        assertEquals(LocalDate.of(2026, 7, 10), saved.date)
        val sets = saved.exercises.single().sets
        assertEquals(2, sets.size)
        assertEquals(SetType.WARMUP, sets[0].type)
        assertEquals(100.0, sets[1].weightKg, 0.0)
        assertTrue(sets[1].completed)
    }

    @Test
    fun `re-saving a session with id 0 and a known stableId replaces it instead of duplicating`() = runTest {
        // Mirrors a backup re-import: the reassembled session always carries id = 0, so only the
        // stableId identifies it as the same session already on the target device.
        val repo = WorkoutSessionRepositoryImpl(FakeWorkoutSessionDao(), ImmediateTransactionRunner())
        repo.save(
            WorkoutSession(
                stableId = "s1", date = LocalDate.of(2026, 7, 10), isActive = false, startedAtMs = 1_000, endedAtMs = 2_000,
                exercises = listOf(
                    SessionExercise(
                        exerciseStableId = "squat", position = 0,
                        sets = listOf(SetEntry(position = 0, weightKg = 100.0, reps = 5, completed = true)),
                    ),
                ),
            ),
        )

        // Re-import of the exact same backup content.
        repo.save(
            WorkoutSession(
                stableId = "s1", date = LocalDate.of(2026, 7, 10), isActive = false, startedAtMs = 1_000, endedAtMs = 2_000,
                exercises = listOf(
                    SessionExercise(
                        exerciseStableId = "squat", position = 0,
                        sets = listOf(SetEntry(position = 0, weightKg = 100.0, reps = 5, completed = true)),
                    ),
                ),
            ),
        )

        val all = repo.sessions().first()
        assertEquals(1, all.size)
        assertEquals(1, all.single().exercises.single().sets.size)
    }

    @Test
    fun `active session reflects the in-progress one and clears once none active`() = runTest {
        val repo = WorkoutSessionRepositoryImpl(FakeWorkoutSessionDao(), ImmediateTransactionRunner())

        repo.save(WorkoutSession(stableId = "done", date = LocalDate.of(2026, 7, 9), isActive = false, startedAtMs = 1, endedAtMs = 2))
        val activeId = repo.save(WorkoutSession(stableId = "live", date = LocalDate.of(2026, 7, 10), isActive = true, startedAtMs = 10))

        assertEquals("live", repo.activeSession().first()?.stableId)

        // Finish it → no active session remains.
        repo.save(repo.session(activeId).first()!!.copy(isActive = false, endedAtMs = 20))
        assertNull(repo.activeSession().first())
    }

    @Test
    fun `sessions are listed newest first and deletable`() = runTest {
        val repo = WorkoutSessionRepositoryImpl(FakeWorkoutSessionDao(), ImmediateTransactionRunner())
        repo.save(WorkoutSession(stableId = "old", date = LocalDate.of(2026, 7, 1), isActive = false, startedAtMs = 1, endedAtMs = 2))
        val newerId = repo.save(WorkoutSession(stableId = "new", date = LocalDate.of(2026, 7, 10), isActive = false, startedAtMs = 3, endedAtMs = 4))

        assertEquals(listOf("new", "old"), repo.sessions().first().map { it.stableId })

        repo.delete(newerId)
        assertEquals(listOf("old"), repo.sessions().first().map { it.stableId })
    }
}

private fun exercise(stableId: String, name: String) = Exercise(
    stableId = stableId, name = name, type = ExerciseType.WEIGHT_REPS, isCustom = false,
)

private class ImmediateTransactionRunner : TransactionRunner {
    override suspend fun <R> transaction(block: suspend () -> R): R = block()
}

/** In-memory [ExerciseDao] modelling the unique-stableId upsert + name ordering. */
private class FakeExerciseDao : ExerciseDao {
    private val rows = MutableStateFlow<List<ExerciseEntity>>(emptyList())
    private var nextId = 1L

    override fun allExercises(): Flow<List<ExerciseEntity>> = rows.map { it.sortedBy { e -> e.name } }
    override fun exerciseByStableId(stableId: String): Flow<ExerciseEntity?> =
        rows.map { list -> list.firstOrNull { it.stableId == stableId } }

    override suspend fun upsertAll(exercises: List<ExerciseEntity>) {
        rows.update { current ->
            val byStable = current.associateBy { it.stableId }.toMutableMap()
            for (e in exercises) byStable[e.stableId] = e.copy(id = byStable[e.stableId]?.id ?: nextId++)
            byStable.values.toList()
        }
    }

    override suspend fun delete(stableId: String) {
        rows.update { it.filterNot { e -> e.stableId == stableId } }
    }
}

/** In-memory [WorkoutTemplateDao] modelling replace-on-id + cascade. */
private class FakeWorkoutTemplateDao : WorkoutTemplateDao {
    private val templates = MutableStateFlow<List<WorkoutTemplateEntity>>(emptyList())
    private val exercises = MutableStateFlow<List<TemplateExerciseEntity>>(emptyList())
    private var nextTemplateId = 1L
    private var nextExerciseId = 1L

    override fun allTemplates(): Flow<List<TemplateWithExercises>> =
        combine(templates, exercises) { ts, tes ->
            ts.sortedBy { it.position }.map { t -> TemplateWithExercises(t, tes.filter { it.templateId == t.id }) }
        }

    override fun templateById(id: Long): Flow<TemplateWithExercises?> =
        combine(templates, exercises) { ts, tes ->
            ts.firstOrNull { it.id == id }?.let { t -> TemplateWithExercises(t, tes.filter { it.templateId == t.id }) }
        }

    override suspend fun insertTemplate(template: WorkoutTemplateEntity): Long {
        // REPLACE triggers on any unique-constraint hit — the id (known-row edit) or, for a fresh
        // insert (id == 0, e.g. backup re-import), the unique stableId index — evicting that row and
        // cascade-clearing its children before the new row takes its place.
        val conflict = templates.value.firstOrNull { it.id == template.id || it.stableId == template.stableId }
        if (conflict != null) {
            templates.update { list -> list.filterNot { it.id == conflict.id } }
            exercises.update { it.filterNot { te -> te.templateId == conflict.id } }
        }
        val id = if (template.id != 0L) template.id else nextTemplateId++
        templates.update { it + template.copy(id = id) }
        return id
    }

    override suspend fun insertTemplateExercises(list: List<TemplateExerciseEntity>) {
        exercises.update { it + list.map { e -> e.copy(id = nextExerciseId++) } }
    }

    override suspend fun deleteTemplateExercises(templateId: Long) {
        exercises.update { it.filterNot { te -> te.templateId == templateId } }
    }

    override suspend fun deleteTemplate(id: Long) {
        templates.update { it.filterNot { t -> t.id == id } }
        exercises.update { it.filterNot { te -> te.templateId == id } }
    }

    override suspend fun positionOf(id: Long): Int? = templates.value.firstOrNull { it.id == id }?.position

    override suspend fun nextPosition(): Int = (templates.value.maxOfOrNull { it.position } ?: -1) + 1

    override suspend fun updatePosition(id: Long, position: Int) {
        templates.update { list -> list.map { if (it.id == id) it.copy(position = position) else it } }
    }
}

/** In-memory [WorkoutSessionDao] modelling replace-on-id + cascade + newest-first ordering. */
private class FakeWorkoutSessionDao : WorkoutSessionDao {
    private val sessions = MutableStateFlow<List<WorkoutSessionEntity>>(emptyList())
    private val exercises = MutableStateFlow<List<SessionExerciseEntity>>(emptyList())
    private val sets = MutableStateFlow<List<SetEntryEntity>>(emptyList())
    private var nextSessionId = 1L
    private var nextExerciseId = 1L
    private var nextSetId = 1L

    private fun graph(session: WorkoutSessionEntity, es: List<SessionExerciseEntity>, ss: List<SetEntryEntity>) =
        SessionWithExercises(
            session,
            es.filter { it.sessionId == session.id }.map { e ->
                SessionExerciseWithSets(e, ss.filter { it.sessionExerciseId == e.id })
            },
        )

    override fun allSessions(): Flow<List<SessionWithExercises>> =
        combine(sessions, exercises, sets) { se, es, ss ->
            se.sortedWith(compareByDescending<WorkoutSessionEntity> { it.date }.thenByDescending { it.startedAtMs })
                .map { graph(it, es, ss) }
        }

    override fun sessionById(id: Long): Flow<SessionWithExercises?> =
        combine(sessions, exercises, sets) { se, es, ss -> se.firstOrNull { it.id == id }?.let { graph(it, es, ss) } }

    override fun activeSession(): Flow<SessionWithExercises?> =
        combine(sessions, exercises, sets) { se, es, ss ->
            se.filter { it.isActive }.maxByOrNull { it.startedAtMs }?.let { graph(it, es, ss) }
        }

    override suspend fun insertSession(session: WorkoutSessionEntity): Long {
        // REPLACE triggers on any unique-constraint hit — the id (known-row edit) or, for a fresh
        // insert (id == 0, e.g. backup re-import), the unique stableId index — evicting that row and
        // cascade-clearing its exercise/set graph before the new row takes its place.
        val conflict = sessions.value.firstOrNull { it.id == session.id || it.stableId == session.stableId }
        if (conflict != null) {
            sessions.update { list -> list.filterNot { it.id == conflict.id } }
            clearGraph(conflict.id)
        }
        val id = if (session.id != 0L) session.id else nextSessionId++
        sessions.update { it + session.copy(id = id) }
        return id
    }

    override suspend fun insertSessionExercise(exercise: SessionExerciseEntity): Long {
        val id = nextExerciseId++
        exercises.update { it + exercise.copy(id = id) }
        return id
    }

    override suspend fun insertSets(list: List<SetEntryEntity>) {
        sets.update { it + list.map { s -> s.copy(id = nextSetId++) } }
    }

    override suspend fun deleteSessionExercises(sessionId: Long) = clearGraph(sessionId)

    override suspend fun deleteSession(id: Long) {
        sessions.update { it.filterNot { s -> s.id == id } }
        clearGraph(id)
    }

    private fun clearGraph(sessionId: Long) {
        val removed = exercises.value.filter { it.sessionId == sessionId }.map { it.id }.toSet()
        exercises.update { it.filterNot { e -> e.sessionId == sessionId } }
        sets.update { it.filterNot { s -> s.sessionExerciseId in removed } }
    }
}
