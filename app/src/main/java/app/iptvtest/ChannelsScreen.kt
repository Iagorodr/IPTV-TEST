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

/**
 * Canais ao vivo: um cartão por canal (as dezenas de cópias do mesmo canal ficam dentro dele,
 * como "fontes" que a pessoa pode trocar dentro do player).
 */
@Composable
fun ChannelsTab(vm: MainViewModel) {
    val cat = vm.catalog
    var selected by rememberSaveable { mutableStateOf(ALL) }
    val favs = vm.favoriteChannels()

    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        if (cat == null || cat.channels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum canal encontrado nesta lista.", color = Muted)
            }
            return@Column
        }

        val sel = if (selected == FAVS && favs.isEmpty()) ALL else selected
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = ALL) {
                ChipItem(text = "Todos (${cat.channels.size})", selected = sel == ALL) { selected = ALL }
            }
            if (favs.isNotEmpty()) {
                item(key = FAVS) {
                    ChipItem(text = "★ Favoritos (${favs.size})", selected = sel == FAVS) { selected = FAVS }
                }
            }
            items(cat.channelCategories, key = { it }) { name ->
                ChipItem(text = name, selected = sel == name) { selected = name }
            }
        }

        val list = remember(sel, cat, favs) {
            when (sel) {
                ALL -> cat.channels
                FAVS -> favs
                else -> cat.channels.filter { it.category == sel }
            }
        }

        Row(modifier = Modifier.padding(horizontal = 44.dp, vertical = 4.dp)) {
            Text(
                text = "${list.size} canais · a bolinha mostra a estabilidade medida (cinza = ainda não testado). " +
                    "Dentro do canal, o botão Fontes deixa você escolher e testar.",
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
