package org.tessoft.qonvert

/*
Copyright 2020, 2021, 2022 Anypodetos (Michael Weber)

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

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import java.lang.Exception
import java.math.BigInteger
import java.math.BigInteger.*
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.*
import kotlin.text.StringBuilder

typealias BigFraction = Pair<BigInteger, BigInteger>

val TWO = 2.toBigInteger()
val TWELVE = 12.toBigInteger()
val SPECIAL_NUMBERS = mapOf("∞" to Pair(ONE, ZERO), "-∞" to Pair(ONE, ZERO), "無" to Pair(ZERO, ZERO))
val FRACTION_CHARS  = mapOf('½' to Pair(1, 2), '⅓' to Pair(1, 3), '¼' to Pair(1, 4), '⅕' to Pair(1, 5), '⅙' to Pair(1, 6),
    '⅐' to Pair(1, 7), '⅛' to Pair(1, 8), '⅑' to Pair(1, 9),'⅒' to Pair(1, 10), '⅔' to Pair(2, 3), '¾' to Pair(3, 4),
    '⅖' to Pair(2, 5), '⅗' to Pair(3, 5), '⅘' to Pair(4, 5), '⅚' to Pair(5, 6), '⅜' to Pair(3, 8), '⅝' to Pair(5, 8),
    '⅞' to Pair(7, 8), '↉' to Pair(0, 1)).mapValues { Pair(it.value.first.toBigInteger(), it.value.second.toBigInteger()) }
const val SUBSCRIPT_DIGITS   = "₀₁₂₃₄₅₆₇₈₉"
const val SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

const val GREEK_DIGITS = "\u0000ΑΒΓΔΕϚΖΗΘ\u0000ΙΚΛΜΝΞΟΠϞ\u0000ΡΣΤΥΦΧΨΩϠ"
val GREEK_OTHER = mapOf('ς' to 'ϛ', 'Ϝ' to 'Ϛ', 'ϝ' to 'ϛ', 'Ϙ' to 'Ϟ', 'ϙ' to 'ϟ', 'Ͳ' to 'Ϡ', 'ͳ' to 'ϡ')
val GREEK_CHARS = GREEK_DIGITS + GREEK_DIGITS.lowercase() + GREEK_OTHER.keys.joinToString("") + "ʹ͵○"

val ROMAN_DIGITS        = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000,
    'ↁ' to 5000, 'ↂ' to 10_000, 'ↇ' to 50_000, 'ↈ' to 100_000)
val ROMAN_UNCIAE        = mapOf('.' to 1, '·' to 1, ':' to 2, '∶' to 2, '∴' to 3, '∷' to 4, '⁙' to 5, 'S' to 6)
val ROMAN_APOSTROPHUS   = mapOf("ↀ" to "M", "CCCIↃↃↃ" to "ↈ", "CCIↃↃ" to "ↂ", "CIↃ" to "M", "IↃↃↃ" to "ↇ", "IↃↃ" to "ↁ", "IↃ" to "D")
val APOSTROPHUS_OPTIONS = listOf("--------", "-+------", "--++-++-", "+-++++++")
val ROMAN_OTHER         = mapOf("(((I)))" to "ↈ", "((I))" to "ↂ", "(I)" to "M", "I)))" to "ↇ", "I))" to "ↁ", "I)" to "D",
    "Ⅰ" to "I", "Ⅱ" to "II", "Ⅲ" to "III", "Ⅳ" to "IV", "Ⅴ" to "V", "Ⅵ" to "VI", "Ⅶ" to "VII", "Ⅷ" to "VIII", "Ⅸ" to "IX",
    "Ⅹ" to "X", "Ⅺ" to "XI", "Ⅻ" to "XII", "Ⅼ" to "L", "ↆ" to "L", "Ⅽ" to "C", "Ⅾ" to "D", "Ⅿ" to "M")
val ROMAN_CHARS = (ROMAN_DIGITS.keys + ROMAN_UNCIAE.keys + ROMAN_OTHER.map { it.key[0] } + ROMAN_OTHER.map { it.key[0].lowercaseChar() } +
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

const val GREEK_ID_CHAR = '\u200A'
const val ROMAN_ID_CHAR = '\u200B'
val UNICODE_RANGE = 0x20.toBigInteger()..0x31FFF.toBigInteger()

val INTERVALS = listOf(Pair(1, 1),
    /* 2 */ Pair(256, 243), /* Pythagorean minor second, cf. 243/128 Pythagorean major seventh */
            Pair(16, 15), /* minor or just diatonic semitone, cf. 8/15 just major seventh */
            Pair(10, 9), /* minor = just whole tone, cf. 9/5 just minor seventh */
            Pair(9, 8), /* major = Pythagorean whole tone, cf. 16/9 Pythagorean minor seventh */
    /* 3 */ Pair(32, 27), Pair(6, 5), Pair(5, 4), Pair(81, 64),
    /* 4 */ Pair(4, 3),
    /* T */ Pair(7, 5), Pair(45, 32), Pair(729, 512), Pair(10, 7),
    /* 5 */ Pair(3, 2),
    /* 6 */ Pair(128, 81), Pair(8, 5), Pair(5, 3), Pair(27, 16),
    /* 7 */ Pair(16, 9), Pair(9, 5), Pair(15, 8), Pair(243, 128),
    ).map { Pair(it.first.toBigInteger(), it.second.toBigInteger()) }

