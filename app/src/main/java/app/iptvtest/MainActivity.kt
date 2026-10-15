package app.iptvtest

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
    val account = vm.account
    val playing = vm.playing
    val series = vm.seriesDetail
    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
        when {
            account == null -> LoginScreen(vm)
            playing != null -> PlayerScreen(playing, onExit = { vm.stopPlaying() })
            series != null -> SeriesScreen(vm)
            else -> HomeScreen(vm)
        }
    }
}
