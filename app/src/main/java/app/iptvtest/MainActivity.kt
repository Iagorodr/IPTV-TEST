package app.iptvtest

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AppTheme {
                App()
            }
        }
    }
}

@Composable
fun App(vm: MainViewModel = viewModel()) {
    // guarda a posição das listas de cada tela, para voltar exatamente onde estava depois de abrir um título
    val holder = rememberSaveableStateHolder()
    val account = vm.account
    val playing = vm.playing
    val catalog = vm.catalog
    val title = vm.openTitle
    val grid = vm.grid
    val chList = vm.channelList

    BackHandler(enabled = account != null && playing == null && vm.canGoBack) {
        vm.back()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
        when {
            account == null -> LoginScreen(vm)
            playing != null -> PlayerScreen(playing, vm.user, onExit = { vm.stopPlaying() })
            catalog == null -> LoadingScreen(vm)
            vm.pickerOpen -> holder.SaveableStateProvider("picker") {
                TeamPickerScreen(vm)
            }
            chList != null -> holder.SaveableStateProvider("chlist|" + chList.title + "|" + chList.subtitle) {
                ChannelListScreen(vm, chList)
            }
            title != null -> holder.SaveableStateProvider("title|" + title.key) {
                TitleScreen(vm, title)
            }
            grid != null -> holder.SaveableStateProvider("grid|" + grid.name) {
                GridScreen(vm, grid)
            }
            else -> holder.SaveableStateProvider("tab|" + vm.tab.name) {
                TabScreen(vm)
            }
        }
    }
}