enum class QFormat {
    POSITIONAL, POSITIONAL_ALT, FRACTION, MIXED, CONTINUED, EGYPTIAN, GREEK_NATURAL, ROMAN_NATURAL, UNICODE
}
enum class NumSystem {
    STANDARD, BALANCED, BIJECTIVE_1, BIJECTIVE_A, /*FACTORIAL,*/ GREEK, ROMAN
}

enum class EgyptianMethod {
    GREEDY, BINARY, GOLOMB /* same results as continued fractions method */, PAIRING, OFF
}

enum class DisplayMode {
    STANDARD, PRETTY, COMPATIBLE
}

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
        if (format in setOf(QFormat.GREEK_NATURAL, QFormat.ROMAN_NATURAL)) {
            this.base = 10
            this.system = if (format == QFormat.GREEK_NATURAL) NumSystem.GREEK else NumSystem.ROMAN
            this.format = QFormat.POSITIONAL
        } else changeBase(base, system, complement)
    }

    //private fun store(numer: BigInteger = ZERO, denom: BigInteger = ONE) = store(Pair(numer, denom))
    private fun store(a: BigFraction) {
        numerator = a.first
        denominator = a.second
    }
    fun changeBase(base: Int, system: NumSystem, complement: Boolean) {
        this.base = base
        this.system = allowedSystem(base, system).first
        this.complement = complement && this.system != NumSystem.BALANCED && numerator < ZERO
        groupSize = if (base in setOf(2, 4, 8, 16, 32, 64)) 4 else 3
    }

    constructor(st: String, base: Int, system: NumSystem): this(base = base, system = system) {
        store(parseInput(st))
    }

    constructor(preferencesEntry: String): this() {
        val split = preferencesEntry.split('/')
        try {
            store(Pair(split[0].toBigInteger(), split[1].toBigInteger()))
            changeBase(split[2].toInt(), NumSystem.valueOf(split[3]), split[4].toBoolean())
            format = QFormat.valueOf(split[5])
        } catch (e: Exception) { error = "\u0015" }
    }

    fun toPreferencesString() = "$numerator/$denominator/$base/$system/$complement/$format"
    fun copy(format: QFormat = this.format) = QNumber(numerator, denominator, base, system, complement, format, error)

    /*   I n p u t   */

    private fun parseInput(st: String): BigFraction {
        var stTrimmed = st.trimStart()
        if (stTrimmed.startsWith('"'))
            return Pair(if (stTrimmed.length > 1) { format = QFormat.UNICODE; stTrimmed.codePointAt(1).toBigInteger() } else ZERO, ONE)
        stTrimmed = stTrimmed.filterNot { it in " \t\n\r" }
        val bracketMinus = stTrimmed.startsWith("-{") || stTrimmed.startsWith("-[")
        if (bracketMinus) stTrimmed = stTrimmed.substring(1)
        val continued = stTrimmed.startsWith('[') || stTrimmed.endsWith(']')
        val egyptian = stTrimmed.startsWith('{') || stTrimmed.endsWith('}')
        if (continued) stTrimmed = stTrimmed.removePrefix("[").removeSuffix("]") else
            if (egyptian) stTrimmed = stTrimmed.removePrefix("{").removeSuffix("}")
        val semi = stTrimmed.indexOf(';')
        var x = Pair(ONE, ZERO)
        when {
            semi == -1 -> { /* positional number or fraction */
                x = parseFraction(stTrimmed)
                if (!isValid) when (stTrimmed) {
                    "🐋💐" -> { error = ""; return Pair(38607030735492.toBigInteger(), ONE) }
                    "🚕"   -> { error = ""; return Pair(77002143071279.toBigInteger(), ONE) }
                    "👾"   -> { error = ""; return Pair(2985842461868634769.toBigInteger(), ONE) }
                }
            }
            egyptian -> { /* Egyptian fraction */
                x = parseFraction(stTrimmed.substring(0, semi))
                for ((i, subSt) in stTrimmed.substring(semi + 1).split(',').reversed().withIndex()) {
                    val c = parseFraction(subSt, if (i > 0) Pair(ZERO, ONE) else Pair(ONE, ZERO))
                    x = reduceFraction(c.first * x.first + c.second * x.second, c.first * x.second)
                }
            }
            else -> {  /* continued fraction */
                for ((i, subSt) in ((stTrimmed.substring(semi + 1).split(',')).reversed() + stTrimmed.substring(0, semi)).withIndex()) {
                    val c = parseFraction(subSt, if (i > 0) Pair(ZERO, ONE) else Pair(ONE, ZERO))
                    x = reduceFraction(c.first * x.first + c.second * x.second, c.second * x.first)
                }
                format = QFormat.CONTINUED
            }
        }
        if (bracketMinus) x = reduceFraction(-x.first, x.second)
        if (continued) format = QFormat.CONTINUED else if (egyptian) format = QFormat.EGYPTIAN
        if (x.first >= ZERO) complement = false /* shouldn’t be necessary, but feels safer */
        return x
    }

    private fun parseDMS(st: String, default: BigFraction = Pair(ZERO, ONE)): BigFraction {
        val deg = st.indexOf('°')
        val second = st.endsWith('"')
        if (deg == -1 && !second) return parseFraction(st, default)
        val minute = st.indexOf('\'', deg)
        val d = if (deg > -1) parseFraction(st.substring(0, deg)) else Pair(ZERO, ONE)
        val m = if (minute > -1 || !second) parseFraction(st.substring(deg + 1, if (minute > -1) minute else st.length)) else Pair(ZERO, ONE)
        val s = if (minute > -1 ||  second) parseFraction(st.substring((if (minute > -1) minute else deg) + 1).removeSuffix("\"")) else Pair(ZERO, ONE)
        return reduceFraction(d.first  * m.second * s.second * 3600.toBigInteger() +
                              d.second * m.first  * s.second *   60.toBigInteger() * (if (deg > -1 && st[0] == '-') -ONE else ONE) +
                              d.second * m.second * s.first                        * (if ((deg > -1 || minute > -1) && st[0] == '-') -ONE else ONE),
                              3600.toBigInteger() * d.second * m.second * s.second)
    }

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
            reduceFraction(numer.first    * denom.second * integer.second * (if (under > -1 && st[0] == '-') -ONE else ONE) +
                           numer.second   * denom.first  * integer.first,
                           integer.second * numer.second * denom.first)
        }
    }

    private fun parsePositional(st: String, default: BigFraction = Pair(ZERO, ONE)): BigFraction {
        if (!isValid) return Pair(ZERO, ZERO)
        with(SPECIAL_NUMBERS[st]) { if (this != null) return this }
        if (st.trimStart { it in "-." }.firstOrNull() ?: ' ' in GREEK_CHARS) with(parseGreek(st)) { if (second != ZERO) return this }
        val tokenBaseSystem = MainActivity.tokenBaseSystem(st.firstOrNull())
        val (useBase, useSystem) = tokenBaseSystem ?: Pair(base, system)
        var startSt = if (tokenBaseSystem == null) 0 else 1
        if (useSystem == NumSystem.ROMAN) with(parseRoman(st.substring(startSt))) { if (second != ZERO) return this }
        val bigBase = useBase.toBigInteger()
        var numer = ZERO;      var numerSub = ZERO
        var denom = ONE;       var denomSub = ZERO
        var neg = false;       var point = false
        var rep = false;       var prePointRecurr = ONE
        var isNumber = false;  var fractionChar = 0.toChar()
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
                    digit = c.digitToInt()
                    if (useSystem == NumSystem.BIJECTIVE_A) nonstandardInput = true
                }
                in 'A'..'Z' -> digit = c.code - 65 + if (useSystem == NumSystem.BALANCED && c > 'I') -26 else valueOfA
                in 'a'..'z' -> digit = c.code - 97 + if (useSystem == NumSystem.BALANCED && c > 'i') -26 else valueOfA
                '-' -> if (numer == ZERO && !neg && !point && !rep && leftPad == -1) neg = true else error = c.toString()
                '.' -> if (!point) point = true else error = c.toString()
                '\'' -> if (!rep) {
                    rep = true
                    numerSub = numer
                    denomSub = denom
                } else error = c.toString()
                in FRACTION_CHARS.keys -> if (!point && !rep) fractionChar = c else error = c.toString()
                'ʹ' -> error = c.toString()
                in GREEK_CHARS -> error = if (useSystem == NumSystem.GREEK) st + GREEK_ID_CHAR else c.toString()
                in ROMAN_CHARS -> error = if (useSystem == NumSystem.ROMAN) st + ROMAN_ID_CHAR else c.toString()
                else -> error = if (error.singleOrNull() in '\uD800'..'\uDBFF' && c in '\uDC00'..'\uDFFF') error + c else c.toString()
            }
            numer += (digit ?: 0).toBigInteger()
            if (digit != null && digit !in minDigit until minDigit + useBase) {
                if (useSystem in setOf(NumSystem.GREEK, NumSystem.ROMAN) && isValid)
                    error = st + (if (useSystem == NumSystem.GREEK) GREEK_ID_CHAR else ROMAN_ID_CHAR) else nonstandardInput = true
            }
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
        with(reduceFraction(numer, denom)) {
            if (st.firstOrNull() == '?') {
                if (this.first in ONE..MainActivity.historyList.size.toBigInteger() && this.second == ONE)
                    MainActivity.historyList[MainActivity.historyList.size - this.first.toInt()].number.let {
                        format = it.format
                        return Pair(it.numerator, it.denominator)
                    } else {
                    error = st
                    return Pair(ZERO, ZERO)
                }
            } else return this
        }
    }

    private fun parseGreek(st: String): BigFraction {
        val negative = if (st.startsWith('-')) 1 else if (st.startsWith("..")) { complement = true; 2 } else 0
        var stTrimmed = st.substring(negative).removeSuffix("ʹ")
        for ((key, value) in GREEK_OTHER) stTrimmed = stTrimmed.replace(key, value)
        var n = ZERO
        var mult = ONE
        for (c in stTrimmed) {
            when (val x = "${GREEK_DIGITS}ʹ͵.○ ".indexOf(c, ignoreCase = true)) {
                -1, 30 -> return Pair(ZERO, ZERO)
                in  1.. 9 -> n += (       x      ).toBigInteger() * mult
                in 11..19 -> n += ( 10 * (x - 10)).toBigInteger() * mult
                in 21..29 -> n += (100 * (x - 20)).toBigInteger() * mult
                31 -> mult *= 1000.toBigInteger()
                32 -> n *= 10_000.toBigInteger()
                33, 34 -> { }
            }
            if (c !in "͵ ") mult = ONE
        }
        if (stTrimmed.uppercase().filter { it != ' ' } != toGreek(n, ONE).uppercase().filter { it != ' ' }.removeSuffix("ʹ")) nonstandardInput = true
        return BigFraction(when (negative) {
               0 ->  n
               1 -> -n
            else ->  n - TEN.pow(complementDigits(n + ONE, 10))
        }, ONE)
    }

    private fun parseRoman(st: String): BigFraction {
        if (st.isEmpty() || 'ß' in st) return Pair(ZERO, ZERO) /* would be upper-cased to SS */
        val negative = if (st.startsWith('-')) 1 else if (st.startsWith("..") && !st.all { it == '.'}) { complement = true; 2 } else 0
        val stTrimmed = st.substring(negative).uppercase().replace('!', '|')
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
            if (high >= 0 && low >= 0) regex += (if (regex.all { it in "\\|" } && high > 0) "\\|(${ROMAN_100_000_R[high]}|${ROMAN_1_R[high]}\\|)" else "\\|") +
                ROMAN_100_000_R[low / 100_000] + ROMAN_10_000_R[low / 10_000 % 10] + ROMAN_1000_R[low / 1000 % 10] +
                    ROMAN_100_R[low / 100 % 10] + ROMAN_10_R[low / 10 % 10] + ROMAN_1_R[low % 10]
            if (stInt[i] == '|') n *= 100_000.toBigInteger()
        }
        if (n < ZERO || !Regex("${regex}N").matches("|$stInt") && !(stInt == "NN" && fracPos == -1)) nonstandardInput = true
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

    fun usefulFormat(aFormat: QFormat) = if (!isValid) false else when (aFormat) {
        QFormat.POSITIONAL -> system !in setOf(NumSystem.BIJECTIVE_1, NumSystem.BIJECTIVE_A, NumSystem.GREEK) || denominator <= ONE
        QFormat.POSITIONAL_ALT -> gcdPower(base.toBigInteger()).first.let {
            (system == NumSystem.STANDARD && it == ONE) || (system == NumSystem.BALANCED && it == TWO)
        }
        QFormat.FRACTION -> denominator != ONE && (system !in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A || denominator != ZERO)
        QFormat.CONTINUED, QFormat.EGYPTIAN -> denominator > ONE
        QFormat.MIXED -> denominator > ONE && numerator.abs() > denominator
        QFormat.GREEK_NATURAL -> system != NumSystem.GREEK && denominator == ONE && numerator > ZERO
        QFormat.ROMAN_NATURAL -> system != NumSystem.ROMAN && denominator == ONE && numerator > ZERO
        QFormat.UNICODE -> denominator == ONE && numerator in UNICODE_RANGE
    }

    private fun gcdPower(bigBase: BigInteger): Pair<BigInteger, Int> {
        var c: BigInteger
        var d = denominator
        var nPre = -1
        do {
            nPre++
            c = d.gcd(bigBase)
            d /= c
        } while (c != ONE && nPre <= MainActivity.maxDigitsAfter)
        return Pair(d, nPre)
    }

    override fun toString() = toString(aFormat = format)
    fun toString(withBaseSystem: Boolean = false, mode: DisplayMode = DisplayMode.STANDARD, aFormat: QFormat = format,
            aEgyptianMethod: EgyptianMethod = EgyptianMethod.OFF): String {
        val result = when (aFormat) {
            QFormat.POSITIONAL     -> toPositional()
            QFormat.POSITIONAL_ALT -> toPositional(alt = true)
            QFormat.FRACTION       -> toFraction()
            QFormat.MIXED          -> toMixed()
            QFormat.CONTINUED      -> toContinued()
            QFormat.EGYPTIAN       -> toEgyptian(aEgyptianMethod)
            QFormat.GREEK_NATURAL, QFormat.ROMAN_NATURAL -> ""
            QFormat.UNICODE        -> toUnicode()
        } + if (withBaseSystem && mode == DisplayMode.STANDARD && aFormat !in setOf(QFormat.GREEK_NATURAL, QFormat.ROMAN_NATURAL, QFormat.UNICODE) &&
                system !in setOf(NumSystem.GREEK, NumSystem.ROMAN))
                    base.toString().map { SUBSCRIPT_DIGITS[it.digitToInt()] }.joinToString("") + MainActivity.numSystemsSuper[system.ordinal]
                        else ""
        return when (mode) {
            DisplayMode.STANDARD   -> result
            DisplayMode.PRETTY     -> makePretty(result).first
            DisplayMode.COMPATIBLE -> makeCompatible(result, system).first
        }
    }

    fun toDMS() = if (denominator > ZERO) {
        (if (numerator.abs() > denominator) intToBase(numerator / denominator - (if (complement) ONE else ZERO), keraia = false) + "° " else "") +
            "" /////////////
    } else toPositional()

    fun toFraction() = if (denominator != ONE) "${intToBase(numerator, keraia = false)}/${intToBase(denominator)}" else intToBase(numerator)
    fun toMixed() = if (denominator > ONE && numerator.abs() > denominator) {
        intToBase(numerator / denominator - (if (complement) ONE else ZERO), keraia = false) + '_' +
            intToBase((numerator % denominator + (if (complement) denominator else ZERO)).let {
                if (system == NumSystem.BALANCED) it else it.abs()
            }, keraia = false) + '/' + intToBase(denominator)
    } else toFraction()

    fun toContinued() = if (denominator > ONE) continuedFraction(numerator, denominator) else toPositional()
    fun toEgyptian(method: EgyptianMethod): String {
        if (method == EgyptianMethod.OFF) return ""
        if (denominator <= ONE) return toPositional()
        val integer = numerator / denominator - if (numerator < ZERO) ONE else ZERO
        val fracNumer = numerator - integer * denominator
        return "{${intToBase(integer)}; " + when (method) {
            EgyptianMethod.GREEDY  -> egyptianFractionGreedy(fracNumer)
            EgyptianMethod.BINARY  -> egyptianFractionBinary(fracNumer)
            EgyptianMethod.GOLOMB  -> egyptianFractionGolomb(fracNumer)
            EgyptianMethod.PAIRING -> egyptianFractionPairing(fracNumer)
            EgyptianMethod.OFF     -> ""
        } + "}"
    }

    fun toPositional(alt: Boolean = false): String {
        if (denominator == ZERO) return if (numerator == ZERO) "無" else "∞"
        if (system == NumSystem.ROMAN) with(toRoman()) { if (this != "") return this }
        if (denominator == ONE && !alt) return intToBase(numerator)
        if (numerator == ZERO && alt && system == NumSystem.STANDARD) return with(digitToChar(base - 1, base, system)) { "..$this$this.'$this" }
        if (system in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A) return toMixed()
        val bigBase = base.toBigInteger()
        val (d, nPre) = gcdPower(bigBase)
        val useAlt = alt && ((system == NumSystem.STANDARD && d == ONE) || (system == NumSystem.BALANCED && d == TWO))
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
            if (useAlt && !cutOff) (if ((system == NumSystem.STANDARD) == (numerator > ZERO || complement)) -ONE else ONE) else
            if (system == NumSystem.BALANCED && numPower.abs() % denominator > denominator / TWO) (if (numerator > ZERO) ONE else -ONE)
                else ZERO
        return intToBase(numPower / denominator + correction, fracDigits = nPre + nRep, repDigits = if (nRep > 0 || cutOff) nRep else -1,
            forceMinus = numerator < ZERO) + (when {
                cutOff -> "…"
                useAlt && system == NumSystem.STANDARD ->
                    (if (denominator == ONE) ".'" else "'") + digitToChar(base - 1, base, NumSystem.STANDARD)
                else -> ""
            })
    }

    private fun intToBase(a: BigInteger, fracDigits: Int = 0, repDigits: Int = -1, forceMinus: Boolean = false, keraia: Boolean = true): String {
        if (system == NumSystem.GREEK) return toGreek(a, ONE, keraia)
        if (system == NumSystem.ROMAN) return toRoman(a, ONE)
        if (system in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A && a == ZERO) return "/"
        val bigBase = base.toBigInteger()
        val x = if (!complement || a >= ZERO) a.abs() else
            a + overflowNumber(max(complementDigits(-a, base) + 2, fracDigits + 1), bigBase, system)
        val s = StringBuilder(x.toString(base).padStart(fracDigits + 1, '0'))
        if (system in setOf(NumSystem.BALANCED, NumSystem.BIJECTIVE_1, NumSystem.BIJECTIVE_A)) {
            var roll = 0
            for (i in s.length - 1 downTo 0) {
                var digit = s[i].code - (if (s[i] in '0'..'9') 48 else 87) + roll
                val changed = roll != 0
                if (system == NumSystem.BALANCED && digit > base / 2) {
                    digit -= base
                    roll = 1
                } else if (system in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A && digit <= 0) {
                    digit += base
                    roll = -1
                } else roll = 0
                if (system == NumSystem.BALANCED && a < ZERO) digit = -digit
                if (changed || roll != 0 || (system == NumSystem.BALANCED && a < ZERO) || system == NumSystem.BIJECTIVE_A)
                    s[i] = digitToChar(digit, base, system)
            }
            when (roll) {
                 1 -> s.insert(0, if (a >= ZERO) '1' else 'z')
                -1 -> s.deleteAt(0)
            }
        }
        if (MainActivity.groupDigits) {
            val nDigits = s.length
            for (i in s.length - fracDigits % groupSize downTo 1 step groupSize) s.insert(i, ' ')
            val fracPos = s.length - 1 - fracDigits - fracDigits / groupSize
            if (fracDigits in 1..nDigits) s[fracPos] = '.'
            if (repDigits in 0..nDigits) s.insert(fracPos + 1 + fracDigits - repDigits + (fracDigits - repDigits) / groupSize, '\'')
        } else {
            if (fracDigits in 1..s.length) s.insert(s.length - fracDigits, '.')
            if (repDigits in 0..s.length) s.insert(s.length - repDigits, '\'')
        }
        return (if (complement && a < ZERO) ".." else if (system != NumSystem.BALANCED && (a < ZERO || forceMinus)) "-" else "") +
            s.trim().toString().let {
                if (MainActivity.lowerDigits) it.lowercase() else it.uppercase()
            }
    }

    private tailrec fun continuedFraction(numer: BigInteger, denom: BigInteger, pre: String = ""): String {
        var n: BigInteger = numer / denom
        if (numer < ZERO && numer % denom != ZERO) n--
        val st = "$pre, ${intToBase(n)}"
        return if (n * denom == numer) '[' + st.substring(2).replaceFirst(',', ';') + ']' else continuedFraction(denom, numer - n * denom, st)
    }

    private fun egyptianFractionGreedy(fracNumer: BigInteger): String {
        val result = StringBuilder()
        var numer = fracNumer
        var denom = denominator
        do {
            val n = if (numer > ONE) denom / numer + ONE else denom
            result.append(", ${intToBase(n)}")
            with(reduceFraction(numer * n - denom, denom * n)) {
                numer = first
                denom = second
            }
        } while (denom > ONE && result.length <= MainActivity.maxDigitsAfter)
        return result.removePrefix(", ").toString() + if (denom > ONE) ", …" else ""
    }

    private fun egyptianBinaryList(r: BigInteger) =
        r.toString(2).reversed().mapIndexed { i, c -> if (c == '1') TWO.pow(i) else null }.filterNotNull()
    private fun egyptianFractionBinary(fracNumer: BigInteger): String {
        var p = TWO
        while (fracNumer * p % denominator >= TWO * p) p *= TWO
        return (egyptianBinaryList(fracNumer * p % denominator).map { p / it * denominator } +
                egyptianBinaryList(fracNumer * p / denominator).map { p / it }).sorted().joinToString { intToBase(it) }
    }

    private fun egyptianFractionGolomb(fracNumer: BigInteger): String {
        val result = StringBuilder()
        var numer = fracNumer
        var denom = denominator
        while (numer > ONE && result.length <= MainActivity.maxDigitsAfter) {
            val modInv = numer.modInverse(denom)
            result.insert(0, "${intToBase(modInv * denom)}, ")
            numer = (modInv * numer - ONE) / denom
            denom = modInv
        }
        return "${intToBase(denom)}, $result".removeSuffix(", ") + if (numer > ONE) ", …" else ""
    }

    private fun egyptianFractionPairing(fracNumer: BigInteger): String {
        val denomMap = hashMapOf(denominator to fracNumer)
        var iterations = 0
        val cutOff = 10 * sqrt(MainActivity.maxDigitsAfter.toDouble()).toInt()
        do {
            var found = false
            iterations++
            for (entry in denomMap) if (entry.value > ONE) {
                if (entry.key % TWO == ZERO) {
                    val newKey = entry.key / TWO
                    denomMap[newKey] = (denomMap[newKey] ?: ZERO) + entry.value / TWO
                } else {
                    val newKey1 = (entry.key + ONE) / TWO
                    val newKey2 = ((entry.key + ONE) * entry.key) / TWO
                    denomMap[newKey1] = (denomMap[newKey1] ?: ZERO) + entry.value / TWO
                    denomMap[newKey2] = (denomMap[newKey2] ?: ZERO) + entry.value / TWO
                }
                if (entry.value % TWO == ZERO) denomMap.remove(entry.key) else denomMap[entry.key] = ONE
                found = true
                break
            }
        } while (found && iterations <= cutOff)
        return if (iterations <= cutOff) denomMap.keys.sorted().joinToString { intToBase(it) } else "…"
    }

    fun toGreek(numer: BigInteger = numerator, denom: BigInteger = denominator, keraia: Boolean = true): String {
        if (denom != ONE) return "" else if (numer == ZERO) return "○"
        var value = if (!complement || numer >= ZERO) numer.abs() else
            numer + overflowNumber(complementDigits(-numer, 10) + 2, TEN, NumSystem.GREEK)
        val s = StringBuilder(if (keraia) "ʹ" else "")
        var i = 0
        while (value > ZERO) {
            if (i % 4 == 0 && i > 0) s.insert(0, " . ")
            with(GREEK_DIGITS[(10 * (i % 4) + (value % TEN).toInt()) % 30]) {
                if (this != '\u0000') s.insert(0, (if (i % 4 == 3) "͵" else "") + this)
            }
            value /= TEN
            i++
        }
        if (numer < ZERO) s.insert(0, if (complement) ".." else "-")
        with(s.toString().replace("  ", " ")) {
            return if (MainActivity.lowerDigits) lowercase().replace('ς', 'σ') else this
        }
    }

    fun toRoman(numer: BigInteger = numerator, denom: BigInteger = denominator): String {
        if (denom == ZERO) return ""
        var bigIntValue = if (!complement || numer >= ZERO) numer.abs() / denom else
            (numer / denom - if (numer % denom == ZERO) ZERO else ONE).let {
                it + overflowNumber(complementDigits(-it, 10) + 1, TEN, NumSystem.ROMAN)
            }
        val s = StringBuilder()
        var pipes = -1
        while (bigIntValue > ZERO) {
            val intValue = (if (bigIntValue >= 400_000.toBigInteger() || (pipes > -1 && bigIntValue >= 100_000.toBigInteger()))
                bigIntValue % 100_000.toBigInteger() else bigIntValue).toInt()
            s.insert(0, "|" + ROMAN_100_000[intValue / 100_000] + ROMAN_10_000[intValue / 10_000 % 10] + ROMAN_1000[intValue / 1000 % 10] +
                ROMAN_100[intValue / 100 % 10] + ROMAN_10[intValue / 10 % 10] + ROMAN_1[intValue % 10])
            pipes++
            bigIntValue /= (if (intValue < 100_000) 100_000 else 400_000).toBigInteger()
        }
        if (s.firstOrNull() == '|') s.deleteAt(0)
        s.insert(0, "|".repeat(pipes.coerceAtLeast(0)))
        if (numer % denom != ZERO) s.append(ROMAN_1_12[(numer.abs() % denom * TWELVE / denom).toInt().let {
            if (!complement || numer >= ZERO) it else 12 - it - if (TWELVE % denom == ZERO) 0 else 1
        }])
        if (s.isEmpty()) s.append("N")
        if (TWELVE % denom != ZERO) s.append(" …")
        var i = 0
        var result = s.toString()
        for ((key, value) in mapOf("CCCC" to "CD") + ROMAN_APOSTROPHUS)
            if (APOSTROPHUS_OPTIONS[MainActivity.apostrophus][i++] == '+') result = result.replace(value, key + if (i > 2) " " else "")
        return (if (numer >= ZERO) "" else if (complement) ".." else "-") +
                (if (MainActivity.lowerDigits) result.lowercase() else result).trimEnd()
    }

    fun toUnicode() = if (denominator == ONE && numerator in UNICODE_RANGE) "\"${String(Character.toChars(numerator.toInt()))}\"" else ""

    fun toInterval(resources: Resources): String {
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
        var p = -1
        for ((i, interval) in (INTERVALS + Pair(TWO, ONE)).withIndex()) {
            val index = i + INTERVALS.size * octaves
            when ((numer * interval.second - denom * interval.first).signum()) {
                -1 -> {
                    when {
                        numer * toleranceNumer * interval.second >= denom * toleranceDenom * interval.first ->
                            intervalName = getIntervalName(index, false, resources).joinToString(" ")
                        p > -1 -> intervalName = getIntervalName(p, false, resources).joinToString(" ")
                        else -> {
                            val name1 = getIntervalName(index - 1, false, resources)
                            val name2 = getIntervalName(index, false, resources)
                            if (name1[1] == name2[1]) name2[1] = ""
                            if (name1[2] == name2[2]) name2[2] = ""
                            if (name1[3] == name2[3]) name1[3] = ""
                            intervalName = resources.getString(R.string.between_intervals,
                                name1.drop(1).joinToString(" "), name2.drop(1).joinToString(" "))

                            val between = resources.getString(R.string.interval_grammar_between).split(',')
                            for ((j, nom) in resources.getString(R.string.interval_grammar_nom).split(',').withIndex())
                                intervalName = intervalName.replace(nom, between[j])

                        }
                    }
                    break
                }
                0 -> {
                    intervalName = getIntervalName(index, true, resources).joinToString(" ")
                    break
                }
                1 -> if (numer * toleranceDenom * interval.second <= denom * toleranceNumer * interval.first) p = index
            }
        }
        val firstLetter = intervalName.indexOfFirst { it.isLetter() }
        return ((intervalName.substring(0, firstLetter + 1).uppercase() +
                intervalName.substring(firstLetter + 1)).replace(Regex("( )+"), " ")).trim()
    }

    private fun getIntervalName(index: Int, exact: Boolean, resources: Resources): Array<String> {
        val intervalNames = resources.getStringArray(R.array.interval_names)
        if (INTERVALS.size != intervalNames.size) return arrayOf("", "WRONG NUMBER", "OF INTERVAL NAMES", "IN RESOURCES")
        val octaves = index / INTERVALS.size
        val basicIndex = index % INTERVALS.size
        val basicInterval = if (basicIndex > 0 || octaves <= 1) intervalNames[basicIndex].replace(Regex("0[0-7]")) {
            resources.getStringArray(R.array.basic_intervals)[it.value.toInt() + if (octaves == 1) 8 else 0]
        }.split('*') else listOf("", "")
        return try { arrayOf(
            if (exact) "" else "~",
            if (octaves < 2) "" else with(resources.getStringArray(R.array.octaves)) {
                if (octaves - 2 < size) this[octaves - 2] else resources.getString(R.string.octaves, octaves)
            } + if ((basicInterval[0] + basicInterval[1]).isNotEmpty()) " +" else "",
            basicInterval[0],
            if (!exact) basicInterval[1].substringBefore(" (") else
                if (octaves > 0) basicInterval[1].substringBeforeLast(',') else basicInterval[1]
        )} catch (e: Exception) { arrayOf("", "INVALID", "INTERVAL NAMES", "IN RESOURCES") }
    }

    fun play(context: Context, onlyRecreate: Boolean = false) {
        val ratio = abs(numerator.toDouble() / denominator.toDouble()).toFloat()
        if (ratio in 1/128.0..128.0) {
            if (!onlyRecreate) {
                val baseFrequency = 440 / 1.5.pow(round(ln(ratio) / ln(1.5) / 2))
                OneTimeBuzzer(baseFrequency, 100, 1.5).play()
                Timer().schedule(1500) { OneTimeBuzzer(baseFrequency * ratio, 100, 1.5).play() }
                Timer().schedule(3000) {
                    OneTimeBuzzer(baseFrequency, 50, 2.0).play()
                    OneTimeBuzzer(baseFrequency * ratio, 50, 2.0).play()
                }
            }
            val wave = WaveView(context)
            wave.ratio = ratio
            wave.autoClose = !onlyRecreate
            MainActivity.playDialog = AlertDialog.Builder(context)
                .setTitle(R.string.interval)
                .setMessage(toInterval(context.resources))
                .setView(wave)
                .setNegativeButton(R.string.close) { _, _ -> }
                .create()
            MainActivity.playDialog?.show()
            wave.layoutParams.height = (160 * context.resources.displayMetrics.scaledDensity).toInt()
            if (!onlyRecreate) Timer().apply {
                MainActivity.playDialogTimer = this
                schedule(5000) { MainActivity.playDialog?.cancel() }
            }
        } else {
            val toast = Toast.makeText(context, context.resources.getString(R.string.no_interval,
                QNumber(ONE, 128.toBigInteger(), base, system, format = QFormat.FRACTION).toString(),
                QNumber(128.toBigInteger(), ONE, base, system).toString(withBaseSystem = true)), Toast.LENGTH_LONG)
            toast.view?.findViewById<TextView>(android.R.id.message)?.gravity = Gravity.CENTER
            toast.show()
        }
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
            'ʹ' -> R.string.err_keraia
            in FRACTION_CHARS.keys -> R.string.err_fraction
            in GREEK_CHARS -> R.string.err_onlyGreek
            in ROMAN_CHARS -> R.string.err_onlyRoman
            else -> when {
                error.lastOrNull() == GREEK_ID_CHAR -> R.string.err_noGreek
                error.lastOrNull() == ROMAN_ID_CHAR -> R.string.err_noRoman
                // error.firstOrNull() == '?' -> R.string.err_notInHistory
                else -> R.string.err_generic
            }
        })
}

