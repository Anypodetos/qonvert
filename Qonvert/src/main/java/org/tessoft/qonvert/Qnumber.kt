package org.tessoft.qonvert

/*
Copyright 2020, 2021, 2022, 2023 Anypodetos (Michael Weber)

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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.math.BigInteger
import java.math.BigInteger.*
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.*

typealias BigFraction = Pair<BigInteger, BigInteger>
val FRACTION_ZERO = Pair(ZERO, ONE)
val FRACTION_ONE = Pair(ONE, ONE)
val FRACTION_INFINITY = Pair(ONE, ZERO)
val FRACTION_MU = Pair(ZERO, ZERO)

const val DIGITS = "0123456789abcdefghijklmnopqrstuvwxyzþчʔʖąḅçḑęƒģḩįʝķļṃņǫꝓɋŗşţųṿẉӽỵẓꝧҷ?¿ⱥƀȼðɇғꞡħɨɉꝁłᵯꞥøᵽꝙꞧꞩŧʉꝟ₩ӿɏƶꝥҹʡƾ"
const val MAX_BASE = DIGITS.length
const val MAX_BAL_BASE = 35
val powersOfTwo = (0 until Int.SIZE_BITS).map { 1 shl it }

val TWO = 2.toBigInteger()
val TWELVE = 12.toBigInteger()
val SIXTY = 60.toBigInteger()
val N3600 = 3600.toBigInteger()
val FRACTION_CHARS = mapOf('½' to Pair(1, 2), '⅓' to Pair(1, 3), '¼' to Pair(1, 4), '⅕' to Pair(1, 5), '⅙' to Pair(1, 6),
    '⅐' to Pair(1, 7), '⅛' to Pair(1, 8), '⅑' to Pair(1, 9),'⅒' to Pair(1, 10), '⅔' to Pair(2, 3), '¾' to Pair(3, 4),
    '⅖' to Pair(2, 5), '⅗' to Pair(3, 5), '⅘' to Pair(4, 5), '⅚' to Pair(5, 6), '⅜' to Pair(3, 8), '⅝' to Pair(5, 8),
    '⅞' to Pair(7, 8), '↉' to Pair(0, 1)).mapValues { Pair(it.value.first.toBigInteger(), it.value.second.toBigInteger()) }
const val SUBSCRIPT_DIGITS   = "₀₁₂₃₄₅₆₇₈₉"
const val SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹"
val UNICODE_RANGE = 0x20.toBigInteger()..0x10FFFF.toBigInteger()

const val GREEK_ID_CHAR = '\u200A'
const val GREEK_DIGITS = "\u0000αβγδεϛζηθ\u0000ικλμνξοπϟ\u0000ρστυφχψωϡ"
val GREEK_OTHER = mapOf('ς' to 'ϛ', 'ϝ' to 'ϛ', 'Ϝ' to 'Ϛ', 'ϙ' to 'ϟ', 'Ϙ' to 'Ϟ', 'ͳ' to 'ϡ', 'Ͳ' to 'Ϡ')
val GREEK_CHARS = GREEK_DIGITS + GREEK_DIGITS.uppercase() + GREEK_OTHER.keys.joinToString("") + "ʹ͵○"

const val ROMAN_ID_CHAR = '\u200B'
val ROMAN_DIGITS        = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100, 'd' to 500, 'm' to 1000,
    'ↁ' to 5000, 'ↂ' to 10_000, 'ↇ' to 50_000, 'ↈ' to 100_000)
val ROMAN_UNCIAE        = mapOf('.' to 1, '·' to 1, '˙' to 1, ':' to 2, '∶' to 2, '∴' to 3, '∷' to 4, '⁙' to 5, 's' to 6)
val ROMAN_APOSTROPHUS   = mapOf("ↀ" to "m", "ccciↄↄↄ" to "ↈ", "cciↄↄ" to "ↂ", "ciↄ" to "m", "iↄↄↄ" to "ↇ", "iↄↄ" to "ↁ", "iↄ" to "d")
val APOSTROPHUS_OPTIONS = listOf("--------", "-+------", "--++-++-", "+-++++++")
val ROMAN_OTHER         = mapOf("(((i)))" to "ↈ", "((i))" to "ↂ", "(i)" to "m", "i)))" to "ↇ", "i))" to "ↁ", "i)" to "d",
    "ⅰ" to "i", "ⅱ" to "ii", "ⅲ" to "iii", "ⅳ" to "iv", "ⅴ" to "v", "ⅵ" to "vi", "ⅶ" to "vii", "ⅷ" to "viii", "ⅸ" to "ix",
    "ⅹ" to "x", "ⅺ" to "xi", "ⅻ" to "xii", "ⅼ" to "l", "ↆ" to "l", "ⅽ" to "c", "ⅾ" to "d", "ⅿ" to "m")
val ROMAN_CHARS = (ROMAN_DIGITS.keys + ROMAN_UNCIAE.keys + ROMAN_OTHER.map { it.key[0] } + ROMAN_OTHER.map { it.key[0].uppercaseChar() } +
    "ↀ)ↄↃ|!".toSet()).filterNot { it in 'A'..'Z' }

val ROMAN_100_000   = listOf("", "ↈ", "ↈↈ", "ↈↈↈ")
val ROMAN_100_000_R = listOf("", "ↈ", "ↈↈ", "ↈↈↈ", "ↈↈↈↈ", "", "", "", "", "")
val ROMAN_10_000    = listOf("", "ↂ", "ↂↂ", "ↂↂↂ", "ↂↇ", "ↇ", "ↇↂ", "ↇↂↂ", "ↇↂↂↂ", "ↂↈ")
val ROMAN_10_000_R  = listOf("", "ↂ", "ↂↂ", "ↂↂↂ", "(ↂↇ|ↂↂↂↂ)", "ↇ", "ↇↂ", "ↇↂↂ", "ↇↂↂↂ", "(ↂↈ|ↇↂↂↂↂ)")
val ROMAN_1000      = listOf("", "m", "mm", "mmm", "mↁ", "ↁ", "ↁm", "ↁmm", "ↁmmm", "mↂ")
val ROMAN_1000_R    = listOf("", "m", "mm", "mmm", "(mↁ|mmmm)", "ↁ", "ↁm", "ↁmm", "ↁmmm", "(mↂ|ↁmmmm)")
val ROMAN_100       = listOf("", "c", "cc", "ccc", "cd", "d", "dc", "dcc", "dccc", "cm")
val ROMAN_100_R     = listOf("", "c", "cc", "ccc", "(cd|cccc)", "d", "dc", "dcc", "dccc", "(cm|dcccc)")
val ROMAN_10        = listOf("", "x", "xx", "xxx", "xl", "l", "lx", "lxx", "lxxx", "xc")
val ROMAN_10_R      = listOf("", "x", "xx", "xxx", "(xl|xxxx)", "l", "lx", "lxx", "lxxx", "(xc|lxxxx)")
val ROMAN_1         = listOf("", "i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix")
val ROMAN_1_R       = listOf("", "i", "ii", "iii", "(iv|iiii)", "v", "vi", "vii", "viii", "(ix|viiii)")
val ROMAN_1_12      = listOf("", "·", ":", "∴", "∷", "⁙", "s", "s·", "s:", "s∴", "s∷", "s⁙")

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
    STANDARD, BALANCED, BIJECTIVE_1, BIJECTIVE_A, GREEK, ROMAN
}
val complementSystems = setOf(NumSystem.STANDARD, NumSystem.GREEK, NumSystem.ROMAN)

enum class EgyptianMethod {
    GREEDY, BINARY, GOLOMB /* same results as continued fractions method */, PAIRING, OFF
}

