package com.momentum.companion.data.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import com.momentum.companion.data.api.models.ActivityRecord
import com.momentum.companion.data.api.models.DailyMetric
import com.momentum.companion.data.api.models.SleepRecord
import com.momentum.companion.data.api.models.SleepStage
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

data class UserProfile(
    val stepsPerMin: Int = 100,
    val weightKg: Float = 70f,
    val heightCm: Int = 170,
    val age: Int = 30,
    val isMale: Boolean = true,
)

object HealthConnectMapper {

    /**
     * Build daily metrics with estimation of passive active minutes and calories.
     *
     * Samsung Health only syncs exercise calories (TotalCaloriesBurned) and exercise
     * sessions, but NOT passive active calories or active minutes from the pedometer.
     *
     * Estimation logic:
     * 1. Deduct estimated exercise steps from total steps to get passive steps
     * 2. Passive active minutes = passive steps / steps per minute
     * 3. Passive active calories = passive minutes * MET-based walking calorie rate
     * 4. Totals = passive + exercise
     *
     * Walking calorie rate uses MET formula:
     *   active kcal/min = ((MET - 1) * 3.5 * weightKg) / 200
     *   with MET = 3.0 for casual walking
     */
    fun buildDailyMetrics(
        steps: Map<LocalDate, Long>,
        exerciseSessions: List<ExerciseSessionRecord>,
        exerciseCalories: Map<LocalDate, Double>,
        userProfile: UserProfile,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<DailyMetric> {
        val zone = ZoneId.systemDefault()
        val dates = generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
            .toList()

        val exercisesByDate = exerciseSessions.groupBy { session ->
            session.startTime.atZone(zone).toLocalDate()
        }

        // MET 3.0 for walking — full metabolic cost during activity
        // (Samsung counts total kcal during movement, not excess above resting)
        // Formula: kcal/min = (MET * 3.5 * weightKg) / 200
        val calPerMin = (3.0 * 3.5 * userProfile.weightKg) / 200.0

        return dates.mapNotNull { date ->
            val daySteps = steps[date] ?: 0L
            val dayExercises = exercisesByDate[date] ?: emptyList()
            val dayExerciseMinutes = dayExercises.sumOf {
                Duration.between(it.startTime, it.endTime).toMinutes()
            }
            val dayExerciseCalories = exerciseCalories[date] ?: 0.0

            if (daySteps == 0L && dayExercises.isEmpty()) return@mapNotNull null

            // Deduct estimated exercise steps from total
            val estimatedExerciseSteps = dayExerciseMinutes * userProfile.stepsPerMin
            val passiveSteps = maxOf(0L, daySteps - estimatedExerciseSteps)

            // Estimate passive active minutes and calories (float division)
            val passiveMinutes = passiveSteps.toDouble() / userProfile.stepsPerMin
            val passiveCalories = passiveMinutes * calPerMin

            DailyMetric(
                date = date.toString(),
                steps = daySteps.toInt(),
                activeCalories = (passiveCalories + dayExerciseCalories).toInt(),
                activeMinutes = (passiveMinutes + dayExerciseMinutes).toInt(),
            )
        }
    }

    fun mapExerciseSessions(sessions: List<ExerciseSessionRecord>): List<ActivityRecord> {
        val zone = ZoneId.systemDefault()
        return sessions.map { session ->
            ActivityRecord(
                hcRecordId = session.metadata.id,
                date = session.startTime.atZone(zone).toLocalDate().toString(),
                startTime = session.startTime.toString(),
                endTime = session.endTime.toString(),
                activityType = exerciseTypeToString(session.exerciseType),
                title = session.title,
                durationMinutes = Duration.between(session.startTime, session.endTime)
                    .toMinutes().toDouble(),
                calories = null,
                distance = null,
                heartRateAvg = null,
                sourceApp = session.metadata.dataOrigin.packageName,
            )
        }
    }

    fun mapSleepSessions(sessions: List<SleepSessionRecord>): List<SleepRecord> {
        val zone = ZoneId.systemDefault()
        return sessions.map { session ->
            val duration = Duration.between(session.startTime, session.endTime)
            SleepRecord(
                date = session.endTime.atZone(zone).toLocalDate().toString(),
                startTime = session.startTime.toString(),
                endTime = session.endTime.toString(),
                durationMinutes = duration.toMinutes().toDouble(),
                score = null,
                stages = session.stages.map { stage ->
                    SleepStage(
                        stage = mapSleepStageType(stage.stage),
                        startTime = stage.startTime.toString(),
                        endTime = stage.endTime.toString(),
                    )
                }.ifEmpty { null },
            )
        }
    }

    fun exerciseTypeToString(type: Int): String {
        return when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "WALKING"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "RUNNING"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "BIKING"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "SWIMMING"
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "WEIGHTLIFTING"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "YOGA"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "HIKING"
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "ELLIPTICAL"
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "STAIR_CLIMBING"
            else -> "OTHER_WORKOUT"
        }
    }

    fun exerciseTypeToLabel(type: Int): String {
        return when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Marche"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Course"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Velo"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Natation"
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Musculation"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Randonnee"
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptique"
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "Escaliers"
            else -> "Autre"
        }
    }

    private fun mapSleepStageType(stage: Int): String {
        return when (stage) {
            SleepSessionRecord.STAGE_TYPE_AWAKE -> "awake"
            SleepSessionRecord.STAGE_TYPE_LIGHT -> "light"
            SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
            SleepSessionRecord.STAGE_TYPE_REM -> "rem"
            SleepSessionRecord.STAGE_TYPE_SLEEPING -> "sleeping"
            else -> "sleeping"
        }
    }
}