class WaveView(context: Context) : View(context) {

    var ratio = 1f
        set(value) { field = (if (value > 1) value else 1 / value) }
    var autoClose = true
    private var waveWidth = 0f
    private var waveHeight = 0f
    private val path = Path()
    private var oldX = 0f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, MainActivity.resolveColor(R.attr.editTextColor))
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12 * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, MainActivity.resolveColor(android.R.attr.textColorHint))
    }

    init {
        isClickable = true
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> oldX = event.x
                MotionEvent.ACTION_MOVE -> {
                    MainActivity.playPhaseShift += 10 * PI.toFloat() * (event.x - oldX) / waveWidth
                    while (MainActivity.playPhaseShift > 2 * PI) MainActivity.playPhaseShift -= 2 * PI.toFloat()
                    while (MainActivity.playPhaseShift < 0) MainActivity.playPhaseShift += 2 * PI.toFloat()
                    oldX = event.x
                    calcWave()
                }
            }
            if (autoClose) performClick()
            true
        }
        setOnClickListener {
            MainActivity.playDialogTimer?.cancel()
            autoClose = false
            invalidate()
        }
    }

    private fun calcWave() {
        if (waveWidth > 0) with (path) {
            reset()
            moveTo(waveWidth, waveHeight * 0.4f)
            lineTo(0f, waveHeight * 0.4f)
            for (x in 0..waveWidth.toInt()) {
                val t = 40 * PI.toFloat() * x / waveWidth
                lineTo(x.toFloat(), waveHeight * (2 - sin(t) - sin(t * ratio - MainActivity.playPhaseShift)) / 5)
            }
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        waveWidth = w.toFloat()
        waveHeight = h.toFloat()
        calcWave()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let{
            it.drawPath(path, linePaint)
            it.drawText(resources.getString(if (autoClose) R.string.interval_keep else R.string.interval_swipe),
                waveWidth / 2, waveHeight - textPaint.textSize / 2, textPaint)
        }
    }
}

