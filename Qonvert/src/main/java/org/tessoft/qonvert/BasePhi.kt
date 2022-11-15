package org.tessoft.qonvert

import java.math.BigInteger
import java.math.BigInteger.*
import java.util.*
import kotlin.math.*

//typealias BigFraction = Pair<BigInteger, BigInteger>

val PHI = PhiNumber(ZERO, ONE)
val INV_PHI = PhiNumber(-ONE, ONE)

const val IS_EMPTY = 0x01
const val INVALID_CHAR = 0x02
const val NONSTANDARD_DIGIT = 0x04
const val IS_COMPLEMENT = 0x08
const val IS_MIXED = 0x10
const val IS_FRACTION = 0x20

/*val FRACTION_CHARS  = mapOf('½' to Pair(1, 2), '⅓' to Pair(1, 3), '¼' to Pair(1, 4), '⅕' to Pair(1, 5), '⅙' to Pair(1, 6),
    '⅐' to Pair(1, 7), '⅛' to Pair(1, 8), '⅑' to Pair(1, 9),'⅒' to Pair(1, 10), '⅔' to Pair(2, 3), '¾' to Pair(3, 4),
    '⅖' to Pair(2, 5), '⅗' to Pair(3, 5), '⅘' to Pair(4, 5), '⅚' to Pair(5, 6), '⅜' to Pair(3, 8), '⅝' to Pair(5, 8),
    '⅞' to Pair(7, 8), '↉' to Pair(0, 1)).mapValues { Pair(it.value.first.toBigInteger(), it.value.second.toBigInteger()) }*/

fun matrixFibonacci(n: Int): Pair<BigInteger, BigInteger> { /* https://stackoverflow.com/questions/24438655/ruby-fibonacci-algorithm/24439070 */
    if (n == 1) return Pair(ZERO, ONE)
    val f = matrixFibonacci(n / 2)
    val c = f.first * f.first + f.second * f.second
    val d = f.second * (f.second + TWO * f.first)
    return if (n % 2 == 0) Pair(c, d) else Pair(d, c + d)
}

fun fibonacci(n: Int): BigInteger = when (n.sign) {
    1 -> matrixFibonacci(n).second
   -1 -> matrixFibonacci(-n).second * if (n % 2 == 0) -ONE else ONE
    else -> ZERO
}

fun phiPower(n: Int) = if (n >= 1) PhiNumber(matrixFibonacci(n)) else matrixFibonacci(1 - n).let {
    if (n % 2 == 0) PhiNumber(it.second, -it.first) else PhiNumber(-it.second, it.first)
}

fun phiPower(n: Int, negative: Boolean) = if (negative && n % 2 != 0) -phiPower(n) else phiPower(n)

/*fun reduceFraction(numer: BigInteger, denom: BigInteger): BigFraction {
    if (denom == ONE) return Pair(numer, ONE)
    var gcd = numer.gcd(denom).coerceAtLeast(ONE)
    if (denom < ZERO || (denom == ZERO && numer < ZERO)) gcd = -gcd
    return Pair(numer / gcd, denom / gcd)
}*/

fun formatNumberPair(a: String, b: String, symbol: String) = (if (a == "0" && b != "0") "" else a) +
    if (b == "0") "" else ((if (b.startsWith('-')) "-" else if (a == "0") "" else "+") + (if (b == "1" || b == "-1") "" else b.removePrefix("-")) + symbol)

class PhiNumber(var a: BigInteger, var b: BigInteger = ZERO, root5: Boolean = false) {

    private val five = 5.toBigInteger()
    var parseResult = 0
        private set

    init {
        if (root5) {
            a -= b
            b *= TWO
        }
    }

    constructor(a: Int, b: Int = 0, root5: Boolean = false): this(a.toBigInteger(), b.toBigInteger(), root5)
    constructor(p: PhiNumber): this(p.a, p.b)
    constructor(pair: Pair<BigInteger, BigInteger>): this(pair.first, pair.second)