class QNumber {

    var numerator: BigInteger = ZERO
        private set
    var denominator: BigInteger = ONE
        private set
    var base = 10
        private set
    var system: NumSystem = NumSystem.STANDARD
        private set
    var complement = false
        private set
    var dms = false
        private set
    var format: QFormat = QFormat.POSITIONAL
        private set

    private var error: String = ""
    val isValid
        get() = error == ""
    var nonstandardInput = false
        private set
    private var groupSize = 1

    constructor(numerator: BigInteger = ZERO, denominator: BigInteger = ONE, base: Int = 10, system: NumSystem = NumSystem.STANDARD,
              complement: Boolean = false, dms: Boolean = false, format: QFormat = QFormat.POSITIONAL, error: String = "") {
        store(reduceFraction(numerator, denominator))
        if (format in setOf(QFormat.GREEK_NATURAL, QFormat.ROMAN_NATURAL)) {
            this.base = 10
            this.system = if (format == QFormat.GREEK_NATURAL) NumSystem.GREEK else NumSystem.ROMAN
            this.format = QFormat.POSITIONAL
        } else {
            changeBase(base, system, complement, dms)
            this.format = format
        }
        this.error = error
    }

    constructor(st: String, base: Int, system: NumSystem) {
        changeBase(base, system, complement = false, dms = false)
        store(parseInput(st))
    }

    constructor(preferencesEntry: String) {
        val split = preferencesEntry.split('/')
        try {
            store(Pair(split[0].toBigInteger(), split[1].toBigInteger()))
            changeBase(split[2].toInt(), NumSystem.valueOf(split[3]), split[4].toBoolean(), split[6].toBoolean())
            format = QFormat.valueOf(split[5])
        } catch (e: Exception) { error = "\u0015" }
    }

    private fun store(a: BigFraction) {
        numerator = a.first
        denominator = a.second
    }

    fun changeBase(base: Int, system: NumSystem, complement: Boolean, dms: Boolean) {
        this.base = base
        this.system = allowedSystem(base, system).first
        this.complement = complement && this.system in complementSystems && base > 0 && numerator < ZERO
        this.dms = dms
        groupSize = if (abs(base) in powersOfTwo) 4 else 3
    }

    fun toPreferencesString() = "$numerator/$denominator/$base/$system/$complement/$format/$dms"
    fun copy(format: QFormat = this.format) = QNumber(numerator, denominator, base, system, complement, dms, format, error)

    /*   I n p u t   */

    private fun parseInput(st: String): BigFraction {
        var stTrimmed = st.trimStart()
        if (stTrimmed.startsWith('"'))
            return Pair(if (stTrimmed.length > 1) { format = QFormat.UNICODE; stTrimmed.codePointAt(1).toBigInteger() } else ZERO, ONE)
        stTrimmed = stTrimmed.filterNot { it.isWhitespace() }
        val bracketMinus = stTrimmed.startsWith("-{") || stTrimmed.startsWith("-[")
        if (bracketMinus) {
            stTrimmed = stTrimmed.substring(1)
            if (system == NumSystem.BALANCED || base < 0) nonstandardInput = true
        }
        val continued = stTrimmed.startsWith('[') || stTrimmed.endsWith(']')
        val egyptian = stTrimmed.startsWith('{') || stTrimmed.endsWith('}')
        if (continued) stTrimmed = stTrimmed.removePrefix("[").removeSuffix("]") else
            if (egyptian) stTrimmed = stTrimmed.removePrefix("{").removeSuffix("}")
        val semi = stTrimmed.indexOf(';')
        var x = FRACTION_INFINITY
        val phiResult = if (abs(base) == 1 && stTrimmed.all { it !in ALL_TOKENS })
            parsePhinary(stTrimmed, forbidden = if (egyptian) IS_CONTINUED else IS_EGYPTIAN, FRACTION_ZERO) else null
        if (phiResult == null) when {
            semi == -1 -> { /* positional number or fraction */
                x = parseDms(stTrimmed)
                if (!isValid) when (stTrimmed) {
                    "🐋💐" -> { error = ""; return Pair(38607030735492.toBigInteger(), ONE) }
                    "🚕"   -> { error = ""; return Pair(77002143071279.toBigInteger(), ONE) }
                    "👾"   -> { error = ""; return Pair(2985842461868634769.toBigInteger(), ONE) }
                }
            }
            egyptian -> { /* Egyptian fraction */
                x = parseDms(stTrimmed.substring(0, semi))
                for ((i, subSt) in stTrimmed.substring(semi + 1).split(',').reversed().withIndex()) {
                    val c = parseDms(subSt, if (i > 0) FRACTION_ZERO else FRACTION_INFINITY)
                    x = reduceFraction(c.first * x.first + c.second * x.second, c.first * x.second)
                }
            }
            else -> { /* continued fraction */
                for ((i, subSt) in ((stTrimmed.substring(semi + 1).split(',')).reversed() + stTrimmed.substring(0, semi)).withIndex()) {
                    val c = parseDms(subSt, if (i > 0) FRACTION_ZERO else FRACTION_INFINITY)
                    x = reduceFraction(c.first * x.first + c.second * x.second, c.second * x.first)
                }
            }
        } else x = phiResult
        if (bracketMinus) x = reduceFraction(-x.first, x.second)
        if (egyptian) format = QFormat.EGYPTIAN else if (continued || semi > -1) format = QFormat.CONTINUED
        if (x.first >= ZERO) complement = false /* shouldn't be necessary, but feels safer */
        return x
    }

