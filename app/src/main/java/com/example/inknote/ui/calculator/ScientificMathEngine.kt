package com.example.inknote.ui.calculator

import kotlin.math.*

/**
 * High-precision evaluation and fraction support modeled closely on the Casio fx-991ES Plus.
 */
data class Fraction(val num: Long, val den: Long) {
    init {
        require(den != 0L) { "Math ERROR: Division by zero" }
    }

    val numerator: Long
    val denominator: Long

    init {
        val g = gcd(abs(num), abs(den))
        val sign = if (den < 0) -1 else 1
        numerator = (num / g) * sign
        denominator = abs(den) / g
    }

    fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()

    override fun toString(): String {
        return if (denominator == 1L) "$numerator" else "$numerator/$denominator"
    }

    companion object {
        private fun gcd(a: Long, b: Long): Long {
            var x = a
            var y = b
            while (y != 0L) {
                val t = y
                y = x % y
                x = t
            }
            return if (x == 0L) 1L else x
        }

        fun fromDouble(value: Double, maxDenominator: Long = 10000L): Fraction? {
            if (value.isNaN() || value.isInfinite() || abs(value) > 1e12) return null
            var h1 = 1L; var h2 = 0L
            var k1 = 0L; var k2 = 1L
            var b = abs(value)
            val isNegative = value < 0

            var a = b.toLong()
            for (step in 0..30) {
                val x = a * h1 + h2
                val y = a * k1 + k2
                if (y > maxDenominator || x < 0 || y < 0) break
                h2 = h1; h1 = x
                k2 = k1; k1 = y

                val diff = b - a
                if (abs(diff) < 1e-11) break
                b = 1.0 / diff
                a = b.toLong()
            }

            if (k1 <= 0L) return null
            val finalNum = if (isNegative) -h1 else h1
            val reconstructed = finalNum.toDouble() / k1.toDouble()
            return if (abs(reconstructed - value) < 1e-6) Fraction(finalNum, k1) else null
        }
    }
}

enum class AngleMode {
    DEG, RAD, GRA
}

object ScientificMathEngine {

    fun factorial(n: Double): Double {
        if (n < 0 || n != floor(n) || n > 170) throw IllegalArgumentException("Math ERROR")
        if (n == 0.0) return 1.0
        var result = 1.0
        val count = n.toInt()
        for (i in 2..count) {
            result *= i
        }
        return result
    }

    fun nPr(n: Double, r: Double): Double {
        if (n < 0 || r < 0 || r > n || n != floor(n) || r != floor(r)) throw IllegalArgumentException("Math ERROR")
        return factorial(n) / factorial(n - r)
    }

    fun nCr(n: Double, r: Double): Double {
        if (n < 0 || r < 0 || r > n || n != floor(n) || r != floor(r)) throw IllegalArgumentException("Math ERROR")
        return factorial(n) / (factorial(r) * factorial(n - r))
    }

    fun calculateMean(numbers: List<Double>): Double {
        if (numbers.isEmpty()) throw IllegalArgumentException("Math ERROR: Empty dataset")
        return numbers.average()
    }

    fun calculateStdDev(numbers: List<Double>, isSample: Boolean = false): Double {
        if (numbers.isEmpty() || (isSample && numbers.size < 2)) throw IllegalArgumentException("Math ERROR")
        val mean = numbers.average()
        val sumSquares = numbers.sumOf { (it - mean) * (it - mean) }
        val divisor = if (isSample) (numbers.size - 1) else numbers.size
        return sqrt(sumSquares / divisor)
    }

    fun calculateSum(numbers: List<Double>): Double {
        if (numbers.isEmpty()) throw IllegalArgumentException("Math ERROR")
        return numbers.sum()
    }

    fun evaluate(expression: String, angleMode: AngleMode, lastAns: Double): Double {
        if (expression.isBlank()) return 0.0
        val sanitized = preprocessExpression(expression, lastAns)
        return ExpressionParser(sanitized, angleMode).parse()
    }

    /**
     * Formats result for display, supporting scientific notation and fraction output.
     */
    fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "Math ERROR"
        if (value == 0.0) return "0"
        val absVal = abs(value)

        // Exact clean integer
        if (value == value.toLong().toDouble() && absVal < 1e12) {
            return value.toLong().toString()
        }

        // Very large or very small: scientific notation mantissa × 10^exp
        if (absVal >= 1e10 || absVal < 1e-4) {
            val sci = String.format(java.util.Locale.US, "%.7e", value)
            val parts = sci.split("e", "E")
            if (parts.size == 2) {
                val mantissa = parts[0].trimEnd('0').trimEnd('.')
                val exp = parts[1].toInt()
                val expSup = toSuperscript(exp.toString())
                return "$mantissa × 10$expSup"
            }
        }

