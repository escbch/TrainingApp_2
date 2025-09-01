package com.example.trainingapp_2.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

enum class WeightMode { MANUAL, ANCHOR_E1RM_FROM_SET1 }

data class ExerciseSet(
    val setIndex: Int,
    val reps: Int,
    val targetRPE: Double?,
    var weight: Double?,
    var achievedRPE: Double?,
    var completed: Boolean = false,
)

data class Exercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sets: List<ExerciseSet>,
    val weightMode: WeightMode = WeightMode.MANUAL,
)

data class TrainingDay(
    val date: LocalDate,
    val exercises: List<Exercise>,
)

data class TrainingDaySummary(
    val totalSets: Int,
    val totalReps: Int,
    val totalWeightMovedKg: Double,
)

/** Simple schedule generator:
 *  Creates all training dates for the active plan (weeks × trainingDays).
 *  Order is deterministic and cycles Mon..Sun picking only selected weekdays. */
fun generateTrainingDates(start: LocalDate, weeks: Int, trainingDays: Set<DayOfWeek>): List<LocalDate> {
    if (trainingDays.isEmpty() || weeks <= 0) return emptyList()
    val endExclusive = start.plusWeeks(weeks.toLong())
    val result = mutableListOf<LocalDate>()
    var d = start
    while (d.isBefore(endExclusive)) {
        if (d.dayOfWeek in trainingDays) result += d
        d = d.plusDays(1)
    }
    return result
}

/** Very lightweight per-day templates depending on days/week. Replace with your desktop-import later. */
fun defaultDayTemplates(daysPerWeek: Int): List<List<Exercise>> {
    // Each entry = one template day (D1..Dk). We’ll cycle over them for the generated dates.
    return when (daysPerWeek) {
        3 -> listOf(
            listOf(
                Exercise(name = "Bench Press", sets = threeBy(5, targetRPE = 7.5)),
                Exercise(name = "DB Bench Press", sets = threeBy(10, targetRPE = 8.0), weightMode = WeightMode.ANCHOR_E1RM_FROM_SET1),
                Exercise(name = "Squat", sets = threeBy(5, targetRPE = 7.5), weightMode = WeightMode.ANCHOR_E1RM_FROM_SET1),
            ),
            listOf(
                Exercise(name = "OHP", sets = threeBy(8, targetRPE = 8.0)),
                Exercise(name = "Pull-Ups", sets = threeBy(8, targetRPE = 8.0)),
                Exercise(name = "Leg Curl", sets = threeBy(12, targetRPE = 8.0)),
            ),
            listOf(
                Exercise(name = "Deadlift", sets = threeBy(5, targetRPE = 7.5)),
                Exercise(name = "Row", sets = threeBy(10, targetRPE = 8.0)),
                Exercise(name = "Leg Press", sets = threeBy(12, targetRPE = 8.0)),
            ),
        )
        4 -> listOf(
            listOf(
                Exercise(name = "Bench Press", sets = threeBy(5, 7.5)),
                Exercise(name = "Incline DB Press", sets = threeBy(10, 8.0)),
            ),
            listOf(
                Exercise(name = "Squat", sets = threeBy(5, 7.5)),
                Exercise(name = "RDL", sets = threeBy(8, 8.0)),
            ),
            listOf(
                Exercise(name = "OHP", sets = threeBy(6, 8.0)),
                Exercise(name = "Row", sets = threeBy(10, 8.0)),
            ),
            listOf(
                Exercise(name = "Deadlift", sets = threeBy(3, 8.0)),
                Exercise(name = "Leg Press", sets = threeBy(12, 8.0)),
            ),
        )
        else -> listOf(
            listOf(
                Exercise(name = "Bench Press", sets = threeBy(5, 7.5)),
                Exercise(name = "Squat", sets = threeBy(5, 7.5)),
            )
        )
    }
}

fun computeDaySummary(day: TrainingDay): TrainingDaySummary {
    var sets = 0
    var reps = 0
    var weight = 0.0
    day.exercises.forEach { ex ->
        ex.sets.forEach { s ->
            sets += 1
            reps += s.reps
            weight += (s.weight ?: 0.0) * s.reps
        }
    }
    return TrainingDaySummary(
        totalSets = sets,
        totalReps = reps,
        totalWeightMovedKg = weight
    )
}

private fun threeBy(reps: Int, targetRPE: Double?) = List(3) { i ->
    ExerciseSet(setIndex = i + 1, reps = reps, targetRPE = targetRPE, weight = null, achievedRPE = null, completed = false)
}

/** E1RM via Epley: 1RM ≈ w * (1 + reps/30). */
fun e1rm(weight: Double?, reps: Int, achievedRPE: Double?): Int? {
    if (weight == null) return null
    val rir = (10.0 - (achievedRPE ?: 10.0)).coerceAtLeast(0.0) // if unknown RPE → assume RIR=0 (RPE10)
    val oneRm = weight * (1.0 + (reps + rir) / 30.0)  // Epley with RIR adjustment
    return kotlin.math.round(oneRm).toInt()
}

fun suggestedWeightFromE1RM(anchorE1RM: Double, reps: Int, targetRPE: Double?): Double {
    val rir = 10.0 - (targetRPE ?: 10.0)
    val denom = 1.0 + (reps + rir) / 30.0
    return (anchorE1RM / denom)
}