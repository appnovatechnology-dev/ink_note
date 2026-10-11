package com.example.inknote.ui.canvas.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.*

object TemplateLayoutRenderer {

    var isDarkTheme: Boolean = false

    private val headerColor get() = if (isDarkTheme) Color(0xFFE4E4E7) else Color(0xFF1E293B)
    private val subHeaderColor get() = if (isDarkTheme) Color(0xFFA1A1AA) else Color(0xFF475569)
    private val gridLineColor get() = if (isDarkTheme) Color(0x38E4E4E7) else Color(0x3364748B)
    private val subtleLineColor get() = if (isDarkTheme) Color(0x22E4E4E7) else Color(0x2294A3B8)
    private val accentBoxColor get() = if (isDarkTheme) Color(0x256366F1) else Color(0x103B82F6)
    private val peachAccentColor get() = if (isDarkTheme) Color(0x25F97316) else Color(0x14F97316)
    private val paperBgColor get() = if (isDarkTheme) Color(0xFF232328) else Color.White

    fun drawTemplate(
        scope: DrawScope,
        templateType: String,
        width: Float,
        height: Float,
        scaleFactor: Float,
        textMeasurer: TextMeasurer,
        isDark: Boolean = isDarkTheme
    ) {
        val prevDark = isDarkTheme
        isDarkTheme = isDark
        with(scope) {
            when (templateType) {
                "HABIT_TRACKER" -> drawHabitTracker(width, height, scaleFactor, textMeasurer)
                "DAILY_GOALS" -> drawDailyGoals(width, height, scaleFactor, textMeasurer)
                "WEEKLY_PLANNER" -> drawWeeklyPlanner(width, height, scaleFactor, textMeasurer)
                "DAILY_PLANNER" -> drawDailyPlanner(width, height, scaleFactor, textMeasurer)
                "GOAL_SETTING" -> drawGoalSetting(width, height, scaleFactor, textMeasurer)
                "ASSIGNMENT_PLANNER" -> drawAssignmentPlanner(width, height, scaleFactor, textMeasurer)
                "MIND_MAP" -> drawMindMap(width, height, scaleFactor, textMeasurer)
                "SEMESTER_OVERVIEW" -> drawSemesterOverview(width, height, scaleFactor, textMeasurer)
                "MONTHLY_PLANNER" -> drawMonthlyPlanner(width, height, scaleFactor, textMeasurer)
            }
        }
        isDarkTheme = prevDark
    }

    // 1. Habit Tracker: Radial / circular 31-day concentric habit tracker with habits list & motivational quote
    private fun DrawScope.drawHabitTracker(width: Float, height: Float, scale: Float, tm: TextMeasurer) {
        drawText(
            textMeasurer = tm,
            text = "Habit Tracker",
            topLeft = Offset(40f * scale, 34f * scale),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )

        drawText(
            textMeasurer = tm,
            text = "Month: ___________________",
            topLeft = Offset(width - 240f * scale, 38f * scale),
            style = TextStyle(fontSize = 12.sp, color = subHeaderColor)
        )

        // Radial habit wheel on center-left
        val centerX = width * 0.42f
        val centerY = height * 0.46f
        val maxRadius = min(width * 0.35f, height * 0.35f)
        val numRings = 10
        val ringSpacing = maxRadius / (numRings + 1)

        // Subtle decorative background circle
        drawCircle(
            color = peachAccentColor,
            radius = maxRadius,
            center = Offset(centerX, centerY)
        )

        // Draw concentric circles for each habit
        for (i in 1..numRings) {
            drawCircle(
                color = gridLineColor,
                radius = (i + 1) * ringSpacing,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1.2f * scale)
            )
        }

