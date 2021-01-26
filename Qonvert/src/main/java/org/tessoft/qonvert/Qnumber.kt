package org.tessoft.qonvert

/*
Copyright 2020, 2021 Anypodetos (Michael Weber)

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

import android.content.res.Resources
import java.lang.Exception
import java.math.BigInteger
import java.math.BigInteger.*
import java.util.*
import kotlin.math.*

typealias BigFraction = Pair<BigInteger, BigInteger>
val TWO = 2.toBigInteger()
val TWELVE = 12.toBigInteger()
val SPECIAL_NUMBERS = mapOf("∞" to Pair(ONE, ZERO), "-∞" to Pair(ONE, ZERO), "無" to Pair(ZERO, ZERO), "λ" to Pair(ZERO, ONE))
val FRACTION_CHARS  = mapOf('½' to Pair(1, 2), '⅓' to Pair(1, 3), '¼' to Pair(1, 4), '⅕' to Pair(1, 5), '⅙' to Pair(1, 6),
    '⅐' to Pair(1, 7), '⅛' to Pair(1, 8), '⅑' to Pair(1, 9),'⅒' to Pair(1, 10), '⅔' to Pair(2, 3), '¾' to Pair(3, 4),
    '⅖' to Pair(2, 5), '⅗' to Pair(3, 5), '⅘' to Pair(4, 5), '⅚' to Pair(5, 6), '⅜' to Pair(3, 8), '⅝' to Pair(5, 8),
    '⅞' to Pair(7, 8), '↉' to Pair(0, 1)).mapValues { Pair(it.value.first.toBigInteger(), it.value.second.toBigInteger()) }
const val SUBSCRIPT_DIGITS   = "₀₁₂₃₄₅₆₇₈₉"
const val SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

val ROMAN_DIGITS        = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000,
    'ↁ' to 5000, 'ↂ' to 10_000, 'ↇ' to 50_000, 'ↈ' to 100_000)
val ROMAN_UNCIAE        = mapOf('.' to 1, '·' to 1, ':' to 2, '∶' to 2, '∴' to 3, '∷' to 4, '⁙' to 5, 'S' to 6)
val ROMAN_APOSTROPHUS   = mapOf("ↀ" to "M", "CCCIↃↃↃ" to "ↈ", "CCIↃↃ" to "ↂ", "CIↃ" to "M", "IↃↃↃ" to "ↇ", "IↃↃ" to "ↁ", "IↃ" to "D")
val APOSTROPHUS_OPTIONS = listOf("-------", "+------", "-++-++-", "-++++++")
val ROMAN_OTHER         = mapOf("(((I)))" to "ↈ", "((I))" to "ↂ", "(I)" to "M", "I)))" to "ↇ", "I))" to "ↁ", "I)" to "D",
    "Ⅰ" to "I", "Ⅱ" to "II", "Ⅲ" to "III", "Ⅳ" to "IV", "Ⅴ" to "V", "Ⅵ" to "VI", "Ⅶ" to "VII", "Ⅷ" to "VIII", "Ⅸ" to "IX",
    "Ⅹ" to "X", "Ⅺ" to "XI", "Ⅻ" to "XII", "Ⅼ" to "L", "ↆ" to "L", "Ⅽ" to "C", "Ⅾ" to "D", "Ⅿ" to "M")
val ROMAN_CHARS = (ROMAN_DIGITS.keys + ROMAN_UNCIAE.keys + ROMAN_OTHER.map { it.key[0] } + ROMAN_OTHER.map { it.key[0].toLowerCase() } +
    "ↀ)Ↄↄ|!".toSet()).filterNot { it in 'A'..'Z' }

val ROMAN_100_000   = listOf("", "ↈ", "ↈↈ", "ↈↈↈ")
val ROMAN_100_000_R = listOf("", "ↈ", "ↈↈ", "ↈↈↈ", "ↈↈↈↈ", "", "", "", "", "")
val ROMAN_10_000    = listOf("", "ↂ", "ↂↂ", "ↂↂↂ", "ↂↇ", "ↇ", "ↇↂ", "ↇↂↂ", "ↇↂↂↂ", "ↂↈ")
val ROMAN_10_000_R  = listOf("", "ↂ", "ↂↂ", "ↂↂↂ", "(ↂↇ|ↂↂↂↂ)", "ↇ", "ↇↂ", "ↇↂↂ", "ↇↂↂↂ", "(ↂↈ|ↇↂↂↂↂ)")
val ROMAN_1000      = listOf("", "M", "MM", "MMM", "Mↁ", "ↁ", "ↁM", "ↁMM", "ↁMMM", "Mↂ")
val ROMAN_1000_R    = listOf("", "M", "MM", "MMM", "(Mↁ|MMMM)", "ↁ", "ↁM", "ↁMM", "ↁMMM", "(Mↂ|ↁMMMM)")
val ROMAN_100       = listOf("", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM")
val ROMAN_100_R     = listOf("", "C", "CC", "CCC", "(CD|CCCC)", "D", "DC", "DCC", "DCCC", "(CM|DCCCC)")
val ROMAN_10        = listOf("", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC")
val ROMAN_10_R      = listOf("", "X", "XX", "XXX", "(XL|XXXX)", "L", "LX", "LXX", "LXXX", "(XC|LXXXX)")
val ROMAN_1         = listOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")
val ROMAN_1_R       = listOf("", "I", "II", "III", "(IV|IIII)", "V", "VI", "VII", "VIII", "(IX|VIIII)")
val ROMAN_1_12      = listOf("", "·", ":", "∴", "∷", "⁙", "S", "S·", "S:", "S∴", "S∷", "S⁙")
const val ROMAN_ID_CHAR = '\u200B'

val INTERVALS = listOf(Pair(1, 1),
    /* 2 */ Pair(25, 24), Pair(256, 243), /*Pair(16, 15),*/ Pair(10, 9), Pair(9, 8),
    /* 3 */ Pair(32, 27), Pair(6, 5), Pair(5, 4), Pair(81, 64),
    /* 4 */ Pair(4, 3),
    /* T */ Pair(25, 18), Pair(729, 512),
    /* 5 */ Pair(3, 2),
    /* 6 */ Pair(128, 81), Pair(8, 5), Pair(5, 3), Pair(27, 16),
    /* 7 */ Pair(16, 9), Pair(9, 5), Pair(15, 8), Pair(243, 128),
    ).map { Pair(it.first.toBigInteger(), it.second.toBigInteger()) }


