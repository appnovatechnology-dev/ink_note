package com.example

import com.example.inknote.ui.calculator.AngleMode
import com.example.inknote.ui.calculator.Fraction
import com.example.inknote.ui.calculator.ScientificMathEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ScientificCalculatorTest {

    @Test
    fun testBasicArithmeticAndPemdas() {
        val res1 = ScientificMathEngine.evaluate("2 + 3 * 4", AngleMode.DEG, 0.0)
        assertEquals(14.0, res1, 1e-6)

        val res2 = ScientificMathEngine.evaluate("(2 + 3) * 4", AngleMode.DEG, 0.0)
        assertEquals(20.0, res2, 1e-6)

        val res3 = ScientificMathEngine.evaluate("10 - 4 / 2", AngleMode.DEG, 0.0)
        assertEquals(8.0, res3, 1e-6)
    }

    @Test
    fun testImplicitMultiplication() {
        val res1 = ScientificMathEngine.evaluate("2(3)", AngleMode.DEG, 0.0)
        assertEquals(6.0, res1, 1e-6)

        val res2 = ScientificMathEngine.evaluate("(2)(3)(4)", AngleMode.DEG, 0.0)
        assertEquals(24.0, res2, 1e-6)

        val res3 = ScientificMathEngine.evaluate("2sin(30)", AngleMode.DEG, 0.0)
        assertEquals(1.0, res3, 1e-6)

        val res4 = ScientificMathEngine.evaluate("2sqrt(16)", AngleMode.DEG, 0.0)
        assertEquals(8.0, res4, 1e-6)
    }

    @Test
    fun testFractionsAndSimplification() {
        val frac1 = Fraction.fromDouble(0.75)
        assertNotNull(frac1)
        assertEquals(3L, frac1!!.numerator)
        assertEquals(4L, frac1.denominator)

        val frac2 = Fraction.fromDouble(0.5)
        assertNotNull(frac2)
        assertEquals(1L, frac2!!.numerator)
        assertEquals(2L, frac2.denominator)

        val frac3 = Fraction.fromDouble(1.5)
        assertNotNull(frac3)
        assertEquals(3L, frac3!!.numerator)
        assertEquals(2L, frac3.denominator)

        val res = ScientificMathEngine.evaluate("frac(1, 2) + frac(1, 4)", AngleMode.DEG, 0.0)
        assertEquals(0.75, res, 1e-6)
        val resFrac = Fraction.fromDouble(res)
        assertEquals(3L, resFrac!!.numerator)
        assertEquals(4L, resFrac.denominator)
    }

    @Test
    fun testPowersAndRoots() {
        val square = ScientificMathEngine.evaluate("5^2", AngleMode.DEG, 0.0)
        assertEquals(25.0, square, 1e-6)

        val cube = ScientificMathEngine.evaluate("3^3", AngleMode.DEG, 0.0)
        assertEquals(27.0, cube, 1e-6)

        val power = ScientificMathEngine.evaluate("2^5", AngleMode.DEG, 0.0)
        assertEquals(32.0, power, 1e-6)

        val sqrt = ScientificMathEngine.evaluate("sqrt(49)", AngleMode.DEG, 0.0)
        assertEquals(7.0, sqrt, 1e-6)

        val cbrt = ScientificMathEngine.evaluate("cbrt(27)", AngleMode.DEG, 0.0)
        assertEquals(3.0, cbrt, 1e-6)

        val nthRoot = ScientificMathEngine.evaluate("root(16, 4)", AngleMode.DEG, 0.0)
        assertEquals(2.0, nthRoot, 1e-6)
    }

    @Test
    fun testTrigonometricInDegAndRad() {
        val sinDeg = ScientificMathEngine.evaluate("sin(30)", AngleMode.DEG, 0.0)
        assertEquals(0.5, sinDeg, 1e-6)

        val cosDeg = ScientificMathEngine.evaluate("cos(60)", AngleMode.DEG, 0.0)
        assertEquals(0.5, cosDeg, 1e-6)

        val tanDeg = ScientificMathEngine.evaluate("tan(45)", AngleMode.DEG, 0.0)
        assertEquals(1.0, tanDeg, 1e-6)

        val cos90 = ScientificMathEngine.evaluate("cos(90)", AngleMode.DEG, 0.0)
        assertEquals(0.0, cos90, 1e-6)

        val asinVal = ScientificMathEngine.evaluate("asin(0.5)", AngleMode.DEG, 0.0)
        assertEquals(30.0, asinVal, 1e-6)

        val sinRad = ScientificMathEngine.evaluate("sin(π/6)", AngleMode.RAD, 0.0)
        assertEquals(0.5, sinRad, 1e-4)
    }

    @Test
    fun testHyperbolicFunctions() {
        val sinh0 = ScientificMathEngine.evaluate("sinh(0)", AngleMode.DEG, 0.0)
        assertEquals(0.0, sinh0, 1e-6)

        val cosh0 = ScientificMathEngine.evaluate("cosh(0)", AngleMode.DEG, 0.0)
        assertEquals(1.0, cosh0, 1e-6)

        val tanh0 = ScientificMathEngine.evaluate("tanh(0)", AngleMode.DEG, 0.0)
        assertEquals(0.0, tanh0, 1e-6)
    }

    @Test
    fun testLogarithms() {
        val log100 = ScientificMathEngine.evaluate("log(100)", AngleMode.DEG, 0.0)
        assertEquals(2.0, log100, 1e-6)

        val lne = ScientificMathEngine.evaluate("ln(e)", AngleMode.DEG, 0.0)
        assertEquals(1.0, lne, 1e-6)

        val logBase = ScientificMathEngine.evaluate("logbase(8, 2)", AngleMode.DEG, 0.0)
        assertEquals(3.0, logBase, 1e-6)
    }

    @Test
    fun testCombinatoricsAndFactorial() {
        val fact5 = ScientificMathEngine.evaluate("5!", AngleMode.DEG, 0.0)
        assertEquals(120.0, fact5, 1e-6)

        val fact0 = ScientificMathEngine.evaluate("0!", AngleMode.DEG, 0.0)
        assertEquals(1.0, fact0, 1e-6)

        val npr = ScientificMathEngine.evaluate("5 P 2", AngleMode.DEG, 0.0)
        assertEquals(20.0, npr, 1e-6)

        val ncr = ScientificMathEngine.evaluate("5 C 2", AngleMode.DEG, 0.0)
        assertEquals(10.0, ncr, 1e-6)
    }

    @Test
    fun testPercentageAndAns() {
        val pct = ScientificMathEngine.evaluate("50 * 20%", AngleMode.DEG, 0.0)
        assertEquals(10.0, pct, 1e-6)

        val ansRes = ScientificMathEngine.evaluate("Ans * 3 + 2", AngleMode.DEG, 10.0)
        assertEquals(32.0, ansRes, 1e-6)
    }

    @Test
    fun testStatistics() {
        val data = listOf(10.0, 20.0, 30.0, 40.0, 50.0)
        val mean = ScientificMathEngine.calculateMean(data)
        assertEquals(30.0, mean, 1e-6)

        val popStdDev = ScientificMathEngine.calculateStdDev(data, isSample = false)
        assertEquals(14.1421356, popStdDev, 1e-4)

        val sampleStdDev = ScientificMathEngine.calculateStdDev(data, isSample = true)
        assertEquals(15.8113883, sampleStdDev, 1e-4)
    }

    @Test
    fun testErrorsHandledWithoutCrashing() {
        try {
            ScientificMathEngine.evaluate("5 / 0", AngleMode.DEG, 0.0)
            fail("Expected division by zero exception")
        } catch (e: Exception) {
            assertTrue(e is ArithmeticException)
        }

        try {
            ScientificMathEngine.evaluate("sqrt(-4)", AngleMode.DEG, 0.0)
            fail("Expected negative sqrt exception")
        } catch (e: Exception) {
            assertTrue(e is IllegalArgumentException)
        }

        try {
            ScientificMathEngine.evaluate("asin(2)", AngleMode.DEG, 0.0)
            fail("Expected domain error for asin")
        } catch (e: Exception) {
            assertTrue(e is IllegalArgumentException)
        }

        try {
            ScientificMathEngine.evaluate("tan(90)", AngleMode.DEG, 0.0)
            fail("Expected tan(90) error")
        } catch (e: Exception) {
            assertTrue(e is ArithmeticException)
        }
    }
}
