package app.iptvtest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.TimeZone

/**
 * Fuso horário usado nos horários dos jogos. Por padrão é o do aparelho, mas muita TV/box vem com o fuso errado
 * (ex.: Portugal/Inglaterra em vez da Bélgica, 1 h atrás), então a pessoa pode escolher o dela nos Ajustes.
 * Trocar muda o fuso padrão do app inteiro; [rev] sobe para as telas se refazerem.
 */
object AppTz {
    /** Fuso que o aparelho tinha quando o app abriu (guardado antes de qualquer troca). */
    private val device: TimeZone = TimeZone.getDefault()

    var rev by mutableIntStateOf(0)
        private set

    /** (código do fuso, nome na tela). Código vazio = o do aparelho. */
    val OPTIONS: List<Pair<String, String>> = listOf(
        "" to "Automático (do aparelho)",
        "Europe/Brussels" to "Bruxelas / Paris / Madri",
        "Europe/Lisbon" to "Lisboa",
        "Europe/London" to "Londres",
        "America/Sao_Paulo" to "Brasília",
    )

    fun apply(id: String) {
        TimeZone.setDefault(if (id.isEmpty()) device else TimeZone.getTimeZone(id))
        rev++
    }

    /** "Europe/Lisbon, UTC+1" para mostrar o que o app está usando agora. */
    fun describe(): String {
        val tz = TimeZone.getDefault()
        val min = tz.getOffset(System.currentTimeMillis()) / 60_000
        val sign = if (min < 0) "-" else "+"
        val a = Math.abs(min)
        val off = if (a % 60 == 0) "UTC$sign${a / 60}" else String.format(Locale.US, "UTC%s%d:%02d", sign, a / 60, a % 60)
        return tz.id + ", " + off
    }
}
