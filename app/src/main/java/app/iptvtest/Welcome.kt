package app.iptvtest

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
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

/** Primeira abertura: uma pergunta por tela (nome, idioma, fuso são obrigatórias; times e gêneros podem ser pulados). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeScreen(vm: MainViewModel) {
    // 0 nome, 1 idioma, 2 fuso | 3 times, 4 gêneros
    var step by remember { mutableIntStateOf(if (vm.welcomeStage == 2) 3 else 0) }
    var name by rememberSaveable { mutableStateOf(vm.user.profileName) }
    var lang by rememberSaveable { mutableStateOf(vm.user.profileLang.ifEmpty { detectLang(vm.deviceLang()) }) }
    val deviceTz = remember { java.util.TimeZone.getDefault().id }
    var tz by rememberSaveable { mutableStateOf(vm.user.tzId.ifEmpty { deviceTz }) }
    var warn by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val primary = remember { FocusRequester() }
    val nameField = remember { FocusRequester() }

    BackHandler(enabled = step in 1..2 || step == 4) { step -= 1 }

    LaunchedEffect(step) {
        delay(160)
        try {
            if (step == 0) nameField.requestFocus() else primary.requestFocus()
        } catch (e: Exception) {
            // a tela ainda não estava pronta
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SunBackdrop(modifier = Modifier.fillMaxSize(), sunX = 0.5f, sunY = 0.18f, sunR = 0.2f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_name), color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Light, letterSpacing = 8.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0..4) {
                    val w by animateDpAsState(if (i == step) 26.dp else 8.dp, label = "dot")
                    Box(
                        Modifier
                            .width(w)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i <= step) Accent else Muted.copy(alpha = 0.3f)),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 14 }) togetherWith fadeOut(tween(110))
                },
                label = "welcome-step",
            ) { st ->
                Column(modifier = Modifier.width(700.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (st) {
                        0 -> {
                            StepHeader("Como podemos te chamar?", "Só para quem desenvolve o app saber quem usa. Nada além disso é enviado.")
                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    name = it
                                    warn = false
                                },
                                placeholder = { Text("Seu nome", color = Muted) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
                                colors = fieldColors(),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.width(420.dp).focusRequester(nameField),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focus.moveFocus(FocusDirection.Down) }),
                            )
                            if (warn) {
                                Spacer(Modifier.height(8.dp))
                                Text("Digite seu nome para continuar.", color = ErrorColor, fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(20.dp))
                            TvButton(text = "Continuar", primary = true, minWidth = 160.dp, focusRequester = primary) {
                                if (name.isBlank()) warn = true else step = 1
                            }
                        }
                        1 -> {
                            StepHeader("Qual idioma você prefere?", "Os menus ainda estão em português. Sua escolha fica guardada para as traduções.")
                            CardGrid {
                                for ((tag, label) in APP_LANGS) ChoiceCard(label, lang == tag) { lang = tag }
                            }
                            Spacer(Modifier.height(20.dp))
                            NavButtons(primary, onBack = { step = 0 }) { step = 2 }
                        }
                        2 -> {
                            val now = remember { System.currentTimeMillis() }
                            val clock = remember(tz) { SportsTime.clock(now, java.util.TimeZone.getTimeZone(tz)) }
                            StepHeader("Qual é o seu fuso horário?", "Os jogos aparecem na sua hora. Agora, nesse fuso, são $clock.")
                            CardGrid {
                                for (z in (listOf(deviceTz) + COMMON_ZONES).distinct()) {
                                    ChoiceCard(
                                        text = z.substringAfter('/').replace('_', ' '),
                                        selected = tz == z,
                                        tag = if (z == deviceTz) "da TV" else null,
                                    ) { tz = z }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            NavButtons(primary, label = "Concluir", onBack = { step = 1 }) {
                                vm.saveProfile(name, lang, tz)
                                step = 3
                            }
                        }
                        3 -> {
                            StepHeader("Seus times do coração", "Opcional. Os jogos deles aparecem no topo do início.")
                            val n = vm.favTeams.size + vm.favLeagues.size
                            TvButton(text = if (n == 0) "Escolher times e ligas" else "Trocar times e ligas", minWidth = 220.dp, focusRequester = primary) { vm.openPicker() }
                            if (n > 0) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    vm.favTeams.joinToString("   ") { "★ " + it.short },
                                    color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                                )
                            }
                            Spacer(Modifier.height(22.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TvButton(text = if (n == 0) "Pular" else "Continuar", primary = n > 0, minWidth = 140.dp) { step = 4 }
                            }
                        }
                        else -> {
                            StepHeader("O que você mais curte?", "Opcional. Escolha até 3 gêneros. Eles aparecem primeiro nos destaques.")
                            GenreChips(vm, centered = true)
                            Spacer(Modifier.height(22.dp))
                            val any = vm.user.favGenres.isNotEmpty()
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TvButton(text = "Voltar", minWidth = 120.dp) { step = 3 }
                                TvButton(text = if (any) "Começar" else "Pular", primary = true, minWidth = 140.dp, focusRequester = primary) { vm.finishWelcome() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, sub: String) {
    Text(title, color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(sub, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.width(560.dp))
    Spacer(Modifier.height(20.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardGrid(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 3,
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
private fun NavButtons(primary: FocusRequester, label: String = "Continuar", onBack: () -> Unit, onNext: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvButton(text = "Voltar", minWidth = 120.dp, onClick = onBack)
        TvButton(text = label, primary = true, minWidth = 160.dp, focusRequester = primary, onClick = onNext)
    }
}

/** Cartão grande de escolha: o escolhido ganha contorno e marca; fica fixo no tamanho, sem pular de lugar. */
@Composable
private fun ChoiceCard(text: String, selected: Boolean, tag: String? = null, onClick: () -> Unit) {
    FocusBox(
        modifier = Modifier.width(212.dp),
        shape = RoundedCornerShape(14.dp),
        focusScale = 1f,
        onClick = onClick,
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when {
                        selected -> Accent.copy(alpha = 0.20f)
                        focused -> PanelAlt
                        else -> Panel.copy(alpha = 0.55f)
                    },
                )
                .border(1.5.dp, if (selected) Accent.copy(alpha = 0.85f) else Color(0x1FFFF3EC), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text, color = if (selected) TextMain else Muted.copy(alpha = 0.95f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (tag != null) Text(tag, color = Accent, fontSize = 11.sp, maxLines = 1)
            }
            if (selected) Text("✓", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Até 3 gêneros preferidos; quem escolhe um quarto troca o mais antigo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreChips(vm: MainViewModel, centered: Boolean = false) {
    val chosen = vm.user.favGenres.toList()
    // lê o contador de dados para recompor quando a escolha muda
    @Suppress("UNUSED_VARIABLE") val rev = vm.dataRev
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, if (centered) Alignment.CenterHorizontally else Alignment.Start),
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
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}
