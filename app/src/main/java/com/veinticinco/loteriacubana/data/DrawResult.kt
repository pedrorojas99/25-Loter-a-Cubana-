package com.veinticinco.loteriacubana.data

/**
 * Resultado oficial de un sorteo. Los dígitos se guardan como String:
 * nunca convertirlos a Int, para preservar ceros iniciales y el orden publicado.
 */
enum class LotteryGame(val digitCount: Int) {
    PICK_2(2),
    PICK_3(3)
}

enum class DrawPeriod {
    MIDDAY,
    EVENING
}

data class DrawResult(
    val drawDate: String,       // ISO-8601: yyyy-MM-dd
    val game: LotteryGame,
    val period: DrawPeriod,
    val winningNumbers: String,
    val fireball: String? = null,
    val source: String = "Florida Lottery",
    val verified: Boolean = false
) {
    init {
        require(drawDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            "drawDate debe usar yyyy-MM-dd"
        }
        require(winningNumbers.length == game.digitCount && winningNumbers.all(Char::isDigit)) {
            "El resultado debe contener exactamente ${game.digitCount} dígitos"
        }
        require(fireball == null || (fireball.length == 1 && fireball.all(Char::isDigit))) {
            "Fireball debe ser un solo dígito"
        }
    }

    /** Clave estable para impedir duplicados de fecha, juego y turno. */
    val uniqueKey: String
        get() = "${drawDate}_${game.name}_${period.name}"
}

/** Estadística descriptiva básica calculada sin alterar los resultados originales. */
object DigitFrequency {
    fun calculate(results: List<DrawResult>): Map<Char, Int> {
        val counts = mutableMapOf<Char, Int>()
        results.forEach { result ->
            result.winningNumbers.forEach { digit ->
                counts[digit] = (counts[digit] ?: 0) + 1
            }
        }
        return ('0'..'9').associateWith { counts[it] ?: 0 }
    }
}
