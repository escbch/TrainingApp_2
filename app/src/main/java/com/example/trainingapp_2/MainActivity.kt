package com.example.trainingapp_2

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.trainingapp_2.core.ActivePlan
import com.example.trainingapp_2.core.Exercise
import com.example.trainingapp_2.core.ExerciseSet
import com.example.trainingapp_2.core.MainViewModel
import com.example.trainingapp_2.core.Plan
import com.example.trainingapp_2.core.TrainingDay
import com.example.trainingapp_2.core.WeightMode
import com.example.trainingapp_2.core.e1rm
import com.example.trainingapp_2.core.suggestedWeightFromE1RM
import java.time.*
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            "This sample uses java.time; desugaring enabled. minSdk 24+."
        }
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AppRoot()
            }
        }
    }
}

/* ---------- Navigation ---------- */

sealed class Route(val path: String) {
    data object Entry : Route("entry")
    data object PlanPicker : Route("plans")
    data object Active : Route("active")
    data object Setup : Route("setup/{planId}") {
        fun build(planId: String) = "setup/$planId"
    }
    data object Day : Route("day/{epochDay}") {
        fun build(date: LocalDate) = "day/${date.toEpochDay()}"
    }
    data object ExerciseDetail : Route("exercise/{epochDay}/{exerciseId}") {
        fun build(date: LocalDate, exerciseId: String) = "exercise/${date.toEpochDay()}/$exerciseId"
    }

    data object Calendar : Route("calendar")

    data object Summary : Route("summary/{epochDay}") {
        fun build(date: LocalDate) = "summary/${date.toEpochDay()}"
    }

