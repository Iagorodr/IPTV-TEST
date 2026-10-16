package app.iptvtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.items as gridItems

private const val ALL = "__all"
private const val FAVS = "__favs"
private const val K4 = "__4k"
private const val YT = "__yt"

/**
 * Canais ao vivo: um cartão por canal (as cópias do mesmo canal ficam dentro dele, como "fontes"), as marcas de TV
 * (Globo, Record, SBT, Band, RedeTV) num cartão só com as praças dentro, e poucas pastas.
 */
@Composable
fun ChannelsTab(vm: MainViewModel) {
    val cat = vm.catalog
    val groups = vm.channelGroups
    var selected by rememberSaveable { mutableStateOf(ALL) }
    val favs = vm.favoriteChannels()

    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        if (cat == null || groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum canal encontrado nesta lista.", color = Muted)
            }
            return@Column
        }

        val all = remember(groups) { groups.flatMap { it.second } }
        val sel = if (selected == FAVS && favs.isEmpty()) ALL else selected
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = ALL) {
                ChipItem(text = "Todos (${all.size})", selected = sel == ALL) { selected = ALL }
            }
            if (favs.isNotEmpty()) {
                item(key = FAVS) {
                    ChipItem(text = "★ Favoritos (${favs.size})", selected = sel == FAVS) { selected = FAVS }
                }
            }
            items(groups, key = { it.first }) { g ->
                ChipItem(text = g.first + " (${g.second.size})", selected = sel == g.first) { selected = g.first }
            }
            item(key = K4) {
                ChipItem(text = "4K", selected = sel == K4) { selected = K4 }
            }
            item(key = YT) {
                ChipItem(text = "YouTube", selected = sel == YT) { selected = YT }
            }
        }

        val list = remember(sel, groups, favs) {
            when (sel) {
                ALL -> all
                FAVS -> favs
                K4 -> all.filter { ChannelGroups.has4k(it) }
                YT -> all.filter { ChannelGroups.isYoutube(it) }
                else -> groups.firstOrNull { it.first == sel }?.second ?: emptyList()
            }
        }

        Row(modifier = Modifier.padding(horizontal = 44.dp, vertical = 4.dp)) {
            Text(
                text = "${list.size} canais · a bolinha mostra a estabilidade medida (cinza = ainda não testado). " +
                    "Nos cartões de TV (Globo, Record...) você escolhe a praça dentro do player.",
                color = Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
            )
        }

        LazyVerticalGrid(
            state = rememberLazyGridState(),
            columns = GridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            gridItems(list, key = { it.key }) { ch ->
                ChannelTile(
                    ch = ch,
                    stability = vm.channelStability(ch),
                    focusId = "ch|" + sel + "|" + ch.key,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.playChannel(ch) },
                )
            }
        }
    }
}