    constructor(s: String, negative: Boolean = false): this(0) {
        val st = s.filterNot { it in " \t\n\r" }
        val minus = st.startsWith('-')
        val complement = st.startsWith("..")
        if (complement) parseResult = parseResult or IS_COMPLEMENT
        val negMarker = if (minus) 1 else if (complement) 2 else 0
        val point = with(st.indexOf('.', negMarker)) { if (this == -1) st.length else this }
        var result = 0.toPhiNumber()
        val firstPlace = point - negMarker - 1
        var power = phiPower(firstPlace, negative)
                                                /*  !!  DIFFERS FROM STANDALONE BasePhi:  !!  */
        for (i in negMarker until st.length) with(DIGITS.indexOf(st[i], ignoreCase = true)) {
            if (this > -1) {
                result += power * toBigInteger()
                power *= if (negative) -INV_PHI else INV_PHI
                if (this > 1) parseResult = parseResult or NONSTANDARD_DIGIT
            } else if (i != point) parseResult = parseResult or INVALID_CHAR
        }
        if (parseResult and INVALID_CHAR == 0) {
            if (st.all { it !in DIGITS }) parseResult = parseResult or IS_EMPTY
            if (complement) result -= phiPower(firstPlace + 1) /// , negative) ?
            a = result.a * if (minus) -ONE else ONE
            b = result.b * if (minus) -ONE else ONE
        }
    }

    operator fun plus(other: PhiNumber) = PhiNumber(a + other.a, b + other.b)
    operator fun plus(other: PhiFraction) = other + this
    operator fun plus(other: BigInteger) = PhiNumber(a + other, b)
    operator fun inc() = PhiNumber(a + ONE, b)

    operator fun minus(other: PhiNumber) = PhiNumber(a - other.a, b - other.b)
    operator fun minus(other: PhiFraction) = -other + this
    operator fun minus(other: BigInteger) = PhiNumber(a - other, b)
    operator fun dec() = PhiNumber(a - ONE, b)

    operator fun unaryPlus() = PhiNumber(this)
    operator fun unaryMinus() = PhiNumber(-a, -b)

    operator fun times(other: PhiNumber) = PhiNumber(a * other.a + b * other.b, a * other.b + b * other.a + b * other.b)
    operator fun times(other: PhiFraction) = other * this
    operator fun times(other: BigInteger) = PhiNumber(a * other, b * other)

    operator fun compareTo(other: PhiNumber): Int {
        val c = other.b - b
        val d = TWO * (a - other.a) - c
        return if (d.signum() == c.signum()) (d * d).compareTo(five * c * c) * c.signum()
            else d.signum() - c.signum()
    }
    operator fun compareTo(other: BigInteger): Int {
        val d = TWO * (a - other) + b
        return if (d.signum() == -b.signum()) (d * d).compareTo(five * b * b) * -b.signum()
            else d.signum() + b.signum()
    }

    override operator fun equals(other: Any?) = if (other is PhiNumber) a == other.a && b == other.b else false
    override fun hashCode() = Objects.hash(a, b)

    override fun toString() = formatNumberPair(a.toString(), b.toString(), "φ")

    fun toString(root5: Boolean) = if (root5) {
        if (b % TWO == ZERO) formatNumberPair((a + b / TWO).toString(), (b / TWO).toString(), "√5̅")
            else formatNumberPair((TWO * a + b).toString() + "/2", (if (b == -ONE) "-" else "") + b.toString(), "√5̅/2")
    } else toString()

    fun toBasePhi(groupDigits: Boolean = false, maxDigits: Int = Int.MAX_VALUE, complement: Boolean = false, negative: Boolean = false): String {
        if (a == ZERO && b == ZERO) return "0"
        if (this < ZERO /* && !negative */) return "-" + (-this).toBasePhi(groupDigits, maxDigits)
        var x = PhiNumber(this)
        val powers = mutableListOf(1.toPhiNumber())
        val base = PHI // if (negative) -PHI else PHI
        while (x >= powers.last()) powers.add(powers.last() * base) /////////  base -φ
        val result = StringBuilder()
        for ((i, p) in powers.dropLast(1).withIndex().reversed()) {
            result.append(if (x >= p) { x -= p; '1' } else '0')
            if (groupDigits && i > 0 && i % 4 == 0) result.append(' ')
        }
        if (x > ZERO) {
            result.append('.')
            var i = 0
            do {
                x *= base
                if (groupDigits && i++ % 4 == 0 && i > 1) result.append(' ')
                result.append(if (x >= ONE) { x -= ONE; '1' } else '0')
            } while (x > ZERO && i < maxDigits)
            if (i >= maxDigits) result.append('…')
        }
        return result.toString()
    }

    fun toPhiFraction() = PhiFraction(this)
    fun toFloat() = a.toFloat() + b.toFloat() * (1f + sqrt(5f)) / 2f
}

class PhiFraction(var aNumer: BigInteger, var bNumer: BigInteger = ZERO, var denom: BigInteger = ONE, root5: Boolean = false) {

