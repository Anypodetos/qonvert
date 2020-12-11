package org.tessoft.qonvert

/*
Copyright 2020 Anypodetos (Michael Weber)

This file is part of Qonvert.

Qonvert is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Qonvert is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Qonvert. If not, see <https://www.gnu.org/licenses/>.

Contact: <https://lemizh.conlang.org/home/contact.php?about=qonvert>
*/

import java.lang.Exception
import java.math.BigInteger
import java.math.BigInteger.ONE
import java.math.BigInteger.ZERO
import kotlin.math.*

fun reduceFraction(numer: BigInteger, denom: BigInteger): Array<BigInteger> {
    if (denom == ONE) return arrayOf(numer, ONE)
    var gcd: BigInteger = numer.gcd(denom).coerceAtLeast(ONE)
    if (denom < ZERO || (denom == ZERO && numer < ZERO)) gcd = -gcd
    return arrayOf(numer / gcd, denom / gcd)
}

fun lnBigInteger(v: BigInteger): Double { /* Thanks to leonbloy @ https://stackoverflow.com/questions/6827516/logarithm-for-biginteger ! */
    if (v.signum() < 1) return if (v.signum() < 0) Double.NaN else Double.NEGATIVE_INFINITY
    val blex: Int = v.bitLength() - 977 /* max binary digits; any value in 60..1023 works here */
    val res = ln((if (blex > 0) v.shiftRight(blex) else v).toDouble())
    return if (blex > 0) res + blex * ln(2.0) else res
}

val surrogateRange = 0xD800..0xDBFF
fun resolveSurrogate(c1: Int, c2: Int) = (c1 - 0xD800) * 0x400 + (c2 - 0xDC00) + 0x10000

fun digitToChar(digit: Int): Char {
    return (when (digit) {
        in -64..-1 -> if (lowerDigits) 123 else 91
        in 0..9 -> 48
        else -> if (lowerDigits) 87 else 55
    } + digit).toChar()
}

val subDigits = arrayOf('₀', '₁', '₂', '₃', '₄', '₅', '₆', '₇', '₈', '₉')
fun toSubDigits(i: Int): String {
    var out = ""
    for (digit in i.toString()) out += subDigits[digit.toInt() - 48]
    return out
}

enum class QFormat {
    POSITIONAL, FRACTION, MIXED, CONTINUED
}
enum class NumSystem {
    STANDARD, BALANCED, BIJECTIVE_1, BIJECTIVE_A, DMS, ROMAN
}
//val numSystemTag = mapOf(NumSystem.STANDARD to "ˢᵗᵈ", NumSystem.BALANCED to "ᵇᵃˡ", NumSystem.BIJECTIVE_1 to "ᵇʲ¹", NumSystem.BIJECTIVE_A to "ᵇʲᵃ", NumSystem.DMS to "ᵈᵐˢ", NumSystem.ROMAN to "ʳᵒᵐ")
fun AllowedBase(base: Int, system: NumSystem) = when (system) {
    NumSystem.STANDARD -> base
    NumSystem.BALANCED -> if (base % 2 == 1) base else (base + 1).coerceAtMost(35)
    NumSystem.BIJECTIVE_1 -> base.coerceAtMost(35)
    NumSystem.BIJECTIVE_A -> base.coerceAtMost(26)
    NumSystem.DMS -> base.coerceAtLeast(8)
    NumSystem.ROMAN -> 10
}

