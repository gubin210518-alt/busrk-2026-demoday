package kr.busrk.precool.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import kr.busrk.precool.data.BoxRepository

@Suppress("DEPRECATION")  // Icons.Filled.List (새 이름은 AutoMirrored)
@Composable
fun BusrkApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }

    // 박스가 명령에 답한 글자를 알기 쉬운 말로 보여줘요
    LaunchedEffect(Unit) {
        BoxRepository.replies.collect { r ->
            val msg = when (r) {
                "OK r" -> "새 딸기 시작! 박스 시간이 0분부터 다시 시작돼요"
                "PIN" -> "PIN이 틀렸어요"
                "LOCK" -> "PIN을 5번 틀려서 1분 동안 잠겼어요"
                "NOPIN" -> "박스에 PIN이 설정되지 않아 원격 명령이 꺼져 있어요 (펌웨어 BLE_PIN)"
                "?" -> "박스가 모르는 명령이에요"
                "SEND_FAIL" -> "박스와 연결되어 있지 않아 보내지 못했어요"
                else -> null   // "OK h", "EMPTY" 는 조용히 처리
            }
            if (msg != null) snack.showSnackbar(msg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) }, label = { Text("현황") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) }, label = { Text("기록") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) }, label = { Text("설정") })
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> DashboardScreen()
                1 -> RecordsScreen()
                else -> SettingsScreen()
            }
        }
    }
}

/** 블루투스 권한 모음 */
object BlePermissions {
    /** 박스를 찾고 연결하는 데 꼭 필요한 권한 */
    fun needed(): List<String> =
        if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    /** 요청할 때는 알림 권한도 같이 물어봐요 (거절해도 연결은 돼요) */
    fun request(): Array<String> =
        (needed() + if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList())
            .toTypedArray()

    fun granted(ctx: Context): Boolean = needed().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    fun adapterOn(ctx: Context): Boolean =
        ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
}