    private val five = 5.toBigInteger()
    var parseResult = 0
        private set

    val a get() = reduceFraction(aNumer, denom)
    val b get() = reduceFraction(bNumer, denom)
    val r get() = reduceFraction(TWO * aNumer + bNumer, TWO * denom)
    val s get() = reduceFraction(bNumer, TWO * denom)

    init {
        if (root5) {
            aNumer -= bNumer
            bNumer *= TWO
        }
        reduce()
    }

    constructor(aNumer: Int, bNumer: Int = 0, denom: Int = 1, root5: Boolean = false):
        this(aNumer.toBigInteger(), bNumer.toBigInteger(), denom.toBigInteger(), root5)
    constructor(p: PhiFraction): this(p.aNumer, p.bNumer, p.denom)
    constructor(n: PhiNumber): this(n.a, n.b, ONE)
    constructor(a: BigFraction, root5: Boolean = false): this(a.first, ZERO, a.second, root5)

    constructor(s: String, negative: Boolean = false): this(0) {
        val st = s.filterNot { it in " \t\n\r" }
        var under = st.indexOf('_')
        var slash = st.indexOf('/')
        if (slash > -1 && under > slash) under = -1
        if (under > -1) parseResult = parseResult or IS_MIXED
        if (slash > -1) parseResult = parseResult or IS_FRACTION
        (if (slash == -1 && under == -1) parsePositional(st, negative) else {
            if (slash == -1) slash = st.length
            parsePositional(st.substring(0, max(under, 0)), negative) +
                parsePositional(st.substring(under + 1, slash), negative) /
                parsePositional(st.substring(min(slash + 1, st.length)), negative, default = 1) *
                    if (st.startsWith("-")) -ONE else ONE
        }).let {
            aNumer = it.aNumer
            bNumer = it.bNumer
            denom = it.denom
            reduce()
        }
    }

    private fun parsePositional(s: String, negative: Boolean = false, default: Int = 0): PhiFraction {
        if (s.all { it in "-.:" }) {
            parseResult = parseResult or IS_EMPTY
            return PhiFraction(default)
        }
        val fraction = FRACTION_CHARS[s.lastOrNull()]
        val st = if (fraction == null) s else s.dropLast(1)
        var point = st.indexOf('.')
        if (point == -1) point = st.length
        val rep = st.indexOf(':')
        return (if (rep == -1 || rep == st.length - if (point < rep) 1 else 2) {
            val n = st.replaceFirst(":", "").toPhiNumber(negative)
            parseResult = parseResult or n.parseResult
            n.toPhiFraction()
        } else {
            val n = st.replaceFirst(":", "").replaceFirst(".", "").toPhiNumber(negative)
            parseResult = parseResult or n.parseResult
            (n - st.substring(0, rep).replaceFirst(".", "").toPhiNumber(negative)).toPhiFraction() /
                (phiPower(st.length - point - if (point < rep) 2 else 1, negative) - phiPower(rep - point + if (point < rep) -1 else +1, negative))
        }) + if (fraction?.second != TEN) PhiFraction(fraction ?: BigFraction(ZERO, ONE)) else PhiFraction(-1, 1, 1)
    }

    operator fun plus(other: PhiFraction) = PhiFraction(aNumer * other.denom + denom * other.aNumer,
                                                        bNumer * other.denom + denom * other.bNumer, denom * other.denom)
    operator fun plus(other: PhiNumber) = PhiFraction(aNumer + denom * other.a, bNumer + denom * other.b, denom)
    //operator fun plus(other: BigFraction) = PhiFraction(aNumer * other.second + denom * other.first, denom * other.second, bNumer, denom)
    operator fun plus(other: BigInteger) = PhiFraction(aNumer + denom * other, bNumer, denom)

    operator fun inc() = PhiFraction(aNumer + denom, bNumer, denom)

    operator fun minus(other: PhiFraction) = PhiFraction(aNumer * other.aNumer - denom * other.denom,
                                                         bNumer * other.bNumer - denom * other.denom, denom * other.denom)
    operator fun minus(other: PhiNumber) = PhiFraction(aNumer - denom * other.a, bNumer - denom * other.b, denom)
    //operator fun minus(other: BigFraction) = PhiFraction(aNumer * other.second - denom * other.first, denom * other.second, bNumer, denom)
    operator fun minus(other: BigInteger) = PhiFraction(aNumer - denom * other, bNumer, denom)

    operator fun dec() = PhiFraction(aNumer - denom, bNumer, denom)