        // Center hub circle
        drawCircle(
            color = paperBgColor,
            radius = ringSpacing,
            center = Offset(centerX, centerY)
        )
        drawCircle(
            color = gridLineColor,
            radius = ringSpacing,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.2f * scale)
        )
        drawText(
            textMeasurer = tm,
            text = "31 Days",
            topLeft = Offset(centerX - 18f * scale, centerY - 6f * scale),
            style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = subHeaderColor)
        )

        // 31 day radial spokes
        val numDays = 31
        for (day in 0 until numDays) {
            val angle = (day * 360f / numDays - 90f) * (Math.PI / 180f)
            val innerX = centerX + ringSpacing * cos(angle).toFloat()
            val innerY = centerY + ringSpacing * sin(angle).toFloat()
            val outerX = centerX + maxRadius * cos(angle).toFloat()
            val outerY = centerY + maxRadius * sin(angle).toFloat()
            drawLine(
                color = subtleLineColor,
                start = Offset(innerX, innerY),
                end = Offset(outerX, outerY),
                strokeWidth = 1f * scale
            )

            // Day label around the rim for milestone days
            if (day % 5 == 0 || day == numDays - 1) {
                val labelX = centerX + (maxRadius + 12f * scale) * cos(angle).toFloat()
                val labelY = centerY + (maxRadius + 12f * scale) * sin(angle).toFloat()
                drawText(
                    textMeasurer = tm,
                    text = "${day + 1}",
                    topLeft = Offset(labelX - 6f * scale, labelY - 5f * scale),
                    style = TextStyle(fontSize = 7.sp, color = subHeaderColor)
                )
            }
        }

        // Habit lines list at the right
        val listStartX = width * 0.72f
        val listStartY = 75f * scale
        drawText(
            textMeasurer = tm,
            text = "Habits to Cultivate:",
            topLeft = Offset(listStartX, listStartY),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 0 until 10) {
            val y = listStartY + (28f + i * 28f) * scale
            // Habit number circle
            drawCircle(color = gridLineColor, radius = 5f * scale, center = Offset(listStartX + 8f * scale, y + 6f * scale), style = Stroke(1.2f * scale))
            drawText(
                textMeasurer = tm,
                text = "${i + 1}",
                topLeft = Offset(listStartX + 5f * scale, y + 1f * scale),
                style = TextStyle(fontSize = 8.sp, color = subHeaderColor)
            )
            drawLine(color = gridLineColor, start = Offset(listStartX + 22f * scale, y + 10f * scale), end = Offset(width - 40f * scale, y + 10f * scale), strokeWidth = 1f * scale)
        }

        // Bottom motivational notes box matching reference image
        val notesY = height - 125f * scale
        drawRect(
            color = peachAccentColor,
            topLeft = Offset(40f * scale, notesY),
            size = Size(width - 80f * scale, 95f * scale)
        )
        drawRect(
            color = gridLineColor,
            topLeft = Offset(40f * scale, notesY),
            size = Size(width - 80f * scale, 95f * scale),
            style = Stroke(width = 1f * scale)
        )
        drawText(
            textMeasurer = tm,
            text = "If you want to change your world, you need to start cultivating good habits.",
            topLeft = Offset(52f * scale, notesY + 12f * scale),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF9A3412))
        )
        for (i in 1..2) {
            val lineY = notesY + 36f * scale + i * 22f * scale
            drawLine(color = subtleLineColor, start = Offset(52f * scale, lineY), end = Offset(width - 52f * scale, lineY), strokeWidth = 1f * scale)
        }
    }

    // 2. Daily Goals: Numbered goals checklist, day selector, priorities, reflections & water tracker
    private fun DrawScope.drawDailyGoals(width: Float, height: Float, scale: Float, tm: TextMeasurer) {
        drawText(
            textMeasurer = tm,
            text = "Daily Goals",
            topLeft = Offset(40f * scale, 34f * scale),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )

        // Day of Week selector circles matching reference image (M, T, W, T, F, S, S)
        val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
        val daysStartX = width - 240f * scale
        dayLetters.forEachIndexed { idx, letter ->
            val cx = daysStartX + idx * 26f * scale
            val cy = 44f * scale
            drawCircle(color = gridLineColor, radius = 9f * scale, center = Offset(cx, cy), style = Stroke(1.2f * scale))
            drawText(
                textMeasurer = tm,
                text = letter,
                topLeft = Offset(cx - 4f * scale, cy - 6f * scale),
                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
            )
        }

        // Section 1: Morning Priorities / My Goals
        val goalsBoxTop = 85f * scale
        val goalsBoxHeight = height * 0.38f
        drawRect(color = accentBoxColor, topLeft = Offset(40f * scale, goalsBoxTop), size = Size(width - 80f * scale, goalsBoxHeight))
        drawRect(color = gridLineColor, topLeft = Offset(40f * scale, goalsBoxTop), size = Size(width - 80f * scale, goalsBoxHeight), style = Stroke(1.2f * scale))

        drawText(
            textMeasurer = tm,
            text = "My Goals / Morning Priorities",
            topLeft = Offset(54f * scale, goalsBoxTop + 14f * scale),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )

        val itemSpacing = (goalsBoxHeight - 60f * scale) / 5f
        for (i in 1..5) {
            val y = goalsBoxTop + 45f * scale + (i - 1) * itemSpacing
            drawRect(
                color = gridLineColor,
                topLeft = Offset(54f * scale, y),
                size = Size(14f * scale, 14f * scale),
                style = Stroke(1.2f * scale)
            )
            drawText(
                textMeasurer = tm,
                text = "$i.",
                topLeft = Offset(74f * scale, y),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
            )
            drawLine(
                color = gridLineColor,
                start = Offset(94f * scale, y + 14f * scale),
                end = Offset(width - 56f * scale, y + 14f * scale),
                strokeWidth = 1f * scale
            )
        }

        // Section 2: Afternoon Tasks
        val midY = goalsBoxTop + goalsBoxHeight + 16f * scale
        val midHeight = height * 0.28f
        drawRect(color = gridLineColor, topLeft = Offset(40f * scale, midY), size = Size(width - 80f * scale, midHeight), style = Stroke(1.2f * scale))
        drawText(
            textMeasurer = tm,
            text = "Afternoon Tasks & Focus",
            topLeft = Offset(54f * scale, midY + 12f * scale),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        val taskSpacing = (midHeight - 50f * scale) / 4f
        for (i in 1..4) {
            val y = midY + 40f * scale + (i - 1) * taskSpacing
            drawCircle(color = gridLineColor, radius = 4f * scale, center = Offset(58f * scale, y + 6f * scale), style = Stroke(1.2f * scale))
            drawLine(color = subtleLineColor, start = Offset(72f * scale, y + 10f * scale), end = Offset(width - 56f * scale, y + 10f * scale), strokeWidth = 1f * scale)
        }

        // Section 3: Bottom reflections & water tracker
        val bottomY = midY + midHeight + 16f * scale
        val colWidth = (width - 100f * scale) * 0.65f
        val bottomHeight = height - bottomY - 35f * scale

        // Reflections box
        drawRect(color = gridLineColor, topLeft = Offset(40f * scale, bottomY), size = Size(colWidth, bottomHeight), style = Stroke(1f * scale))
        drawText(
            textMeasurer = tm,
            text = "Daily Reflections & Notes",
            topLeft = Offset(52f * scale, bottomY + 10f * scale),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 0 until 3) {
            val y = bottomY + 36f * scale + i * 22f * scale
            drawLine(color = subtleLineColor, start = Offset(52f * scale, y + 10f * scale), end = Offset(40f * scale + colWidth - 14f * scale, y + 10f * scale), strokeWidth = 1f * scale)
        }

        // Water Tracker Box
        val waterBoxX = 52f * scale + colWidth
        val waterBoxWidth = width - waterBoxX - 40f * scale
        drawRect(color = accentBoxColor, topLeft = Offset(waterBoxX, bottomY), size = Size(waterBoxWidth, bottomHeight))
        drawRect(color = gridLineColor, topLeft = Offset(waterBoxX, bottomY), size = Size(waterBoxWidth, bottomHeight), style = Stroke(1f * scale))
        drawText(
            textMeasurer = tm,
            text = "Water Intake",
            topLeft = Offset(waterBoxX + 12f * scale, bottomY + 10f * scale),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
        )
        for (i in 0 until 8) {
            val col = i % 4
            val row = i / 4
            val gx = waterBoxX + 18f * scale + col * 26f * scale
            val gy = bottomY + 38f * scale + row * 26f * scale
            drawCircle(color = Color(0xFF38BDF8), radius = 7f * scale, center = Offset(gx, gy), style = Stroke(1.4f * scale))
        }
    }

    // 3. Weekly Planner: To Do list, Schedule column, Assignments / Study, Deadlines table
    private fun DrawScope.drawWeeklyPlanner(width: Float, height: Float, scale: Float, tm: TextMeasurer) {
        drawText(
            textMeasurer = tm,
            text = "Weekly Planner",
            topLeft = Offset(40f * scale, 30f * scale),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )

        drawText(
            textMeasurer = tm,
            text = "Week of: ___________________",
            topLeft = Offset(width - 240f * scale, 34f * scale),
            style = TextStyle(fontSize = 12.sp, color = subHeaderColor)
        )

        val splitX = width * 0.44f
        val topContentY = 70f * scale
        val midY = height * 0.50f
        val bottomY = height - 40f * scale

        // Left Top: To Do List
        drawRect(color = gridLineColor, topLeft = Offset(40f * scale, topContentY), size = Size(splitX - 50f * scale, midY - topContentY - 12f * scale), style = Stroke(1f * scale))
        drawText(
            textMeasurer = tm,
            text = "To Do",
            topLeft = Offset(52f * scale, topContentY + 10f * scale),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 0 until 6) {
            val y = topContentY + 36f * scale + i * 26f * scale
            drawRect(color = gridLineColor, topLeft = Offset(52f * scale, y), size = Size(12f * scale, 12f * scale), style = Stroke(1.2f * scale))
            drawLine(color = subtleLineColor, start = Offset(72f * scale, y + 12f * scale), end = Offset(splitX - 60f * scale, y + 12f * scale), strokeWidth = 1f * scale)
        }

        // Left Middle: Assignments / To Study
        val assignHeight = (bottomY - midY) * 0.52f
        drawRect(color = gridLineColor, topLeft = Offset(40f * scale, midY), size = Size(splitX - 50f * scale, assignHeight), style = Stroke(1f * scale))
        drawText(
            textMeasurer = tm,
            text = "Assignments / To Study",
            topLeft = Offset(52f * scale, midY + 10f * scale),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 0 until 4) {
            val y = midY + 36f * scale + i * 24f * scale
            drawLine(color = subtleLineColor, start = Offset(52f * scale, y + 10f * scale), end = Offset(splitX - 60f * scale, y + 10f * scale), strokeWidth = 1f * scale)
        }

        // Left Bottom: Upcoming Deadlines (DUE | NOTES) Table
        val deadlineY = midY + assignHeight + 12f * scale
        drawRect(color = accentBoxColor, topLeft = Offset(40f * scale, deadlineY), size = Size(splitX - 50f * scale, bottomY - deadlineY))
        drawRect(color = gridLineColor, topLeft = Offset(40f * scale, deadlineY), size = Size(splitX - 50f * scale, bottomY - deadlineY), style = Stroke(1f * scale))
        drawText(
            textMeasurer = tm,
            text = "Upcoming Deadlines",
            topLeft = Offset(52f * scale, deadlineY + 8f * scale),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        // Table columns: Due | Notes
        val tableColX = 40f * scale + (splitX - 50f * scale) * 0.35f
        drawLine(color = gridLineColor, start = Offset(tableColX, deadlineY + 26f * scale), end = Offset(tableColX, bottomY), strokeWidth = 1f * scale)
        drawText(textMeasurer = tm, text = "Due", topLeft = Offset(48f * scale, deadlineY + 28f * scale), style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = subHeaderColor))
        drawText(textMeasurer = tm, text = "Notes / Subject", topLeft = Offset(tableColX + 8f * scale, deadlineY + 28f * scale), style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = subHeaderColor))
        for (i in 1..3) {
            val y = deadlineY + 28f * scale + i * 22f * scale
            drawLine(color = subtleLineColor, start = Offset(40f * scale, y), end = Offset(splitX - 10f * scale, y), strokeWidth = 1f * scale)
        }

        // Right Column: Day-by-Day Schedule (Monday to Sunday)
        val rightX = splitX + 10f * scale
        val rightWidth = width - rightX - 40f * scale
        drawRect(color = gridLineColor, topLeft = Offset(rightX, topContentY), size = Size(rightWidth, bottomY - topContentY), style = Stroke(1f * scale))

        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday / Sunday")
        val dayHeight = (bottomY - topContentY) / days.size
        days.forEachIndexed { idx, day ->
            val y = topContentY + idx * dayHeight
            if (idx > 0) {
                drawLine(color = gridLineColor, start = Offset(rightX, y), end = Offset(rightX + rightWidth, y), strokeWidth = 1f * scale)
            }
            drawText(
                textMeasurer = tm,
                text = day,
                topLeft = Offset(rightX + 12f * scale, y + 6f * scale),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
            )
            // Lined slots inside each day
            for (lineIdx in 1..2) {
                val subY = y + 14f * scale + lineIdx * 18f * scale
                if (subY < y + dayHeight - 4f * scale) {
                    drawLine(color = subtleLineColor, start = Offset(rightX + 12f * scale, subY), end = Offset(rightX + rightWidth - 12f * scale, subY), strokeWidth = 1f * scale)
                }
            }
        }
    }

    // 4. Daily Planner: Hourly schedule (6 AM - 10 PM), Top Priorities, Other Tasks & Notes
    private fun DrawScope.drawDailyPlanner(width: Float, height: Float, scale: Float, tm: TextMeasurer) {
        drawText(
            textMeasurer = tm,
            text = "Daily Planner",
            topLeft = Offset(40f * scale, 30f * scale),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )

        drawText(
            textMeasurer = tm,
            text = "Date: ____________ | Day: M T W T F S S",
            topLeft = Offset(width - 280f * scale, 34f * scale),
            style = TextStyle(fontSize = 11.sp, color = subHeaderColor)
        )

        val splitX = width * 0.46f
        val startY = 70f * scale
        val endY = height - 40f * scale

        // Left: Hourly Schedule (6 AM to 10 PM)
        drawRect(color = gridLineColor, topLeft = Offset(40f * scale, startY), size = Size(splitX - 50f * scale, endY - startY), style = Stroke(1.2f * scale))
        drawText(
            textMeasurer = tm,
            text = "Schedule",
            topLeft = Offset(52f * scale, startY + 8f * scale),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        val hours = (6..22).toList()
        val hourStep = (endY - startY - 35f * scale) / hours.size
        hours.forEachIndexed { idx, hr ->
            val y = startY + 30f * scale + idx * hourStep
            val label = if (hr < 12) "$hr AM" else if (hr == 12) "12 PM" else "${hr - 12} PM"
            drawText(
                textMeasurer = tm,
                text = label,
                topLeft = Offset(46f * scale, y),
                style = TextStyle(fontSize = 9.sp, color = subHeaderColor)
            )
            drawLine(
                color = subtleLineColor,
                start = Offset(84f * scale, y + 6f * scale),
                end = Offset(splitX - 54f * scale, y + 6f * scale),
                strokeWidth = 1f * scale
            )
        }

        // Right Top: Goal / Inspiration
        val rightX = splitX + 10f * scale
        val rightWidth = width - rightX - 40f * scale
        val inspHeight = 70f * scale
        drawRect(color = accentBoxColor, topLeft = Offset(rightX, startY), size = Size(rightWidth, inspHeight))
        drawRect(color = gridLineColor, topLeft = Offset(rightX, startY), size = Size(rightWidth, inspHeight), style = Stroke(1f * scale))
        drawText(
            textMeasurer = tm,
            text = "Goal / Inspiration:",
            topLeft = Offset(rightX + 12f * scale, startY + 8f * scale),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        drawLine(color = subtleLineColor, start = Offset(rightX + 12f * scale, startY + 38f * scale), end = Offset(rightX + rightWidth - 12f * scale, startY + 38f * scale), strokeWidth = 1f * scale)

        // Right Middle: Top Priorities
        val prioY = startY + inspHeight + 12f * scale
        val prioHeight = (endY - prioY) * 0.44f
        drawRect(color = gridLineColor, topLeft = Offset(rightX, prioY), size = Size(rightWidth, prioHeight), style = Stroke(1f * scale))
        drawText(
            textMeasurer = tm,
            text = "Top Priorities",
            topLeft = Offset(rightX + 12f * scale, prioY + 10f * scale),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 1..4) {
            val y = prioY + 36f * scale + (i - 1) * 28f * scale
            drawRect(color = gridLineColor, topLeft = Offset(rightX + 12f * scale, y), size = Size(12f * scale, 12f * scale), style = Stroke(1.2f * scale))
            drawLine(color = subtleLineColor, start = Offset(rightX + 32f * scale, y + 12f * scale), end = Offset(rightX + rightWidth - 12f * scale, y + 12f * scale), strokeWidth = 1f * scale)
        }

        // Right Bottom: Other Tasks & Notes
        val otherTasksY = prioY + prioHeight + 12f * scale
        drawRect(color = gridLineColor, topLeft = Offset(rightX, otherTasksY), size = Size(rightWidth, endY - otherTasksY), style = Stroke(1f * scale))
        drawText(
            textMeasurer = tm,
            text = "Other Tasks & Notes",
            topLeft = Offset(rightX + 12f * scale, otherTasksY + 10f * scale),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 0 until 6) {
            val y = otherTasksY + 36f * scale + i * 26f * scale
            drawCircle(color = gridLineColor, radius = 3.5f * scale, center = Offset(rightX + 16f * scale, y + 6f * scale), style = Stroke(1f * scale))
            drawLine(color = subtleLineColor, start = Offset(rightX + 28f * scale, y + 10f * scale), end = Offset(rightX + rightWidth - 12f * scale, y + 10f * scale), strokeWidth = 1f * scale)
        }
    }

    // 5. Goal Setting: My Goal box, Action Steps, Why, Motivation, Milestones
    private fun DrawScope.drawGoalSetting(width: Float, height: Float, scale: Float, tm: TextMeasurer) {
        drawText(
            textMeasurer = tm,
            text = "Goal Setting",
            topLeft = Offset(40f * scale, 30f * scale),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )

        drawText(
            textMeasurer = tm,
            text = "Target Date: _______________",
            topLeft = Offset(width - 240f * scale, 34f * scale),
            style = TextStyle(fontSize = 12.sp, color = subHeaderColor)
        )

        val topContentY = 70f * scale
        val myGoalHeight = 90f * scale

        // My Goal Box
        drawRect(color = accentBoxColor, topLeft = Offset(40f * scale, topContentY), size = Size(width - 80f * scale, myGoalHeight))
        drawRect(color = gridLineColor, topLeft = Offset(40f * scale, topContentY), size = Size(width - 80f * scale, myGoalHeight), style = Stroke(1.2f * scale))
        drawText(
            textMeasurer = tm,
            text = "MY GOAL:",
            topLeft = Offset(52f * scale, topContentY + 10f * scale),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 1..2) {
            val ly = topContentY + 36f * scale + i * 22f * scale
            drawLine(color = subtleLineColor, start = Offset(52f * scale, ly), end = Offset(width - 52f * scale, ly), strokeWidth = 1f * scale)
        }

        // Action Steps (Numbered 1 to 8)
        val stepsY = topContentY + myGoalHeight + 16f * scale
        val stepsHeight = height * 0.36f
        drawRect(color = gridLineColor, topLeft = Offset(40f * scale, stepsY), size = Size(width - 80f * scale, stepsHeight), style = Stroke(1.2f * scale))
        drawText(
            textMeasurer = tm,
            text = "ACTION STEPS",
            topLeft = Offset(52f * scale, stepsY + 10f * scale),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        val stepSpacing = (stepsHeight - 45f * scale) / 6f
        for (i in 1..6) {
            val y = stepsY + 36f * scale + (i - 1) * stepSpacing
            drawRect(color = gridLineColor, topLeft = Offset(52f * scale, y), size = Size(12f * scale, 12f * scale), style = Stroke(1.2f * scale))
            drawText(textMeasurer = tm, text = "$i.", topLeft = Offset(72f * scale, y), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = subHeaderColor))
            drawLine(color = subtleLineColor, start = Offset(90f * scale, y + 12f * scale), end = Offset(width - 56f * scale, y + 12f * scale), strokeWidth = 1f * scale)
        }

        // Why / Motivation & Milestones (2 columns at bottom)
        val bottomY = stepsY + stepsHeight + 16f * scale
        val colWidth = (width - 96f * scale) / 2f
        val bottomHeight = height - bottomY - 40f * scale

        // Column 1: Why & Motivation
        drawRect(color = gridLineColor, topLeft = Offset(40f * scale, bottomY), size = Size(colWidth, bottomHeight), style = Stroke(1f * scale))
        drawText(
            textMeasurer = tm,
            text = "WHY? (Motivation & Purpose)",
            topLeft = Offset(52f * scale, bottomY + 10f * scale),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 0 until 4) {
            val y = bottomY + 36f * scale + i * 24f * scale
            drawLine(color = subtleLineColor, start = Offset(52f * scale, y + 10f * scale), end = Offset(40f * scale + colWidth - 12f * scale, y + 10f * scale), strokeWidth = 1f * scale)
        }

        // Column 2: Milestones & Reward
        val col2X = 56f * scale + colWidth
        drawRect(color = gridLineColor, topLeft = Offset(col2X, bottomY), size = Size(colWidth, bottomHeight), style = Stroke(1f * scale))
        drawText(
            textMeasurer = tm,
            text = "MILESTONES & REWARD",
            topLeft = Offset(col2X + 12f * scale, bottomY + 10f * scale),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 0 until 4) {
            val y = bottomY + 36f * scale + i * 24f * scale
            drawCircle(color = gridLineColor, radius = 4f * scale, center = Offset(col2X + 16f * scale, y + 6f * scale), style = Stroke(1f * scale))
            drawLine(color = subtleLineColor, start = Offset(col2X + 28f * scale, y + 10f * scale), end = Offset(col2X + colWidth - 12f * scale, y + 10f * scale), strokeWidth = 1f * scale)
        }
    }

    // 6. Assignment Planner: Course, Due Date, Resources details & lined/dot-grid free space
    private fun DrawScope.drawAssignmentPlanner(width: Float, height: Float, scale: Float, tm: TextMeasurer) {
        drawText(
            textMeasurer = tm,
            text = "Assignment Planner",
            topLeft = Offset(40f * scale, 30f * scale),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )

        val splitX = width * 0.45f
        val startY = 70f * scale
        val endY = height - 40f * scale

        // Left Details column: Course, Due Date, Resources, Requirements
        val leftWidth = splitX - 50f * scale
        drawRect(color = accentBoxColor, topLeft = Offset(40f * scale, startY), size = Size(leftWidth, endY - startY))
        drawRect(color = gridLineColor, topLeft = Offset(40f * scale, startY), size = Size(leftWidth, endY - startY), style = Stroke(1.2f * scale))

        drawText(
            textMeasurer = tm,
            text = "DETAILS",
            topLeft = Offset(52f * scale, startY + 12f * scale),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )

        val detailLabels = listOf("Course:", "Due Date:", "Project / Title:", "Resources & Links:", "Key Requirements:")
        var curY = startY + 45f * scale
        detailLabels.forEach { label ->
            drawText(
                textMeasurer = tm,
                text = label,
                topLeft = Offset(52f * scale, curY),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = subHeaderColor)
            )
            drawLine(color = gridLineColor, start = Offset(52f * scale, curY + 22f * scale), end = Offset(40f * scale + leftWidth - 14f * scale, curY + 22f * scale), strokeWidth = 1f * scale)
            curY += 45f * scale
        }

        // Left Tasks checklist box
        drawText(
            textMeasurer = tm,
            text = "Task Breakdown:",
            topLeft = Offset(52f * scale, curY + 8f * scale),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        curY += 32f * scale
        for (i in 1..4) {
            drawRect(color = gridLineColor, topLeft = Offset(52f * scale, curY), size = Size(10f * scale, 10f * scale), style = Stroke(1f * scale))
            drawLine(color = subtleLineColor, start = Offset(68f * scale, curY + 10f * scale), end = Offset(40f * scale + leftWidth - 14f * scale, curY + 10f * scale), strokeWidth = 1f * scale)
            curY += 26f * scale
        }

        // Right Free Space Column (Lined & Dot-grid)
        val rightX = splitX + 10f * scale
        val rightWidth = width - rightX - 40f * scale
        drawRect(color = gridLineColor, topLeft = Offset(rightX, startY), size = Size(rightWidth, endY - startY), style = Stroke(1.2f * scale))
        drawText(
            textMeasurer = tm,
            text = "FREE SPACE (for sketches, notes, brainstorming)",
            topLeft = Offset(rightX + 14f * scale, startY + 12f * scale),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
        )

        // Draw note lines and dot-grid combination
        var lineY = startY + 45f * scale
        while (lineY < endY - 20f * scale) {
            drawLine(color = subtleLineColor, start = Offset(rightX + 14f * scale, lineY), end = Offset(rightX + rightWidth - 14f * scale, lineY), strokeWidth = 1f * scale)
            // Subtle dot in center
            drawCircle(color = gridLineColor, radius = 1.2f * scale, center = Offset(rightX + rightWidth / 2f, lineY))
            lineY += 28f * scale
        }
    }

    // 7. Mind Map: Central "Main Topic" box with branching connected subtopics
    private fun DrawScope.drawMindMap(width: Float, height: Float, scale: Float, tm: TextMeasurer) {
        drawText(
            textMeasurer = tm,
            text = "Mind Map",
            topLeft = Offset(40f * scale, 30f * scale),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )

        val centerX = width / 2f
        val centerY = height / 2f
        val mainBoxWidth = 160f * scale
        val mainBoxHeight = 60f * scale

        // Central Main Topic Box
        val mainRect = Rect(centerX - mainBoxWidth / 2f, centerY - mainBoxHeight / 2f, centerX + mainBoxWidth / 2f, centerY + mainBoxHeight / 2f)
        drawRect(color = Color(0xFFE0E7FF), topLeft = mainRect.topLeft, size = mainRect.size)
        drawRect(color = Color(0xFF6366F1), topLeft = mainRect.topLeft, size = mainRect.size, style = Stroke(2.2f * scale))
        drawText(
            textMeasurer = tm,
            text = "MAIN TOPIC",
            topLeft = Offset(centerX - 42f * scale, centerY - 10f * scale),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF312E81))
        )

        // Branching Subtopics (Top, Bottom, Left, Right)
        val subBoxW = 125f * scale
        val subBoxH = 46f * scale
        val offsets = listOf(
            Offset(centerX, centerY - 165f * scale), // Top
            Offset(centerX, centerY + 165f * scale), // Bottom
            Offset(centerX - 230f * scale, centerY), // Left
            Offset(centerX + 230f * scale, centerY)  // Right
        )

        offsets.forEachIndexed { i, target ->
            // Connecting branch line
            drawLine(color = Color(0xFF818CF8), start = Offset(centerX, centerY), end = target, strokeWidth = 2.0f * scale)
            // Subtopic box
            val subRect = Rect(target.x - subBoxW / 2f, target.y - subBoxH / 2f, target.x + subBoxW / 2f, target.y + subBoxH / 2f)
            drawRect(color = Color(0xFFF8FAFC), topLeft = subRect.topLeft, size = subRect.size)
            drawRect(color = gridLineColor, topLeft = subRect.topLeft, size = subRect.size, style = Stroke(1.4f * scale))
            drawText(
                textMeasurer = tm,
                text = "Subtopic ${i + 1}",
                topLeft = Offset(target.x - 30f * scale, target.y - 8f * scale),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = subHeaderColor)
            )

            // Radiating child nodes
            val childDir = if (target.x != centerX) (if (target.x < centerX) -1f else 1f) else 0f
            val childYDir = if (target.y != centerY) (if (target.y < centerY) -1f else 1f) else 0f
            for (c in listOf(-1, 1)) {
                val childX = target.x + (if (childDir != 0f) childDir * 110f * scale else c * 95f * scale)
                val childY = target.y + (if (childYDir != 0f) childYDir * 85f * scale else c * 75f * scale)
                drawLine(color = subtleLineColor, start = target, end = Offset(childX, childY), strokeWidth = 1.2f * scale)
                drawRect(color = gridLineColor, topLeft = Offset(childX - 35f * scale, childY - 15f * scale), size = Size(70f * scale, 30f * scale), style = Stroke(1f * scale))
            }
        }
    }

    // 8. Semester Overview: Weekly breakdown grid, My Schedule day/time grid, Goals/Notes
    private fun DrawScope.drawSemesterOverview(width: Float, height: Float, scale: Float, tm: TextMeasurer) {
        drawText(
            textMeasurer = tm,
            text = "Semester Overview",
            topLeft = Offset(40f * scale, 30f * scale),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )

        drawText(
            textMeasurer = tm,
            text = "Semester / Year: ____________",
            topLeft = Offset(width - 240f * scale, 34f * scale),
            style = TextStyle(fontSize = 12.sp, color = subHeaderColor)
        )

        val splitX = width * 0.46f
        val startY = 70f * scale
        val endY = height - 40f * scale

        // Left Column: Weekly Breakdown (Weeks 1 to 16)
        val leftWidth = splitX - 50f * scale
        drawRect(color = gridLineColor, topLeft = Offset(40f * scale, startY), size = Size(leftWidth, endY - startY), style = Stroke(1.2f * scale))
        drawText(
            textMeasurer = tm,
            text = "WEEKLY BREAKDOWN",
            topLeft = Offset(52f * scale, startY + 10f * scale),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        val weekStep = (endY - startY - 35f * scale) / 16f
        for (w in 1..16) {
            val y = startY + 30f * scale + (w - 1) * weekStep
            drawText(textMeasurer = tm, text = "W$w", topLeft = Offset(46f * scale, y), style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = subHeaderColor))
            drawLine(color = subtleLineColor, start = Offset(74f * scale, y + 6f * scale), end = Offset(40f * scale + leftWidth - 10f * scale, y + 6f * scale), strokeWidth = 1f * scale)
        }

        // Right Top: Class Schedule Grid (Mon-Fri)
        val rightX = splitX + 10f * scale
        val rightWidth = width - rightX - 40f * scale
        val schedHeight = (endY - startY) * 0.58f
        drawRect(color = gridLineColor, topLeft = Offset(rightX, startY), size = Size(rightWidth, schedHeight), style = Stroke(1.2f * scale))
        drawText(
            textMeasurer = tm,
            text = "MY SCHEDULE",
            topLeft = Offset(rightX + 12f * scale, startY + 10f * scale),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )

        // Mon - Fri table header
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
        val colW = rightWidth / days.size
        days.forEachIndexed { idx, d ->
            val colX = rightX + idx * colW
            drawText(textMeasurer = tm, text = d, topLeft = Offset(colX + 10f * scale, startY + 30f * scale), style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = subHeaderColor))
            if (idx > 0) drawLine(color = subtleLineColor, start = Offset(colX, startY + 28f * scale), end = Offset(colX, startY + schedHeight), strokeWidth = 1f * scale)
        }

        // Horizontal time slots
        for (t in 1..5) {
            val ty = startY + 30f * scale + t * ((schedHeight - 35f * scale) / 5f)
            drawLine(color = subtleLineColor, start = Offset(rightX, ty), end = Offset(rightX + rightWidth, ty), strokeWidth = 1f * scale)
        }

        // Right Bottom: Goals & Notes
        val goalsY = startY + schedHeight + 14f * scale
        drawRect(color = accentBoxColor, topLeft = Offset(rightX, goalsY), size = Size(rightWidth, endY - goalsY))
        drawRect(color = gridLineColor, topLeft = Offset(rightX, goalsY), size = Size(rightWidth, endY - goalsY), style = Stroke(1f * scale))
        drawText(
            textMeasurer = tm,
            text = "SEMESTER GOALS & IMPORTANT NOTES",
            topLeft = Offset(rightX + 12f * scale, goalsY + 10f * scale),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 0 until 4) {
            val y = goalsY + 34f * scale + i * 24f * scale
            drawLine(color = subtleLineColor, start = Offset(rightX + 14f * scale, y + 8f * scale), end = Offset(rightX + rightWidth - 14f * scale, y + 8f * scale), strokeWidth = 1f * scale)
        }
    }

    // 9. Monthly Planner: Calendar grid (Sun-Sat), Goals/Inspiration, Top Priorities
    private fun DrawScope.drawMonthlyPlanner(width: Float, height: Float, scale: Float, tm: TextMeasurer) {
        drawText(
            textMeasurer = tm,
            text = "Monthly Planner",
            topLeft = Offset(40f * scale, 30f * scale),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )

        drawText(
            textMeasurer = tm,
            text = "Month: ____________ Year: ______",
            topLeft = Offset(width - 260f * scale, 34f * scale),
            style = TextStyle(fontSize = 12.sp, color = subHeaderColor)
        )

        val startY = 70f * scale
        val calendarHeight = height * 0.60f
        val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val colWidth = (width - 80f * scale) / 7f
        val numWeeks = 5
        val rowHeight = calendarHeight / numWeeks

        // Calendar Grid Header
        daysOfWeek.forEachIndexed { idx, day ->
            drawText(
                textMeasurer = tm,
                text = day,
                topLeft = Offset(40f * scale + idx * colWidth + 10f * scale, startY - 18f * scale),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
            )
        }

        // Draw 5x7 Calendar Grid
        drawRect(color = gridLineColor, topLeft = Offset(40f * scale, startY), size = Size(width - 80f * scale, calendarHeight), style = Stroke(1.2f * scale))
        for (col in 1..6) {
            val x = 40f * scale + col * colWidth
            drawLine(color = gridLineColor, start = Offset(x, startY), end = Offset(x, startY + calendarHeight), strokeWidth = 1f * scale)
        }
        for (row in 1 until numWeeks) {
            val y = startY + row * rowHeight
            drawLine(color = gridLineColor, start = Offset(40f * scale, y), end = Offset(width - 40f * scale, y), strokeWidth = 1f * scale)
        }

        // Bottom two sections: Goals & Top Priorities
        val bottomY = startY + calendarHeight + 16f * scale
        val bottomColWidth = (width - 96f * scale) / 2f
        val bottomHeight = height - bottomY - 35f * scale

        // Goals / Inspiration
        drawRect(color = accentBoxColor, topLeft = Offset(40f * scale, bottomY), size = Size(bottomColWidth, bottomHeight))
        drawRect(color = gridLineColor, topLeft = Offset(40f * scale, bottomY), size = Size(bottomColWidth, bottomHeight), style = Stroke(1f * scale))
        drawText(
            textMeasurer = tm,
            text = "GOALS / INSPIRATION",
            topLeft = Offset(52f * scale, bottomY + 10f * scale),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 0 until 3) {
            val y = bottomY + 34f * scale + i * 24f * scale
            drawLine(color = subtleLineColor, start = Offset(52f * scale, y + 8f * scale), end = Offset(40f * scale + bottomColWidth - 12f * scale, y + 8f * scale), strokeWidth = 1f * scale)
        }

        // Top Priorities
        val col2X = 56f * scale + bottomColWidth
        drawRect(color = gridLineColor, topLeft = Offset(col2X, bottomY), size = Size(bottomColWidth, bottomHeight), style = Stroke(1f * scale))
        drawText(
            textMeasurer = tm,
            text = "TOP PRIORITIES",
            topLeft = Offset(col2X + 12f * scale, bottomY + 10f * scale),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 0 until 3) {
            val y = bottomY + 34f * scale + i * 24f * scale
            drawCircle(color = gridLineColor, radius = 3.5f * scale, center = Offset(col2X + 16f * scale, y + 6f * scale), style = Stroke(1f * scale))
            drawLine(color = subtleLineColor, start = Offset(col2X + 28f * scale, y + 8f * scale), end = Offset(col2X + bottomColWidth - 12f * scale, y + 8f * scale), strokeWidth = 1f * scale)
        }
    }

    // ==========================================
    // PURPOSE-BUILT THUMBNAIL RENDERERS
    // ==========================================

    fun drawThumbnail(
        scope: DrawScope,
        templateType: String,
        width: Float,
        height: Float,
        textMeasurer: TextMeasurer,
        isDark: Boolean = isDarkTheme
    ) {
        val prevDark = isDarkTheme
        isDarkTheme = isDark
        with(scope) {
            when (templateType) {
                "HABIT_TRACKER" -> drawHabitTrackerThumbnail(width, height, textMeasurer)
                "DAILY_GOALS" -> drawDailyGoalsThumbnail(width, height, textMeasurer)
                "WEEKLY_PLANNER" -> drawWeeklyPlannerThumbnail(width, height, textMeasurer)
                "DAILY_PLANNER" -> drawDailyPlannerThumbnail(width, height, textMeasurer)
                "GOAL_SETTING" -> drawGoalSettingThumbnail(width, height, textMeasurer)
                "ASSIGNMENT_PLANNER" -> drawAssignmentPlannerThumbnail(width, height, textMeasurer)
                "MIND_MAP" -> drawMindMapThumbnail(width, height, textMeasurer)
                "SEMESTER_OVERVIEW" -> drawSemesterOverviewThumbnail(width, height, textMeasurer)
                "MONTHLY_PLANNER" -> drawMonthlyPlannerThumbnail(width, height, textMeasurer)
                "CORNELL" -> drawCornellThumbnail(width, height, textMeasurer)
                "PLANNER" -> drawPlannerThumbnail(width, height, textMeasurer)
                "BLANK" -> drawBlankThumbnail(width, height)
                "LINED_WIDE" -> drawLinedThumbnail(width, height, isNarrow = false)
                "LINED_NARROW", "RULED" -> drawLinedThumbnail(width, height, isNarrow = true)
                "GRID_SMALL" -> drawGridThumbnail(width, height, isLarge = false)
                "GRID_LARGE" -> drawGridThumbnail(width, height, isLarge = true)
                "DOTTED" -> drawDottedThumbnail(width, height)
                "ISOMETRIC" -> drawIsometricThumbnail(width, height)
                "MUSIC" -> drawMusicThumbnail(width, height)
                else -> drawBlankThumbnail(width, height)
            }
        }
        isDarkTheme = prevDark
    }

    private fun DrawScope.drawHabitTrackerThumbnail(width: Float, height: Float, tm: TextMeasurer) {
        val s = width / 180f
        drawText(
            textMeasurer = tm,
            text = "HABIT TRACKER",
            topLeft = Offset(12f * s, 10f * s),
            style = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Black, color = headerColor, letterSpacing = 0.5.sp)
        )
        drawLine(
            color = gridLineColor,
            start = Offset(width - 50f * s, 15f * s),
            end = Offset(width - 12f * s, 15f * s),
            strokeWidth = 1f * s
        )

        val centerX = width * 0.38f
        val centerY = height * 0.44f
        val maxRadius = min(width * 0.28f, height * 0.24f)
        val numRings = 5
        val ringSpacing = maxRadius / (numRings + 1)

        drawCircle(
            color = Color(0x18F97316),
            radius = maxRadius,
            center = Offset(centerX, centerY)
        )

        for (i in 1..numRings) {
            drawCircle(
                color = gridLineColor,
                radius = (i + 1) * ringSpacing,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1f * s)
            )
        }

        drawCircle(color = paperBgColor, radius = ringSpacing, center = Offset(centerX, centerY))
        drawCircle(color = gridLineColor, radius = ringSpacing, center = Offset(centerX, centerY), style = Stroke(width = 1f * s))
        drawText(
            textMeasurer = tm,
            text = "31",
            topLeft = Offset(centerX - 4f * s, centerY - 4f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
        )

        for (i in 0 until 12) {
            val angle = (i * 30f - 90f) * (Math.PI / 180f)
            val innerX = centerX + ringSpacing * cos(angle).toFloat()
            val innerY = centerY + ringSpacing * sin(angle).toFloat()
            val outerX = centerX + maxRadius * cos(angle).toFloat()
            val outerY = centerY + maxRadius * sin(angle).toFloat()
            drawLine(color = subtleLineColor, start = Offset(innerX, innerY), end = Offset(outerX, outerY), strokeWidth = 0.8f * s)
        }

        val listStartX = width * 0.70f
        val listStartY = height * 0.22f
        drawText(
            textMeasurer = tm,
            text = "HABITS",
            topLeft = Offset(listStartX, listStartY - 10f * s),
            style = TextStyle(fontSize = 5.5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
        )
        for (i in 0 until 6) {
            val y = listStartY + i * 14f * s
            drawCircle(color = Color(0xFFF97316), radius = 2.5f * s, center = Offset(listStartX + 4f * s, y + 4f * s), style = Stroke(1f * s))
            drawLine(color = subtleLineColor, start = Offset(listStartX + 11f * s, y + 4f * s), end = Offset(width - 12f * s, y + 4f * s), strokeWidth = 1f * s)
        }

        val boxY = height - 32f * s
        val boxH = 22f * s
        drawRoundRect(
            color = Color(0x18F97316),
            topLeft = Offset(12f * s, boxY),
            size = Size(width - 24f * s, boxH),
            cornerRadius = CornerRadius(4f * s)
        )
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(12f * s, boxY),
            size = Size(width - 24f * s, boxH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(width = 1f * s)
        )
        drawLine(color = subtleLineColor, start = Offset(18f * s, boxY + 7f * s), end = Offset(width - 18f * s, boxY + 7f * s), strokeWidth = 1f * s)
        drawLine(color = subtleLineColor, start = Offset(18f * s, boxY + 15f * s), end = Offset(width - 32f * s, boxY + 15f * s), strokeWidth = 1f * s)
    }

    private fun DrawScope.drawDailyGoalsThumbnail(width: Float, height: Float, tm: TextMeasurer) {
        val s = width / 180f
        drawText(
            textMeasurer = tm,
            text = "DAILY GOALS",
            topLeft = Offset(12f * s, 10f * s),
            style = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Black, color = headerColor, letterSpacing = 0.5.sp)
        )

        val days = listOf("M", "T", "W", "T", "F", "S", "S")
        val dStartX = width - 64f * s
        days.forEachIndexed { idx, _ ->
            val cx = dStartX + idx * 8f * s
            val cy = 14f * s
            drawCircle(color = gridLineColor, radius = 3.2f * s, center = Offset(cx, cy), style = Stroke(0.8f * s))
        }

        val goalsTop = 26f * s
        val goalsH = height * 0.34f
        drawRoundRect(
            color = Color(0x103B82F6),
            topLeft = Offset(12f * s, goalsTop),
            size = Size(width - 24f * s, goalsH),
            cornerRadius = CornerRadius(4f * s)
        )
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(12f * s, goalsTop),
            size = Size(width - 24f * s, goalsH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "MY GOALS",
            topLeft = Offset(18f * s, goalsTop + 6f * s),
            style = TextStyle(fontSize = 5.5.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 1..4) {
            val y = goalsTop + 14f * s + (i - 1) * 11f * s
            drawRect(color = gridLineColor, topLeft = Offset(18f * s, y), size = Size(5.5f * s, 5.5f * s), style = Stroke(0.9f * s))
            drawLine(color = subtleLineColor, start = Offset(27f * s, y + 4f * s), end = Offset(width - 20f * s, y + 4f * s), strokeWidth = 1f * s)
        }

        val midTop = goalsTop + goalsH + 6f * s
        val midH = height * 0.22f
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(12f * s, midTop),
            size = Size(width - 24f * s, midH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "FOCUS & TASKS",
            topLeft = Offset(18f * s, midTop + 5f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
        )
        for (i in 1..3) {
            val y = midTop + 12f * s + (i - 1) * 9f * s
            drawCircle(color = gridLineColor, radius = 2f * s, center = Offset(20f * s, y + 3f * s), style = Stroke(0.8f * s))
            drawLine(color = subtleLineColor, start = Offset(27f * s, y + 3f * s), end = Offset(width - 20f * s, y + 3f * s), strokeWidth = 0.9f * s)
        }

        val botTop = midTop + midH + 6f * s
        val botH = height - botTop - 10f * s
        val leftW = (width - 28f * s) * 0.65f
        val rightW = (width - 28f * s) * 0.35f

        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(12f * s, botTop),
            size = Size(leftW, botH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "REFLECTIONS",
            topLeft = Offset(16f * s, botTop + 5f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
        )
        for (i in 1..2) {
            val ly = botTop + 11f * s + (i - 1) * 8f * s
            drawLine(color = subtleLineColor, start = Offset(16f * s, ly), end = Offset(12f * s + leftW - 6f * s, ly), strokeWidth = 0.8f * s)
        }

        val waterX = 14f * s + leftW
        drawRoundRect(
            color = Color(0x100284C7),
            topLeft = Offset(waterX, botTop),
            size = Size(rightW, botH),
            cornerRadius = CornerRadius(4f * s)
        )
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(waterX, botTop),
            size = Size(rightW, botH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "WATER",
            topLeft = Offset(waterX + 5f * s, botTop + 5f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
        )
        for (i in 0 until 6) {
            val col = i % 3
            val row = i / 3
            val gx = waterX + 7f * s + col * 7f * s
            val gy = botTop + 13f * s + row * 7f * s
            drawCircle(color = Color(0xFF38BDF8), radius = 2.2f * s, center = Offset(gx, gy), style = Stroke(0.9f * s))
        }
    }

    private fun DrawScope.drawWeeklyPlannerThumbnail(width: Float, height: Float, tm: TextMeasurer) {
        val s = width / 180f
        drawText(
            textMeasurer = tm,
            text = "WEEKLY PLANNER",
            topLeft = Offset(12f * s, 10f * s),
            style = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Black, color = headerColor, letterSpacing = 0.5.sp)
        )

        val splitX = width * 0.42f
        val topY = 24f * s
        val botY = height - 10f * s

        val leftH1 = (botY - topY) * 0.55f
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(12f * s, topY),
            size = Size(splitX - 16f * s, leftH1),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "TO DO",
            topLeft = Offset(16f * s, topY + 5f * s),
            style = TextStyle(fontSize = 5.5.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 1..5) {
            val y = topY + 12f * s + (i - 1) * 9f * s
            drawRect(color = gridLineColor, topLeft = Offset(16f * s, y), size = Size(4.5f * s, 4.5f * s), style = Stroke(0.8f * s))
            drawLine(color = subtleLineColor, start = Offset(24f * s, y + 3f * s), end = Offset(splitX - 8f * s, y + 3f * s), strokeWidth = 0.9f * s)
        }

        val deadTop = topY + leftH1 + 5f * s
        val leftH2 = botY - deadTop
        drawRoundRect(
            color = Color(0x103B82F6),
            topLeft = Offset(12f * s, deadTop),
            size = Size(splitX - 16f * s, leftH2),
            cornerRadius = CornerRadius(4f * s)
        )
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(12f * s, deadTop),
            size = Size(splitX - 16f * s, leftH2),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "DEADLINES",
            topLeft = Offset(16f * s, deadTop + 5f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 1..3) {
            val ly = deadTop + 12f * s + (i - 1) * 7f * s
            drawLine(color = subtleLineColor, start = Offset(16f * s, ly), end = Offset(splitX - 8f * s, ly), strokeWidth = 0.8f * s)
        }

        val rightX = splitX + 2f * s
        val rightW = width - rightX - 12f * s
        val days = listOf("MON", "TUE", "WED", "THU", "FRI", "WEEKEND")
        val dayH = (botY - topY) / days.size
        days.forEachIndexed { idx, day ->
            val dy = topY + idx * dayH
            drawRoundRect(
                color = gridLineColor,
                topLeft = Offset(rightX, dy),
                size = Size(rightW, dayH - 3f * s),
                cornerRadius = CornerRadius(3f * s),
                style = Stroke(0.9f * s)
            )
            drawText(
                textMeasurer = tm,
                text = day,
                topLeft = Offset(rightX + 5f * s, dy + 3f * s),
                style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
            )
            drawLine(
                color = subtleLineColor,
                start = Offset(rightX + 26f * s, dy + 7f * s),
                end = Offset(rightX + rightW - 5f * s, dy + 7f * s),
                strokeWidth = 0.8f * s
            )
        }
    }

    private fun DrawScope.drawDailyPlannerThumbnail(width: Float, height: Float, tm: TextMeasurer) {
        val s = width / 180f
        drawText(
            textMeasurer = tm,
            text = "DAILY PLANNER",
            topLeft = Offset(12f * s, 10f * s),
            style = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Black, color = headerColor, letterSpacing = 0.5.sp)
        )

        val splitX = width * 0.44f
        val topY = 24f * s
        val botY = height - 10f * s

        val schedW = splitX - 16f * s
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(12f * s, topY),
            size = Size(schedW, botY - topY),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "SCHEDULE",
            topLeft = Offset(16f * s, topY + 5f * s),
            style = TextStyle(fontSize = 5.5.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        val hours = listOf("8A", "10A", "12P", "2P", "4P", "6P", "8P")
        val hStep = (botY - topY - 16f * s) / hours.size
        hours.forEachIndexed { idx, hr ->
            val hy = topY + 14f * s + idx * hStep
            drawText(
                textMeasurer = tm,
                text = hr,
                topLeft = Offset(15f * s, hy),
                style = TextStyle(fontSize = 4.5.sp, fontWeight = FontWeight.SemiBold, color = subHeaderColor)
            )
            drawLine(
                color = subtleLineColor,
                start = Offset(29f * s, hy + 3f * s),
                end = Offset(12f * s + schedW - 5f * s, hy + 3f * s),
                strokeWidth = 0.8f * s
            )
        }

        val rightX = splitX + 2f * s
        val rightW = width - rightX - 12f * s
        val prioH = (botY - topY) * 0.45f

        drawRoundRect(
            color = Color(0x103B82F6),
            topLeft = Offset(rightX, topY),
            size = Size(rightW, prioH),
            cornerRadius = CornerRadius(4f * s)
        )
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(rightX, topY),
            size = Size(rightW, prioH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "PRIORITIES",
            topLeft = Offset(rightX + 5f * s, topY + 5f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 1..4) {
            val py = topY + 12f * s + (i - 1) * 9.5f * s
            drawRect(color = gridLineColor, topLeft = Offset(rightX + 5f * s, py), size = Size(4.5f * s, 4.5f * s), style = Stroke(0.8f * s))
            drawLine(color = subtleLineColor, start = Offset(rightX + 13f * s, py + 3f * s), end = Offset(rightX + rightW - 5f * s, py + 3f * s), strokeWidth = 0.8f * s)
        }

        val taskTop = topY + prioH + 5f * s
        val taskH = botY - taskTop
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(rightX, taskTop),
            size = Size(rightW, taskH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "OTHER TASKS",
            topLeft = Offset(rightX + 5f * s, taskTop + 5f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
        )
        for (i in 1..4) {
            val ty = taskTop + 12f * s + (i - 1) * 9.5f * s
            drawCircle(color = gridLineColor, radius = 2f * s, center = Offset(rightX + 7f * s, ty + 3f * s), style = Stroke(0.8f * s))
            drawLine(color = subtleLineColor, start = Offset(rightX + 13f * s, ty + 3f * s), end = Offset(rightX + rightW - 5f * s, ty + 3f * s), strokeWidth = 0.8f * s)
        }
    }

    private fun DrawScope.drawGoalSettingThumbnail(width: Float, height: Float, tm: TextMeasurer) {
        val s = width / 180f
        drawText(
            textMeasurer = tm,
            text = "GOAL SETTING",
            topLeft = Offset(12f * s, 10f * s),
            style = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Black, color = headerColor, letterSpacing = 0.5.sp)
        )

        val topY = 24f * s
        val botY = height - 10f * s

        val goalH = 26f * s
        drawRoundRect(
            color = Color(0x103B82F6),
            topLeft = Offset(12f * s, topY),
            size = Size(width - 24f * s, goalH),
            cornerRadius = CornerRadius(4f * s)
        )
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(12f * s, topY),
            size = Size(width - 24f * s, goalH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "MY GOAL",
            topLeft = Offset(16f * s, topY + 4f * s),
            style = TextStyle(fontSize = 5.5.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        drawLine(color = subtleLineColor, start = Offset(16f * s, topY + 14f * s), end = Offset(width - 16f * s, topY + 14f * s), strokeWidth = 1f * s)
        drawLine(color = subtleLineColor, start = Offset(16f * s, topY + 20f * s), end = Offset(width - 32f * s, topY + 20f * s), strokeWidth = 1f * s)

        val stepTop = topY + goalH + 6f * s
        val stepH = (botY - stepTop) * 0.52f
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(12f * s, stepTop),
            size = Size(width - 24f * s, stepH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "ACTION STEPS",
            topLeft = Offset(16f * s, stepTop + 5f * s),
            style = TextStyle(fontSize = 5.5.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 1..4) {
            val sy = stepTop + 13f * s + (i - 1) * 9.5f * s
            drawRect(color = gridLineColor, topLeft = Offset(16f * s, sy), size = Size(4.5f * s, 4.5f * s), style = Stroke(0.8f * s))
            drawLine(color = subtleLineColor, start = Offset(24f * s, sy + 3f * s), end = Offset(width - 16f * s, sy + 3f * s), strokeWidth = 0.9f * s)
        }

        val botSectionTop = stepTop + stepH + 6f * s
        val botSectionH = botY - botSectionTop
        val colW = (width - 28f * s) / 2f

        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(12f * s, botSectionTop),
            size = Size(colW, botSectionH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "WHY",
            topLeft = Offset(16f * s, botSectionTop + 4f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
        )
        for (i in 1..2) {
            val ly = botSectionTop + 10f * s + (i - 1) * 8f * s
            drawLine(color = subtleLineColor, start = Offset(16f * s, ly), end = Offset(12f * s + colW - 6f * s, ly), strokeWidth = 0.8f * s)
        }

        val mileX = 16f * s + colW
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(mileX, botSectionTop),
            size = Size(colW, botSectionH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "MILESTONES",
            topLeft = Offset(mileX + 4f * s, botSectionTop + 4f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
        )
        for (i in 1..2) {
            val ly = botSectionTop + 10f * s + (i - 1) * 8f * s
            drawCircle(color = gridLineColor, radius = 2f * s, center = Offset(mileX + 6f * s, ly), style = Stroke(0.8f * s))
            drawLine(color = subtleLineColor, start = Offset(mileX + 11f * s, ly), end = Offset(mileX + colW - 6f * s, ly), strokeWidth = 0.8f * s)
        }
    }

    private fun DrawScope.drawAssignmentPlannerThumbnail(width: Float, height: Float, tm: TextMeasurer) {
        val s = width / 180f
        drawText(
            textMeasurer = tm,
            text = "ASSIGNMENT PLANNER",
            topLeft = Offset(12f * s, 10f * s),
            style = TextStyle(fontSize = 6.5.sp, fontWeight = FontWeight.Black, color = headerColor, letterSpacing = 0.5.sp)
        )

        val splitX = width * 0.44f
        val topY = 24f * s
        val botY = height - 10f * s

        val leftW = splitX - 16f * s
        drawRoundRect(
            color = Color(0x103B82F6),
            topLeft = Offset(12f * s, topY),
            size = Size(leftW, botY - topY),
            cornerRadius = CornerRadius(4f * s)
        )
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(12f * s, topY),
            size = Size(leftW, botY - topY),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "DETAILS",
            topLeft = Offset(16f * s, topY + 5f * s),
            style = TextStyle(fontSize = 5.5.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        val fields = listOf("Course", "Due Date", "Tasks")
        fields.forEachIndexed { idx, fld ->
            val fy = topY + 14f * s + idx * 16f * s
            drawText(
                textMeasurer = tm,
                text = fld,
                topLeft = Offset(16f * s, fy),
                style = TextStyle(fontSize = 4.5.sp, fontWeight = FontWeight.SemiBold, color = subHeaderColor)
            )
            drawLine(color = subtleLineColor, start = Offset(16f * s, fy + 7f * s), end = Offset(12f * s + leftW - 6f * s, fy + 7f * s), strokeWidth = 0.8f * s)
        }
        for (i in 1..3) {
            val ty = topY + 66f * s + (i - 1) * 9f * s
            drawRect(color = gridLineColor, topLeft = Offset(16f * s, ty), size = Size(4f * s, 4f * s), style = Stroke(0.8f * s))
            drawLine(color = subtleLineColor, start = Offset(23f * s, ty + 2.5f * s), end = Offset(12f * s + leftW - 6f * s, ty + 2.5f * s), strokeWidth = 0.8f * s)
        }

        val rightX = splitX + 2f * s
        val rightW = width - rightX - 12f * s
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(rightX, topY),
            size = Size(rightW, botY - topY),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "FREE SPACE",
            topLeft = Offset(rightX + 6f * s, topY + 5f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
        )
        var ly = topY + 16f * s
        while (ly < botY - 8f * s) {
            drawLine(color = subtleLineColor, start = Offset(rightX + 6f * s, ly), end = Offset(rightX + rightW - 6f * s, ly), strokeWidth = 0.8f * s)
            drawCircle(color = gridLineColor, radius = 0.8f * s, center = Offset(rightX + rightW / 2f, ly))
            ly += 11f * s
        }
    }

    private fun DrawScope.drawMindMapThumbnail(width: Float, height: Float, tm: TextMeasurer) {
        val s = width / 180f
        drawText(
            textMeasurer = tm,
            text = "MIND MAP",
            topLeft = Offset(12f * s, 10f * s),
            style = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Black, color = headerColor, letterSpacing = 0.5.sp)
        )

        val cx = width / 2f
        val cy = height * 0.52f

        val mainW = 54f * s
        val mainH = 22f * s
        drawRoundRect(
            color = Color(0xFFEEF2FF),
            topLeft = Offset(cx - mainW / 2f, cy - mainH / 2f),
            size = Size(mainW, mainH),
            cornerRadius = CornerRadius(5f * s)
        )
        drawRoundRect(
            color = Color(0xFF6366F1),
            topLeft = Offset(cx - mainW / 2f, cy - mainH / 2f),
            size = Size(mainW, mainH),
            cornerRadius = CornerRadius(5f * s),
            style = Stroke(1.4f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "MAIN TOPIC",
            topLeft = Offset(cx - 18f * s, cy - 4f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF312E81))
        )

        val subW = 40f * s
        val subH = 16f * s
        val targets = listOf(
            Offset(cx, cy - 42f * s),
            Offset(cx, cy + 42f * s),
            Offset(cx - 52f * s, cy),
            Offset(cx + 52f * s, cy)
        )

        targets.forEachIndexed { i, tgt ->
            drawLine(color = Color(0xFF818CF8), start = Offset(cx, cy), end = tgt, strokeWidth = 1.2f * s)
            drawRoundRect(
                color = paperBgColor,
                topLeft = Offset(tgt.x - subW / 2f, tgt.y - subH / 2f),
                size = Size(subW, subH),
                cornerRadius = CornerRadius(3f * s)
            )
            drawRoundRect(
                color = gridLineColor,
                topLeft = Offset(tgt.x - subW / 2f, tgt.y - subH / 2f),
                size = Size(subW, subH),
                cornerRadius = CornerRadius(3f * s),
                style = Stroke(1f * s)
            )
            drawText(
                textMeasurer = tm,
                text = "Topic ${i + 1}",
                topLeft = Offset(tgt.x - 12f * s, tgt.y - 3.5f * s),
                style = TextStyle(fontSize = 4.5.sp, fontWeight = FontWeight.SemiBold, color = subHeaderColor)
            )
        }
    }

    private fun DrawScope.drawSemesterOverviewThumbnail(width: Float, height: Float, tm: TextMeasurer) {
        val s = width / 180f
        drawText(
            textMeasurer = tm,
            text = "SEMESTER OVERVIEW",
            topLeft = Offset(12f * s, 10f * s),
            style = TextStyle(fontSize = 6.5.sp, fontWeight = FontWeight.Black, color = headerColor, letterSpacing = 0.5.sp)
        )

        val splitX = width * 0.42f
        val topY = 24f * s
        val botY = height - 10f * s

        val leftW = splitX - 16f * s
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(12f * s, topY),
            size = Size(leftW, botY - topY),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "WEEKS",
            topLeft = Offset(16f * s, topY + 5f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        val wStep = (botY - topY - 14f * s) / 8f
        for (w in 1..8) {
            val wy = topY + 12f * s + (w - 1) * wStep
            drawText(
                textMeasurer = tm,
                text = "W$w",
                topLeft = Offset(15f * s, wy),
                style = TextStyle(fontSize = 4.5.sp, color = subHeaderColor)
            )
            drawLine(color = subtleLineColor, start = Offset(24f * s, wy + 3f * s), end = Offset(12f * s + leftW - 5f * s, wy + 3f * s), strokeWidth = 0.8f * s)
        }

        val rightX = splitX + 2f * s
        val rightW = width - rightX - 12f * s
        val schedH = (botY - topY) * 0.55f

        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(rightX, topY),
            size = Size(rightW, schedH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "SCHEDULE",
            topLeft = Offset(rightX + 5f * s, topY + 4f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        val dLetters = listOf("M", "T", "W", "T", "F")
        val colW = rightW / 5f
        dLetters.forEachIndexed { i, dl ->
            val dx = rightX + i * colW
            drawText(textMeasurer = tm, text = dl, topLeft = Offset(dx + 4f * s, topY + 11f * s), style = TextStyle(fontSize = 4.5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor))
            if (i > 0) drawLine(color = subtleLineColor, start = Offset(dx, topY + 10f * s), end = Offset(dx, topY + schedH), strokeWidth = 0.8f * s)
        }
        for (r in 1..4) {
            val ry = topY + 18f * s + (r - 1) * ((schedH - 20f * s) / 4f)
            drawLine(color = subtleLineColor, start = Offset(rightX, ry), end = Offset(rightX + rightW, ry), strokeWidth = 0.8f * s)
        }

        val goalTop = topY + schedH + 5f * s
        val goalH = botY - goalTop
        drawRoundRect(
            color = Color(0x103B82F6),
            topLeft = Offset(rightX, goalTop),
            size = Size(rightW, goalH),
            cornerRadius = CornerRadius(4f * s)
        )
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(rightX, goalTop),
            size = Size(rightW, goalH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "NOTES",
            topLeft = Offset(rightX + 5f * s, goalTop + 4f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 1..3) {
            val ly = goalTop + 10f * s + (i - 1) * 7f * s
            drawLine(color = subtleLineColor, start = Offset(rightX + 5f * s, ly), end = Offset(rightX + rightW - 5f * s, ly), strokeWidth = 0.8f * s)
        }
    }

    private fun DrawScope.drawMonthlyPlannerThumbnail(width: Float, height: Float, tm: TextMeasurer) {
        val s = width / 180f
        drawText(
            textMeasurer = tm,
            text = "MONTHLY PLANNER",
            topLeft = Offset(12f * s, 10f * s),
            style = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Black, color = headerColor, letterSpacing = 0.5.sp)
        )

        val topY = 24f * s
        val calH = (height - topY - 10f * s) * 0.60f
        val calW = width - 24f * s
        val colW = calW / 7f

        val days = listOf("S", "M", "T", "W", "T", "F", "S")
        days.forEachIndexed { i, d ->
            drawText(
                textMeasurer = tm,
                text = d,
                topLeft = Offset(12f * s + i * colW + 4f * s, topY),
                style = TextStyle(fontSize = 4.5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
            )
        }

        val gridTop = topY + 8f * s
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(12f * s, gridTop),
            size = Size(calW, calH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        for (c in 1..6) {
            val cx = 12f * s + c * colW
            drawLine(color = gridLineColor, start = Offset(cx, gridTop), end = Offset(cx, gridTop + calH), strokeWidth = 0.8f * s)
        }
        val rowH = calH / 5f
        for (r in 1..4) {
            val ry = gridTop + r * rowH
            drawLine(color = gridLineColor, start = Offset(12f * s, ry), end = Offset(12f * s + calW, ry), strokeWidth = 0.8f * s)
        }

        val botTop = gridTop + calH + 6f * s
        val botH = height - botTop - 10f * s
        val halfW = (width - 28f * s) / 2f

        drawRoundRect(
            color = Color(0x103B82F6),
            topLeft = Offset(12f * s, botTop),
            size = Size(halfW, botH),
            cornerRadius = CornerRadius(4f * s)
        )
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(12f * s, botTop),
            size = Size(halfW, botH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "GOALS",
            topLeft = Offset(16f * s, botTop + 4f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = headerColor)
        )
        for (i in 1..2) {
            val ly = botTop + 10f * s + (i - 1) * 8f * s
            drawLine(color = subtleLineColor, start = Offset(16f * s, ly), end = Offset(12f * s + halfW - 6f * s, ly), strokeWidth = 0.8f * s)
        }

        val prioX = 16f * s + halfW
        drawRoundRect(
            color = gridLineColor,
            topLeft = Offset(prioX, botTop),
            size = Size(halfW, botH),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
        drawText(
            textMeasurer = tm,
            text = "PRIORITIES",
            topLeft = Offset(prioX + 4f * s, botTop + 4f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
        )
        for (i in 1..2) {
            val ly = botTop + 10f * s + (i - 1) * 8f * s
            drawCircle(color = gridLineColor, radius = 2f * s, center = Offset(prioX + 6f * s, ly), style = Stroke(0.8f * s))
            drawLine(color = subtleLineColor, start = Offset(prioX + 11f * s, ly), end = Offset(prioX + halfW - 6f * s, ly), strokeWidth = 0.8f * s)
        }
    }

    private fun DrawScope.drawCornellThumbnail(width: Float, height: Float, tm: TextMeasurer) {
        val s = width / 180f
        val topH = height * 0.15f
        val botH = height * 0.18f
        val cueW = width * 0.32f

        drawLine(color = gridLineColor, start = Offset(10f * s, topH), end = Offset(width - 10f * s, topH), strokeWidth = 1.2f * s)
        drawText(
            textMeasurer = tm,
            text = "CORNELL NOTES",
            topLeft = Offset(12f * s, 8f * s),
            style = TextStyle(fontSize = 6.5.sp, fontWeight = FontWeight.Black, color = headerColor)
        )

        drawLine(color = gridLineColor, start = Offset(cueW, topH), end = Offset(cueW, height - botH), strokeWidth = 1.2f * s)
        drawText(
            textMeasurer = tm,
            text = "CUES",
            topLeft = Offset(14f * s, topH + 6f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
        )

        drawText(
            textMeasurer = tm,
            text = "NOTES",
            topLeft = Offset(cueW + 8f * s, topH + 6f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
        )
        var ly = topH + 18f * s
        while (ly < height - botH - 6f * s) {
            drawLine(color = subtleLineColor, start = Offset(cueW + 8f * s, ly), end = Offset(width - 12f * s, ly), strokeWidth = 0.9f * s)
            ly += 11f * s
        }

        drawLine(color = gridLineColor, start = Offset(10f * s, height - botH), end = Offset(width - 10f * s, height - botH), strokeWidth = 1.2f * s)
        drawText(
            textMeasurer = tm,
            text = "SUMMARY",
            topLeft = Offset(14f * s, height - botH + 5f * s),
            style = TextStyle(fontSize = 5.sp, fontWeight = FontWeight.Bold, color = subHeaderColor)
        )
        drawLine(color = subtleLineColor, start = Offset(14f * s, height - botH + 14f * s), end = Offset(width - 14f * s, height - botH + 14f * s), strokeWidth = 0.9f * s)
    }

    private fun DrawScope.drawPlannerThumbnail(width: Float, height: Float, tm: TextMeasurer) {
        drawDailyPlannerThumbnail(width, height, tm)
    }

    private fun DrawScope.drawBlankThumbnail(width: Float, height: Float) {
        val s = width / 180f
        val corner = 16f * s
        val path = Path().apply {
            moveTo(width - 10f * s - corner, 10f * s)
            lineTo(width - 10f * s, 10f * s + corner)
            lineTo(width - 10f * s - corner, 10f * s + corner)
            close()
        }
        drawPath(path, color = Color(0x2294A3B8))
        drawRoundRect(
            color = Color(0x33CBD5E1),
            topLeft = Offset(10f * s, 10f * s),
            size = Size(width - 20f * s, height - 20f * s),
            cornerRadius = CornerRadius(4f * s),
            style = Stroke(1f * s)
        )
    }

    private fun DrawScope.drawLinedThumbnail(width: Float, height: Float, isNarrow: Boolean) {
        val s = width / 180f
        val spacing = if (isNarrow) 9f * s else 13f * s
        val marginX = 26f * s
        drawLine(color = Color(0x55EF4444), start = Offset(marginX, 10f * s), end = Offset(marginX, height - 10f * s), strokeWidth = 1f * s)

        var y = 20f * s
        while (y < height - 14f * s) {
            drawLine(color = gridLineColor, start = Offset(12f * s, y), end = Offset(width - 12f * s, y), strokeWidth = 0.9f * s)
            y += spacing
        }
    }

    private fun DrawScope.drawGridThumbnail(width: Float, height: Float, isLarge: Boolean) {
        val s = width / 180f
        val step = if (isLarge) 14f * s else 9f * s
        var x = 10f * s
        while (x < width - 10f * s) {
            drawLine(color = subtleLineColor, start = Offset(x, 10f * s), end = Offset(x, height - 10f * s), strokeWidth = 0.8f * s)
            x += step
        }
        var y = 10f * s
        while (y < height - 10f * s) {
            drawLine(color = subtleLineColor, start = Offset(10f * s, y), end = Offset(width - 10f * s, y), strokeWidth = 0.8f * s)
            y += step
        }
    }

    private fun DrawScope.drawDottedThumbnail(width: Float, height: Float) {
        val s = width / 180f
        val step = 11f * s
        var x = 14f * s
        while (x < width - 12f * s) {
            var y = 14f * s
            while (y < height - 12f * s) {
                drawCircle(color = Color(0x6664748B), radius = 1.1f * s, center = Offset(x, y))
                y += step
            }
            x += step
        }
    }

    private fun DrawScope.drawIsometricThumbnail(width: Float, height: Float) {
        val s = width / 180f
        val step = 12f * s
        val rowH = step * 0.866f
        var y = 14f * s
        var row = 0
        while (y < height - 12f * s) {
            val xOff = if (row % 2 == 0) 14f * s else 14f * s + step / 2f
            var x = xOff
            while (x < width - 12f * s) {
                drawCircle(color = Color(0x6664748B), radius = 1.1f * s, center = Offset(x, y))
                x += step
            }
            y += rowH
            row++
        }
    }

    private fun DrawScope.drawMusicThumbnail(width: Float, height: Float) {
        val s = width / 180f
        val staffSpacing = 3.2f * s
        val staffSetStep = (height - 30f * s) / 4f
        for (set in 0..3) {
            val startY = 18f * s + set * staffSetStep
            for (line in 0..4) {
                val y = startY + line * staffSpacing
                drawLine(color = Color(0x55334155), start = Offset(12f * s, y), end = Offset(width - 12f * s, y), strokeWidth = 0.9f * s)
            }
            drawLine(color = Color(0x55334155), start = Offset(12f * s, startY), end = Offset(12f * s, startY + 4 * staffSpacing), strokeWidth = 1.2f * s)
            drawLine(color = Color(0x44334155), start = Offset(width / 2f, startY), end = Offset(width / 2f, startY + 4 * staffSpacing), strokeWidth = 0.8f * s)
            drawLine(color = Color(0x55334155), start = Offset(width - 12f * s, startY), end = Offset(width - 12f * s, startY + 4 * staffSpacing), strokeWidth = 1.2f * s)
        }
    }
}