private fun lnBigInteger(a: BigInteger): Double { /* Thanks to leonbloy @ https://stackoverflow.com/questions/6827516/logarithm-for-biginteger ! */
    if (a.signum() < 1) return if (a.signum() < 0) Double.NaN else Double.NEGATIVE_INFINITY
    val blex: Int = a.bitLength() - 977
    val res = ln((if (blex > 0) a.shiftRight(blex) else a).toDouble()) /* numbers larger than 2^977 are considered too big for floating point operations */
    return if (blex > 0) res + blex * ln(2.0) else res
}
private fun complementDigits(a: BigInteger, base: Int) = if (a > ZERO) ceil(lnBigInteger(a) / ln(base.toDouble())).roundToInt() else 0

private fun reduceFraction(numer: BigInteger, denom: BigInteger): BigFraction {
    if (denom == ONE) return Pair(numer, ONE)
    var gcd: BigInteger = numer.gcd(denom).coerceAtLeast(ONE)
    if (denom < ZERO || (denom == ZERO && numer < ZERO)) gcd = -gcd
    return Pair(numer / gcd, denom / gcd)
}

fun digitToChar(digit: Int, base: Int, system: NumSystem) = ((if (system != NumSystem.BIJECTIVE_A || base > 26) when (digit) {
        in -64..-1 -> if (MainActivity.lowerDigits) 123 else 91
        in 0..9 -> 48
        else -> if (MainActivity.lowerDigits) 87 else 55
    } else (if (MainActivity.lowerDigits) 96 else 64)) + digit).toChar()

