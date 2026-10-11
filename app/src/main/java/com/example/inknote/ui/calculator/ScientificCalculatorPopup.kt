package com.example.inknote.ui.calculator

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Session-scoped state for the Casio fx-991ES Plus calculator.
 * Persists while the popup is closed and reopened within the same session.
 */
object CalculatorSession {
    var expression by mutableStateOf("")
    var resultText by mutableStateOf("0")
    var resultFraction by mutableStateOf<Fraction?>(null)
    var isShowingDecimal by mutableStateOf(false)
    var angleMode by mutableStateOf(AngleMode.DEG)
    var isShiftActive by mutableStateOf(false)
    var isHypActive by mutableStateOf(false)
    var isStatModeActive by mutableStateOf(false)
    var memoryValue by mutableDoubleStateOf(0.0)
    var lastAns by mutableDoubleStateOf(0.0)
    var isNaturalDisplay by mutableStateOf(true) // Natural-V.P.A.M. textbook display vs Linear display
    var dragOffsetX by mutableFloatStateOf(32f)
    var dragOffsetY by mutableFloatStateOf(64f)

    fun evaluate() {
        if (expression.isBlank()) {
            resultText = "0"
            resultFraction = null
            isShowingDecimal = false
            return
        }
        try {
            val res = ScientificMathEngine.evaluate(expression, angleMode, lastAns)
            lastAns = res
            val frac = Fraction.fromDouble(res, maxDenominator = 10000L)
            resultFraction = if (frac != null && frac.denominator > 1L) frac else null
            isShowingDecimal = false
            resultText = ScientificMathEngine.formatNumber(res)
        } catch (e: Exception) {
            resultFraction = null
            isShowingDecimal = false
            resultText = if (e.message?.contains("Math") == true || e is ArithmeticException) {
                "Math ERROR"
            } else {
                "Syntax ERROR"
            }
        }
    }

    fun toggleFractionDecimal() {
        if (resultText == "Math ERROR" || resultText == "Syntax ERROR") return
        if (resultFraction != null) {
            isShowingDecimal = !isShowingDecimal
        } else {
            // Attempt to turn current numeric result into fraction
            val d = resultText.replace("×", "").replace("10", "").toDoubleOrNull()
            if (d != null) {
                val frac = Fraction.fromDouble(d, maxDenominator = 10000L)
                if (frac != null && frac.denominator > 1L) {
                    resultFraction = frac
                    isShowingDecimal = false
                }
            }
        }
    }

    fun deleteToken() {
        if (expression.isEmpty()) return
        val tokens = listOf(
            "asinh(", "acosh(", "atanh(",
            "sinh(", "cosh(", "tanh(",
            "asin(", "acos(", "atan(",
            "sin(", "cos(", "tan(",
            "sqrt(", "cbrt(", "root(", "frac(",
            "logbase(", "log(", "ln(",
            "*10^", "10^", "e^", "Ans"
        )
        for (token in tokens) {
            if (expression.endsWith(token)) {
                expression = expression.removeSuffix(token)
                return
            }
        }
        expression = expression.dropLast(1)
    }

    fun allClear() {
        expression = ""
        resultText = "0"
        resultFraction = null
        isShowingDecimal = false
        isShiftActive = false
        isHypActive = false
    }

    fun evaluateStat(action: StatAction) {
        try {
            val cleaned = expression.replace(",", " ").trim()
            val numbers = cleaned.split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }
            if (numbers.isEmpty()) {
                resultText = "STAT ERROR"
                resultFraction = null
                return
            }
            val res = when (action) {
                StatAction.MEAN -> ScientificMathEngine.calculateMean(numbers)
                StatAction.POP_STD_DEV -> ScientificMathEngine.calculateStdDev(numbers, isSample = false)
                StatAction.SAMPLE_STD_DEV -> ScientificMathEngine.calculateStdDev(numbers, isSample = true)
                StatAction.SUM -> ScientificMathEngine.calculateSum(numbers)
                StatAction.COUNT -> numbers.size.toDouble()
            }
            lastAns = res
            resultFraction = null
            resultText = ScientificMathEngine.formatNumber(res)
        } catch (_: Exception) {
            resultText = "STAT ERROR"
            resultFraction = null
        }
    }
}

enum class StatAction {
    MEAN, POP_STD_DEV, SAMPLE_STD_DEV, SUM, COUNT
}

/**
 * Floating draggable scientific calculator modeled closely on the Casio fx-991ES Plus.
 * Includes Natural-V.P.A.M. two-line textbook display, stacked fractions, and complete scientific functions.
 */
