package kr.busrk.precool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kr.busrk.precool.ui.BusrkApp
import kr.busrk.precool.ui.BusrkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BusrkTheme {
                BusrkApp()
            }
        }
    }
}
