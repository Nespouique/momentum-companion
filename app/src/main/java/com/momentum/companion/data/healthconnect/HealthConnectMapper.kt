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

object HealthConnectMapper {

    fun buildDailyMetrics(
        steps: Map<LocalDate, Long>,
        calories: Map<LocalDate, Double>,
        exercises: List<ExerciseSessionRecord>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<DailyMetric> {
        val zone = ZoneId.systemDefault()

        val activeMinutesByDay = exercises.groupBy { session ->
            session.startTime.atZone(zone).toLocalDate()
        }.mapValues { (_, sessions) ->
            sessions.sumOf { session ->
                Duration.between(session.startTime, session.endTime).toMinutes()
            }
        }

        val dates = generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
            .toList()

        return dates.mapNotNull { date ->
            val daySteps = steps[date]
            val dayCals = calories[date]
            val dayMins = activeMinutesByDay[date]

            if (daySteps != null || dayCals != null || dayMins != null) {
                DailyMetric(
                    date = date.toString(),
                    steps = daySteps?.toInt(),
                    activeCalories = dayCals?.toInt(),
                    activeMinutes = dayMins?.toInt(),
                )
            } else {
                null
            }
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
