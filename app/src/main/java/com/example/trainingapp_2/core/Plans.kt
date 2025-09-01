package com.example.trainingapp_2.core

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

data class Plan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val weeks: Int,
    val daysPerWeek: Int,
)

data class ActivePlan(
    val planId: String,
    val startDate: LocalDate,
    val trainingDays: Set<DayOfWeek>,
)

interface PlanRepository {
    val plans: State<List<Plan>>
    val activePlan: State<ActivePlan?>
    fun activatePlan(planId: String, startDate: LocalDate, days: Set<DayOfWeek>)
    fun clearActivePlan()
    fun getPlan(planId: String): Plan?
}

class InMemoryPlanRepository : PlanRepository {
    private val _plans = mutableStateOf(
        listOf(
            Plan(name = "Powerbuilding 3-Day", weeks = 8, daysPerWeek = 3),
            Plan(name = "Hypertrophy 4-Day", weeks = 10, daysPerWeek = 4),
            Plan(name = "Strength 5-Day", weeks = 12, daysPerWeek = 5),
        )
    )
    private val _activePlan = mutableStateOf<ActivePlan?>(null)

    override val plans: State<List<Plan>> = _plans
    override val activePlan: State<ActivePlan?> = _activePlan

    override fun activatePlan(planId: String, startDate: LocalDate, days: Set<DayOfWeek>) {
        _activePlan.value = ActivePlan(planId, startDate, days)
    }

    override fun clearActivePlan() {
        _activePlan.value = null
    }

    override fun getPlan(planId: String): Plan? = _plans.value.find { it.id == planId }
}

object ServiceLocator {
    val repo: PlanRepository = InMemoryPlanRepository()
}