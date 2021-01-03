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
import java.math.BigInteger.ONE
import java.math.BigInteger.ZERO
import java.util.*
import kotlin.math.*

typealias BigFraction = Pair<BigInteger, BigInteger>
val TWO = 2.toBigInteger()
val TWELVE = 12.toBigInteger()

fun lnBigInteger(v: BigInteger): Double { /* Thanks to leonbloy @ https://stackoverflow.com/questions/6827516/logarithm-for-biginteger ! */
    if (v.signum() < 1) return if (v.signum() < 0) Double.NaN else Double.NEGATIVE_INFINITY
    val blex: Int = v.bitLength() - 977 /* max binary digits; any value in 60..1023 works here */
    val res = ln((if (blex > 0) v.shiftRight(blex) else v).toDouble())
    return if (blex > 0) res + blex * ln(2.0) else res
}

fun reduceFraction(numer: BigInteger, denom: BigInteger): BigFraction {
    if (denom == ONE) return Pair(numer, ONE)
    var gcd: BigInteger = numer.gcd(denom).coerceAtLeast(ONE)
    if (denom < ZERO || (denom == ZERO && numer < ZERO)) gcd = -gcd
    return Pair(numer / gcd, denom / gcd)
}

val surrogateRange = 0xD800..0xDBFF
fun resolveSurrogate(c1: Int, c2: Int) = (c1 - 0xD800) * 0x400 + (c2 - 0xDC00) + 0x10000

enum class QFormat {
    POSITIONAL, FRACTION, MIXED, CONTINUED, UNICODE
}
enum class NumSystem(val short: String) {
    STANDARD("std"), BALANCED("bal"), BIJECTIVE_1("bj1"), BIJECTIVE_A("bja"), /*DMS("dms"), FACTORIAL("fact"),*/ ROMAN("rom")
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

class QNumber(numerator: BigInteger = ZERO, denominator: BigInteger = ONE, base: Int = 10, system: NumSystem = NumSystem.STANDARD,
              complement: Boolean = false, format: QFormat = QFormat.POSITIONAL, error: Int = 0) {

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
    var error = error
        private set
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
        if (stTrimmed.startsWith('"')) {
            var code = if (stTrimmed.length < 2) 0 else stTrimmed[1].toInt()
            if (code in surrogateRange && stTrimmed.length > 2) code = resolveSurrogate(code, stTrimmed[2].toInt())
            store(code.toBigInteger())
            if (code > 0) format = QFormat.UNICODE
        } else when (stTrimmed.trimEnd()) {
            "🐋💐" -> store(38607030735492.toBigInteger())
            "🚕"   -> store(77002143071279.toBigInteger())
            "👾"   -> store(2985842461868634769.toBigInteger())
            else -> {
                stTrimmed = stTrimmed.filterNot { it in " \t\n\r" }
                val egyptian = stTrimmed.startsWith('{') || stTrimmed.endsWith('}')
                stTrimmed = if (egyptian) stTrimmed.removePrefix("{").removeSuffix("}") else stTrimmed.removePrefix("[").removeSuffix("]")
                val semi = stTrimmed.indexOf(';')
                when {
                    semi == -1 -> store(parseFraction(stTrimmed))
                    egyptian -> {
                        var x = parseFraction(stTrimmed.substring(0, semi))
                        for ((i, subSt) in stTrimmed.substring(semi + 1).split(',').reversed().withIndex()) {
                            val c = parseFraction(subSt, if (i > 0) Pair(ZERO, ONE) else Pair(ONE, ZERO))
                            x = reduceFraction(c.first * x.first + c.second * x.second, c.first * x.second)
                        }
                        store(x)
                        format = QFormat.MIXED
                    }
                    else -> {
                        var x = Pair(ONE, ZERO)
                        for ((i, subSt) in ((stTrimmed.substring(semi + 1).split(',')).reversed() + stTrimmed.substring(0, semi)).withIndex()) {
                        val c = parseFraction(subSt, if (i > 0) Pair(ZERO, ONE) else Pair(ONE, ZERO))
                        x = reduceFraction(c.first * x.first + c.second * x.second, c.second * x.first)
                        }
                        store(x)
                        format = QFormat.CONTINUED
                    }
                }
            }
        }
        if (error > 0) nonstandardInput = false /* don’t show BOTH error (uninterpretable input) and (interpretable) nonstandardInput */
    }