@Composable
fun ScientificCalculatorPopup(
    isOpen: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Tapping outside closes the popup
                detectTapGestures { onClose() }
            }
    ) {
        Surface(
            modifier = modifier
                .offset {
                    IntOffset(
                        CalculatorSession.dragOffsetX.roundToInt(),
                        CalculatorSession.dragOffsetY.roundToInt()
                    )
                }
                .width(360.dp)
                .wrapContentHeight()
                .padding(6.dp)
                .shadow(20.dp, RoundedCornerShape(18.dp))
                .pointerInput(Unit) {
                    // Consume clicks inside popup so backdrop tap is not triggered
                    detectTapGestures { }
                },
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF1E232A), // Casio dark casing
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                // 1. Header Bar: Model Name, Drag Handle, Angle Mode & Close (X)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    CalculatorSession.dragOffsetX += dragAmount.x
                                    CalculatorSession.dragOffsetY += dragAmount.y
                                }
                            }
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Drag Inkstead Calculator",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "INKSTEAD CALCULATOR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Angle mode badge (DEG / RAD / GRA)
                        Surface(
                            onClick = {
                                CalculatorSession.angleMode = when (CalculatorSession.angleMode) {
                                    AngleMode.DEG -> AngleMode.RAD
                                    AngleMode.RAD -> AngleMode.GRA
                                    AngleMode.GRA -> AngleMode.DEG
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0F172A)
                        ) {
                            Text(
                                text = CalculatorSession.angleMode.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Close button (X)
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Calculator",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Natural-V.P.A.M. Two-Line LCD Display
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFC4CEC2), // Authentic Casio Natural-V.P.A.M. LCD screen
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF0F172A))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Status indicator row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (CalculatorSession.isShiftActive) {
                                    Text("S", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFFB45309))
                                }
                                if (CalculatorSession.isHypActive) {
                                    Text("HYP", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E293B))
                                }
                                if (CalculatorSession.memoryValue != 0.0) {
                                    Text("M", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E293B))
                                }
                                if (CalculatorSession.isStatModeActive) {
                                    Text("STAT", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E293B))
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    when (CalculatorSession.angleMode) {
                                        AngleMode.DEG -> "D"
                                        AngleMode.RAD -> "R"
                                        AngleMode.GRA -> "G"
                                    },
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                // Natural vs Linear display toggle indicator
                                Text(
                                    if (CalculatorSession.isNaturalDisplay) "[Math]" else "[Line]",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.clickable {
                                        CalculatorSession.isNaturalDisplay = !CalculatorSession.isNaturalDisplay
                                    }
                                )
                            }
                        }

                        // Input Line (Top line showing natural expression)
                        val displayExpression = if (CalculatorSession.expression.isBlank()) {
                            "0"
                        } else if (CalculatorSession.isNaturalDisplay) {
                            formatNaturalText(CalculatorSession.expression)
                        } else {
                            CalculatorSession.expression
                        }

                        Text(
                            text = displayExpression,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Result Line (Bottom line: supports stacked fraction or decimal)
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            val frac = CalculatorSession.resultFraction
                            if (CalculatorSession.isNaturalDisplay && frac != null && !CalculatorSession.isShowingDecimal) {
                                // Natural Stacked Fraction Layout (Numerator / horizontal bar / Denominator)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(
                                        text = frac.numerator.toString(),
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        lineHeight = 13.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(maxOf(30.dp, (frac.toString().length * 10).dp))
                                            .height(1.5.dp)
                                            .background(Color(0xFF0F172A))
                                    )
                                    Text(
                                        text = frac.denominator.toString(),
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        lineHeight = 13.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = CalculatorSession.resultText,
                                    fontSize = 20.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Optional STAT Mode Toolbar (mean, std dev, sum, n)
                AnimatedVisibility(visible = CalculatorSession.isStatModeActive) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Text(
                            "STAT Mode: enter numbers separated by comma or space",
                            fontSize = 9.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            CalcKey(text = "x̄ (Mean)", bgColor = Color(0xFF334155), textColor = Color(0xFFA5B4FC), modifier = Modifier.weight(1f)) {
                                CalculatorSession.evaluateStat(StatAction.MEAN)
                            }
                            CalcKey(text = "σ (Pop)", bgColor = Color(0xFF334155), textColor = Color(0xFFA5B4FC), modifier = Modifier.weight(1f)) {
                                CalculatorSession.evaluateStat(StatAction.POP_STD_DEV)
                            }
                            CalcKey(text = "s (Sample)", bgColor = Color(0xFF334155), textColor = Color(0xFFA5B4FC), modifier = Modifier.weight(1f)) {
                                CalculatorSession.evaluateStat(StatAction.SAMPLE_STD_DEV)
                            }
                            CalcKey(text = "Σx", bgColor = Color(0xFF334155), textColor = Color(0xFFA5B4FC), modifier = Modifier.weight(0.7f)) {
                                CalculatorSession.evaluateStat(StatAction.SUM)
                            }
                            CalcKey(text = "n", bgColor = Color(0xFF334155), textColor = Color(0xFFA5B4FC), modifier = Modifier.weight(0.6f)) {
                                CalculatorSession.evaluateStat(StatAction.COUNT)
                            }
                        }
                    }
                }

                // 3. Casio fx-991ES Plus Keypad Layout
                // Row 1: SHIFT, hyp, S<=>D, STAT, DEL, AC
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CalcKey(
                        text = "SHIFT",
                        isShift = CalculatorSession.isShiftActive,
                        bgColor = if (CalculatorSession.isShiftActive) Color(0xFFD97706) else Color(0xFF475569),
                        textColor = Color(0xFFFDE68A),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        CalculatorSession.isShiftActive = !CalculatorSession.isShiftActive
                    }

                    CalcKey(
                        text = if (CalculatorSession.isHypActive) "HYP*" else "hyp",
                        bgColor = if (CalculatorSession.isHypActive) Color(0xFF6366F1) else Color(0xFF334155),
                        textColor = Color.White,
                        modifier = Modifier.weight(0.9f)
                    ) {
                        CalculatorSession.isHypActive = !CalculatorSession.isHypActive
                    }

                    CalcKey(
                        text = "S<=>D",
                        bgColor = Color(0xFF334155),
                        textColor = Color(0xFF38BDF8),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        CalculatorSession.toggleFractionDecimal()
                    }

                    CalcKey(
                        text = if (CalculatorSession.isStatModeActive) "STAT*" else "STAT",
                        bgColor = if (CalculatorSession.isStatModeActive) Color(0xFF0D9488) else Color(0xFF334155),
                        textColor = Color(0xFF5EEAD4),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        CalculatorSession.isStatModeActive = !CalculatorSession.isStatModeActive
                    }

                    CalcKey(
                        text = "DEL",
                        bgColor = Color(0xFFDC2626),
                        textColor = Color.White,
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.deleteToken()
                    }

                    CalcKey(
                        text = "AC",
                        bgColor = Color(0xFFEA580C),
                        textColor = Color.White,
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.allClear()
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 2: ■/□ (fraction), √, x², xʸ, log, ln
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CalcKey(
                        text = if (CalculatorSession.isShiftActive) "d/c" else "■/□",
                        subText = "frac",
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.expression += "frac("
                        CalculatorSession.isShiftActive = false
                    }

                    CalcKey(
                        text = if (CalculatorSession.isShiftActive) "∛" else "√",
                        subText = if (CalculatorSession.isShiftActive) "cbrt" else "sqrt",
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.expression += if (CalculatorSession.isShiftActive) "cbrt(" else "sqrt("
                        CalculatorSession.isShiftActive = false
                    }

                    CalcKey(
                        text = if (CalculatorSession.isShiftActive) "x³" else "x²",
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.expression += if (CalculatorSession.isShiftActive) "^3" else "^2"
                        CalculatorSession.isShiftActive = false
                    }

                    CalcKey(
                        text = if (CalculatorSession.isShiftActive) "ⁿ√" else "xʸ",
                        subText = if (CalculatorSession.isShiftActive) "root" else "^",
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.expression += if (CalculatorSession.isShiftActive) "root(" else "^"
                        CalculatorSession.isShiftActive = false
                    }

                    CalcKey(
                        text = if (CalculatorSession.isShiftActive) "10ˣ" else "log",
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.expression += if (CalculatorSession.isShiftActive) "*10^" else "log("
                        CalculatorSession.isShiftActive = false
                    }

                    CalcKey(
                        text = if (CalculatorSession.isShiftActive) "eˣ" else "ln",
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.expression += if (CalculatorSession.isShiftActive) "e^" else "ln("
                        CalculatorSession.isShiftActive = false
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 3: (−), log_b / comma, sin, cos, tan, x! / %
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CalcKey(
                        text = "(−)",
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.expression += "-"
                    }

                    CalcKey(
                        text = if (CalculatorSession.isShiftActive) "," else "log_b",
                        subText = if (CalculatorSession.isShiftActive) "comma" else "base",
                        modifier = Modifier.weight(1f)
                    ) {
                        if (CalculatorSession.isShiftActive) {
                            CalculatorSession.expression += ","
                        } else {
                            CalculatorSession.expression += "logbase("
                        }
                        CalculatorSession.isShiftActive = false
                    }

                    CalcKey(
                        text = when {
                            CalculatorSession.isHypActive && CalculatorSession.isShiftActive -> "asinh"
                            CalculatorSession.isHypActive -> "sinh"
                            CalculatorSession.isShiftActive -> "sin⁻¹"
                            else -> "sin"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.expression += when {
                            CalculatorSession.isHypActive && CalculatorSession.isShiftActive -> "asinh("
                            CalculatorSession.isHypActive -> "sinh("
                            CalculatorSession.isShiftActive -> "asin("
                            else -> "sin("
                        }
                        CalculatorSession.isShiftActive = false
                        CalculatorSession.isHypActive = false
                    }

                    CalcKey(
                        text = when {
                            CalculatorSession.isHypActive && CalculatorSession.isShiftActive -> "acosh"
                            CalculatorSession.isHypActive -> "cosh"
                            CalculatorSession.isShiftActive -> "cos⁻¹"
                            else -> "cos"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.expression += when {
                            CalculatorSession.isHypActive && CalculatorSession.isShiftActive -> "acosh("
                            CalculatorSession.isHypActive -> "cosh("
                            CalculatorSession.isShiftActive -> "acos("
                            else -> "cos("
                        }
                        CalculatorSession.isShiftActive = false
                        CalculatorSession.isHypActive = false
                    }

                    CalcKey(
                        text = when {
                            CalculatorSession.isHypActive && CalculatorSession.isShiftActive -> "atanh"
                            CalculatorSession.isHypActive -> "tanh"
                            CalculatorSession.isShiftActive -> "tan⁻¹"
                            else -> "tan"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.expression += when {
                            CalculatorSession.isHypActive && CalculatorSession.isShiftActive -> "atanh("
                            CalculatorSession.isHypActive -> "tanh("
                            CalculatorSession.isShiftActive -> "atan("
                            else -> "tan("
                        }
                        CalculatorSession.isShiftActive = false
                        CalculatorSession.isHypActive = false
                    }

                    CalcKey(
                        text = if (CalculatorSession.isShiftActive) "%" else "x!",
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.expression += if (CalculatorSession.isShiftActive) "%" else "!"
                        CalculatorSession.isShiftActive = false
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 4: 7, 8, 9, (, ), M+ / M-
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CalcKey(text = "7", isNumber = true, modifier = Modifier.weight(1f)) { CalculatorSession.expression += "7" }
                    CalcKey(text = "8", isNumber = true, modifier = Modifier.weight(1f)) { CalculatorSession.expression += "8" }
                    CalcKey(text = "9", isNumber = true, modifier = Modifier.weight(1f)) { CalculatorSession.expression += "9" }
                    CalcKey(text = "(", modifier = Modifier.weight(1f)) { CalculatorSession.expression += "(" }
                    CalcKey(text = ")", modifier = Modifier.weight(1f)) { CalculatorSession.expression += ")" }
                    CalcKey(
                        text = if (CalculatorSession.isShiftActive) "M-" else "M+",
                        subText = "M",
                        modifier = Modifier.weight(1f)
                    ) {
                        try {
                            val v = ScientificMathEngine.evaluate(
                                CalculatorSession.expression.ifBlank { CalculatorSession.resultText },
                                CalculatorSession.angleMode,
                                CalculatorSession.lastAns
                            )
                            if (CalculatorSession.isShiftActive) {
                                CalculatorSession.memoryValue -= v
                            } else {
                                CalculatorSession.memoryValue += v
                            }
                        } catch (_: Exception) {}
                        CalculatorSession.isShiftActive = false
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 5: 4, 5, 6, ×, ÷, MR / MC
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CalcKey(text = "4", isNumber = true, modifier = Modifier.weight(1f)) { CalculatorSession.expression += "4" }
                    CalcKey(text = "5", isNumber = true, modifier = Modifier.weight(1f)) { CalculatorSession.expression += "5" }
                    CalcKey(text = "6", isNumber = true, modifier = Modifier.weight(1f)) { CalculatorSession.expression += "6" }
                    CalcKey(text = "×", modifier = Modifier.weight(1f)) { CalculatorSession.expression += "×" }
                    CalcKey(text = "÷", modifier = Modifier.weight(1f)) { CalculatorSession.expression += "÷" }
                    CalcKey(
                        text = if (CalculatorSession.isShiftActive) "MC" else "MR",
                        modifier = Modifier.weight(1f)
                    ) {
                        if (CalculatorSession.isShiftActive) {
                            CalculatorSession.memoryValue = 0.0
                        } else {
                            CalculatorSession.expression += ScientificMathEngine.formatNumber(CalculatorSession.memoryValue)
                        }
                        CalculatorSession.isShiftActive = false
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 6: 1, 2, 3, +, −, π / e
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CalcKey(text = "1", isNumber = true, modifier = Modifier.weight(1f)) { CalculatorSession.expression += "1" }
                    CalcKey(text = "2", isNumber = true, modifier = Modifier.weight(1f)) { CalculatorSession.expression += "2" }
                    CalcKey(text = "3", isNumber = true, modifier = Modifier.weight(1f)) { CalculatorSession.expression += "3" }
                    CalcKey(text = "+", modifier = Modifier.weight(1f)) { CalculatorSession.expression += "+" }
                    CalcKey(text = "−", modifier = Modifier.weight(1f)) { CalculatorSession.expression += "−" }
                    CalcKey(
                        text = if (CalculatorSession.isShiftActive) "e" else "π",
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.expression += if (CalculatorSession.isShiftActive) "e" else "π"
                        CalculatorSession.isShiftActive = false
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 7: 0, ., ×10ˣ, Ans, nCr / nPr, =
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CalcKey(text = "0", isNumber = true, modifier = Modifier.weight(1f)) { CalculatorSession.expression += "0" }
                    CalcKey(text = ".", isNumber = true, modifier = Modifier.weight(1f)) { CalculatorSession.expression += "." }
                    CalcKey(text = "×10ˣ", modifier = Modifier.weight(1.1f)) {
                        CalculatorSession.expression += "*10^"
                    }
                    CalcKey(text = "Ans", modifier = Modifier.weight(1f)) {
                        CalculatorSession.expression += "Ans"
                    }
                    CalcKey(
                        text = if (CalculatorSession.isShiftActive) "nPr" else "nCr",
                        modifier = Modifier.weight(1f)
                    ) {
                        CalculatorSession.expression += if (CalculatorSession.isShiftActive) "P" else "C"
                        CalculatorSession.isShiftActive = false
                    }
                    CalcKey(
                        text = "=",
                        bgColor = Color(0xFF0284C7),
                        textColor = Color.White,
                        modifier = Modifier.weight(1.2f)
                    ) {
                        CalculatorSession.evaluate()
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcKey(
    text: String,
    subText: String? = null,
    isShift: Boolean = false,
    isNumber: Boolean = false,
    bgColor: Color = if (isNumber) Color(0xFF334155) else Color(0xFF1E293B),
    textColor: Color = if (isNumber) Color.White else Color(0xFFE2E8F0),
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569).copy(alpha = 0.5f)),
        modifier = modifier.height(34.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = if (text.length > 4) 9.sp else if (text.length > 2) 11.sp else 13.sp,
                fontWeight = if (isNumber) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Formats mathematical input to natural textbook notation for the Natural-V.P.A.M. LCD display.
 */
private fun formatNaturalText(expr: String): String {
    val sups = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '-' to '⁻', '+' to '⁺'
    )
    var s = expr
        .replace("sqrt(", "√(")
        .replace("cbrt(", "∛(")
        .replace("root(", "ⁿ√(")
        .replace("frac(", "■/□(")
        .replace("asin(", "sin⁻¹(")
        .replace("acos(", "cos⁻¹(")
        .replace("atan(", "tan⁻¹(")
        .replace("asinh(", "sinh⁻¹(")
        .replace("acosh(", "cosh⁻¹(")
        .replace("atanh(", "tanh⁻¹(")
        .replace("*10^", " × 10^")
        .replace("*", "×")
        .replace("/", "÷")
        .replace("-", "−")

    // Replace ^ followed by digits or +- with unicode superscripts
    s = s.replace(Regex("\\^([0-9+\\-]+)")) { matchResult ->
        val digits = matchResult.groupValues[1]
        digits.map { sups[it] ?: it }.joinToString("")
    }

    return s
}
