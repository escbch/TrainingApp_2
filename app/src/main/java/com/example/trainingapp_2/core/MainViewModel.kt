package com.example.trainingapp_2.core

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import java.time.DayOfWeek
import java.time.LocalDate

class MainViewModel(
    private val repo: PlanRepository = ServiceLocator.repo
) : ViewModel() {
    val plans get() = repo.plans.value
    val activePlan get() = repo.activePlan.value

    private val dayState = mutableStateMapOf<LocalDate, TrainingDay>()

    fun planById(id: String) = repo.getPlan(id)

    fun activate(planId: String, start: LocalDate, days: Set<DayOfWeek>) {
        repo.activatePlan(planId, start, days)
        // (Re)build schedule
        rebuildSchedule()
    }
    fun clearActive() {
        repo.clearActivePlan()
        dayState.clear()
    }

    fun getTrainingDays(): List<TrainingDay> =
        dayState.values.sortedBy { it.date }

    fun getTrainingDay(date: LocalDate): TrainingDay? = dayState[date]

    fun updateSet(
        date: LocalDate,
        exerciseId: String,
        setIndex: Int,
        weight: Double? = null,
        achievedRPE: Double? = null,     // ✅
        completed: Boolean? = null
    ) {
        val td = dayState[date] ?: return
        val newExercises = td.exercises.map { ex ->
            if (ex.id != exerciseId) ex else {
                val newSets = ex.sets.map { s ->
                    if (s.setIndex == setIndex) {
                        s.copy(
                            weight = weight ?: s.weight,
                            achievedRPE = achievedRPE ?: s.achievedRPE,   // ✅
                            completed = completed ?: s.completed
                        )
                    } else s
                }
                ex.copy(sets = newSets)
            }
        }
        dayState[date] = td.copy(exercises = newExercises)
    }

    private fun rebuildSchedule() {
        dayState.clear()
        val ap = activePlan ?: return
        val plan = planById(ap.planId) ?: return
        val dates = generateTrainingDates(ap.startDate, plan.weeks, ap.trainingDays)
        val templates = defaultDayTemplates(plan.daysPerWeek)
        if (templates.isEmpty()) return

        dates.forEachIndexed { idx, date ->
            val tpl = templates[idx % templates.size]
            // Deep copy template to get fresh set instances
            val exercises = tpl.map { ex ->
                ex.copy(
                    id = ex.id, // keep ID stable; ok for demo
                    sets = ex.sets.map { it.copy() }
                )
            }
            dayState[date] = TrainingDay(date = date, exercises = exercises)
        }
    }
}