class QNumber(numerator: BigInteger, denominator: BigInteger, base: Int = 10, system: NumSystem = NumSystem.STANDARD,
              private var complement: Boolean = false, format: QFormat = QFormat.POSITIONAL, error: Int = 0) {

    lateinit var numerator: BigInteger
        private set
    lateinit var denominator: BigInteger
        private set
    var base = base
        private set
    var system = system
        private set
    var format = format
        private set
    var error = error
        private set
    var wrongDigits = false
        private set
    private val isBalanced get() = system == NumSystem.BALANCED && base % 2 == 1
    private val groupSize get() = if (base in setOf(2, 4, 8, 16, 32, 64)) 4 else 3

    init { store(reduceFraction(numerator, denominator)) }

    private fun store(a: Array<BigInteger>) {
        numerator = a[0]
        denominator = a[1]
    }

    constructor(st: String, base: Int = 10, system: NumSystem = NumSystem.STANDARD): this(ZERO, ONE, base, system) {
        var stTrimmed = st.filterNot { it == ' ' }
        if (stTrimmed.startsWith('"')) {
            var code = if (stTrimmed.length < 2) 0 else stTrimmed[1].toInt()
            if (code in surrogateRange && stTrimmed.length > 2) code = resolveSurrogate(code, stTrimmed[2].toInt())
            store(arrayOf(code.toBigInteger(), ONE))
        } else {
            stTrimmed = stTrimmed.removePrefix("[").removeSuffix("]")
            val semi = stTrimmed.indexOf(';')
            if (semi == -1) store(parseFraction(stTrimmed)) else {
                var c: Array<BigInteger>
                var x = arrayOf(ONE, ZERO)
                for ((i, sst) in ((stTrimmed.substring(semi + 1).split(',')).reversed() + stTrimmed.substring(0, semi)).withIndex()) {
                    c = if (i > 0 || sst != "") parseFraction(sst) else arrayOf(ONE, ZERO)
                    x = reduceFraction(c[0] * x[0] + c[1] * x[1], c[1] * x[0])
                }
                store(x)
                format = QFormat.CONTINUED
            }
        }
    }

    constructor(st: String?): this(ZERO, ONE) { /* for loading history entry from SharedPreferences */
        if (st != null) try {
            val split = st.split('/')
            numerator   = split[0].toBigInteger()
            denominator = split[1].toBigInteger()
            base        = split[2].toInt()
            system      = NumSystem.valueOf(split[3])
            complement  = split[4].toBoolean()
            format      = QFormat.valueOf(split[5])
        } catch (e: Exception) { error = 0xF }
    }

    fun changeBase(base: Int, system: NumSystem, complement: Boolean) {
        this.base = base
        this.system = system
        this.complement = complement
    }

    fun errorCode() = intToBase(error.toBigInteger())

    fun toPositional() = rationalToBase(numerator, denominator)
    fun toFraction() = if (denominator != ONE) "${intToBase(numerator)}/${intToBase(denominator)}" else intToBase(numerator)
    fun toMixed() = if (denominator > ONE && numerator.abs() > denominator) {
        val forComplement = complement && numerator < ZERO
        intToBase( numerator / denominator - (if (forComplement) ONE else ZERO)) + '_' +
        intToBase((numerator % denominator + (if (forComplement) denominator else ZERO)).abs()) + '/' + intToBase(denominator)
    } else toFraction()
    fun toContinued() = if (denominator > ONE) continuedFraction(numerator, denominator) else toPositional()

    override fun toString(): String = toString(aFormat = format)
    fun toString(withBase: Boolean = false, aFormat: QFormat = format) = when (aFormat) {
        QFormat.POSITIONAL -> toPositional()
        QFormat.FRACTION   -> toFraction()
        QFormat.MIXED      -> toMixed()
        QFormat.CONTINUED  -> toContinued()
    } + if (withBase) toSubDigits(base) + (if (isBalanced) "ᵇᵃˡ" else "") else ""

    fun toRoman(): String {
        if (denominator != ONE || ZERO >= numerator ||  numerator >= 4000.toBigInteger()) return ""
        return arrayOf("", "M", "MM", "MMM")                                   [numerator.toInt() / 1000] +
            arrayOf("", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM")[numerator.toInt() /  100 % 10] +
            arrayOf("", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC")[numerator.toInt() /   10 % 10] +
            arrayOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")[numerator.toInt()        % 10]
    }

    fun toSaveString() = "${numerator}/${denominator}/$base/$system/$complement/$format"

    fun copy() = QNumber(numerator, denominator, base, system, complement, format, error)

    /*   I n p u t   */

    private fun parsePositional(st: String, default: BigInteger = ZERO): Array<BigInteger> {
        if (error > 0) return arrayOf(ZERO, ONE)
        when (st) {
            "∞", "-∞" -> return arrayOf(ONE,  ZERO)
            "無"      -> return arrayOf(ZERO, ZERO)
        }
        var numer = ZERO;   var numersub = ZERO
        var denom = ONE;    var denomsub = ZERO
        var point = false;  var rep = false
        var prePointRecurr = ONE
        var neg = false
        var startSt = 1
        val useBase = when (if (st != "") st[0] else ' ') {
            '@' -> 2
            '#' -> 8
            '$', '€', '£', '¥' -> 10
            '%' -> 12
            '&' -> 16
            else -> {startSt--; base}
        }
        if (st.length == startSt) return arrayOf(default, ONE)
        val bigBase = useBase.toBigInteger()
        val minDigit = if (isBalanced) (1 - useBase) / 2 else 0
        var leftPad = if (!st.substring(startSt).startsWith("..")) -1 else {
            if (isBalanced) return arrayOf(ONE,  ZERO)
            complement = true
            startSt += 2
            0
        }
        for (c in st.substring(startSt)) {
            if (c in '0'..'9' || c in 'A'..'Z' || c in 'a'..'z') {
                numer *= bigBase
                if (point) denom *= bigBase else {
                    if (rep) prePointRecurr *= bigBase
                    if (leftPad > -1) leftPad++
                }
            }
            var digit = 0
            when (c) {
                in '0'..'9' -> digit = c.toInt() - 48
                in 'A'..'Z' -> digit = c.toInt() - 65 + if (isBalanced && c > 'I') -26 else 10
                in 'a'..'z' -> digit = c.toInt() - 97 + if (isBalanced && c > 'i') -26 else 10
                '-' -> if (numer == ZERO && !neg && leftPad == -1) neg = true else error = 0x2D
                '.' -> if (!point) point = true else error = 0x2E
                '\'' -> if (!rep) {
                    rep = true
                    numersub = numer
                    denomsub = denom
                } else error = 0x27
                ' ' -> { }
                else -> error = if (error in surrogateRange) resolveSurrogate(error, c.toInt()) else c.toInt()
            }
            numer += digit.toBigInteger()
            if (digit !in minDigit until minDigit + useBase) wrongDigits = true
            if (error > 0 && error !in surrogateRange) return arrayOf(ZERO, ONE)
        }
        denom *= prePointRecurr
        if (denomsub != denom) {
            numer -= numersub
            denom -= denomsub
        }
        numer *= prePointRecurr
        if (neg) numer = -numer
            else if (leftPad > -1) numer -= denom * bigBase.pow(leftPad)
        return reduceFraction(numer, denom)
    }

    private fun parseFraction(st: String): Array<BigInteger> {
        var under = st.indexOf('_')
        var slash = st.indexOf('/')
        if (slash > -1 && under > slash) under = -1
        return if (slash == -1 && under == -1) parsePositional(st) else {
            format = if (under == -1) QFormat.FRACTION else QFormat.MIXED
            if (slash == -1) slash = st.length
            val integ = parsePositional(st.substring(0, max(under, 0)))
            val numer = parsePositional(st.substring(under + 1, slash))
            val denom = parsePositional(st.substring(min(slash + 1, st.length)), ONE)
            if (under > -1 && st[0] == '-') numer[0] = -numer[0]
            reduceFraction(numer[0] * denom[1] * integ[1] + numer[1] * denom[0] * integ[0],
                           integ[1] * numer[1] * denom[0])
        }
    }

    /*   O u t p u t   */

    private fun intToBase(a: BigInteger, fracDigits: Int = 0, recurring: Boolean = false): String {
        if (a == ZERO) return "0"
        val bigBase = base.toBigInteger()
        var x = if (complement && a < ZERO) a + bigBase.pow(ceil(lnBigInteger(-a) / ln(base.toDouble())).roundToInt() + 2) - (if (recurring) ONE else ZERO)
            else a.abs()
        var digit: Int;  var nDigits = 0
        var st = ""
        while (x != ZERO || nDigits < fracDigits + 1) {
            digit = (x % bigBase).toInt()
            if (isBalanced) {
                if (digit > base / 2) {
                    digit -= base
                    x += bigBase
                }
                if (a < ZERO) digit = -digit
            }
            st = digitToChar(digit) + (if (nDigits == 0 || (nDigits - fracDigits) % groupSize != 0) "" else
                if (nDigits == fracDigits) "." else if (groupDigits) " " else "") + st
            x /= bigBase
            nDigits++
        }
        return if (complement && a < ZERO) "..$st" else if (a < ZERO && !isBalanced) "-$st" else st
    }

    private fun rationalToBase(numer: BigInteger, denom: BigInteger): String {
        if (denom == ONE) return intToBase(numer) else if (denom == ZERO)
            return if (numer == ZERO) "無" else "∞"
        val bigBase = base.toBigInteger()
        var c: BigInteger
        var d = denom
        var nPre = -1
        do {
            nPre++
            c = d.gcd(bigBase)
            d /= c
        } while (c != ONE && nPre <= maxDigitsAfter)
        var nRep = 0
        if (d > ONE && nPre <= maxDigitsAfter) do nRep++ while ((bigBase.pow(nRep) - ONE) % d != ZERO && nRep <= maxDigitsAfter - nPre)
        val rounded = nRep > maxDigitsAfter - nPre
        if (rounded) {
            nPre = maxDigitsAfter
            nRep = 0
        }
        val numpower = numer * bigBase.pow(nPre + nRep)
        val up = if (!isBalanced || numpower.abs() % denom <= denom / 2.toBigInteger()) ZERO else if (numer > ZERO) ONE else -ONE
        val outSt = intToBase(numpower / denom + up, nPre + nRep, nRep > 0)

        val posRep = (outSt.length - (if (groupDigits) (nPre + nRep - 1) / groupSize - nPre / groupSize else 0) - nRep).coerceAtMost(outSt.length)
        return outSt.substring(0, posRep) + (if (nRep > 0) '\'' + outSt.substring(posRep) else "") + (if (rounded) "…" else "")
    }

    private tailrec fun continuedFraction(numer: BigInteger, denom: BigInteger, pre: String = ""): String {
        var i: BigInteger = numer / denom
        if (numer < ZERO && numer % denom != ZERO) i--
        val st = "$pre, ${intToBase(i)}"
        return if (i * denom == numer) '[' + st.substring(2).replaceFirst(',', ';') + ']' else continuedFraction(denom, numer - i * denom, st)
    }

    /*  C a l c  -  maybe for V 2.0  */

    operator fun compareTo(other: QNumber) = (numerator * other.denominator - denominator * other.numerator).signum()
    operator fun compareTo(other: BigInteger) = (numerator - denominator * other).signum()
    override operator fun equals(other: Any?) = if (other is QNumber) compareTo(other) == 0 else if (other is BigInteger) compareTo(other) == 0 else false
    override fun hashCode() = 31 * numerator.hashCode() + denominator.hashCode()

    operator fun plus (other: QNumber) = QNumber(numerator * other.denominator + denominator * other.numerator, denominator * other.denominator, base, system, complement, format)
    operator fun minus(other: QNumber) = QNumber(numerator * other.denominator - denominator * other.numerator, denominator * other.denominator, base, system, complement, format)
    operator fun times(other: QNumber) = QNumber(numerator * other.numerator,                                   denominator * other.denominator, base, system, complement, format)
    operator fun div  (other: QNumber) = QNumber(numerator * other.denominator,                                 denominator * other.denominator, base, system, complement, format)

    fun abs() = QNumber(numerator.abs(), denominator,                     base, system, complement, format)
    fun inv() = QNumber(denominator, numerator,                           base, system, complement, format)
    fun sqr() = QNumber(numerator * numerator, denominator * denominator, base, system, complement, format)
}