    operator fun unaryPlus() = PhiFraction(this)
    operator fun unaryMinus() = PhiFraction(-aNumer, -bNumer, denom)

    operator fun times(other: PhiFraction) = PhiFraction(aNumer * other.aNumer + bNumer * other.bNumer,
        aNumer * other.bNumer + other.aNumer * bNumer + bNumer * other.bNumer, denom * other.denom)
    operator fun times(other: PhiNumber) = PhiFraction(aNumer * other.a + bNumer * other.b,
        aNumer * other.b + other.a * bNumer + bNumer * other.b, denom)
    operator fun times(other: BigFraction) = PhiFraction(aNumer * other.first, bNumer * other.first, denom * other.second)
    operator fun times(other: BigInteger) = PhiFraction(aNumer * other, bNumer * other, denom)

    operator fun div(other: PhiFraction): PhiFraction { /* (a+bφ)/(c+dφ) = [(ac+ad-bd)+(bc-ad)φ] / (c²+cd-d²) */
        val denomNumer = (other.aNumer * other.aNumer * other.denom * other.denom + other.aNumer * other.denom * other.bNumer * other.denom -
                          other.denom * other.denom * other.bNumer * other.bNumer) * denom * other.denom * denom * other.denom
        val denomDenom = other.denom * other.denom * other.denom * other.denom
        return PhiFraction(denomDenom *
            (aNumer * other.aNumer * denom * other.denom + aNumer * other.denom * denom * other.bNumer - denom * other.denom * bNumer * other.bNumer),
            denomDenom * (bNumer * other.aNumer * denom * other.denom - denom * other.denom * aNumer * other.bNumer),
            denomNumer)
    }
    operator fun div(other: PhiNumber): PhiFraction {
        val newDenom = (other.a * other.a + other.a * other.b - other.b * other.b) * denom
        return PhiFraction(aNumer * other.a + aNumer * other.b - bNumer * other.b, bNumer * other.a - aNumer * other.b, newDenom)
    }
    operator fun div(other: BigFraction) = PhiFraction(aNumer * other.second, bNumer * other.second, denom * other.first)
    operator fun div(other: BigInteger) = PhiFraction(aNumer, bNumer, denom * other)

    operator fun compareTo(other: PhiFraction): Int {
        if (denom == ZERO) return 1
        if (other.denom == ZERO) return -1
        val cNumer = other.bNumer * denom - bNumer * other.denom
        val cDenom = denom * other.denom
        val zNumer = TWO * (aNumer * other.denom - other.aNumer * denom)
        val zDenom = denom * other.denom
        val dNumer = zNumer * cDenom - cNumer * zDenom
        val dDenom = zDenom * cDenom
        val cSignum = cNumer.signum() / cDenom.signum()
        val dSignum = dNumer.signum() / dDenom.signum()
        return if (dSignum == cSignum) (dNumer * dNumer * cDenom * cDenom).compareTo(five * cNumer * cNumer * dDenom * dDenom) * cSignum
            else dSignum - cSignum
    }

    operator fun compareTo(other: PhiNumber): Int {
        if (denom == ZERO) return 1
        val cNumer = other.b * denom - bNumer
        val dNumer = TWO * (aNumer - other.a * denom) - cNumer
        val cSignum = cNumer.signum() / denom.signum()
        val dSignum = dNumer.signum() / denom.signum()
        return if (dSignum == cSignum) (dNumer * dNumer * denom * denom).compareTo(five * cNumer * cNumer * denom * denom) * cSignum
            else dSignum - cSignum
    }

    operator fun compareTo(other: BigFraction): Int {
        if (denom == ZERO) return 1
        if (other.second == ZERO) return -1
        val zNumer = TWO * (aNumer * other.second - other.first * denom)
        val zDenom = denom * other.second
        val dNumer = zNumer * denom + zDenom * bNumer
        val dDenom = zDenom * denom
        val cSignum = -bNumer.signum() / denom.signum()
        val dSignum = dNumer.signum() / dDenom.signum()
        return if (dSignum == cSignum) (dNumer * dNumer * denom * denom).compareTo(five * bNumer * bNumer * dDenom * dDenom) * cSignum
            else dSignum - cSignum
    }