        val formatted = String.format(java.util.Locale.US, "%.10f", value).trimEnd('0').trimEnd('.')
        return if (formatted.length > 12) {
            String.format(java.util.Locale.US, "%.8g", value)
        } else {
            formatted
        }
    }

    fun toSuperscript(s: String): String {
        val map = mapOf(
            '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
            '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
            '-' to '⁻', '+' to '⁺'
        )
        return s.map { map[it] ?: it }.joinToString("")
    }

    private fun preprocessExpression(expr: String, lastAns: Double): String {
        var s = expr
            .replace("Ans", "($lastAns)")
            .replace("π", "(${Math.PI})")
            .replace(Regex("(?<![a-zA-Z])e(?![a-zA-Z])"), "(${Math.E})")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")

        // Handle implicit multiplication:
        // 1. Number, ')', '!', or '%' followed by '(' -> insert '*'
        s = s.replace(Regex("(\\d|\\)|!|%)\\s*\\("), "$1*(")

        // 2. ')' followed by number or letter -> insert '*'
        s = s.replace(Regex("\\)\\s*(\\d|[a-zA-Z])"), ")*$1")

        // 3. Number, '!', or '%' followed by function or constant name (excluding binary operators P and C)
        s = s.replace(Regex("(\\d|!|%)\\s*([a-zA-BD-OQ-Z])"), "$1*$2")

        return s
    }

    private class ExpressionParser(val str: String, val angleMode: AngleMode) {
        var pos = -1
        var ch = ' '

        fun nextChar() {
            ch = if (++pos < str.length) str[pos] else '\u0000'
        }

        fun eat(charToEat: Char): Boolean {
            while (ch == ' ') nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw IllegalArgumentException("Syntax ERROR")
            return x
        }

        // Expression = Term + Term | Term - Term
        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+') -> x += parseTerm()
                    eat('-') -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        // Term = Factor * Factor | Factor / Factor | Factor P Factor | Factor C Factor
        fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*') -> x *= parseFactor()
                    eat('/') -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Math ERROR")
                        x /= divisor
                    }
                    eat('P') -> {
                        val r = parseFactor()
                        x = nPr(x, r)
                    }
                    eat('C') -> {
                        val r = parseFactor()
                        x = nCr(x, r)
                    }
                    else -> return x
                }
            }
        }

        // Factor = +Factor | -Factor | ( Expression ) | number | function | Factor ^ Factor | Factor ! | Factor %
        fun parseFactor(): Double {
            if (eat('+')) return +parseFactor()
            if (eat('-')) return -parseFactor()

            var x: Double
            val startPos = this.pos

            if (eat('(')) {
                x = parseExpression()
                if (!eat(')')) throw IllegalArgumentException("Syntax ERROR: Missing ')'")
            } else if ((ch in '0'..'9') || ch == '.') {
                while ((ch in '0'..'9') || ch == '.') nextChar()
                val numStr = str.substring(startPos, this.pos)
                x = numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Syntax ERROR")
            } else if (ch in 'a'..'z' || ch in 'A'..'Z') {
                while (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '_') nextChar()
                val func = str.substring(startPos, this.pos)
                if (eat('(')) {
                    val arg = parseExpression()
                    var arg2: Double? = null
                    if (eat(',')) {
                        arg2 = parseExpression()
                    }
                    if (!eat(')')) throw IllegalArgumentException("Syntax ERROR: Missing ')'")
                    x = applyFunction(func, arg, arg2)
                } else {
                    throw IllegalArgumentException("Syntax ERROR: Expected '(' after $func")
                }
            } else {
                throw IllegalArgumentException("Syntax ERROR at '${if (ch == '\u0000') "END" else ch.toString()}'")
            }

            // Postfix operators: ^ and ! and %
            while (true) {
                when {
                    eat('^') -> {
                        val exponent = parseFactor()
                        x = x.pow(exponent)
                    }
                    eat('!') -> {
                        x = factorial(x)
                    }
                    eat('%') -> {
                        x = x / 100.0
                    }
                    else -> break
                }
            }

            return x
        }

        private fun applyFunction(func: String, arg: Double, arg2: Double?): Double {
            return when (func.lowercase()) {
                "sqrt" -> {
                    if (arg < 0) throw IllegalArgumentException("Math ERROR")
                    sqrt(arg)
                }
                "cbrt" -> {
                    if (arg < 0) -(-arg).pow(1.0 / 3.0) else arg.pow(1.0 / 3.0)
                }
                "sin" -> {
                    if (angleMode == AngleMode.DEG) {
                        val normalized = ((arg % 360.0) + 360.0) % 360.0
                        if (normalized == 0.0 || normalized == 180.0) return 0.0
                        if (normalized == 30.0 || normalized == 150.0) return 0.5
                        if (normalized == 210.0 || normalized == 330.0) return -0.5
                        if (normalized == 90.0) return 1.0
                        if (normalized == 270.0) return -1.0
                    }
                    sin(toRadians(arg))
                }
                "cos" -> {
                    if (angleMode == AngleMode.DEG) {
                        val normalized = ((arg % 360.0) + 360.0) % 360.0
                        if (normalized == 90.0 || normalized == 270.0) return 0.0
                        if (normalized == 60.0 || normalized == 300.0) return 0.5
                        if (normalized == 120.0 || normalized == 240.0) return -0.5
                        if (normalized == 0.0) return 1.0
                        if (normalized == 180.0) return -1.0
                    }
                    cos(toRadians(arg))
                }
                "tan" -> {
                    if (angleMode == AngleMode.DEG) {
                        val normalized = ((arg % 360.0) + 360.0) % 360.0
                        if (normalized == 90.0 || normalized == 270.0) throw ArithmeticException("Math ERROR")
                        if (normalized == 0.0 || normalized == 180.0) return 0.0
                        if (normalized == 45.0 || normalized == 225.0) return 1.0
                        if (normalized == 135.0 || normalized == 315.0) return -1.0
                    }
                    val rad = toRadians(arg)
                    if (abs(cos(rad)) < 1e-14) throw ArithmeticException("Math ERROR")
                    tan(rad)
                }
                "asin", "sin_inv" -> {
                    if (arg < -1.0 || arg > 1.0) throw IllegalArgumentException("Math ERROR")
                    if (arg == 0.0) return 0.0
                    if (arg == 1.0 && angleMode == AngleMode.DEG) return 90.0
                    if (arg == -1.0 && angleMode == AngleMode.DEG) return -90.0
                    if (arg == 0.5 && angleMode == AngleMode.DEG) return 30.0
                    if (arg == -0.5 && angleMode == AngleMode.DEG) return -30.0
                    fromRadians(asin(arg))
                }
                "acos", "cos_inv" -> {
                    if (arg < -1.0 || arg > 1.0) throw IllegalArgumentException("Math ERROR")
                    if (arg == 1.0 && angleMode == AngleMode.DEG) return 0.0
                    if (arg == 0.0 && angleMode == AngleMode.DEG) return 90.0
                    if (arg == -1.0 && angleMode == AngleMode.DEG) return 180.0
                    if (arg == 0.5 && angleMode == AngleMode.DEG) return 60.0
                    fromRadians(acos(arg))
                }
                "atan", "tan_inv" -> {
                    if (arg == 0.0) return 0.0
                    if (arg == 1.0 && angleMode == AngleMode.DEG) return 45.0
                    if (arg == -1.0 && angleMode == AngleMode.DEG) return -45.0
                    fromRadians(atan(arg))
                }
                "sinh" -> sinh(arg)
                "cosh" -> cosh(arg)
                "tanh" -> tanh(arg)
                "asinh" -> ln(arg + sqrt(arg * arg + 1.0))
                "acosh" -> {
                    if (arg < 1.0) throw IllegalArgumentException("Math ERROR")
                    ln(arg + sqrt(arg * arg - 1.0))
                }
                "atanh" -> {
                    if (arg <= -1.0 || arg >= 1.0) throw IllegalArgumentException("Math ERROR")
                    0.5 * ln((1.0 + arg) / (1.0 - arg))
                }
                "ln" -> {
                    if (arg <= 0) throw IllegalArgumentException("Math ERROR")
                    ln(arg)
                }
                "log", "log10" -> {
                    if (arg <= 0) throw IllegalArgumentException("Math ERROR")
                    log10(arg)
                }
                "logbase", "log_b" -> {
                    val base = arg2 ?: 10.0
                    if (arg <= 0 || base <= 0 || base == 1.0) throw IllegalArgumentException("Math ERROR")
                    ln(arg) / ln(base)
                }
                "root" -> {
                    val n = arg2 ?: 2.0
                    if (n == 0.0) throw ArithmeticException("Math ERROR")
                    if (arg < 0 && n % 2.0 == 0.0) throw IllegalArgumentException("Math ERROR")
                    if (arg < 0) -(-arg).pow(1.0 / n) else arg.pow(1.0 / n)
                }
                "frac" -> {
                    val den = arg2 ?: 1.0
                    if (den == 0.0) throw ArithmeticException("Math ERROR")
                    arg / den
                }
                else -> throw IllegalArgumentException("Syntax ERROR: Unknown function $func")
            }
        }

        private fun toRadians(angle: Double): Double {
            return when (angleMode) {
                AngleMode.DEG -> Math.toRadians(angle)
                AngleMode.RAD -> angle
                AngleMode.GRA -> angle * (Math.PI / 200.0)
            }
        }

        private fun fromRadians(rad: Double): Double {
            return when (angleMode) {
                AngleMode.DEG -> Math.toDegrees(rad)
                AngleMode.RAD -> rad
                AngleMode.GRA -> rad * (200.0 / Math.PI)
            }
        }
    }
}
