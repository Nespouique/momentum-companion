package com.momentum.companion.data.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import com.momentum.companion.data.api.models.ActivityRecord
import com.momentum.companion.data.api.models.DailyMetric
import com.momentum.companion.data.api.models.SleepRecord
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectMapperTest {

    private val zone = ZoneId.systemDefault()

    // -----------------------------------------------------------------------
    // Helper: create a mocked ExerciseSessionRecord
    // -----------------------------------------------------------------------
    private fun mockExerciseSession(
        id: String = "exercise-1",
        startTime: Instant,
        endTime: Instant,
        exerciseType: Int = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        title: String? = null,
        packageName: String = "com.test.app",
    ): ExerciseSessionRecord {
        val dataOrigin = mockk<DataOrigin> {
            every { this@mockk.packageName } returns packageName
        }
        val metadata = mockk<Metadata> {
            every { this@mockk.id } returns id
            every { this@mockk.dataOrigin } returns dataOrigin
        }
        return mockk {
            every { this@mockk.startTime } returns startTime
            every { this@mockk.endTime } returns endTime
            every { this@mockk.exerciseType } returns exerciseType
            every { this@mockk.title } returns title
            every { this@mockk.metadata } returns metadata
        }
    }

    // -----------------------------------------------------------------------
    // Helper: create a mocked SleepSessionRecord
    // -----------------------------------------------------------------------
    private fun mockSleepSession(
        startTime: Instant,
        endTime: Instant,
        stages: List<SleepSessionRecord.Stage> = emptyList(),
    ): SleepSessionRecord {
        return mockk {
            every { this@mockk.startTime } returns startTime
            every { this@mockk.endTime } returns endTime
            every { this@mockk.stages } returns stages
        }
    }

    private fun mockSleepStage(
        stage: Int,
        startTime: Instant,
        endTime: Instant,
    ): SleepSessionRecord.Stage {
        return mockk {
            every { this@mockk.stage } returns stage
            every { this@mockk.startTime } returns startTime
            every { this@mockk.endTime } returns endTime
        }
    }

    // -----------------------------------------------------------------------
    // buildDailyMetrics
    // -----------------------------------------------------------------------

    @Test
    fun should_returnMetricsForMultipleDays_when_dataExistsForSeveralDates() {
        val date1 = LocalDate.of(2025, 6, 1)
        val date2 = LocalDate.of(2025, 6, 2)
        val date3 = LocalDate.of(2025, 6, 3)

        val steps = mapOf(date1 to 8000L, date2 to 12000L, date3 to 5000L)
        val calories = mapOf(date1 to 350.0, date2 to 500.0)

        // 30-minute exercise on date1
        val exerciseStart = date1.atTime(9, 0).atZone(zone).toInstant()
        val exerciseEnd = date1.atTime(9, 30).atZone(zone).toInstant()
        val exercises = listOf(mockExerciseSession(startTime = exerciseStart, endTime = exerciseEnd))

        val result = HealthConnectMapper.buildDailyMetrics(
            steps = steps,
            exerciseSessions = exercises,
            exerciseCalories = calories,
            userProfile = UserProfile(),
            startDate = date1,
            endDate = date3,
        )

        assertEquals(3, result.size)

        val metric1 = result.first { it.date == date1.toString() }
        assertEquals(8000, metric1.steps)
        // activeCalories and activeMinutes are estimated by the mapper — just assert non-negative
        assertTrue(metric1.activeCalories >= 0)
        assertTrue(metric1.activeMinutes >= 0)

        val metric2 = result.first { it.date == date2.toString() }
        assertEquals(12000, metric2.steps)
        assertTrue(metric2.activeCalories >= 0)
        assertTrue(metric2.activeMinutes >= 0)

        val metric3 = result.first { it.date == date3.toString() }
        assertEquals(5000, metric3.steps)
        assertTrue(metric3.activeCalories >= 0)
        assertTrue(metric3.activeMinutes >= 0)
    }

    @Test
    fun should_returnEmptyList_when_noDataExistsForAnyDay() {
        val startDate = LocalDate.of(2025, 6, 1)
        val endDate = LocalDate.of(2025, 6, 3)

        val result = HealthConnectMapper.buildDailyMetrics(
            steps = emptyMap(),
            exerciseSessions = emptyList(),
            exerciseCalories = emptyMap(),
            userProfile = UserProfile(),
            startDate = startDate,
            endDate = endDate,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun should_excludeDaysWithNoMetrics_when_someDaysHaveNoData() {
        val date1 = LocalDate.of(2025, 6, 1)
        val date2 = LocalDate.of(2025, 6, 2)
        val date3 = LocalDate.of(2025, 6, 3)

        // Only date1 and date3 have steps; date2 has nothing
        val steps = mapOf(date1 to 4000L, date3 to 7000L)

        val result = HealthConnectMapper.buildDailyMetrics(
            steps = steps,
            exerciseSessions = emptyList(),
            exerciseCalories = emptyMap(),
            userProfile = UserProfile(),
            startDate = date1,
            endDate = date3,
        )

        assertEquals(2, result.size)
        assertEquals(date1.toString(), result[0].date)
        assertEquals(date3.toString(), result[1].date)
        assertTrue(result.none { it.date == date2.toString() })
    }

    @Test
    fun should_aggregateActiveMinutesFromMultipleExercises_when_sameDayHasMultipleSessions() {
        val date = LocalDate.of(2025, 6, 10)

        // 20-minute morning run
        val run = mockExerciseSession(
            id = "run-1",
            startTime = date.atTime(7, 0).atZone(zone).toInstant(),
            endTime = date.atTime(7, 20).atZone(zone).toInstant(),
        )
        // 45-minute evening walk
        val walk = mockExerciseSession(
            id = "walk-1",
            startTime = date.atTime(18, 0).atZone(zone).toInstant(),
            endTime = date.atTime(18, 45).atZone(zone).toInstant(),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
        )

        val result = HealthConnectMapper.buildDailyMetrics(
            steps = emptyMap(),
            exerciseSessions = listOf(run, walk),
            exerciseCalories = emptyMap(),
            userProfile = UserProfile(),
            startDate = date,
            endDate = date,
        )

        assertEquals(1, result.size)
        // 20 + 45 = 65 exercise minutes; passive minutes added on top via steps estimation
        assertTrue(result[0].activeMinutes >= 65)
    }

    // -----------------------------------------------------------------------
    // mapExerciseSessions
    // -----------------------------------------------------------------------

    @Test
    fun should_mapExerciseTypeCorrectly_when_sessionHasKnownType() {
        val start = Instant.parse("2025-06-15T08:00:00Z")
        val end = Instant.parse("2025-06-15T08:45:00Z")

        val session = mockExerciseSession(
            id = "yoga-1",
            startTime = start,
            endTime = end,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
            title = "Morning Yoga",
            packageName = "com.fitness.tracker",
        )

        val result = HealthConnectMapper.mapExerciseSessions(listOf(session))

        assertEquals(1, result.size)
        val record = result[0]
        assertEquals("yoga-1", record.hcRecordId)
        assertEquals("YOGA", record.activityType)
        assertEquals("Morning Yoga", record.title)
        assertEquals("com.fitness.tracker", record.sourceApp)
    }

    @Test
    fun should_calculateDurationCorrectly_when_sessionSpansMultipleHours() {
        val start = Instant.parse("2025-06-15T06:00:00Z")
        val end = Instant.parse("2025-06-15T08:30:00Z") // 2h30 = 150 minutes

        val session = mockExerciseSession(
            id = "hike-1",
            startTime = start,
            endTime = end,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
        )

        val result = HealthConnectMapper.mapExerciseSessions(listOf(session))

        assertEquals(1, result.size)
        assertEquals(150.0, result[0].durationMinutes)
    }

    @Test
    fun should_setCaloriesAndDistanceToNull_when_mapExerciseSessions() {
        val start = Instant.parse("2025-06-15T10:00:00Z")
        val end = Instant.parse("2025-06-15T10:30:00Z")

        val session = mockExerciseSession(startTime = start, endTime = end)
        val result = HealthConnectMapper.mapExerciseSessions(listOf(session))

        assertNull(result[0].calories)
        assertNull(result[0].distance)
        assertNull(result[0].heartRateAvg)
    }

    @Test
    fun should_mapUnknownExerciseTypeToOtherWorkout_when_typeIsNotRecognized() {
        val start = Instant.parse("2025-06-15T10:00:00Z")
        val end = Instant.parse("2025-06-15T11:00:00Z")

        val session = mockExerciseSession(
            startTime = start,
            endTime = end,
            exerciseType = 9999,
        )

        val result = HealthConnectMapper.mapExerciseSessions(listOf(session))

        assertEquals("OTHER_WORKOUT", result[0].activityType)
    }

    // -----------------------------------------------------------------------
    // mapSleepSessions
    // -----------------------------------------------------------------------

    @Test
    fun should_useSleepEndDateAsRecordDate_when_sessionCrossesMidnight() {
        // Sleep from 11pm June 14 to 7am June 15
        val sleepStart = LocalDate.of(2025, 6, 14)
            .atTime(23, 0).atZone(zone).toInstant()
        val sleepEnd = LocalDate.of(2025, 6, 15)
            .atTime(7, 0).atZone(zone).toInstant()

        val lightStart = LocalDate.of(2025, 6, 14)
            .atTime(23, 15).atZone(zone).toInstant()
        val lightEnd = LocalDate.of(2025, 6, 15)
            .atTime(1, 0).atZone(zone).toInstant()
        val deepStart = lightEnd
        val deepEnd = LocalDate.of(2025, 6, 15)
            .atTime(4, 0).atZone(zone).toInstant()

        val stages = listOf(
            mockSleepStage(SleepSessionRecord.STAGE_TYPE_LIGHT, lightStart, lightEnd),
            mockSleepStage(SleepSessionRecord.STAGE_TYPE_DEEP, deepStart, deepEnd),
        )

        val session = mockSleepSession(
            startTime = sleepStart,
            endTime = sleepEnd,
            stages = stages,
        )

        val result = HealthConnectMapper.mapSleepSessions(listOf(session))

        assertEquals(1, result.size)
        val record = result[0]
        // Wake-up date (endTime date) should be used
        assertEquals(LocalDate.of(2025, 6, 15).toString(), record.date)
        // 8 hours = 480 minutes
        assertEquals(480.0, record.durationMinutes)
        assertNull(record.score)
        assertNotNull(record.stages)
        assertEquals(2, record.stages!!.size)
        assertEquals("light", record.stages!![0].stage)
        assertEquals("deep", record.stages!![1].stage)
    }

    @Test
    fun should_returnNullStages_when_sleepSessionHasNoStages() {
        val sleepStart = Instant.parse("2025-06-14T22:00:00Z")
        val sleepEnd = Instant.parse("2025-06-15T06:00:00Z")

        val session = mockSleepSession(
            startTime = sleepStart,
            endTime = sleepEnd,
            stages = emptyList(),
        )

        val result = HealthConnectMapper.mapSleepSessions(listOf(session))

        assertEquals(1, result.size)
        assertNull(result[0].stages)
        assertEquals(480.0, result[0].durationMinutes) // 8 hours
    }

    @Test
    fun should_mapAllSleepStageTypes_when_sessionContainsVariousStages() {
        val base = Instant.parse("2025-06-14T23:00:00Z")

        val stages = listOf(
            mockSleepStage(SleepSessionRecord.STAGE_TYPE_AWAKE, base, base.plusSeconds(600)),
            mockSleepStage(SleepSessionRecord.STAGE_TYPE_LIGHT, base.plusSeconds(600), base.plusSeconds(1200)),
            mockSleepStage(SleepSessionRecord.STAGE_TYPE_DEEP, base.plusSeconds(1200), base.plusSeconds(1800)),
            mockSleepStage(SleepSessionRecord.STAGE_TYPE_REM, base.plusSeconds(1800), base.plusSeconds(2400)),
            mockSleepStage(SleepSessionRecord.STAGE_TYPE_SLEEPING, base.plusSeconds(2400), base.plusSeconds(3000)),
        )

        val session = mockSleepSession(
            startTime = base,
            endTime = base.plusSeconds(3000),
            stages = stages,
        )

        val result = HealthConnectMapper.mapSleepSessions(listOf(session))

        val mapped = result[0].stages!!
        assertEquals(5, mapped.size)
        assertEquals("awake", mapped[0].stage)
        assertEquals("light", mapped[1].stage)
        assertEquals("deep", mapped[2].stage)
        assertEquals("rem", mapped[3].stage)
        assertEquals("sleeping", mapped[4].stage)
    }

    // -----------------------------------------------------------------------
    // exerciseTypeToString / exerciseTypeToLabel
    // -----------------------------------------------------------------------

    @Test
    fun should_returnCorrectStringCode_when_knownExerciseTypesAreProvided() {
        assertEquals("WALKING", HealthConnectMapper.exerciseTypeToString(ExerciseSessionRecord.EXERCISE_TYPE_WALKING))
        assertEquals("RUNNING", HealthConnectMapper.exerciseTypeToString(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING))
        assertEquals("BIKING", HealthConnectMapper.exerciseTypeToString(ExerciseSessionRecord.EXERCISE_TYPE_BIKING))
        assertEquals("SWIMMING", HealthConnectMapper.exerciseTypeToString(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER))
        assertEquals("WEIGHTLIFTING", HealthConnectMapper.exerciseTypeToString(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING))
        assertEquals("YOGA", HealthConnectMapper.exerciseTypeToString(ExerciseSessionRecord.EXERCISE_TYPE_YOGA))
        assertEquals("HIKING", HealthConnectMapper.exerciseTypeToString(ExerciseSessionRecord.EXERCISE_TYPE_HIKING))
        assertEquals("ELLIPTICAL", HealthConnectMapper.exerciseTypeToString(ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL))
        assertEquals("STAIR_CLIMBING", HealthConnectMapper.exerciseTypeToString(ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING))
    }

    @Test
    fun should_returnOtherWorkout_when_unknownExerciseTypeIsProvided() {
        assertEquals("OTHER_WORKOUT", HealthConnectMapper.exerciseTypeToString(-1))
        assertEquals("OTHER_WORKOUT", HealthConnectMapper.exerciseTypeToString(99999))
    }

    @Test
    fun should_returnCorrectFrenchLabel_when_knownExerciseTypesAreProvided() {
        assertEquals("Marche", HealthConnectMapper.exerciseTypeToLabel(ExerciseSessionRecord.EXERCISE_TYPE_WALKING))
        assertEquals("Course", HealthConnectMapper.exerciseTypeToLabel(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING))
        assertEquals("Velo", HealthConnectMapper.exerciseTypeToLabel(ExerciseSessionRecord.EXERCISE_TYPE_BIKING))
        assertEquals("Natation", HealthConnectMapper.exerciseTypeToLabel(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER))
        assertEquals("Musculation", HealthConnectMapper.exerciseTypeToLabel(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING))
        assertEquals("Yoga", HealthConnectMapper.exerciseTypeToLabel(ExerciseSessionRecord.EXERCISE_TYPE_YOGA))
        assertEquals("Randonnee", HealthConnectMapper.exerciseTypeToLabel(ExerciseSessionRecord.EXERCISE_TYPE_HIKING))
        assertEquals("Elliptique", HealthConnectMapper.exerciseTypeToLabel(ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL))
        assertEquals("Escaliers", HealthConnectMapper.exerciseTypeToLabel(ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING))
    }

    @Test
    fun should_returnAutre_when_unknownExerciseTypeIsProvidedForLabel() {
        assertEquals("Autre", HealthConnectMapper.exerciseTypeToLabel(-1))
        assertEquals("Autre", HealthConnectMapper.exerciseTypeToLabel(99999))
    }
}