    constructor(preferencesEntry: String): this() {
        val split = preferencesEntry.split('/')
        try {
            store(split[0].toBigInteger(), split[1].toBigInteger())
            changeBase(split[2].toInt(), NumSystem.valueOf(split[3]), split[4].toBoolean())
            format = QFormat.valueOf(split[5])
        } catch (e: Exception) { error = 0x15 }
    }

    fun copy() = QNumber(numerator, denominator, base, system, complement, format, error)
    fun toSaveString() = "${numerator}/${denominator}/$base/$system/$complement/$format"
    fun errorCode() = intToBase(error.toBigInteger())

    private fun wrappingPoint(digits: Int, base: BigInteger): BigInteger = if (system !in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A)
         base.pow(digits) else (base.pow(digits + 1) - ONE) / (base - ONE)

    /*   I n p u t   */

    private fun parseFraction(st: String, default: BigFraction = Pair(ZERO, ONE)): BigFraction {
        var under = st.indexOf('_')
        var slash = st.indexOf('/')
        if (slash > -1 && under > slash) under = -1
        return if (slash == -1 && under == -1) parsePositional(st, default) else {
            format = if (under == -1) QFormat.FRACTION else QFormat.MIXED
            if (slash == -1) slash = st.length
            val integer = parsePositional(st.substring(0, max(under, 0)))
            val numer =   parsePositional(st.substring(under + 1, slash))
            val denom =   parsePositional(st.substring(min(slash + 1, st.length)), Pair(ONE, ONE))
            reduceFraction(numer.first  * denom.second * integer.second * (if (under > -1 && st[0] == '-') -ONE else ONE) +
                           numer.second * denom.first  * integer.first,
                           integer.second * numer.second * denom.first)
        }
    }