    private fun parseDms(st: String, default: BigFraction = FRACTION_ZERO): BigFraction {
        if (st.isEmpty()) return default
        val degs = st.indexOf('°')
        val minutes = st.indexOf('\'', degs)
        val seconds = st.endsWith('"')
        if (degs == -1 && minutes == -1 && !seconds) return parseFraction(st, default)
        dms = true
        val d = if (degs > -1) parseFraction(st.substring(0, degs)) else FRACTION_ZERO
        val m = if (minutes > -1 || !seconds) parseFraction(st.substring(degs + 1, if (minutes > -1) minutes else st.length)) else FRACTION_ZERO
        val s = if (minutes > -1 ||  seconds) parseFraction(st.substring((if (minutes > -1) minutes else degs) + 1).removeSuffix("\"")) else FRACTION_ZERO
        val minus = st.dropWhile { it in ALL_TOKENS }.startsWith('-')
        val result = reduceFraction(d.first  * m.second * s.second * N3600 +
                              d.second * m.first  * s.second * SIXTY * (if (degs > -1 && minus) -ONE else ONE) +
                              d.second * m.second * s.first          * (if ((degs > -1 || minutes > -1) && minus) -ONE else ONE),
                              N3600 * d.second * m.second * s.second)
        if (result.second != ZERO && N3600 % result.second == ZERO) format = QFormat.POSITIONAL
        return result
    }

    private fun parseFraction(st: String, default: BigFraction = FRACTION_ZERO): BigFraction {
        var under = st.indexOf('_')
        var slash = st.indexOf('/')
        if (slash > -1 && under > slash) under = -1
        if (slash > -1 || under > -1) format = if (under == -1) QFormat.FRACTION else QFormat.MIXED
        if (abs(base) == 1 && st.all { it !in ALL_TOKENS }) with(parsePhinary(st, forbidden = IS_CONTINUED or IS_EGYPTIAN, default))
            { if (this != null) return this }
        return if (slash == -1 && under == -1) parsePositional(st, default) else {
            if (slash == -1) slash = st.length
            val denom =   parsePositional(st.substring(min(slash + 1, st.length)), FRACTION_ONE)
            val numer =   parsePositional(st.substring(under + 1, slash))
            val integer = parsePositional(st.substring(0, max(under, 0)))
            reduceFraction(numer.first * denom.second * integer.second * (if (under > -1 && st.dropWhile { it in ALL_TOKENS }.startsWith('-')) -ONE else ONE) +
                numer.second * denom.first  * integer.first, integer.second * numer.second * denom.first)
        }
    }

    private fun parsePositional(st: String, default: BigFraction = FRACTION_ZERO): BigFraction {
        if (!isValid) return FRACTION_MU
        val tokenBaseSystem = MainActivity.tokenBaseSystem(st.firstOrNull())
        val (useBase, useSystem) = tokenBaseSystem ?: Pair(base, system)
        var bareSt = st.substring(if (tokenBaseSystem == null) 0 else 1)
        with(mapOf("∞" to ONE, "-∞" to ONE, "無" to ZERO, "-無" to ZERO)[bareSt]) {
            if (this != null) return Pair(this, ZERO)
        }
        if ((bareSt.trimStart { it in "-." }.firstOrNull() ?: ' ') in GREEK_CHARS) with(parseGreek(bareSt)) { if (this != null) return this }
        if (useSystem == NumSystem.ROMAN) with(parseRoman(bareSt)) { if (this != null) return if (bareSt.all { it in "-|!" }) default else this }
        if (abs(useBase) == 1) with(parsePhinary(bareSt, forbidden = IS_ANY_FRACTION, default)) { if (this != null) return this }
        val bigBase = useBase.toBigInteger()
        var numer = ZERO;      var numerSub = ZERO
        var denom = ONE;       var denomSub = ZERO
        var neg = false;       var point = false
        var rep = false;       var prePointRep = ONE
        var isNumber = false;  var fractionChar = 0.toChar()
        val digitRange = digitRange(useBase, useSystem)
        var leftPad = if (!bareSt.startsWith("..")) -1 else {
            complement = true
            isNumber = true
            bareSt = bareSt.substring(2)
            0
        }

        for (c in bareSt) {
            val lowerC = c.lowercaseChar()
            if (fractionChar != 0.toChar()) error = fractionChar.toString()
            var digit: Int? = null
            when (lowerC) {
                in DIGITS -> {
                    isNumber = true
                    numer *= bigBase
                    if (point) denom *= bigBase else {
                        if (rep) prePointRep *= bigBase
                        if (leftPad > -1) leftPad++
                    }
                    digit = DIGITS.indexOf(lowerC) - (if (useSystem == NumSystem.BALANCED && lowerC in 'j'..'z') 36 else 0) -
                        (if (useSystem == NumSystem.BIJECTIVE_A && lowerC !in '0'..'9') 9 else 0)
                }
                '-' -> if (!isNumber && !neg && !point && !rep && leftPad == -1) neg = true else error = c.toString()
                '.' -> if (!point) point = true else error = c.toString()
                '˙', ':' -> if (!rep) {
                    rep = true
                    numerSub = numer
                    denomSub = denom
                } else error = c.toString()
                in FRACTION_CHARS.keys -> fractionChar = c
                'ʹ' -> error = c.toString()
                in GREEK_CHARS -> error = if (useSystem == NumSystem.GREEK) st + GREEK_ID_CHAR else c.toString()
                in ROMAN_CHARS -> error = if (useSystem == NumSystem.ROMAN) st + ROMAN_ID_CHAR else c.toString()
                else -> error = if (error.singleOrNull() in '\uD800'..'\uDBFF' && c in '\uDC00'..'\uDFFF') error + c else c.toString()
            }
            numer += (digit ?: 0).toBigInteger()
            if (useSystem == NumSystem.BIJECTIVE_A && c in '0'..'9') nonstandardInput = true
            if (digit != null && digit !in digitRange) when {
                useSystem == NumSystem.BALANCED && isValid && lowerC in DIGITS.substring(36) -> error = c.toString()
                useSystem in setOf(NumSystem.GREEK, NumSystem.ROMAN) && isValid -> error = st + (if (useSystem == NumSystem.GREEK)
                    GREEK_ID_CHAR else ROMAN_ID_CHAR)
                else -> nonstandardInput = true
            }
        }

        if (!isValid) return FRACTION_MU
        if (((useSystem == NumSystem.BALANCED || useBase < 0) && neg) || (useSystem in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A && (point || rep)))
            nonstandardInput = true
        if (complement && (useSystem !in complementSystems || useBase < 0)) { /* must come after nonstandardInput check */
            complement = false
            return FRACTION_INFINITY
        }
        if (!isNumber && fractionChar == 0.toChar()) return default
        denom *= prePointRep
        if (denomSub != denom) {
            numer -= numerSub
            denom -= denomSub
        }
        numer *= prePointRep
        if (fractionChar != 0.toChar()) with(FRACTION_CHARS[fractionChar] ?: FRACTION_ZERO) {
            val fracDenom = if (second < TEN) second else bigBase
            numer = numer * fracDenom + denom * first
            denom *= fracDenom
            format = if (isNumber) QFormat.MIXED else QFormat.FRACTION
            if (first.toInt() !in digitRange || (second < TEN && second.toInt() !in digitRange) || (second == TEN && useSystem == NumSystem.BIJECTIVE_1) ||
                useSystem == NumSystem.BIJECTIVE_A) nonstandardInput = true
        }
        if (neg) numer = -numer
            else if (leftPad > -1) numer -= denom * bigBase.pow(leftPad)
        with(reduceFraction(numer, denom)) {
            if (st.firstOrNull() == '℅') {
                if (this.first in ONE..MainActivity.historyList.size.toBigInteger() && this.second == ONE)
                    MainActivity.historyList[MainActivity.historyList.size - this.first.toInt()].number.let {
                        format = it.format
                        return Pair(it.numerator, it.denominator)
                    } else {
                    error = st
                    return FRACTION_MU
                }
            } else return this
        }
    }