    data object Options : Route("options")
}

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Route.Entry.path) {
        composable(Route.Entry.path) {
            LaunchedEffect(vm.activePlan) {
                if (vm.activePlan == null) {
                    nav.navigate(Route.PlanPicker.path) {
                        popUpTo(Route.Entry.path) { inclusive = true }
                    }
                } else {
                    nav.navigate(Route.Active.path) {
                        popUpTo(Route.Entry.path) { inclusive = true }
                    }
                }
            }
        }

        composable(Route.PlanPicker.path) {
            PlanPickerScreen(
                plans = vm.plans,
                onSelect = { plan -> nav.navigate(Route.Setup.build(plan.id)) }
            )
        }

        composable(
            Route.Setup.path,
            arguments = listOf(navArgument("planId") { type = NavType.StringType })
        ) { backStackEntry ->
            val planId = backStackEntry.arguments?.getString("planId")!!
            val plan = vm.planById(planId)
            if (plan == null) {
                ErrorScreen("Unknown plan.")
            } else {
                SetupScheduleScreen(
                    plan = plan,
                    onConfirm = { startDate, days ->
                        vm.activate(plan.id, startDate, days)
                        nav.navigate(Route.Active.path) {
                            popUpTo(Route.PlanPicker.path) { inclusive = true }
                        }
                    },
                    onCancel = { nav.popBackStack() }
                )
            }
        }

        composable(Route.Active.path) {
            val active = vm.activePlan
            if (active == null) {
                ErrorScreen("No active plan.")
            } else {
                val plan = vm.planById(active.planId)
                ActivePlanScreen(
                    plan = plan,
                    active = active,
                    onReset = {
                        vm.clearActive()
                        nav.navigate(Route.PlanPicker.path) {
                            popUpTo(Route.Active.path) { inclusive = true }
                        }
                    },
                    onOpenCalendar = { nav.navigate(Route.Calendar.path) }
                )
            }
        }

        composable(
            Route.Day.path,
            arguments = listOf(navArgument("epochDay") { type = NavType.LongType })
        ) { backStackEntry ->
            val epoch = backStackEntry.arguments?.getLong("epochDay")!!
            val date = LocalDate.ofEpochDay(epoch)
            val day = vm.getTrainingDay(date)
            if (day == null) {
                ErrorScreen("Unknown day")
            } else {
                val missing = vm.countMissingEntries(date)
                TrainingDayScreen(
                    day = day,
                    missingCount = missing,
                    onOpenExercise = { exId -> nav.navigate(Route.ExerciseDetail.build(date, exId)) },
                    onFinish = { fillZeros ->
                        if (fillZeros) vm.fillMissingWithZeros(date)
                        nav.navigate(Route.Summary.build(date))
                    }
                )
            }
        }

        composable(
            Route.ExerciseDetail.path,
            arguments = listOf(
                navArgument("epochDay") { type = NavType.LongType },
                navArgument("exerciseId") { type = NavType.StringType },
            )
        ) { backStackEntry ->
            val epoch = backStackEntry.arguments?.getLong("epochDay")!!
            val date = LocalDate.ofEpochDay(epoch)
            val exerciseId = backStackEntry.arguments?.getString("exerciseId")!!
            val day = vm.getTrainingDay(date)
            val exercise = day?.exercises?.find { it.id == exerciseId }
            if (day == null || exercise == null) {
                ErrorScreen("Unknown exercise")
            } else {
                ExerciseDetailScreen(
                    date = date,
                    exercise = exercise,
                    onUpdate = { setIndex, newWeight, completed, achievedRpe ->
                        vm.updateSet(date, exercise.id, setIndex, newWeight, completed, achievedRpe)
                    }
                )
            }
        }

        composable(Route.Calendar.path) {
            CalendarScreen(
                trainingDates = vm.getTrainingDays().map { it.date }.toSet(),
                onOpenDay = { date -> nav.navigate(Route.Day.build(date)) }
            )
        }

        composable(
            Route.Summary.path,
            arguments = listOf(navArgument("epochDay") { type = NavType.LongType })
        ) { backStackEntry ->
            val epoch = backStackEntry.arguments?.getLong("epochDay")!!
            val date = LocalDate.ofEpochDay(epoch)
            val sum = vm.summaryFor(date)
            if (sum == null) {
                ErrorScreen("No summary for $date")
            } else {
                SummaryScreen(
                    date = date,
                    summary = sum,
                    onDone = {
                        val popped = nav.popBackStack(Route.Active.path, inclusive = false)
                        if (!popped)  {
                            nav.navigate(Route.Entry.path) {
                                popUpTo(nav.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    })
            }
        }

        composable(Route.Options.path) {
            OptionsScreen(
                current = vm.options.restSeconds,
                onSave = { vm.setRestSeconds(it); nav.popBackStack()}
            )
        }
    }
}

/* ---------- Screens ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanPickerScreen(
    plans: List<Plan>,
    onSelect: (Plan) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Select a Plan") }) }
    ) { padding ->
        if (plans.isEmpty()) {
            Box(Modifier
                .fillMaxSize()
                .padding(padding), contentAlignment = Alignment.Center) {
                Text("No plans available yet.")
            }
        } else {
            LazyColumn(Modifier
                .fillMaxSize()
                .padding(padding)) {
                items(plans) { p ->
                    ElevatedCard(
                        onClick = { onSelect(p) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("${p.weeks} weeks · ${p.daysPerWeek} days/week", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScheduleScreen(
    plan: Plan,
    onConfirm: (LocalDate, Set<DayOfWeek>) -> Unit,
    onCancel: () -> Unit
) {
    var selectedDays by remember { mutableStateOf<Set<DayOfWeek>>(emptySet()) }
    val dateState = rememberDatePickerState()
    val selectedDate: LocalDate? = dateState.selectedDateMillis
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

    val canConfirm = selectedDate != null &&
            selectedDays.isNotEmpty() &&
            selectedDays.size == plan.daysPerWeek

    Scaffold(
        topBar = { TopAppBar(title = { Text("Start ${plan.name}") }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }

                    Button(
                        enabled = canConfirm,
                        onClick = { onConfirm(selectedDate!!, selectedDays) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Confirm") }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()) // ✅ content scrolls
                .imePadding()                          // ✅ stays above keyboard
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("1) Choose a start date", style = MaterialTheme.typography.titleMedium)
            DatePicker(state = dateState, showModeToggle = false)

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            Text(
                "2) Choose your training days (max ${plan.daysPerWeek})",
                style = MaterialTheme.typography.titleMedium
            )
            DayOfWeekSelector(
                selected = selectedDays,
                onToggle = { d ->
                    selectedDays = if (d in selectedDays) selectedDays - d
                    else if (selectedDays.size < plan.daysPerWeek) selectedDays + d else selectedDays
                }
            )

            selectedDate?.let { Text("Selected start: $it") }
            if (selectedDays.isNotEmpty()) {
                Text("Selected days: " + selectedDays.sortedBy { it.value }.joinToString { it.name.take(3) })
            }

            Spacer(Modifier.height(80.dp)) // ✅ ensures content isn't hidden under bottom bar
        }
    }
}

@Composable
fun DayOfWeekSelector(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit
) {
    val weekdays = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    )
    val weekend = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Top row: Mon–Fri (centered with equal spacing)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            weekdays.forEach { day ->
                DayChip(day, selected.contains(day)) { onToggle(day) }
            }
        }

        // Bottom row: Sat & Sun centered
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            weekend.forEach { day ->
                DayChip(day, selected.contains(day)) { onToggle(day) }
            }
        }
    }
}

@Composable
private fun DayChip(day: DayOfWeek, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(day.name.take(3)) }, // e.g., MON, TUE — swap for localized label if you like
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivePlanScreen(
    plan: Plan?,
    active: ActivePlan,
    onReset: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy") }
    val endDate = active.startDate.plusDays((plan?.weeks?.toLong() ?: 0L) * 7 - 1)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Active Plan") }) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(plan?.name ?: "Unknown plan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Duration: ${active.startDate.format(formatter)} → ${endDate.format(formatter)}")
            Text("Training days: " + active.trainingDays.sortedBy { it.value }.joinToString { it.name.take(3) })

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Next steps", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("• Add per-day workouts and exercises\n• Track sets with weight/RPE and completion")
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onReset) { Text("Reset active plan") }
            Button(onClick = onOpenCalendar) {Text("Calendar")}
        }
    }
}

@Composable
fun ErrorScreen(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(msg)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingDayScreen(
    day: TrainingDay,
    missingCount: Int,
    onOpenExercise: (String) -> Unit,
    onFinish: (fillZeros: Boolean) -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Training ${day.date}") }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = {
                        if (missingCount > 0) {
                            showConfirm = true
                        } else {
                            onFinish(false) // no missing, go straight to summary
                        }
                    }) { Text("Finish day") }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(day.exercises) { ex ->
                ElevatedCard(
                    onClick = { onOpenExercise(ex.id) },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(ex.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("${ex.sets.size} sets")
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) } // keep list above bottom bar
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Missing entries") },
            text = { Text("There are $missingCount sets with no entries. Fill missing values with 0 to continue?") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onFinish(true) // fill zeros then go to summary
                }) { Text("Fill with 0 & continue") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Stay here") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    date: LocalDate,
    exercise: Exercise,
    onUpdate: (setIndex: Int, newWeight: Double?, achievedRpe: Double?, completed: Boolean?) -> Unit
) {
    val anchorE1rm: Double? = remember(exercise) {
        if (exercise.weightMode == WeightMode.ANCHOR_E1RM_FROM_SET1) {
            val s1 = exercise.sets.firstOrNull()
            s1?.weight?.let {
                w -> e1rm(w, s1.reps, s1.achievedRPE)?.toDouble()
            }
        } else null
    }
    Scaffold(topBar = { TopAppBar(title = { Text(exercise.name) }) }) { padding ->
        LazyColumn(Modifier
            .fillMaxSize()
            .padding(padding)) {
            items(exercise.sets) { s ->
                val suggested = if (anchorE1rm != null && s.setIndex > 1)
                    suggestedWeightFromE1RM(anchorE1rm, s.reps, s.targetRPE)
                else null

                ExerciseSetCard(
                    set = s,
                    suggestedWeight = suggested,
                    onChangeWeight = { w -> onUpdate(s.setIndex, w, null, null) },
                    onChangeRpe = { r -> onUpdate(s.setIndex, null, r, null) },
                    onToggleComplete = { c -> onUpdate(s.setIndex, null, null, c) }
                )
            }
        }
    }
}

@Composable
private fun ExerciseSetCard(
    set: ExerciseSet,
    suggestedWeight: Double?,
    onChangeWeight: (Double?) -> Unit,
    onChangeRpe: (Double?) -> Unit,
    onToggleComplete: (Boolean) -> Unit
) {
    var weightText by remember(set.setIndex) { mutableStateOf(set.weight?.toString() ?: "") }
    var rpeText by remember(set.setIndex) { mutableStateOf(set.achievedRPE?.toString() ?: "") }

    ElevatedCard(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Set ${set.setIndex}", fontWeight = FontWeight.SemiBold)
            Text("Reps: ${set.reps}")
            set.targetRPE?.let { Text("Target RPE: $it") }

            OutlinedTextField(
                value = weightText,
                onValueChange = { txt ->
                    weightText = txt.filter { it.isDigit() || it == '.' || it == ',' }
                    val parsed = weightText.replace(',', '.').toDoubleOrNull()
                    onChangeWeight(parsed)
                },
                label = { Text("Weight (kg)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = rpeText,
                onValueChange = { txt ->
                    rpeText = txt.filter { it.isDigit() || it == '.' || it == ',' }
                    val parsed = rpeText.replace(',', '.').toDoubleOrNull()
                        ?.coerceIn(1.0, 10.0) // clamp to valid RPE range
                    onChangeRpe(parsed)
                },
                label = { Text("Reached RPE") },
                placeholder = { Text("e.g., 8.5") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            if (suggestedWeight != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Suggested: ${"%.1f".format(suggestedWeight)} kg", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        onChangeWeight(suggestedWeight)
                    }) { Text("Apply") }
                }
            }

            val e1 = e1rm(set.weight, set.reps, set.achievedRPE)
            Text("E1RM: ${e1?.let { "$it kg" } ?: "—"}")

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(checked = set.completed, onCheckedChange = { onToggleComplete(it) })
                Text(if (set.completed) "Completed" else "Not completed")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    trainingDates: Set<LocalDate>,
    onOpenDay: (LocalDate) -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val firstDayOfWeek = DayOfWeek.MONDAY
    val weeks = remember(currentMonth) { buildMonthGrid(currentMonth, firstDayOfWeek) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${currentMonth.month.name.lowercase().replaceFirstChar { it.titlecase() }} ${currentMonth.year}") },
                navigationIcon = {
                    IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
                    }
                },
                actions = {
                    IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {

            Spacer(Modifier.height(8.dp))

            // Weekday header (Mon..Sun)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
                ).forEach { dow ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(dow.name.take(2), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Calendar grid
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                weeks.forEach { week ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        week.forEach { dateOrNull ->
                            DayCell(
                                date = dateOrNull,
                                isCurrentMonth = dateOrNull?.let { it.monthValue == currentMonth.monthValue && it.year == currentMonth.year } == true,
                                isTrainingDay = dateOrNull != null && trainingDates.contains(dateOrNull),
                                onClick = { d ->
                                    if (trainingDates.contains(d)) onOpenDay(d)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isCurrentMonth: Boolean,
    isTrainingDay: Boolean,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val disabled = date == null || !isCurrentMonth
    val clickable = !disabled && isTrainingDay

    val shape = MaterialTheme.shapes.medium
    val bg = when {
        disabled -> MaterialTheme.colorScheme.surface
        isTrainingDay -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        disabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        isTrainingDay -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = shape,
        color = bg,
        tonalElevation = if (isTrainingDay) 2.dp else 0.dp,
        modifier = modifier
            .aspectRatio(1f) // square cell
            .then(
                if (clickable)
                    Modifier
                        .clip(shape)
                        .clickable { onClick(date) }
                else Modifier
            )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (date != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = content
                    )
                    // Small dot indicator for training days
                    if (isTrainingDay) {
                        Spacer(Modifier.height(2.dp))
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    date: LocalDate,
    summary: com.example.trainingapp_2.core.TrainingDaySummary,
    onDone: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Summary $date") }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = onDone) { Text("Back to main") }
                }
            }
        }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Totals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Sets: ${summary.totalSets}")
                    Text("Reps: ${summary.totalReps}")
                    Text("Weight moved: ${"%.1f".format(summary.totalWeightMovedKg)} kg")
                }
            }

            // Placeholder for future per-muscle summary
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("By muscle group (coming soon)", fontWeight = FontWeight.SemiBold)
                    Text("We’ll aggregate sets/volume per group once exercises are tagged.")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsScreen(current: Int, onSave: (Int) -> Unit) {
    var seconds by remember { mutableIntStateOf(current) }
    Scaffold(topBar = { TopAppBar(title = { Text("Options") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Rest timer (seconds)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = seconds.toString(),
                onValueChange = { seconds = it.filter { c -> c.isDigit() }.toIntOrNull()?.coerceIn(10, 600) ?: seconds },
                singleLine = true,
                label = { Text("Seconds") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { onSave(seconds) }) { Text("Save") }
        }
    }
}


/** Build a month grid starting on [firstDayOfWeek], returning up to 6 weeks × 7 days.
 *  Nulls mark leading/trailing blanks in the grid. */
private fun buildMonthGrid(
    ym: YearMonth,
    firstDayOfWeek: DayOfWeek
): List<List<LocalDate?>> {
    val firstOfMonth = ym.atDay(1)
    val daysInMonth = ym.lengthOfMonth()
    val leading = ((firstOfMonth.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7

    val cells = mutableListOf<LocalDate?>()
    repeat(leading) { cells += null }
    repeat(daysInMonth) { day -> cells += ym.atDay(day + 1) }

    // Pad to full weeks (7) and up to 6 rows if needed
    while (cells.size % 7 != 0) cells += null
    if (cells.size <= 35) { // ensure up to 6 rows for stable layout
        while (cells.size < 42) cells += null
    }

    return cells.chunked(7)
}