fun lnBigInteger(a: BigInteger): Double { /* Thanks to leonbloy @ https://stackoverflow.com/questions/6827516/logarithm-for-biginteger ! */
    if (a.signum() < 1) return if (a.signum() < 0) Double.NaN else Double.NEGATIVE_INFINITY
    val blex: Int = a.bitLength() - 977
    val res = ln((if (blex > 0) a.shiftRight(blex) else a).toDouble()) /* numbers larger than 2^977 are considered too big for floating point operations */
    return if (blex > 0) res + blex * ln(2.0) else res
}
fun complementDigits(a: BigInteger, base: Int) = if (a > ZERO) ceil(lnBigInteger(a) / ln(base.toDouble())).roundToInt() else 0

fun reduceFraction(numer: BigInteger, denom: BigInteger): BigFraction {
    if (denom == ONE) return Pair(numer, ONE)
    var gcd: BigInteger = numer.gcd(denom).coerceAtLeast(ONE)
    if (denom < ZERO || (denom == ZERO && numer < ZERO)) gcd = -gcd
    return Pair(numer / gcd, denom / gcd)
}

enum class QFormat {
    POSITIONAL, FRACTION, MIXED, CONTINUED, EGYPTIAN, UNICODE
}
enum class NumSystem {
    STANDARD, BALANCED, BIJECTIVE_1, BIJECTIVE_A, /*DMS, FACTORIAL,*/ ROMAN
}

fun digitToChar(digit: Int, base: Int, system: NumSystem) = ((if (system != NumSystem.BIJECTIVE_A || base > 26) when (digit) {
        in -64..-1 -> if (MainActivity.lowerDigits) 123 else 91
        in 0..9 -> 48
        else -> if (MainActivity.lowerDigits) 87 else 55
    } else (if (MainActivity.lowerDigits) 96 else 64)) + digit).toChar()

fun minDigit(base: Int, system: NumSystem) = when (system) {
    NumSystem.STANDARD, /*NumSystem.DMS, NumSystem.FACTORIAL,*/ NumSystem.ROMAN -> 0
    NumSystem.BALANCED -> if (base % 2 == 1) (1 - base) / 2 else 0
    NumSystem.BIJECTIVE_1 -> if (base <= 35) 1 else 0
    NumSystem.BIJECTIVE_A -> if (base <= 26) 1 else 0
}