    private fun parsePhinary(st: String, forbidden: Int, default: BigFraction): BigFraction? {
        val x = PhiNumber(st.replace('˙', ':'), if (forbidden and IS_CONTINUED == 0) IS_CONTINUED else IS_EGYPTIAN)
        return if (x.parseResult and (INVALID_CHAR or forbidden) != 0) {
            null
        } else if (x.bNumer == ZERO) {
            if (x.parseResult and NONSTANDARD_DIGIT != 0) nonstandardInput = true
            if (x.parseResult and IS_COMPLEMENT != 0) complement = true
            if (x.parseResult and IS_NUMBER != 0) x.a else default
        } else {
            if (isValid) error = x.toBasePhi(groupDigits = MainActivity.groupDigits,
                maxDigits = if (x.denom == ONE) Int.MAX_VALUE else MainActivity.maxDigitsAfter).replace(':', '˙') + "ᵩ"
            null
        }
    }

    private fun parseGreek(st: String): BigFraction? {
        val negative = if (st.startsWith('-')) 1 else if (st.startsWith("..")) { complement = true; 2 } else 0
        var stTrimmed = st.substring(negative).removeSuffix("ʹ")
        for ((key, value) in GREEK_OTHER) stTrimmed = stTrimmed.replace(key, value)
        var n = ZERO
        var mult = ONE
        for (c in stTrimmed) {
            when (val x = "${GREEK_DIGITS}ʹ͵.○ ".indexOf(c, ignoreCase = true)) {
                -1, 30 -> return null
                in  1.. 9 -> n += (       x      ).toBigInteger() * mult
                in 11..19 -> n += ( 10 * (x - 10)).toBigInteger() * mult
                in 21..29 -> n += (100 * (x - 20)).toBigInteger() * mult
                31 -> mult *= 1000.toBigInteger()
                32 -> n *= 10_000.toBigInteger()
                33, 34 -> Unit
            }
            if (c !in "͵ ") mult = ONE
        }
        if (!stTrimmed.filter { it != ' ' }.equals(toGreek(n, ONE).filter { it != ' ' }.removeSuffix("ʹ"), ignoreCase = true) && stTrimmed.trim() != "͵")
            nonstandardInput = true
        return BigFraction(when (negative) {
               0 ->  n
               1 -> -n
            else ->  n - TEN.pow(complementDigits(n + ONE, 10))
        }, ONE)
    }