    operator fun compareTo(other: BigInteger): Int {
        if (denom == ZERO) return 1
        val dNumer = TWO * (aNumer - other * denom) * denom + denom * bNumer
        val dDenom = denom * denom
        val cSignum = -bNumer.signum() / denom.signum()
        val dSignum = dNumer.signum() / dDenom.signum()
        return if (dSignum == cSignum) (dNumer * dNumer * denom * denom).compareTo(five * bNumer * bNumer * dDenom * dDenom) * cSignum
            else dSignum - cSignum
    }

    override operator fun equals(other: Any?) = if (other is PhiFraction)
        aNumer == other.aNumer && bNumer == other.bNumer && denom == other.denom else false

    override fun hashCode() = Objects.hash(aNumer, bNumer, denom)

    fun abs() = if (this > ZERO) PhiFraction(this) else -this

    override fun toString() = formatNumberPair(a.toStringX(), b.first.toString(), "φ" + if (b.second == ONE) "" else "/${b.second}")

    fun toString(root5: Boolean) = if (root5) with(s) {
        formatNumberPair(r.toStringX(), first.toString(), "√5̅" + if (second == ONE) "" else "/${second}")
    } else toString()

    fun toBasePhi(groupDigits: Boolean = false, maxDigits: Int = Int.MAX_VALUE, complement: Boolean = false, alt: Boolean = false, negative: Boolean = false):
            String {
        if (denom == ZERO) return if (aNumer == ZERO && bNumer == ZERO) "NaN" else "Infinity"
        if (this < ZERO && !complement && !negative) return "-" + (-this).toBasePhi(groupDigits, maxDigits, false, alt) ///////// base -φ
        var x = PhiFraction(abs())
        val powers = mutableListOf(PhiNumber(ONE), PHI)
        val base = if (negative) -PHI else PHI ////////// base -φ
        while (x >= powers.last()) powers.add(powers.last() * base) //////////////////////  base -φ
        if (this < ZERO && complement) {
            if (powers.size % 2 == 0) powers.add(powers.last() * base)
            x = powers.last() - x
        }
        val result = StringBuilder()
        for ((i, p) in powers.dropLast(1).withIndex().reversed()) {
            result.append(if (x >= p) { x -= p; '1' } else '0')
            if (groupDigits && i > 0 && i % 4 == 0) result.append(' ')
        }
        if (x > ZERO) {
            result.append('.')
            val point = result.length
            val remainders = mutableListOf<PhiFraction>()
            do {
                remainders.add(x)
                x *= base
                result.append(if (x >= ONE) { x -= ONE; '1' } else '0')
            } while (x > ZERO && x !in remainders && remainders.size < maxDigits)
            val rep = remainders.indexOf(x)
            if (rep > -1) result.insert(point + rep, ':')
            if (groupDigits) for (i in result.length - 1 - (result.length - point + if (rep > -1) 3 else 0) % 4 downTo point + 1 step 4)
                result.insert(i + if (i <= point + rep) 0 else 1, ' ')
            if (remainders.size >= maxDigits) result.append('…')
        }
        if (this < ZERO && complement) result.insert(0, if (!groupDigits) "..1010" else if (powers.size % 4 == 3) "..10 10" else "..1010 ")
        return if (!alt || ':' in result) result.toString() else when (result.toString()) {
            "1" -> "0.:10"
            "0" -> "..1010.:10"
            else -> result.dropLast(1).toString() + ":01"
        }
    }

    fun toFloat() = (aNumer.toFloat() + bNumer.toFloat() * (1f + sqrt(5f)) / 2f) / denom.toFloat()

    fun reduce() {
        var gcd = aNumer.gcd(bNumer).gcd(denom).coerceAtLeast(ONE)
        if (denom < ZERO || (denom == ZERO && aNumer < ZERO)) gcd = -gcd
        aNumer /= gcd
        bNumer /= gcd
        denom /= gcd
    }
}

fun Int.toPhiNumber() = PhiNumber(this)
fun BigInteger.toPhiNumber() = PhiNumber(this)
fun String.toPhiNumber(negative: Boolean = false) = PhiNumber(this, negative)

fun Int.toPhiFraction() = PhiFraction(this)
fun BigInteger.toPhiFraction() = PhiFraction(this)
fun BigFraction.toPhiFraction() = PhiFraction(this)
fun String.toPhiFraction(negative: Boolean = false) = PhiFraction(this, negative)

fun Int.toBasePhi() = toPhiNumber().toBasePhi()
fun BigInteger.toBasePhi() = toPhiNumber().toBasePhi()
fun BigFraction.toBasePhi() = toPhiFraction().toBasePhi()

fun BigFraction.toStringX() = first.toString() + if (second == ONE) "" else "/$second"