    private fun parsePositional(st: String, default: BigFraction = Pair(ZERO, ONE)): BigFraction {
        if (error > 0) return Pair(ZERO, ZERO)
        when (st) {
            "∞", "-∞" -> return Pair(ONE, ZERO)
            "無"      -> return Pair(ZERO, ZERO)
            "λ"       -> return Pair(ZERO, ONE)
        }
        var startSt = 1
        var useSystem = NumSystem.STANDARD
        val useBase = when (if (st != "") st[0] else ' ') {
            '@' ->  2
            '#' ->  8
            '$', '€', '£', '¥' -> 10
            '%' -> 12
            '&' -> 16
            else -> {
                startSt--
                useSystem = system
                base
            }
        }
        if (startSt == 0 && useSystem == NumSystem.ROMAN) parseRoman(st).let { if (it.second != ZERO) return it }
        val bigBase = useBase.toBigInteger()

        var numer = ZERO;   var numerSub = ZERO
        var denom = ONE;    var denomSub = ZERO
        var neg = false;    var point = false;  var rep = false;   var prePointRecurr = ONE
        var isNumber = false
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
            var digit: Int? = null
            when (c) {
                in '0'..'9' -> {
                    digit = c.toInt() - 48
                    if (useSystem == NumSystem.BIJECTIVE_A) nonstandardInput = true
                }
                in 'A'..'Z' -> digit = c.toInt() - 65 + if (useSystem == NumSystem.BALANCED && c > 'I') -26 else valueOfA
                in 'a'..'z' -> digit = c.toInt() - 97 + if (useSystem == NumSystem.BALANCED && c > 'i') -26 else valueOfA
                '-' -> if (numer == ZERO && !neg && !point && !rep && leftPad == -1) neg = true else error = 0x2D
                '.' -> if (!point) point = true else error = 0x2E
                '\'' -> if (!rep) {
                    rep = true
                    numerSub = numer
                    denomSub = denom
                } else error = 0x27
                else -> error = if (error in surrogateRange) resolveSurrogate(error, c.toInt()) else c.toInt()
            }
            numer += (digit ?: 0).toBigInteger()
            if (digit != null && digit !in minDigit until minDigit + useBase)
                (if (useSystem == NumSystem.ROMAN) error = c.toInt() else nonstandardInput = true)
            if (error > 0 && error !in surrogateRange) return Pair(ZERO, ZERO)
        }
        if ((useSystem == NumSystem.BALANCED && neg) || (useSystem in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A && (point || rep))) nonstandardInput = true
        if (useSystem == NumSystem.BALANCED && complement) {   /* must come after wrongDigits check */
            complement = false
            return Pair(ONE, ZERO)
        }
        if (!isNumber) return default
        denom *= prePointRecurr
        if (denomSub != denom) {
            numer -= numerSub
            denom -= denomSub
        }
        numer *= prePointRecurr
        if (neg) numer = -numer
            else if (leftPad > -1) numer -= denom * wrappingPoint(leftPad, bigBase)
        return reduceFraction(numer, denom)
    }

    private val romanApostrophusM = mapOf("ↀ" to "M")
    private val romanApostrophus  = mapOf("CCCIↃↃↃ" to "ↈ", "CCIↃↃ" to "ↂ", "CIↃ" to "M", "IↃↃↃ" to "ↇ", "IↃↃ" to "ↁ", "IↃ" to "D")
    private val romanBrackets     = mapOf("(((I)))" to "ↈ", "((I))" to "ↂ", "(I)" to "M", "I)))" to "ↇ", "I))" to "ↁ", "I)" to "D")

    private fun parseRoman(st: String): BigFraction {  /* REALLY permissive */
        val digitsMap = mapOf('N' to 0, 'I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000,
            'ↁ' to 5000, 'ↂ' to 10000, 'ↇ' to 50000, 'ↈ' to 100000)
        val ouncesMap = mapOf('.' to 1, '·' to 1, ':' to 2, '…' to 3, '∴' to 3, '∷' to 4, '⁙' to 5, 'S' to 6)
        val negative = if (st.startsWith('-')) 1 else if (st.startsWith("..") && !st.all { it == '.'}) { complement = true; 2 } else 0
        val stTrimmed = st.substring(negative).toUpperCase(Locale.ROOT)
        val fracPos = stTrimmed.indexOfAny(ouncesMap.keys.toCharArray())
        var stInt = (if (fracPos == -1) stTrimmed else stTrimmed.substring(0, fracPos)) + "N"
        for ((long, short) in romanApostrophusM + romanApostrophus + romanBrackets) stInt = stInt.replace(long, short)
        var n = 0
        for (i in stInt.indices) {
            val d = digitsMap[stInt[i]] ?: return Pair(ZERO, ZERO) /* invalid character */
            var sign = 0
            var j = i + 1
            while (sign == 0 && j < stInt.length) sign = (d - (digitsMap[stInt[j++]] ?: 0)).sign
            n += sign * d
        }
        if (!Regex(arrayOf("", "ↈ", "ↈↈ", "ↈↈↈ")                                                           [n / 100000] +
            arrayOf("", "ↂ", "ↂↂ", "ↂↂↂ", "ↂↇ|ↂↂↂↂ", "ↇ", "ↇↂ", "ↇↂↂ", "ↇↂↂↂ", "(ↂↈ|ↇↂↂↂↂ)")[n / 10000 % 10] +
            arrayOf("", "M", "MM", "MMM", "(Mↁ|MMMM)", "ↁ", "ↁM", "ↁMM", "ↁMMM", "(Mↂ|ↁMMMM")               [n / 1000 % 10] +
            arrayOf("", "C", "CC", "CCC", "(CD|CCCC)", "D", "DC", "DCC", "DCCC", "(CM|DCCCC)")                    [n / 100 % 10] +
            arrayOf("", "X", "XX", "XXX", "(XL|XXXX)", "L", "LX", "LXX", "LXXX", "(XC|LXXXX)")                    [n /  10 % 10] +
            arrayOf("", "I", "II", "III", "(IV|IIII)", "V", "VI", "VII", "VIII", "(IX|VIIII)")                    [n       % 10] +
                "N").matches(stInt) && !(stInt == "NN" && fracPos == -1)) nonstandardInput = true
        var ounces = 0
        if (fracPos > -1) for (c in stTrimmed.substring(fracPos))
            ounces += ouncesMap[c] ?: return Pair(ZERO, ZERO) /* invalid character */
        if ((ounces >= 6 && stTrimmed[fracPos] != 'S') || ounces >= 12) nonstandardInput = true
        n = 12 * n + ounces
        return reduceFraction(when (negative) {
               0 ->  n
               1 -> -n
            else ->  n - 12 * 400000
        }.toBigInteger(), TWELVE)
    }

    /*   O u t p u t   */

    override fun toString(): String = toString(aFormat = format)
    fun toString(withBase: Boolean = false, aFormat: QFormat = format) = when (aFormat) {
        QFormat.POSITIONAL -> toPositional()
        QFormat.FRACTION   -> toFraction()
        QFormat.MIXED      -> toMixed()
        QFormat.CONTINUED  -> toContinued()
        QFormat.UNICODE    -> toUnicode()
    } + if (withBase && aFormat != QFormat.UNICODE) ((if (system != NumSystem.ROMAN) (base.toString().map {
        arrayOf('₀', '₁', '₂', '₃', '₄', '₅', '₆', '₇', '₈', '₉')[it.toInt() - 48]
    } .joinToString("")) else " ") + when (system) {
        NumSystem.STANDARD    -> ""
        NumSystem.BALANCED    -> "ᵇᵃˡ"
        NumSystem.BIJECTIVE_1 -> "ᵇʲ¹"
        NumSystem.BIJECTIVE_A -> "ᵇʲᵃ"
        //NumSystem.DMS         -> "ᵈᵐˢ"
        //NumSystem.FACTORIAL   -> "!"
         NumSystem.ROMAN      -> "ʳᵒᵐ"
    }) else ""

    fun toUnicode() = if (denominator == ONE  && numerator in 0x20.toBigInteger()..0x31FFF.toBigInteger())
        "\"" + numerator.toInt().toChar() + "\"" else ""

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
        if (system == NumSystem.ROMAN) toRoman().let { if (it != "") return it }
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
        val rounded = nRep > MainActivity.maxDigitsAfter - nPre
        if (rounded) nRep = MainActivity.maxDigitsAfter - nPre
        val numPower = numerator * bigBase.pow(nPre + nRep)
        val up = if (system == NumSystem.STANDARD || numPower.abs() % denominator <= denominator / TWO) ZERO
            else if (numerator > ZERO) ONE else -ONE
        val outSt = intToBase(numPower / denominator + up, nPre + nRep, nRep > 0)
        val posRep = (outSt.length - (if (MainActivity.groupDigits) (nPre + nRep - 1) / groupSize - nPre / groupSize else 0) - nRep)
            .coerceAtMost(outSt.length)
        return outSt.substring(0, posRep) + (if (nRep > 0) '\'' + outSt.substring(posRep) else "") + (if (rounded) "…" else "")
    }

    private fun intToBase(a: BigInteger, fracDigits: Int = 0, recurring: Boolean = false): String {
        if (system == NumSystem.ROMAN) toRoman(a, ONE).let { if (it != "") return it }
        val bigBase = base.toBigInteger()
        var x = if (complement && a < ZERO) a + wrappingPoint(ceil(lnBigInteger(-a) / ln(base.toDouble())).roundToInt() + 2, bigBase) -
            (if (recurring) ONE else ZERO)
                else a.abs()
        var digit: Int;  var nDigits = 0
        var st = ""
        while (x != ZERO || nDigits < fracDigits + 1) {
            digit = (x % bigBase).toInt()
            when (system) {
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
                else -> { }
            }
            st = digitToChar(digit, base, system) + (if (nDigits == 0 || (nDigits - fracDigits) % groupSize != 0) "" else
                if (nDigits == fracDigits) "." else if (MainActivity.groupDigits) " " else "") + st
            x /= bigBase
            nDigits++
        }
        return if (complement && a < ZERO) "..$st" else if (system != NumSystem.BALANCED && a < ZERO) "-$st" else
            if (system in NumSystem.BIJECTIVE_1..NumSystem.BIJECTIVE_A && a == ZERO) "λ" else st
    }

    private tailrec fun continuedFraction(numer: BigInteger, denom: BigInteger, pre: String = ""): String {
        var i: BigInteger = numer / denom
        if (numer < ZERO && numer % denom != ZERO) i--
        val st = "$pre, ${intToBase(i)}"
        return if (i * denom == numer) '[' + st.substring(2).replaceFirst(',', ';') + ']' else continuedFraction(denom, numer - i * denom, st)
    }

    fun toRoman(numer: BigInteger = numerator, denom: BigInteger = denominator, showNonPositive: Boolean = true): String {
        val absNumer = if (numer >= ZERO) numer else if (complement) numer + denom * 400000.toBigInteger() else -numer
        if (denom == ZERO || absNumer / denom !in ZERO..399999.toBigInteger() || (numer <= ZERO && !showNonPositive)) return ""
        val intValue = (absNumer / denom).toInt()
        var result = arrayOf("", "ↈ", "ↈↈ", "ↈↈↈ")                                      [intValue / 100000] +
            arrayOf("", "ↂ", "ↂↂ", "ↂↂↂ", "ↂↇ", "ↇ", "ↇↂ", "ↇↂↂ", "ↇↂↂↂ", "ↂↈ") [intValue /  10000 % 10] +
            arrayOf("", "M", "MM", "MMM", "Mↁ", "ↁ", "ↁM", "ↁMM", "ↁMMM", "Mↂ")           [intValue /   1000 % 10] +
            arrayOf("", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM")                [intValue /    100 % 10] +
            arrayOf("", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC")                [intValue /     10 % 10] +
            arrayOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")                [intValue          % 10] +
            arrayOf("", "·", ":", "∴", "∷", "⁙", "S", "S·", "S:", "S∴", "S∷", "S⁙")[(absNumer % denom * TWELVE / denom).toInt()]
        if (result.isEmpty()) result = "N"
        if (TWELVE % denom != ZERO) result += " …"
        var i = 0
        for ((long, short) in (if (MainActivity.apostrophus == 1000) romanApostrophusM else romanApostrophus)) {
            if (i++ >= MainActivity.apostrophus) break
            result = result.replace(short, long + if (MainActivity.apostrophus < 1000) " " else "")
        }
        return (if (numer >= ZERO) "" else if (complement) ".." else "-") +
            if (MainActivity.lowerDigits) result.toLowerCase(Locale.ROOT) else result
    }

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
        // names of musical intervals: https://de.wikipedia.org/wiki/Tonstruktur_(mathematische_Beschreibung)
        // 4.1 "double octave + between unison and minor second", 8.1 ditto
        // 7.9 "double octave + between major seventh and octave"
        // 7.99 "~ double octave + octave" vs. 8.01 "~ triple octave"

        val intervals = arrayOf(Pair(1, 1),                                         ///////////  more intervals
            /* 2 */ Pair(256, 243),/*Pair(16, 15),*/ Pair(9, 8),
            /* 3 */ Pair(32, 27), Pair(6, 5), Pair(5, 4), Pair(81, 64),
            /* 4 */ Pair(4, 3),
            /* T */ /*Pair(7, 5),*/ Pair(729, 512), /*Pair(10, 7),*/
            /* 5 */ Pair(3, 2),
            /* 6 */ Pair(128, 81), Pair(8, 5), Pair(5, 3), Pair(27, 16),
            /* 7 */ Pair(16, 9), /*Pair(9, 5),*/ /*Pair(15, 8),*/ Pair(243, 128),
            /* 8 */ Pair(2, 1))
        val intervalNames = resources.getStringArray(R.array.interval_names)
        var intervalName = if (octaves < 2) "" else resources.getStringArray(R.array.octaves).let {
            if (octaves - 2 < it.size) it[octaves - 2] else resources.getString(R.string.octaves, octaves)
        } + " + "
        for (i in intervals.indices) {
            val ratioNumer = numer * intervals[i].second.toBigInteger()
            val ratioDenom = denom * intervals[i].first.toBigInteger()
            if (ratioNumer * toleranceDenom <= ratioDenom * toleranceNumer) {
                intervalName = when {
                    ratioNumer == ratioDenom -> intervalName + intervalNames[i]
                    ratioNumer * toleranceNumer >= ratioDenom * toleranceDenom -> "~ " + intervalName + intervalNames[i].substringBefore(" (")
                    else -> intervalName + resources.getString(R.string.between_intervals,
                        intervalNames[i - 1].substringBefore(" ("), intervalNames[i].substringBefore(" ("))
                }
                break
            }
        }
        return intervalName.removeSuffix(" + " + intervalNames[0]).replace(Regex("0[0-9]")) {
            resources.getStringArray(R.array.basic_intervals)[it.value.toInt() + if (octaves == 1) 8 else 0]
        }
    }

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