    private fun parseRoman(st: String): BigFraction? {
        val negative = if (st.startsWith('-')) 1 else if (st.startsWith("..") && !st.all { it == '.'}) { complement = true; 2 } else 0
        val stTrimmed = st.substring(negative).lowercase().replace('!', '|')
        val fracPos = stTrimmed.indexOfAny(ROMAN_UNCIAE.keys.toCharArray())
        var stInt = (if (fracPos == -1) stTrimmed else stTrimmed.substring(0, fracPos)) + "n"
        for ((key, value) in ROMAN_APOSTROPHUS + ROMAN_OTHER) stInt = stInt.replace(key, value)
        var n = ZERO
        var regex = ""
        for (i in stInt.indices) if (stInt[i] !in "|n") {
            val d = ROMAN_DIGITS[stInt[i]] ?: return null /* invalid character */
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
        if (n < ZERO || !Regex("${regex}n").matches("|$stInt") && !(stInt == "nn" && fracPos == -1)) nonstandardInput = true
        var unciae = 0
        if (fracPos > -1) for (c in stTrimmed.substring(fracPos))
            unciae += ROMAN_UNCIAE[c] ?: return null /* invalid character */
        if ((unciae >= 6 && stTrimmed[fracPos] != 's') || unciae >= 12) nonstandardInput = true
        n = TWELVE * n + unciae.toBigInteger()
        return reduceFraction(when (negative) {
               0 ->  n
               1 -> -n
            else ->  n - TWELVE * TEN.pow(complementDigits(n / TWELVE + ONE, 10))
        }, TWELVE)
    }

    /*   O u t p u t   */

    fun usefulFormat(aFormat: QFormat): Boolean = if (!isValid) false else when (aFormat) {
        QFormat.POSITIONAL -> system !in setOf(NumSystem.BIJECTIVE_1, NumSystem.BIJECTIVE_A, NumSystem.GREEK) || denominator <= ONE ||
            (dms && N3600 % denominator == ZERO)
        QFormat.POSITIONAL_ALT -> {
            val d = if (abs(base) == 1) denominator else
                with(gcdPower()) { if (second + (if (system == NumSystem.STANDARD) 0 else 1) > MainActivity.maxDigitsAfter) ZERO else first }
            if (!dms) (system == NumSystem.STANDARD && ((base > 0 && d == ONE) || (base < 0 && (ONE - base.toBigInteger()).let { basePlusOne ->
                QNumber(numerator * basePlusOne - denominator, denominator * basePlusOne, -base, system).usefulFormat(QFormat.POSITIONAL_ALT)
            }))) || (system == NumSystem.BALANCED && d == TWO)
                else QNumber(numerator * N3600, denominator, base, system).usefulFormat(QFormat.POSITIONAL_ALT)
        }
        QFormat.FRACTION -> (denominator != ONE || (abs(base) == 1 && numerator.abs() > ONE)) &&
            (system !in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A || denominator != ZERO)
        QFormat.MIXED -> if (denominator <= ONE && !(abs(base) == 1 && numerator.abs() > ONE)) false else {
            val useDms = dms && abs(base) > 1 && N3600 % denominator != ZERO
            (!useDms && numerator.abs() > denominator) ||
                (useDms && ((SIXTY * numerator) % denominator + if (complement) denominator else ZERO).abs() * SIXTY > denominator)
        }
        QFormat.CONTINUED -> denominator > ONE || (abs(base) == 1 && numerator.abs() > ONE)
        QFormat.EGYPTIAN -> denominator > ONE && abs(base) > 1
        QFormat.GREEK_NATURAL -> system != NumSystem.GREEK && denominator == ONE && numerator > ZERO
        QFormat.ROMAN_NATURAL -> system != NumSystem.ROMAN && denominator == ONE && numerator > ZERO
        QFormat.UNICODE -> denominator == ONE && numerator in UNICODE_RANGE
    }

    private fun gcdPower(): Pair<BigInteger, Int> {
        val bigBase = base.toBigInteger()
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
            aEgyptianMethod: EgyptianMethod = MainActivity.preferredEgyptianMethod()): String {
        val result = when (aFormat) {
            QFormat.POSITIONAL     -> toPositional()
            QFormat.POSITIONAL_ALT -> toPositional(alt = true)
            QFormat.FRACTION       -> toFraction()
            QFormat.MIXED          -> toMixed()
            QFormat.CONTINUED      -> toContinued()
            QFormat.EGYPTIAN       -> toEgyptian(aEgyptianMethod)
            QFormat.GREEK_NATURAL, QFormat.ROMAN_NATURAL -> "" /* handled in constructor */
            QFormat.UNICODE        -> toUnicode()
        }
        return when (mode) {
            DisplayMode.STANDARD   -> result
            DisplayMode.DISSECT   -> makeDissect(result, base, system).first
            DisplayMode.PRETTY     -> makePretty(result).first
            DisplayMode.COMPATIBLE -> makeCompatible(result, base, system).first
        } + if (withBaseSystem && mode in setOf (DisplayMode.STANDARD, DisplayMode.DISSECT) && system !in setOf(NumSystem.GREEK, NumSystem.ROMAN) &&
                    aFormat !in setOf(QFormat.GREEK_NATURAL, QFormat.ROMAN_NATURAL, QFormat.UNICODE))
            (if (base > 0) "" else "₋") + (if (abs(base) > 1) abs(base).toString().map { SUBSCRIPT_DIGITS[it.digitToInt()] }.joinToString("") else "ᵩ") +
                MainActivity.numSystemsSuper[system.ordinal]
                    else ""
    }

    private fun toDms(format: QFormat) = if (denominator > ZERO) {
        var degs = numerator / denominator - (if (complement && denominator != ONE) ONE else ZERO)
        val minNumer = SIXTY * (numerator - degs * denominator)
        var mins = minNumer / denominator
        var secNumer = SIXTY * (minNumer - mins * denominator)
        var zeroAlt = ""
        if (secNumer == ZERO && format == QFormat.POSITIONAL_ALT) {
            secNumer = (if (mins == -ONE) -SIXTY else SIXTY) * denominator
            when (mins.signum()) {
                -1 -> mins++
                 0 -> {
                     mins = (if (degs == -ONE) -59 else 59).toBigInteger()
                     if (degs == ZERO) zeroAlt = with(digitToChar(base - 1, base, system)) { "..$this$this° " }
                         else if ((degs > ZERO) != complement) degs-- else degs++
                 }
                 1 -> mins--
            }
        }
        val keepSign = system == NumSystem.BALANCED || base < 0
        ((if ((degs != ZERO || numerator == ZERO) && zeroAlt == "") intToBase(degs, keraia = false) + "° " else zeroAlt) +
            (if (mins != ZERO) intToBase(if (degs == ZERO || keepSign) mins else mins.abs(), keraia = false) + "' " else "") +
            (if (secNumer != ZERO) QNumber(if ((degs == ZERO && mins == ZERO) || keepSign) secNumer else secNumer.abs(), denominator, base, system)
                .toString(aFormat = format).removeSuffix("ʹ") + "\"" else "")
        ).trimEnd()
    } else toPositional()

    fun toFraction() = if (dms && abs(base) > 1 && denominator != ZERO && N3600 % denominator != ZERO) toDms(QFormat.FRACTION) else
        if (abs(base) == 1) PhiNumber(numerator, ZERO, denominator).toBasePhiFraction(groupDigits = MainActivity.groupDigits, complement = complement) else
            if (denominator != ONE) "${intToBase(numerator, keraia = false)}/${intToBase(denominator)}" else intToBase(numerator)

    fun toMixed() = if (dms && abs(base) > 1 && denominator != ZERO && N3600 % denominator != ZERO) toDms(QFormat.MIXED) else
        if (numerator.abs() > denominator) {
            if (abs(base) == 1) PhiNumber(numerator, ZERO, denominator).toBasePhiMixed(groupDigits = MainActivity.groupDigits, complement = complement) else
                intToBase(numerator / denominator - (if (complement) ONE else ZERO), keraia = false) + if (denominator > ONE) ('_' +
                    intToBase((numerator % denominator + (if (complement) denominator else ZERO)).let {
                        if (system == NumSystem.BALANCED || base < 0) it else it.abs()
                    }, keraia = false) + '/' + intToBase(denominator)) else ""
        } else toFraction()

    fun toContinued() = if (abs(base) == 1 && !(numerator.abs() <= ONE && denominator <= ONE)) "[" +
        PhiNumber(numerator, ZERO, denominator).toBasePhiContinued(groupDigits = MainActivity.groupDigits, complement = complement) + "]" else
            if (denominator > ONE) continuedFraction(numerator, denominator) else toPositional()

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
            else -> ""
        } + "}"
    }

    fun toPositional(alt: Boolean = false): String {
        if (denominator == ZERO) return if (numerator == ZERO) "無" else "∞"
        if (dms) return toDms(if (alt) QFormat.POSITIONAL_ALT else QFormat.POSITIONAL)
        if (system == NumSystem.ROMAN) with(toRoman()) { if (this != "") return this }
        if (abs(base) == 1) return PhiNumber(numerator, ZERO, denominator).toBasePhi(groupDigits = MainActivity.groupDigits,
            maxDigits = if (alt) Int.MAX_VALUE else MainActivity.maxDigitsAfter, alt = alt, complement = complement).replace(':', '˙')
        if (denominator == ONE && !alt) return intToBase(numerator)
        if (numerator == ZERO && alt && system == NumSystem.STANDARD && base > 0) return with(digitToChar(base - 1, base, system)) { "..$this$this.˙$this" }
        if (system in setOf(NumSystem.BIJECTIVE_1, NumSystem.BIJECTIVE_A, NumSystem.GREEK)) return toMixed()
        val bigBase = base.toBigInteger()
        val (d, nPre) = gcdPower()
        val oneMinusD = ONE - d
        var nRep = 0
        var remainder = ONE
        if (d > ONE && nPre <= MainActivity.maxDigitsAfter) do {
            nRep++
            remainder = remainder * bigBase % d
        } while (remainder != ONE && remainder != oneMinusD && nRep <= MainActivity.maxDigitsAfter - nPre)
        if (base < 0 && nRep == 1 && usefulFormat(QFormat.POSITIONAL_ALT)) nRep = 2
        val cutOff = nRep > MainActivity.maxDigitsAfter - nPre
        if (cutOff) nRep = MainActivity.maxDigitsAfter - nPre
        val nFrac = nPre + nRep
        val numPower = numerator * bigBase.abs().pow(nFrac)
        return intToBase(numPower / denominator + when {
            complement && (nRep > 0 || cutOff) -> -ONE
            base < 0 && system == NumSystem.STANDARD && (nRep > 0 || cutOff) -> {
                val posPlace = ((nFrac % 2 == 0) == (numerator > ZERO)) != (cutOff && nRep % 2 != 0) /* when cutOff, nRep includes the 1st invisible digit */
                if ((numPower.abs() % denominator) * (-bigBase + ONE) > denominator * (if (posPlace) ONE else -bigBase) -
                    if (alt == posPlace) ONE /* turns ">" into ">=" */ else ZERO) (if (numerator > ZERO) ONE else -ONE) else ZERO
            }
            alt && !cutOff -> if ((system == NumSystem.STANDARD) == (numerator > ZERO || complement)) -ONE else ONE
            system == NumSystem.BALANCED && numPower.abs() % denominator > denominator / TWO -> if (numerator > ZERO) ONE else -ONE
            else -> ZERO
        }, fracDigits = nFrac, repDigits = if (nRep > 0 || cutOff) nRep else -1, forceMinus = numerator < ZERO) +
        when {
            cutOff -> "…"
            alt && system == NumSystem.STANDARD && base > 0 ->
                (if (denominator == ONE) ".˙" else if (MainActivity.groupDigits && nFrac % groupSize == 0) " ˙" else "˙") +
                    digitToChar(base - 1, base, NumSystem.STANDARD)
            else -> ""
        }
    }

    private fun intToBase(a: BigInteger, fracDigits: Int = 0, repDigits: Int = -1, forceMinus: Boolean = false, keraia: Boolean = true): String {
        if (system == NumSystem.GREEK) return toGreek(a, ONE, keraia)
        if (system == NumSystem.ROMAN) return toRoman(a, ONE)
        if (abs(base) == 1) return PhiNumber(a).toBasePhi(groupDigits = MainActivity.groupDigits, maxDigits = MainActivity.maxDigitsAfter,
            complement = complement)
        if (system in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A && a == ZERO) return "/"
        val x = if (!complement || a >= ZERO) a.abs() else a + base.toBigInteger().pow(max(complementDigits(-a, base) + 2, fracDigits + 1))
        val s = x.toStringBuilder(abs(base), fracDigits + 1)
        if (system in setOf(NumSystem.BALANCED, NumSystem.BIJECTIVE_1, NumSystem.BIJECTIVE_A) || base < 0) {
            val intDigits = s.length - fracDigits
            var roll = 0
            for (i in s.lastIndex downTo 0) {
                var digit = DIGITS.indexOf(s[i]) + roll
                val changed = roll != 0
                val negPlace = base < 0 && (((intDigits - i) % 2 == 0) != (a < ZERO))
                if (system == NumSystem.BALANCED && digit > abs(base) / 2) {
                    digit -= abs(base)
                    roll = +1
                } else if (system in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A && digit <= 0) {
                    digit += abs(base)
                    roll = if (negPlace) +1 else -1
                } else roll = 0
                val balancedXor = system == NumSystem.BALANCED && ((a < ZERO) != (base < 0 && negPlace != (a < ZERO)))
                if (balancedXor) digit = -digit
                if (base < 0 && system != NumSystem.BALANCED) {
                    if (negPlace && digit in 1 until -base) {
                        digit = -base - digit
                        roll += if (roll == 0) 1 else -1 /* make up for sign switch of base that was added to digit above */
                    } else if (digit >= -base + if (system in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A) 1 else 0) {
                        digit += base
                        roll += 1
                    }
                }
                if (changed || roll != 0 || balancedXor || system == NumSystem.BIJECTIVE_A) s[i] = digitToChar(digit, base, system)
            }
            when (roll) {
                 1 -> if (s.firstOrNull() == digitToChar(abs(base), base, system)) s.deleteAt(0) else
                     s.insert(0, if (system == NumSystem.BALANCED && ((base < 0 && ((intDigits + 1) % 2 == 0)) != (a < ZERO))) 'z' else
                        if (system == NumSystem.BIJECTIVE_A) 'a' else '1')
                -1 -> s.deleteAt(0)
            }
        }
        if (MainActivity.groupDigits) {
            val nDigits = s.length
            for (i in s.length - fracDigits % groupSize downTo 1 step groupSize) s.insert(i, ' ')
            val fracPos = s.lastIndex - fracDigits - fracDigits / groupSize
            if (fracDigits in 1..nDigits) s[fracPos] = '.'
            if (repDigits in 0..nDigits) s.insert(fracPos + 1 + fracDigits - repDigits + (fracDigits - repDigits) / groupSize, '˙')
        } else {
            if (fracDigits in 1..s.length) s.insert(s.length - fracDigits, '.')
            if (repDigits in 0..s.length) s.insert(s.length - repDigits, '˙')
        }
        return (if (complement && a < ZERO) ".." else if (system != NumSystem.BALANCED && base > 0 && (a < ZERO || forceMinus)) "-" else "") +
            s.trim().toString().let {
                if (MainActivity.lowerDigits) it else it.uppercase()
            }
    }

    private tailrec fun continuedFraction(numer: BigInteger, denom: BigInteger, pre: String = ""): String {
        var (n, rem) = numer.divideAndRemainder(denom)
        if (numer < ZERO && rem != ZERO) n--
        val st = "$pre, ${intToBase(n)}"
        return if (rem == ZERO) '[' + st.substring(2).replaceFirst(',', ';') + ']' else continuedFraction(denom, numer - n * denom, st)
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

    private fun egyptianFractionBinary(fracNumer: BigInteger): String {
        var p = TWO
        while (fracNumer * p % denominator >= TWO * p) p *= TWO
        return (egyptianBinaryList(fracNumer * p % denominator).map { p / it * denominator } +
                egyptianBinaryList(fracNumer * p / denominator).map { p / it }).sorted().joinToString { intToBase(it) }
    }
    private fun egyptianBinaryList(r: BigInteger) =
        r.toString(2).reversed().mapIndexed { i, c -> if (c == '1') TWO.pow(i) else null }.filterNotNull()

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
                val valueDiv = entry.value.divideAndRemainder(TWO)
                if (entry.key % TWO == ZERO) {
                    val newKey = entry.key / TWO
                    denomMap[newKey] = (denomMap[newKey] ?: ZERO) + valueDiv[0]
                } else {
                    val newKey1 = (entry.key + ONE) / TWO
                    val newKey2 = ((entry.key + ONE) * entry.key) / TWO
                    denomMap[newKey1] = (denomMap[newKey1] ?: ZERO) + valueDiv[0]
                    denomMap[newKey2] = (denomMap[newKey2] ?: ZERO) + valueDiv[0]
                }
                if (valueDiv[1] == ZERO) denomMap.remove(entry.key) else denomMap[entry.key] = ONE
                found = true
                break
            }
        } while (found && iterations <= cutOff)
        return if (iterations <= cutOff) denomMap.keys.sorted().joinToString { intToBase(it) } else "…"
    }

    fun toGreek(numer: BigInteger = numerator, denom: BigInteger = denominator, keraia: Boolean = true): String {
        if (denom != ONE) return "" else if (numer == ZERO) return "○"
        var value = if (!complement || numer >= ZERO) numer.abs() else
            numer + TEN.pow(complementDigits(-numer, 10) + 2)
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
        s.toString().replace("  ", " ").let {
            return if (MainActivity.lowerDigits) it else it.uppercase()
        }
    }

    fun toRoman(numer: BigInteger = numerator, denom: BigInteger = denominator): String {
        if (denom == ZERO) return ""
        var bigIntValue = if (!complement || numer >= ZERO) numer.abs() / denom else
            (numer / denom - if (numer % denom == ZERO) ZERO else ONE).let {
                it + TEN.pow(complementDigits(-it, 10) + 1)
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
        if (s.isEmpty()) s.append("n")
        if (TWELVE % denom != ZERO) s.append(" …")
        var i = 0
        var result = s.toString()
        for ((key, value) in mapOf("cccc" to "cd") + ROMAN_APOSTROPHUS)
            if (APOSTROPHUS_OPTIONS[MainActivity.apostrophus][i++] == '+') result = result.replace(value, key + if (i > 2) " " else "")
        return (if (numer >= ZERO) "" else if (complement) ".." else "-") +
            (if (MainActivity.lowerDigits) result else result.uppercase()).trimEnd()
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
        } else Toast.makeText(context, context.resources.getString(R.string.no_interval,
            QNumber(ONE, 128.toBigInteger(), base, system, format = QFormat.FRACTION).toString(),
            QNumber(128.toBigInteger(), ONE, base, system).toString(withBaseSystem = true)), Toast.LENGTH_LONG).show()
    }

    fun errorMessage(resources: Resources) = "\"$error\" " +
        (if (error.codePointCount(0, error.length) == 1) "(${intToBase(error.codePointAt(0).toBigInteger())}) " else "") +
        (if (error.length > 1 && error.endsWith("ᵩ"))
                with(error.dropLast(1).removeSuffix("₋").replaceFirst("˙", ":").toPhiNumber(negative = error.endsWith("₋ᵩ"))) {
            val root5 = "√" + intToBase(5.toBigInteger(), keraia = false)
            val rx = r;  val sx = s
            "(" + ((if (rx != FRACTION_ZERO) QNumber(rx.first, rx.second, base, system).toFraction() + "+" else "") +
                QNumber(sx.first, sx.second, base, system).toFraction().let {
                    val st = (if ("/" in it) it.replace("/", "$root5/") else it + root5)
                    if (sx.first.abs() != ONE) st else if (sx.first == ONE) st.drop(1) else "-" + st.drop(2)
                }).replace("+-", "-") + " ≈ ${toFloat()}₁₀) "
        } else "") +
        resources.getString(when (error.singleOrNull()?.lowercaseChar() ?: ' ') {
            '"' -> R.string.err_quote
            '[', ']' -> R.string.err_bracket
            '{', '}' -> R.string.err_brace
            ';' -> R.string.err_semicolon
            ',' -> R.string.err_comma
            '_' -> R.string.err_underscore
            '/' -> R.string.err_slash
            '.' -> R.string.err_twoPoints
            '˙', ':' -> R.string.err_twoReps
            '°' -> R.string.err_twoDegs
            '\'' -> R.string.err_twoMins
            in "-$ALL_TOKENS" -> R.string.err_baseTokenOrMinus
            '…' -> R.string.err_ellipsis
            '∞' -> R.string.err_infinity
            '無' -> R.string.err_undefined
            'ʹ' -> R.string.err_keraia
            in DIGITS -> R.string.err_balancedDigit
            in FRACTION_CHARS.keys -> R.string.err_fraction
            in GREEK_CHARS -> R.string.err_onlyGreek
            in ROMAN_CHARS -> R.string.err_onlyRoman
            '\u0015' -> R.string.err_load
            else -> when (error.lastOrNull()) {
                GREEK_ID_CHAR -> R.string.err_noGreek
                ROMAN_ID_CHAR -> R.string.err_noRoman
                'ᵩ' -> R.string.err_irrational
                // error.startsWith('℅') -> R.string.err_notInHistory
                else -> R.string.err_generic
            }
        })
}

