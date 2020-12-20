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

import android.content.res.Resources
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.math.BigInteger
import java.math.BigInteger.ONE
import java.math.BigInteger.ZERO
import java.util.*
import kotlin.math.*

fun reduceFraction(numer: BigInteger, denom: BigInteger): Pair<BigInteger, BigInteger> {
    if (denom == ONE) return Pair(numer, ONE)
    var gcd: BigInteger = numer.gcd(denom).coerceAtLeast(ONE)
    if (denom < ZERO || (denom == ZERO && numer < ZERO)) gcd = -gcd
    return Pair(numer / gcd, denom / gcd)
}

fun lnBigInteger(v: BigInteger): Double { /* Thanks to leonbloy @ https://stackoverflow.com/questions/6827516/logarithm-for-biginteger ! */
    if (v.signum() < 1) return if (v.signum() < 0) Double.NaN else Double.NEGATIVE_INFINITY
    val blex: Int = v.bitLength() - 977 /* max binary digits; any value in 60..1023 works here */
    val res = ln((if (blex > 0) v.shiftRight(blex) else v).toDouble())
    return if (blex > 0) res + blex * ln(2.0) else res
}

val surrogateRange = 0xD800..0xDBFF
fun resolveSurrogate(c1: Int, c2: Int) = (c1 - 0xD800) * 0x400 + (c2 - 0xDC00) + 0x10000

fun digitToChar(digit: Int) = (when (digit) {
        in -64..-1 -> if (lowerDigits) 123 else 91
        in 0..9 -> 48
        else -> if (lowerDigits) 87 else 55
    } + digit).toChar()


enum class QFormat {
    POSITIONAL, FRACTION, MIXED, CONTINUED, UNICODE
}
enum class NumSystem {
    STANDARD, BALANCED, BIJECTIVE_1, BIJECTIVE_A, /*DMS, FACTORIAL,*/ ROMAN
}
fun allowedBase(base: Int, system: NumSystem) = when (system) {
    NumSystem.STANDARD -> base
    NumSystem.BALANCED -> if (base % 2 == 1) base else (base + 1).coerceAtMost(35)
    NumSystem.BIJECTIVE_1 -> base.coerceAtMost(35)
    NumSystem.BIJECTIVE_A -> base.coerceAtMost(26)
    //NumSystem.DMS -> base.coerceAtLeast(8)
    NumSystem.ROMAN -> 10
}

