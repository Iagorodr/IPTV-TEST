package app.iptvtest

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Idiomas oferecidos na primeira abertura (a etiqueta é a do Android, ex.: pt-BR). */
val APP_LANGS: List<Pair<String, String>> = listOf(
    "pt-BR" to "Português (Brasil)",
    "pt-PT" to "Português (Portugal)",
    "en" to "English",
    "es" to "Español",
    "fr" to "Français",
    "it" to "Italiano",
    "de" to "Deutsch",
    "nl" to "Nederlands",
)

/** Gêneros que a pessoa pode preferir (os mesmos nomes que o app usa nas prateleiras). */
val PICK_GENRES: List<String> = listOf(
    "Ação", "Aventura", "Animação", "Comédia", "Crime", "Documentários", "Drama",
    "Família", "Fantasia", "Ficção Científica", "Guerra", "Romance", "Suspense", "Terror",
)

private val COMMON_ZONES = listOf(
    "Europe/Lisbon", "Europe/London", "Europe/Brussels", "Europe/Paris", "Europe/Madrid",
    "Atlantic/Azores", "America/Sao_Paulo", "America/Manaus", "America/New_York", "America/Toronto",
)

/** A língua que o aparelho já usa, escolhida entre as opções. */
fun detectLang(device: String): String =
    APP_LANGS.firstOrNull { it.first.equals(device, true) }?.first
        ?: APP_LANGS.firstOrNull { device.startsWith(it.first.substringBefore('-'), true) }?.first
        ?: "pt-BR"

/** Primeira abertura. Parte 1 (nome, idioma e fuso) é obrigatória; a parte 2 (times e gêneros) pode ser pulada. */
@Composable
fun WelcomeScreen(vm: MainViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        SunBackdrop(modifier = Modifier.fillMaxSize(), sunX = 0.5f, sunY = 0.25f, sunR = 0.17f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_name), color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Light, letterSpacing = 6.sp)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(30.dp).height(2.dp).background(Accent))
            Spacer(Modifier.height(18.dp))
            Column(modifier = Modifier.width(640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (vm.welcomeStage == 1) StepOne(vm) else StepTwo(vm)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepOne(vm: MainViewModel) {
    var name by rememberSaveable { mutableStateOf(vm.user.profileName) }
    var lang by rememberSaveable { mutableStateOf(vm.user.profileLang.ifEmpty { detectLang(vm.deviceLang()) }) }
    val deviceTz = remember { java.util.TimeZone.getDefault().id }
    var tz by rememberSaveable { mutableStateOf(vm.user.tzId.ifEmpty { deviceTz }) }
    var tzOpen by remember { mutableStateOf(false) }
    var warn by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    Text("Bem-vindo!", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text("Três perguntas rápidas para começar.", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = name,
        onValueChange = {
            name = it
            warn = false
        },
        label = { Text("Seu nome") },
        singleLine = true,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.moveFocus(FocusDirection.Down) }),
    )
    Text(
        "O nome é enviado a quem desenvolve o app, só para saber quem usa.",
        color = Muted,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
    Spacer(Modifier.height(14.dp))

    Text("Idioma", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((tag, label) in APP_LANGS) ChipItem(text = label, selected = lang == tag) { lang = tag }
    }
    Text(
        "Os menus por enquanto estão só em português; sua escolha fica guardada para as traduções.",
        color = Muted,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
    Spacer(Modifier.height(14.dp))

    Text("Fuso horário", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(tz.replace('_', ' ') + if (tz == deviceTz) " (da TV)" else "", color = Accent, fontSize = 14.sp)
        ChipItem(text = if (tzOpen) "Fechar" else "Alterar", selected = false) { tzOpen = !tzOpen }
    }
    if (tzOpen) {
        Spacer(Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (z in (listOf(deviceTz) + COMMON_ZONES).distinct()) {
                ChipItem(text = z.substringAfter('/').replace('_', ' '), selected = tz == z) { tz = z }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    if (warn) {
        Text("Digite seu nome para continuar.", color = ErrorColor, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
    }
    TvButton(text = "Continuar", primary = true) {
        if (name.isBlank()) warn = true else vm.saveProfile(name, lang, tz)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepTwo(vm: MainViewModel) {
    Text("Personalize o seu início", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text("Opcional: dá para pular e escolher depois em Ajustes.", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(18.dp))

    Text("Times favoritos (esportes)", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        TvButton(text = "Escolher times") { vm.openPicker() }
        val n = vm.favTeams.size
        if (n > 0) Text("$n escolhido" + (if (n > 1) "s" else ""), color = Accent, fontSize = 13.sp)
    }
    Spacer(Modifier.height(18.dp))

    Text("3 gêneros de filmes e séries", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    GenreChips(vm)
    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvButton(text = "Concluir", primary = true) { vm.finishWelcome() }
        TvButton(text = "Pular") { vm.finishWelcome() }
    }
}

/** Até 3 gêneros preferidos; quem escolhe um quarto troca o mais antigo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreChips(vm: MainViewModel) {
    val chosen = vm.user.favGenres.toList()
    // lê o contador de dados para recompor quando a escolha muda
    @Suppress("UNUSED_VARIABLE") val rev = vm.dataRev
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (g in PICK_GENRES) {
            ChipItem(text = g, selected = g in chosen) {
                val next = if (g in chosen) chosen - g else (chosen + g).takeLast(3)
                vm.setFavGenres(next)
            }
        }
    }
    Text(
        if (chosen.isEmpty()) "Nenhum escolhido." else chosen.size.toString() + " de 3: " + chosen.joinToString(", "),
        color = Muted,
        fontSize = 11.sp,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}