fun BigInteger.toStringBuilder(base: Int, digits: Int): StringBuilder {
    if (base <= 36) return StringBuilder(toString(base).padStart(digits, '0'))
    val s = StringBuilder()
    largeToStringBuilder(s, base, digits)
    return s.reverse()
}

/* The following thanks to Sweeper @ https://stackoverflow.com/questions/74679019/converting-a-biginteger-to-a-string-in-some-base-speed-issue-kotlin ! */
val LOG_TWO = ln(2.0)

private fun BigInteger.largeToStringBuilder(s: StringBuilder, base: Int, digits: Int) {
    val bitLength = bitLength()
    if (bitLength <= 640) {
        val bigBase = base.toBigInteger()
        val oldLength = s.length
        var x = this
        while (x > ZERO) {
            val div = x.divideAndRemainder(bigBase)
            s.append(DIGITS[div[1].toInt()])
            x = div[0]
        }
        s.append("0".repeat((digits - (s.length - oldLength)).coerceAtLeast(0)))
    } else {
        val n = (ln(bitLength * LOG_TWO / ln(base.toDouble())) / LOG_TWO - 1.0).roundToInt()
        val expectedDigits = 1 shl n
        val div = divideAndRemainder(base.toBigInteger().pow(expectedDigits))
        div[1].largeToStringBuilder(s, base, expectedDigits)
        div[0].largeToStringBuilder(s, base, digits - expectedDigits)
    }
}