class QNumber(numerator: BigInteger = ZERO, denominator: BigInteger = ONE, base: Int = 10, system: NumSystem = NumSystem.STANDARD,
              private var complement: Boolean = false, format: QFormat = QFormat.POSITIONAL, error: Int = 0) {

    lateinit var numerator: BigInteger
        private set
    lateinit var denominator: BigInteger
        private set
    var base = 1
        private set
    lateinit var system: NumSystem
        private set
    var format = format
        private set
    var error = error
        private set
    var wrongDigits = false
        private set
    private var groupSize = 1

    init {
        store(reduceFraction(numerator, denominator))
        changeBase(base, system, complement)
    }

    private fun store(numer: BigInteger, denom: BigInteger = ONE) = store(Pair(numer, denom))
    private fun store(a: Pair<BigInteger, BigInteger>) {
        numerator = a.first
        denominator = a.second
    }
    fun changeBase(base: Int, system: NumSystem, complement: Boolean) {
        this.base = base
        this.system = if (allowedBase(base, system) == base) system else NumSystem.STANDARD
        this.complement = complement && (this.system != NumSystem.BALANCED)
        groupSize = if (base in setOf(2, 4, 8, 16, 32, 64)) 4 else 3
    }

    constructor(st: String, base: Int, system: NumSystem): this(base = base, system = system) {
        var stTrimmed = st.trimStart()
        if (stTrimmed.startsWith('"')) {
            var code = if (stTrimmed.length < 2) 0 else stTrimmed[1].toInt()
            if (code in surrogateRange && stTrimmed.length > 2) code = resolveSurrogate(code, stTrimmed[2].toInt())
            store(code.toBigInteger())
            if (code > 0) format = QFormat.UNICODE
        } else {
            stTrimmed = stTrimmed.filterNot { it == ' ' }.removePrefix("[").removeSuffix("]")
            val semi = stTrimmed.indexOf(';')
            if (semi == -1) store(parseFraction(stTrimmed)) else {
                var c: Pair<BigInteger, BigInteger>
                var x = Pair(ONE, ZERO)
                for ((i, sst) in ((stTrimmed.substring(semi + 1).split(',')).reversed() + stTrimmed.substring(0, semi)).withIndex()) {
                    c = if (i > 0 || sst != "") parseFraction(sst) else Pair(ONE, ZERO)
                    x = reduceFraction(c.first * x.first + c.second * x.second, c.second * x.first)
                }
                store(x)
                format = QFormat.CONTINUED
            }
        }
    }

    constructor(st: String): this() { /* for loading history entry from SharedPreferences */
        val split = st.split('/')
        try {
            store(split[0].toBigInteger(), split[1].toBigInteger())
            changeBase(split[2].toInt(), NumSystem.valueOf(split[3]), split[4].toBoolean())
            format = QFormat.valueOf(split[5])
        } catch (e: Exception) { error = 0x15 }
    }

    fun copy() = QNumber(numerator, denominator, base, system, complement, format, error)
    fun errorCode() = intToBase(error.toBigInteger())
    fun toSaveString() = "${numerator}/${denominator}/$base/$system/$complement/$format"

    /*   I n p u t   */

    private fun parseFraction(st: String): Pair<BigInteger, BigInteger> {
        var under = st.indexOf('_')
        var slash = st.indexOf('/')
        if (slash > -1 && under > slash) under = -1
        return if (slash == -1 && under == -1) parsePositional(st) else {
            format = if (under == -1) QFormat.FRACTION else QFormat.MIXED
            if (slash == -1) slash = st.length
            val integ = parsePositional(st.substring(0, max(under, 0)))
            val numer = parsePositional(st.substring(under + 1, slash))
            val denom = parsePositional(st.substring(min(slash + 1, st.length)), ONE)
            reduceFraction(numer.first  * denom.second * integ.second * (if (under > -1 && st[0] == '-') -ONE else ONE) +
                           numer.second * denom.first  * integ.first,
                           integ.second * numer.second * denom.first)
        }
    }

    private fun parsePositional(st: String, default: BigInteger = ZERO): Pair<BigInteger, BigInteger> {
        if (error > 0) return Pair(ZERO, ZERO)
        when (st) {
            "∞", "-∞" -> return Pair(ONE,  ZERO)
            "無"      -> return Pair(ZERO, ZERO)
            "λ"       -> return Pair(ZERO, ONE)
        }
        var startSt = 1
        val useBase = when (if (st != "") st[0] else ' ') {
            '@' ->  2
            '#' ->  8
            '$', '€', '£', '¥'
                -> 10
            '%' -> 12
            '&' -> 16
            else -> { startSt--; base }
        }
        if (st.length == startSt) return Pair(default, ONE)
        if (startSt == 0 && system == NumSystem.ROMAN) parseRoman(st).let { if (it.second != ZERO) return it}
        val bigBase = useBase.toBigInteger()

        var numer = ZERO;   var numerSub = ZERO
        var denom = ONE;    var denomSub = ZERO
        var point = false;  var rep = false
        var prePointRecurr = ONE
        var neg = false
        val minDigit = when (system) {
            NumSystem.STANDARD, /*NumSystem.DMS, NumSystem.FACTORIAL,*/ NumSystem.ROMAN -> 0
            NumSystem.BALANCED -> (1 - useBase) / 2
            NumSystem.BIJECTIVE_1, NumSystem.BIJECTIVE_A -> 1
        }
        var leftPad = if (!st.substring(startSt).startsWith("..")) -1 else {
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
                in 'A'..'Z' -> digit = c.toInt() - 65 + if (system == NumSystem.BALANCED && c > 'I') -26 else if (system == NumSystem.BIJECTIVE_A) 1 else 10
                in 'a'..'z' -> digit = c.toInt() - 97 + if (system == NumSystem.BALANCED && c > 'i') -26 else if (system == NumSystem.BIJECTIVE_A) 1 else 10
                '-' -> if (numer == ZERO && !neg && leftPad == -1) neg = true else error = 0x2D
                '.' -> if (!point) point = true else error = 0x2E
                '\'' -> if (!rep) {
                    rep = true
                    numerSub = numer
                    denomSub = denom
                } else error = 0x27
                ' ' -> { }
                else -> error = if (error in surrogateRange) resolveSurrogate(error, c.toInt()) else c.toInt()
            }
            numer += digit.toBigInteger()
            if (digit !in minDigit until minDigit + useBase) wrongDigits = true
            if (error > 0 && error !in surrogateRange) return Pair(ZERO, ZERO)
        }
        if (system == NumSystem.BALANCED && complement) {   /* must come after wrongDigits check */
            complement = false
            return Pair(ONE, ZERO)
        }
        denom *= prePointRecurr
        if (denomSub != denom) {
            numer -= numerSub
            denom -= denomSub
        }
        numer *= prePointRecurr
        if (neg) numer = -numer
            else if (leftPad > -1) numer -= denom * bigBase.pow(leftPad)
        return reduceFraction(numer, denom)
    }

    private val romanApostrophusM = mapOf("ↀ" to "M")
    private val romanApostrophus  = mapOf("CCCIↃↃↃ" to "ↈ", "IↃↃↃ" to "ↇ", "CCIↃↃ" to "ↂ", "IↃↃ" to "ↁ", "CIↃ" to "M", "IↃ" to "D")
    private val romanBrackets     = mapOf("(((I)))" to "ↈ", "I)))" to "ↇ", "((I))" to "ↂ", "I))" to "ↁ", "(I)" to "M", "I)" to "D")

    private fun parseRoman(st: String): Pair<BigInteger, BigInteger> {  /* REALLY permissive */
        val digitsMap = mapOf('0' to 0, 'I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000,
            'ↁ' to 5000, 'ↂ' to 10000, 'ↇ' to 50000, 'ↈ' to 100000)
        val ouncesMap = mapOf('.' to 1, '·' to 1, ':' to 2, '∴' to 3, '∷' to 4, '⁙' to 5, 'S' to 6)
        val fractional = st.indexOfAny(ouncesMap.keys.toCharArray(), ignoreCase = true)
        var stInt = (if (fractional == -1) st else st.substring(0, fractional)).toUpperCase(Locale.ROOT)
        for ((long, short) in romanApostrophusM + romanApostrophus + romanBrackets) stInt = stInt.replace(long, short)
        var n = 0
        for (i in stInt.indices) {
            val d = digitsMap[stInt[i]] ?: return Pair(ZERO, ZERO) /* invalid character */
            var sign = 0
            var j = i + 1
            while (sign == 0 && j < stInt.length) sign = (d - (digitsMap[stInt[j++]] ?: 0)).sign
            n += sign * d
        }
        var ounces = 0
        if (fractional > -1) for (c in st.substring(fractional).toUpperCase(Locale.ROOT))
            ounces += ouncesMap[c] ?: return Pair(ZERO, ZERO) /* invalid character */
        return reduceFraction((12 * n + ounces).toBigInteger(), 12.toBigInteger())
    }

    /*   O u t p u t   */

    override fun toString(): String = toString(aFormat = format)
    fun toString(withBase: Boolean = false, aFormat: QFormat = format) = when (aFormat) {
        QFormat.POSITIONAL -> toPositional()
        QFormat.FRACTION   -> toFraction()
        QFormat.MIXED      -> toMixed()
        QFormat.CONTINUED  -> toContinued()
        QFormat.UNICODE    -> toUnicode()
    } + if (withBase && aFormat != QFormat.UNICODE) base.toString().map {
        arrayOf('₀', '₁', '₂', '₃', '₄', '₅', '₆', '₇', '₈', '₉')[it.toInt() - 48]
    } .joinToString("") + when (system) {
        NumSystem.STANDARD    -> ""
        NumSystem.BALANCED    -> "ᵇᵃˡ"
        NumSystem.BIJECTIVE_1 -> "ᵇʲ¹"
        NumSystem.BIJECTIVE_A -> "ᵇʲᵃ"
        //NumSystem.DMS         -> "ᵈᵐˢ"
        //NumSystem.FACTORIAL   -> "!"
        NumSystem.ROMAN       -> "ʳᵒᵐ"
    } else ""

    fun toUnicode() = if (denominator == ONE  && numerator in 0x20.toBigInteger()..0x31FFF.toBigInteger())
        '"' + String(Character.toChars(numerator.toInt())) + '"' else ""

    fun toFraction() = if (denominator != ONE) "${intToBase(numerator)}/${intToBase(denominator)}" else intToBase(numerator)
    fun toMixed() = if (denominator > ONE && numerator.abs() > denominator) {
        val forComplement = complement && numerator < ZERO
        intToBase( numerator / denominator - (if (forComplement) ONE else ZERO)) + '_' +
        intToBase((numerator % denominator + (if (forComplement) denominator else ZERO)).abs()) + '/' + intToBase(denominator)
    } else toFraction()
    fun toContinued() = if (denominator > ONE) continuedFraction(numerator, denominator) else toPositional()

    fun toPositional(): String {
        if (denominator == ONE) return intToBase(numerator) else if (denominator == ZERO)
            return if (numerator == ZERO) "無" else "∞"
        if (system == NumSystem.BIJECTIVE_1 || system == NumSystem.BIJECTIVE_A) return toMixed()
        val bigBase = base.toBigInteger()
        var c: BigInteger
        var d = denominator
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
        val numPower = numerator * bigBase.pow(nPre + nRep)
        val up = if (system == NumSystem.STANDARD || numPower.abs() % denominator <= denominator / 2.toBigInteger()) ZERO else if (numerator > ZERO) ONE else -ONE
        val outSt = intToBase(numPower / denominator + up, nPre + nRep, nRep > 0)

        val posRep = (outSt.length - (if (groupDigits) (nPre + nRep - 1) / groupSize - nPre / groupSize else 0) - nRep).coerceAtMost(outSt.length)
        return outSt.substring(0, posRep) + (if (nRep > 0) '\'' + outSt.substring(posRep) else "") + (if (rounded) "…" else "")
    }

    fun toRoman() = if (denominator != ZERO && 12.toBigInteger() % denominator == ZERO && numerator / denominator in ZERO..399999.toBigInteger()) {
        val intValue = (numerator / denominator).toInt()
        var result = arrayOf("", "ↈ", "ↈↈ", "ↈↈↈ")                                          [intValue / 100000] +
                arrayOf("", "ↂ", "ↂↂ", "ↂↂↂ", "ↂↇ", "ↇ", "ↇↂ", "ↇↂↂ", "ↇↂↂↂ", "ↂↈ") [intValue /  10000 % 10] +
                arrayOf("", "M", "MM", "MMM", "Mↁ", "ↁ", "ↁM", "ↁMM", "ↁMMM", "Mↂ")           [intValue /   1000 % 10] +
                arrayOf("", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM")                [intValue /    100 % 10] +
                arrayOf("", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC")                [intValue /     10 % 10] +
                arrayOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")                [intValue          % 10] +
                arrayOf("", "·", ":", "∴", "∷", "⁙", "S", "S·", "S:", "S∴", "S∷", "S⁙")[(numerator % denominator * 12.toBigInteger() / denominator).toInt()]
        var i = 0
        for ((long, short) in (if (apostrophus == 1000) romanApostrophusM else romanApostrophus)) {
            if (i++ >= apostrophus) break
            result = result.replace(short, long + if (apostrophus < 1000) " " else "")
        }
        if (lowerDigits) result.toLowerCase(Locale.ROOT) else result
    } else ""

    fun toInterval(resources: Resources): String {        // name of musical interval: https://de.wikipedia.org/wiki/Tonstruktur_(mathematische_Beschreibung)
        //if (numerator == ZERO || denominator == ZERO)
            return ""
        val numer = numerator.abs().max(denominator)
        var denom = denominator.min(numerator.abs())
        var octaves = 0
        while (denom * 2.toBigInteger() <= numer) {
            denom *= 2.toBigInteger()
            octaves++
        }
        val intervals = arrayOf(Pair(1, 1), Pair(16, 15), Pair(9, 8), Pair(6, 5), Pair(5, 4), Pair(4, 3),  ////////////////////// check, esp. tritone
                                Pair(10, 7), Pair(3, 2), Pair(8, 5), Pair(5, 3), Pair(9, 5), Pair(15, 8), Pair(2, 1))
        var intervalName = "somewhere in between"                                                           //////////////////////
        for (i in intervals.indices)
                    if ((numer.toDouble() / denom.toDouble() * intervals[i].second.toDouble() / intervals[i].first.toDouble()) in 0.98..1.02) {
            intervalName = (if (numer * intervals[i].second.toBigInteger() == denom * intervals[i].first.toBigInteger() && i != 6) "" else "~ ") +
                resources.getStringArray(R.array.intervals)[i + if (octaves == 1) 12 else 0]
            break
        }
        return if (octaves < 2) intervalName else resources.getString(R.string.octaves, octaves) + (if (numer == denom) "" else " + $intervalName")
    }

    private fun intToBase(a: BigInteger, fracDigits: Int = 0, recurring: Boolean = false): String {
        if (a == ZERO) return "0"
        val bigBase = base.toBigInteger()
        var x = if (complement && a < ZERO) a + bigBase.pow(ceil(lnBigInteger(-a) / ln(base.toDouble())).roundToInt() + 2) - (if (recurring) ONE else ZERO)
            else a.abs()
        var digit: Int;  var nDigits = 0
        var st = ""
        while (x != ZERO || nDigits < fracDigits + 1) {
            digit = (x % bigBase).toInt()
            if (system == NumSystem.BALANCED) {
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
        return if (complement && a < ZERO) "..$st" else if (a < ZERO && system == NumSystem.STANDARD) "-$st" else st
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
}