fun minDigit(base: Int, system: NumSystem) = when (system) {
    NumSystem.STANDARD, /*NumSystem.FACTORIAL,*/ NumSystem.GREEK, NumSystem.ROMAN -> 0
    NumSystem.BALANCED -> if (base % 2 == 1) (1 - base) / 2 else 0
    NumSystem.BIJECTIVE_1 -> if (base <= 35) 1 else 0
    NumSystem.BIJECTIVE_A -> if (base <= 26) 1 else 0
}

fun allowedBase(base: Int, system: NumSystem) = when (system) {
    NumSystem.STANDARD -> base
    NumSystem.BALANCED -> if (base % 2 == 1) base else (base + 1).coerceAtMost(35)
    NumSystem.BIJECTIVE_1 -> base.coerceAtMost(35)
    NumSystem.BIJECTIVE_A -> base.coerceAtMost(26)
    NumSystem.GREEK, NumSystem.ROMAN -> 10
}

fun allowedSystem(base: Int, system: NumSystem): Pair<NumSystem, Boolean> {
    val baseAllowed = allowedBase(base, system) == base
    return Pair(if (baseAllowed) system else NumSystem.STANDARD, baseAllowed)
}

private fun overflowNumber(digits: Int, base: BigInteger, system: NumSystem): BigInteger =
    if (system !in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A) base.pow(digits) else (base.pow(digits + 1) - ONE) / (base - ONE)