fun BigInteger.ln(): Double { /* Thanks to leonbloy @ https://stackoverflow.com/questions/6827516/logarithm-for-biginteger ! */
    if (signum() < 1) return if (signum() < 0) Double.NaN else Double.NEGATIVE_INFINITY
    val blex: Int = bitLength() - 977
    val res = ln((if (blex > 0) shiftRight(blex) else this).toDouble()) /* numbers larger than 2^977 are considered too big for floating point operations */
    return if (blex > 0) res + blex * ln(2.0) else res
}
private fun complementDigits(a: BigInteger, base: Int) = if (a > ZERO) ceil(a.ln() / ln(base.toDouble())).roundToInt() else 0

fun reduceFraction(numer: BigInteger, denom: BigInteger): BigFraction {
    if (denom == ONE) return Pair(numer, ONE)
    var gcd = numer.gcd(denom).coerceAtLeast(ONE)
    if (denom < ZERO || (denom == ZERO && numer < ZERO)) gcd = -gcd
    return Pair(numer / gcd, denom / gcd)
}

fun digitToChar(digit: Int, base: Int, system: NumSystem) =
    (if (digit >= 0) DIGITS[digit + if (system != NumSystem.BIJECTIVE_A || abs(base) > MAX_BASE - 10) 0 else 9] else (digit + 123).toChar()).let {
        if (MainActivity.lowerDigits) it else it.uppercaseChar()
    }