fun allowedBase(base: Int, system: NumSystem) = when (system) {
    NumSystem.STANDARD -> base
    NumSystem.BALANCED -> if (base % 2 == 1) base else (base + 1).coerceAtMost(35)
    NumSystem.BIJECTIVE_1 -> base.coerceAtMost(35)
    NumSystem.BIJECTIVE_A -> base.coerceAtMost(26)
    //NumSystem.DMS -> base.coerceAtLeast(8)
    NumSystem.ROMAN -> 10
}

fun overflowNumber(digits: Int, base: BigInteger, system: NumSystem): BigInteger = if (system !in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A)
     base.pow(digits) else (base.pow(digits + 1) - ONE) / (base - ONE)

class QNumber(numerator: BigInteger = ZERO, denominator: BigInteger = ONE, base: Int = 10, system: NumSystem = NumSystem.STANDARD,
              complement: Boolean = false, format: QFormat = QFormat.POSITIONAL, private var error: String = "") {

    lateinit var numerator: BigInteger
        private set
    lateinit var denominator: BigInteger
        private set
    var base = 1
        private set
    lateinit var system: NumSystem
        private set
    var complement = false
        private set
    var format = format
        private set
    val isValid
        get() = error == ""
    var nonstandardInput = false
        private set
    private var groupSize = 1

    init {
        store(reduceFraction(numerator, denominator))
        changeBase(base, system, complement)
    }

    private fun store(numer: BigInteger = ZERO, denom: BigInteger = ONE) = store(Pair(numer, denom))
    private fun store(a: BigFraction) {
        numerator = a.first
        denominator = a.second
    }
    fun changeBase(base: Int, system: NumSystem, complement: Boolean) {
        this.base = base
        this.system = if (allowedBase(base, system) == base) system else NumSystem.STANDARD
        this.complement = complement && this.system != NumSystem.BALANCED && numerator < ZERO
        groupSize = if (base in setOf(2, 4, 8, 16, 32, 64)) 4 else 3
    }

    constructor(st: String, base: Int, system: NumSystem): this(base = base, system = system) {
        var stTrimmed = st.trimStart()
        if (stTrimmed.startsWith('"'))
            store(if (stTrimmed.length > 1) { format = QFormat.UNICODE; stTrimmed.codePointAt(1).toBigInteger() } else ZERO)
        else when (stTrimmed.trimEnd()) {
            "🐋💐" -> store(38607030735492.toBigInteger())
            "🚕"   -> store(77002143071279.toBigInteger())
            "👾"   -> store(2985842461868634769.toBigInteger())
            else -> {
                stTrimmed = stTrimmed.filterNot { it in " \t\n\r" }
                val bracketMinus = stTrimmed.startsWith("-{") || stTrimmed.startsWith("-[")
                if (bracketMinus) stTrimmed = stTrimmed.substring(1)
                val egyptian = stTrimmed.startsWith('{') || stTrimmed.endsWith('}')
                stTrimmed = if (egyptian) stTrimmed.removePrefix("{").removeSuffix("}") else stTrimmed.removePrefix("[").removeSuffix("]")
                val semi = stTrimmed.indexOf(';')
                var x = Pair(ONE, ZERO)
                when {
                    semi == -1 -> x = parseFraction(stTrimmed)
                    egyptian -> { /* Egyptian fraction */
                        x = parseFraction(stTrimmed.substring(0, semi))
                        for ((i, subSt) in stTrimmed.substring(semi + 1).split(',').reversed().withIndex()) {
                            val c = parseFraction(subSt, if (i > 0) Pair(ZERO, ONE) else Pair(ONE, ZERO))
                            x = reduceFraction(c.first * x.first + c.second * x.second, c.first * x.second)
                        }
                        format = QFormat.EGYPTIAN
                    }
                    else -> { /* continued fraction */
                        for ((i, subSt) in ((stTrimmed.substring(semi + 1).split(',')).reversed() + stTrimmed.substring(0, semi)).withIndex()) {
                            val c = parseFraction(subSt, if (i > 0) Pair(ZERO, ONE) else Pair(ONE, ZERO))
                            x = reduceFraction(c.first * x.first + c.second * x.second, c.second * x.first)
                        }
                        format = QFormat.CONTINUED
                    }
                }
                if (bracketMinus) x = reduceFraction(-x.first, x.second)
                if (x.first >= ZERO) complement = false /* shouldn’t be necessary, but feels safer */
                store(x)
            }
        }
    }

    constructor(preferencesEntry: String): this() {
        val split = preferencesEntry.split('/')
        try {
            store(split[0].toBigInteger(), split[1].toBigInteger())
            changeBase(split[2].toInt(), NumSystem.valueOf(split[3]), split[4].toBoolean())
            format = QFormat.valueOf(split[5])
        } catch (e: Exception) { error = "\u0015" }
    }

    fun toPreferencesString() = "$numerator/$denominator/$base/$system/$complement/$format"
    fun copy() = QNumber(numerator, denominator, base, system, complement, format, error)

    /*   I n p u t   */

    private fun parseFraction(st: String, default: BigFraction = Pair(ZERO, ONE)): BigFraction {
        var under = st.indexOf('_')
        var slash = st.indexOf('/')
        if (slash > -1 && under > slash) under = -1
        return if (slash == -1 && under == -1) parsePositional(st, default) else {
            if (slash == -1) slash = st.length
            val denom =   parsePositional(st.substring(min(slash + 1, st.length)), Pair(ONE, ONE))
            val numer =   parsePositional(st.substring(under + 1, slash))
            val integer = parsePositional(st.substring(0, max(under, 0)))
            format = if (under == -1) QFormat.FRACTION else QFormat.MIXED
            reduceFraction(numer.first  * denom.second * integer.second * (if (under > -1 && st[0] == '-') -ONE else ONE) +
                           numer.second * denom.first  * integer.first,
                           integer.second * numer.second * denom.first)
        }
    }

    private fun parsePositional(st: String, default: BigFraction = Pair(ZERO, ONE)): BigFraction {
        if (!isValid) return Pair(ZERO, ZERO)
        with(SPECIAL_NUMBERS[st]) { if (this != null) return this }
        val tokenBaseSystem = MainActivity.tokenBaseSystem(if (st.isNotEmpty()) st[0] else ' ')
        val (useBase, useSystem) = tokenBaseSystem ?: Pair(base, system)
        var startSt = if (tokenBaseSystem == null) 0 else 1
        if (useSystem == NumSystem.ROMAN) with(parseRoman(st.substring(startSt))) { if (second != ZERO) return this }
        val bigBase = useBase.toBigInteger()

        var numer = ZERO;       var numerSub = ZERO
        var denom = ONE;        var denomSub = ZERO
        var neg = false;        var point = false
        var rep = false;        var prePointRecurr = ONE
        var isNumber = false;   var fractionChar = 0.toChar()
        val minDigit = minDigit(useBase, useSystem)
        val valueOfA = if (useSystem == NumSystem.BIJECTIVE_A) 1 else 10
        var leftPad = if (!st.substring(startSt).startsWith("..")) -1 else {
            complement = true
            isNumber = true
            startSt += 2
            0
        }

        for (c in st.substring(startSt)) {
            if (c in '0'..'9' || c in 'A'..'Z' || c in 'a'..'z') {
                isNumber = true
                numer *= bigBase
                if (point) denom *= bigBase else {
                    if (rep) prePointRecurr *= bigBase
                    if (leftPad > -1) leftPad++
                }
            }
            if (fractionChar != 0.toChar()) error = fractionChar.toString()
            var digit: Int? = null
            when (c) {
                in '0'..'9' -> {
                    digit = c.toInt() - 48
                    if (useSystem == NumSystem.BIJECTIVE_A) nonstandardInput = true
                }
                in 'A'..'Z' -> digit = c.toInt() - 65 + if (useSystem == NumSystem.BALANCED && c > 'I') -26 else valueOfA
                in 'a'..'z' -> digit = c.toInt() - 97 + if (useSystem == NumSystem.BALANCED && c > 'i') -26 else valueOfA
                '-' -> if (numer == ZERO && !neg && !point && !rep && leftPad == -1) neg = true else error = c.toString()
                '.' -> if (!point) point = true else error = c.toString()
                '\'' -> if (!rep) {
                    rep = true
                    numerSub = numer
                    denomSub = denom
                } else error = c.toString()
                in FRACTION_CHARS.keys -> if (!point && !rep) fractionChar = c else error = c.toString()
                in ROMAN_CHARS -> error = if (useSystem == NumSystem.ROMAN) st + ROMAN_ID_CHAR else c.toString()
                else -> error = if (error.singleOrNull() in '\uD800'..'\uDBFF' && c in '\uDC00'..'\uDFFF') error + c else c.toString()
            }
            numer += (digit ?: 0).toBigInteger()
            if (digit != null && digit !in minDigit until minDigit + useBase)
                (if (useSystem == NumSystem.ROMAN && isValid) error = st + ROMAN_ID_CHAR else nonstandardInput = true)
        }

        if (!isValid) return Pair(ZERO, ZERO)
        if ((useSystem == NumSystem.BALANCED && neg) || (useSystem in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A && (point || rep)))
            nonstandardInput = true
        if (useSystem == NumSystem.BALANCED && complement) {   /* must come after nonstandardInput check */
            complement = false
            return Pair(ONE, ZERO)
        }
        if (!isNumber && fractionChar == 0.toChar()) return default
        denom *= prePointRecurr
        if (denomSub != denom) {
            numer -= numerSub
            denom -= denomSub
        }
        numer *= prePointRecurr
        if (fractionChar != 0.toChar()) with(FRACTION_CHARS[fractionChar] ?: Pair(ZERO, ONE)) {
            val fracDenom = if (second < TEN) second else bigBase
            numer = numer * fracDenom + denom * first
            denom *= fracDenom
            format = if (isNumber) QFormat.MIXED else QFormat.FRACTION
        }
        if (neg) numer = -numer
            else if (leftPad > -1) numer -= denom * overflowNumber(leftPad, bigBase, useSystem)
        return reduceFraction(numer, denom)
    }

    private fun parseRoman(st: String): BigFraction {
        if (st.isEmpty() /*|| 'ß' in st*/) return Pair(ZERO, ZERO) /* would get upper-cased to SS */
        val negative = if (st.startsWith('-')) 1 else if (st.startsWith("..") && !st.all { it == '.'}) { complement = true; 2 } else 0
        val stTrimmed = st.substring(negative).toUpperCase(Locale.ENGLISH).replace('!', '|')
        val fracPos = stTrimmed.indexOfAny(ROMAN_UNCIAE.keys.toCharArray())
        var stInt = (if (fracPos == -1) stTrimmed else stTrimmed.substring(0, fracPos)) + "N"
        for ((key, value) in ROMAN_APOSTROPHUS + ROMAN_OTHER) stInt = stInt.replace(key, value)
        var n = ZERO
        var regex = ""
        for (i in stInt.indices) if (stInt[i] !in "|N") {
            val d = ROMAN_DIGITS[stInt[i]] ?: return Pair(ZERO, ZERO) /* invalid character */
            var sign = 0
            var j = i + 1
            while (sign == 0 && j < stInt.length) sign = (d - (ROMAN_DIGITS[stInt[j++]] ?: 0)).sign
            n += (sign * d).toBigInteger()
        } else {
            val high = (n / 100_000.toBigInteger()).toInt() % 10
            val low = (n % 100_000.toBigInteger()).toInt()
            regex += (if (regex.all { it in "\\|" } && high > 0) "\\|(${ROMAN_100_000_R[high]}|${ROMAN_1_R[high]}\\|)" else "\\|") +
                ROMAN_100_000_R[low / 100_000] + ROMAN_10_000_R[low / 10_000 % 10] + ROMAN_1000_R[low / 1000 % 10] +
                    ROMAN_100_R[low / 100 % 10] + ROMAN_10_R[low / 10 % 10] + ROMAN_1_R[low % 10]
            if (stInt[i] == '|') n *= 100_000.toBigInteger()
        }
        if (!Regex("${regex}N").matches("|$stInt") && !(stInt == "NN" && fracPos == -1)) nonstandardInput = true
        var unciae = 0
        if (fracPos > -1) for (c in stTrimmed.substring(fracPos))
            unciae += ROMAN_UNCIAE[c] ?: return Pair(ZERO, ZERO) /* invalid character */
        if ((unciae >= 6 && stTrimmed[fracPos] != 'S') || unciae >= 12) nonstandardInput = true
        n = TWELVE * n + unciae.toBigInteger()
        return reduceFraction(when (negative) {
               0 ->  n
               1 -> -n
            else ->  n - TWELVE * TEN.pow(complementDigits(n / TWELVE + ONE, 10))
        }, TWELVE)
    }

    /*   O u t p u t   */

    override fun toString(): String = toString(aFormat = format)
    fun toString(withBaseSystem: Boolean = false, aFormat: QFormat = format) = when (aFormat) {
        QFormat.POSITIONAL -> toPositional()
        QFormat.FRACTION   -> toFraction()
        QFormat.MIXED      -> toMixed()
        QFormat.CONTINUED  -> toContinued()
        QFormat.EGYPTIAN   -> toMixed() // for now
        QFormat.UNICODE    -> toUnicode()
    } + if (withBaseSystem && aFormat != QFormat.UNICODE) ((if (system != NumSystem.ROMAN) (base.toString().map {
        SUBSCRIPT_DIGITS[it.toInt() - 48]
    }.joinToString("")) else " ") + MainActivity.numSystemsSuper[system.ordinal]) else ""

    fun toFraction() = if (denominator != ONE) "${intToBase(numerator)}/${intToBase(denominator)}" else intToBase(numerator)
    fun toMixed() = if (denominator > ONE && numerator.abs() > denominator) {
        intToBase(numerator / denominator - (if (complement) ONE else ZERO)) + '_' +
            intToBase((numerator % denominator + (if (complement) denominator else ZERO)).let {
                if (system == NumSystem.BALANCED) it else it.abs()
            }) + '/' + intToBase(denominator)
    } else toFraction()
    fun toContinued() = if (denominator > ONE) continuedFraction(numerator, denominator) else toPositional()

    fun toPositional(): String {
        if (denominator == ZERO) return if (numerator == ZERO) "無" else "∞"
        if (system == NumSystem.ROMAN) with(toRoman()) { if (this != "") return this }
        if (denominator == ONE) return intToBase(numerator)
        if (system in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A) return toMixed()
        val bigBase = base.toBigInteger()
        var c: BigInteger
        var d = denominator
        var nPre = -1
        do {
            nPre++
            c = d.gcd(bigBase)
            d /= c
        } while (c != ONE && nPre <= MainActivity.maxDigitsAfter)
        var nRep = 0
        var power = ONE
        if (d > ONE && nPre <= MainActivity.maxDigitsAfter) do {
            nRep++
            power *= bigBase
        } while ((power - ONE) % d != ZERO && nRep <= MainActivity.maxDigitsAfter - nPre)
        val cutOff = nRep > MainActivity.maxDigitsAfter - nPre
        if (cutOff) nRep = MainActivity.maxDigitsAfter - nPre
        val numPower = numerator * bigBase.pow(nPre + nRep)
        val correction = if (complement && (nRep > 0 || cutOff)) -ONE else
            if (system == NumSystem.BALANCED && numPower.abs() % denominator > denominator / TWO) { if (numerator > ZERO) ONE else -ONE }
                else ZERO
        return intToBase(numPower / denominator + correction, fracDigits = nPre + nRep, repDigits = if (nRep > 0 || cutOff) nRep else -1,
            forceMinus = numerator < ZERO) + (if (cutOff) "…" else "")
    }

    private fun intToBase(a: BigInteger, fracDigits: Int = 0, repDigits: Int = -1, forceMinus: Boolean = false): String {
        if (system == NumSystem.ROMAN) with(toRoman(a, ONE)) { if (this != "") return this }
        val bigBase = base.toBigInteger()
        var x = if (!complement || a >= ZERO) a.abs() else
            a + overflowNumber(max(complementDigits(-a, base) + 2, fracDigits + 1), bigBase, system)
        var digit: Int;  var nDigits = 0
        var st = ""
        while (x != ZERO || nDigits < fracDigits + 1) {
            digit = (x % bigBase).toInt()
            when (system) {
                NumSystem.STANDARD, NumSystem.ROMAN -> { }
                NumSystem.BALANCED -> {
                    if (digit > base / 2) {
                        digit -= base
                        x += bigBase
                    }
                    if (a < ZERO) digit = -digit
                }
                NumSystem.BIJECTIVE_1, NumSystem.BIJECTIVE_A -> if (digit == 0 && a != ZERO) {
                    digit = base
                    x -= bigBase
                }
            }
            st = digitToChar(digit, base, system) + (if (nDigits == 0 || (nDigits - fracDigits) % groupSize != 0) "" else
                if (nDigits == fracDigits) "." else if (MainActivity.groupDigits) " " else "") + (if (nDigits == repDigits) "'" else "") + st
            x /= bigBase
            nDigits++
        }
        return if (complement && a < ZERO) "..$st" else if (system != NumSystem.BALANCED && (a < ZERO || forceMinus)) "-$st" else
            if (system in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A && a == ZERO) "λ" else st
    }

    private tailrec fun continuedFraction(numer: BigInteger, denom: BigInteger, pre: String = ""): String {
        var n: BigInteger = numer / denom
        if (numer < ZERO && numer % denom != ZERO) n--
        val st = "$pre, ${intToBase(n)}"
        return if (n * denom == numer) '[' + st.substring(2).replaceFirst(',', ';') + ']' else continuedFraction(denom, numer - n * denom, st)
    }

    fun toRoman(numer: BigInteger = numerator, denom: BigInteger = denominator, showNonPositive: Boolean = true): String {
        if (denom == ZERO || (numer <= ZERO && !showNonPositive)) return ""
        var bigIntValue = if (!complement || numer >= ZERO) numer.abs() / denom else
            (numer / denom - if (numer % denom == ZERO) ZERO else ONE).let {
                it + overflowNumber(complementDigits(-it, 10) + 1, TEN, NumSystem.ROMAN)
            }
        var result = ""
        var pipes = -1
        while (bigIntValue > ZERO) {
            val intValue = (if (bigIntValue >= 400_000.toBigInteger() || (pipes > -1 && bigIntValue >= 100_000.toBigInteger()))
                bigIntValue % 100_000.toBigInteger() else bigIntValue).toInt()
            result = "|" + ROMAN_100_000[intValue / 100_000] + ROMAN_10_000[intValue / 10_000 % 10] + ROMAN_1000[intValue / 1000 % 10] +
                ROMAN_100[intValue / 100 % 10] + ROMAN_10[intValue / 10 % 10] + ROMAN_1[intValue % 10] + result
            pipes++
            bigIntValue /= (if (intValue < 100_000) 100_000 else 400_000).toBigInteger()
        }
        result = "|".repeat(pipes.coerceAtLeast(0)) + result.removePrefix("|")
        if (numer % denom != ZERO) result += ROMAN_1_12[(numer.abs() % denom * TWELVE / denom).toInt().let {
            if (!complement || numer >= ZERO) it else 12 - it - if (TWELVE % denom == ZERO) 0 else 1
        }]
        if (result.isEmpty()) result = "N"
        if (TWELVE % denom != ZERO) result += " …"
        var i = 0
        for ((key, value) in ROMAN_APOSTROPHUS)
            if (APOSTROPHUS_OPTIONS[MainActivity.apostrophus][i++] == '+') result = result.replace(value, key + if (i > 1) " " else "")
        return (if (numer >= ZERO) "" else if (complement) ".." else "-") +
            if (MainActivity.lowerDigits) result.toLowerCase(Locale.ENGLISH) else result
    }

    fun toUnicode() = if (denominator == ONE && numerator in 0x20.toBigInteger()..0x31FFF.toBigInteger())
        "\"${String(Character.toChars(numerator.toInt()))}\"" else ""

    fun toInterval(resources: Resources): String {
        return ""
        if (numerator == ZERO || denominator == ZERO) return ""
        val toleranceNumer = 87.toBigInteger()
        val toleranceDenom = 86.toBigInteger()  /* 87/86 ≈ 20¢ */
        val numer = numerator.abs().max(denominator)
        var denom = denominator.min(numerator.abs())

        var octaves = 0
        while (denom * TWO <= numer) {
            denom *= TWO
            octaves++
        }
        var intervalName = ""
        for ((i, interval) in (INTERVALS + Pair(TWO, ONE)).withIndex()) {
            val ratioNumer = numer * interval.second
            val ratioDenom = denom * interval.first
            if (ratioNumer * toleranceDenom <= ratioDenom * toleranceNumer) {
                val index = i + INTERVALS.size * octaves
                intervalName = when {
                    ratioNumer == ratioDenom -> getIntervalName(index, true, resources).joinToString(" ")
                    ratioNumer * toleranceNumer >= ratioDenom * toleranceDenom ->
                                                getIntervalName(index, false, resources).joinToString(" ")
                    else -> {
                        val name1 = getIntervalName(index - 1, false, resources)
                        val name2 = getIntervalName(index, false, resources)
                        if (name1[1] == name2[1]) name2[1] = ""
                        if (name1[2] == name2[2]) name2[2] = ""
                        if (name1[3] == name2[3]) name1[3] = ""
                        resources.getString(R.string.between_intervals, name1.drop(1).joinToString(" "), name2.drop(1).joinToString(" "))
                    }
                }
                break
            }
        }
        return intervalName.replace(Regex("( )+"), " ")
    }

    private fun getIntervalName(index: Int, exact: Boolean, resources: Resources): Array<String> {
        val intervalNames = resources.getStringArray(R.array.interval_names)
        if (INTERVALS.size != intervalNames.size) return arrayOf("", "WRONG NUMBER", "OF INTERVAL NAMES", "IN RESOURCES")
        val octaves = index / INTERVALS.size
        val basicIndex = index % INTERVALS.size
        val basicInterval = if (basicIndex > 0 || octaves <= 1) intervalNames[basicIndex].replace(Regex("0[0-7]")) {
            resources.getStringArray(R.array.basic_intervals)[it.value.toInt() + if (octaves == 1) 8 else 0]
        }.split('+') else listOf("", "")
        return try { arrayOf(
            if (exact) "" else "~",
            if (octaves < 2) "" else with(resources.getStringArray(R.array.octaves)) {
                if (octaves - 2 < size) this[octaves - 2] else resources.getString(R.string.octaves, octaves)
            } + if ((basicInterval[0] + basicInterval[1]).isNotEmpty()) " +" else "",
            basicInterval[0],
            if (exact) basicInterval[1] else basicInterval[1].substringBefore(" (")
        )} catch (e: Exception) { arrayOf("", "INVALID", "INTERVAL NAMES", "IN RESOURCES") }
    }

    fun errorMessage(resources: Resources) = "\"$error\" " +
        (if (error.codePointCount(0, error.length) == 1) "(${intToBase(error.codePointAt(0).toBigInteger())}) " else "") +
        resources.getString(when (error.singleOrNull() ?: ' ') {
            '"' -> R.string.err_quote
            '[', ']' -> R.string.err_bracket
            '{', '}' -> R.string.err_brace
            ';' -> R.string.err_semicolon
            ',' -> R.string.err_comma
            '_' -> R.string.err_underscore
            '/' -> R.string.err_slash
            '.' -> R.string.err_twoPoints
            '\'' -> R.string.err_twoReps
            in "@#$€£¥%&-" -> R.string.err_baseTokenOrMinus
            '…' -> R.string.err_ellipsis
            '∞' -> R.string.err_infinity
            '無' -> R.string.err_undefined
            'λ' -> R.string.err_empty
            in FRACTION_CHARS.keys -> R.string.err_fraction
            in ROMAN_CHARS -> R.string.err_onlyRoman
            else -> if (error.lastOrNull() == ROMAN_ID_CHAR) R.string.err_noRoman else R.string.err_generic
        })

            /*  C a l c  -  maybe for V 2.0  */
/*
    operator fun compareTo(other: QNumber) = (numerator * other.denominator - denominator * other.numerator).signum()
    operator fun compareTo(other: BigInteger) = (numerator - denominator * other).signum()
    override operator fun equals(other: Any?) = when (other) {
        is QNumber -> compareTo(other) == 0
        is BigInteger -> compareTo(other) == 0
        else -> throw (IllegalArgumentException("Can only compare a QNumber to a QNumber or a BigInteger"))
    }
    override fun hashCode() = 31 * numerator.hashCode() + denominator.hashCode()

    operator fun plus (other: QNumber) = QNumber(numerator * other.denominator + denominator * other.numerator, denominator * other.denominator, base, system, complement, format)
    operator fun minus(other: QNumber) = QNumber(numerator * other.denominator - denominator * other.numerator, denominator * other.denominator, base, system, complement, format)
    operator fun times(other: QNumber) = QNumber(numerator * other.numerator,                                   denominator * other.denominator, base, system, complement, format)
    operator fun div  (other: QNumber) = QNumber(numerator * other.denominator,                                 denominator * other.denominator, base, system, complement, format)

    fun abs() = QNumber(numerator.abs(), denominator,                     base, system, complement, format)
    fun inv() = QNumber(denominator, numerator,                           base, system, complement, format)
    fun sqr() = QNumber(numerator * numerator, denominator * denominator, base, system, complement, format)
 */
}