fun digitRange(base: Int, system: NumSystem, add: Int = 0): IntRange {
    val min = when (system) {
        NumSystem.STANDARD, NumSystem.GREEK, NumSystem.ROMAN -> 0
        NumSystem.BALANCED -> if (base % 2 != 0 && abs(base) <= MAX_BAL_BASE) (1 - abs(base)) / 2 else 0
        NumSystem.BIJECTIVE_1 -> if (abs(base) <= MAX_BASE - 1) 1 else 0
        NumSystem.BIJECTIVE_A -> if (abs(base) <= MAX_BASE - 10) 1 else 0
    } + add
    return min until min + abs(base).coerceAtLeast(2)
}

fun baseToString(base: Int) = when (base) {
     1 ->  "φ"
    -1 -> "-φ"
    else -> base.toString()
}

fun saneBase(base: Int, default: Int) = if (base in -1..0) default else base.coerceIn(-MAX_BASE..MAX_BASE)

fun allowedBase(base: Int, system: NumSystem) = when (system) {
    NumSystem.STANDARD -> base.coerceIn(-MAX_BASE, MAX_BASE)
    NumSystem.BALANCED -> (if (base % 2 != 0) abs(base) else abs(base) + 1).coerceIn(3, MAX_BAL_BASE) * base.sign
    NumSystem.BIJECTIVE_1 -> abs(base).coerceIn(2, MAX_BASE - 1) * base.sign
    NumSystem.BIJECTIVE_A -> abs(base).coerceIn(2, MAX_BASE - 10) * base.sign
    NumSystem.GREEK, NumSystem.ROMAN -> 10
}

fun allowedSystem(base: Int, system: NumSystem): Pair<NumSystem, Boolean> {
    val baseAllowed = allowedBase(base, system) == base
    return Pair(if (baseAllowed) system else NumSystem.STANDARD, baseAllowed)
}
