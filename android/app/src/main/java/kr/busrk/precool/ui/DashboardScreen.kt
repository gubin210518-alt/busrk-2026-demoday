package kr.busrk.precool.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kr.busrk.precool.BoxService
import kr.busrk.precool.ble.BoxConfig
import kr.busrk.precool.ble.BoxScanner
import kr.busrk.precool.ble.BoxStatus
import kr.busrk.precool.ble.Level
import kr.busrk.precool.ble.etaText
import kr.busrk.precool.data.BoxRepository
import kr.busrk.precool.data.Link
import kr.busrk.precool.data.Point
import kr.busrk.precool.data.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val clockFmt = SimpleDateFormat("a h:mm", Locale.KOREA)
private fun clock(ms: Long): String = clockFmt.format(Date(ms))
private fun c1(v: Double?): String = v?.let { String.format(Locale.US, "%.1f", it) } ?: "--.-"

@Composable
fun DashboardScreen() {
    val link by BoxRepository.link.collectAsStateWithLifecycle()
    val st by BoxRepository.status.collectAsStateWithLifecycle()
    val cfg by BoxRepository.config.collectAsStateWithLifecycle()
    val pts by BoxRepository.points.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(1000); value = System.currentTimeMillis() }
    }
    var showPin by remember { mutableStateOf(false) }
    val live = link is Link.Connected || link is Link.Reconnecting

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("부스럭 예냉박스", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("식품의 골든타임을 지키는 예냉박스 · 원격 확인", color = BoxColors.Muted, fontSize = 13.sp)
            }
        }
        item { LinkCard(link) }
        val s = st
        if (s != null && live) {
            item { StatusCard(s, now) }
            item { TempRow(s, cfg) }
            item { EtaCard(s, cfg) }
            item { ChartCard(pts, cfg) }
            item { ActionCard(cfg, link is Link.Connected, onReset = { showPin = true }) }
        }
        item {
            Text(
                "예측은 참고 표시예요. 딸기 품질과 출하 판단은 사람이 해요.",
                color = BoxColors.Muted, fontSize = 12.sp,
            )
        }
    }

    if (showPin) PinDialog(onDismiss = { showPin = false }, onOk = { pin ->
        showPin = false
        if (!BoxRepository.send("r$pin")) BoxRepository.postReply("SEND_FAIL")
    })
}

// ---------------- 연결 ----------------
@Composable
private fun LinkCard(link: Link) {
    val ctx = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (link) {
                is Link.Idle -> ConnectPanel(null)
                is Link.Failed -> ConnectPanel(link.message)
                is Link.Connecting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp)); Text("${link.name} 연결 중…")
                    }
                    OutlinedButton(onClick = { BoxService.stop(ctx) }) { Text("취소") }
                }
                is Link.Connected -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(BoxColors.Green); Spacer(Modifier.width(8.dp))
                        Text("${link.name} 연결됨", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { BoxService.stop(ctx) }) { Text("연결 끊기") }
                    }
                    Text("화면을 꺼도 상단 알림이 떠 있는 동안 계속 확인해요.", color = BoxColors.Muted, fontSize = 12.sp)
                }
                is Link.Reconnecting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(BoxColors.Yellow); Spacer(Modifier.width(8.dp))
                        Text("${link.name} 다시 찾는 중", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { BoxService.stop(ctx) }) { Text("그만") }
                    }
                    Text("블루투스는 약 10m 안에서 연결돼요. 가까이 가면 자동으로 다시 연결해요. 아래는 마지막으로 받은 값이에요.",
                        color = BoxColors.Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ConnectPanel(errorText: String?) {
    val ctx = LocalContext.current
    val found by BoxScanner.found.collectAsStateWithLifecycle()
    val scanning by BoxScanner.scanning.collectAsStateWithLifecycle()
    val scanErr by BoxScanner.error.collectAsStateWithLifecycle()
    var hint by remember { mutableStateOf<String?>(null) }
    var scannedOnce by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sp = remember { Prefs.of(ctx) }
    val lastAddr = sp.getString(Prefs.LAST_ADDRESS, null)
    val lastName = sp.getString(Prefs.LAST_NAME, null) ?: "예냉박스"

    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val p = pending; pending = null
        if (BlePermissions.adapterOn(ctx)) p?.invoke() else hint = "블루투스를 켜야 박스를 찾을 수 있어요"
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val p = pending
        if (!BlePermissions.granted(ctx)) {
            pending = null
            hint = "주변 기기(블루투스) 권한이 있어야 박스를 찾을 수 있어요. 설정 → 앱 → 부스럭 예냉박스 → 권한에서 켤 수 있어요"
        } else if (!BlePermissions.adapterOn(ctx)) {
            btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else { pending = null; p?.invoke() }
    }
    fun withBle(action: () -> Unit) {
        hint = null
        when {
            !BlePermissions.granted(ctx) -> { pending = action; permLauncher.launch(BlePermissions.request()) }
            !BlePermissions.adapterOn(ctx) -> { pending = action; btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            else -> action()
        }
    }
    fun connect(address: String, name: String) {
        BoxScanner.stop()
        BoxService.connect(ctx, address, name)
    }

    Text("박스 연결", fontWeight = FontWeight.SemiBold)
    if (errorText != null) Text(errorText, color = BoxColors.Red, fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { withBle { scannedOnce = true; BoxScanner.start(ctx) } }, enabled = !scanning) {
            Text(if (scanning) "찾는 중…" else "예냉박스 찾기")
        }
        if (lastAddr != null) {
            OutlinedButton(onClick = { withBle { connect(lastAddr, lastName) } }) { Text("$lastName 다시 연결") }
        }
    }
    found.forEach { f ->
        HorizontalDivider(color = BoxColors.Line)
        Row(
            Modifier.fillMaxWidth().clickable { withBle { connect(f.address, f.name) } }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(f.name, fontWeight = FontWeight.SemiBold)
                Text("신호 ${signalText(f.rssi)} · ${f.address}", color = BoxColors.Muted, fontSize = 12.sp)
            }
            Text("연결", color = BoxColors.Purple, fontWeight = FontWeight.SemiBold)
        }
    }
    val msg = hint ?: scanErr
    if (msg != null) Text(msg, color = BoxColors.Red, fontSize = 13.sp)
    if (scannedOnce && !scanning && found.isEmpty() && msg == null) {
        Text(
            "박스를 못 찾았어요. 박스 전원이 켜져 있는지, 블루투스판 펌웨어(precool_ai_ble)를 올렸는지 확인해 주세요. " +
                "안드로이드 11 이하 휴대폰은 위치(GPS)도 켜야 찾을 수 있어요.",
            color = BoxColors.Muted, fontSize = 12.sp,
        )
    }
}

private fun signalText(rssi: Int) = when {
    rssi >= -60 -> "강함"
    rssi >= -80 -> "보통"
    else -> "약함"
}

@Composable
private fun Dot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

// ---------------- 상태 ----------------
private fun levelColor(l: Level) = when (l) {
    Level.COOLING -> BoxColors.Yellow
    Level.DONE -> BoxColors.Green
    else -> BoxColors.Red
}

@Composable
private fun StatusCard(s: BoxStatus, now: Long) {
    val col = levelColor(s.level)
    Card(colors = CardDefaults.cardColors(containerColor = col.copy(alpha = 0.10f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // 신호등
            Column(
                Modifier.background(Color(0xFF2B2F3A), RoundedCornerShape(10.dp)).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val red = s.level == Level.LATE_RISK || s.level == Level.LATE || s.level == Level.SENSOR_ERR
                Dot(if (red) BoxColors.Red else Color(0xFF4A4F5C), 16)
                Dot(if (s.level == Level.COOLING) BoxColors.Yellow else Color(0xFF4A4F5C), 16)
                Dot(if (s.level == Level.DONE) BoxColors.Green else Color(0xFF4A4F5C), 16)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.level.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = col)
                Text(s.level.sub, fontSize = 14.sp)
                if (s.level == Level.DONE && s.doneAtMin != null) Text("${s.doneAtMin}분째에 도달했어요", fontSize = 13.sp, color = BoxColors.Muted)
                if (s.lidAlarm) Text("뚜껑이 30초 넘게 열려 있어요!", color = BoxColors.Red, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                else if (s.lidOpen) Text("뚜껑 열림 · 팬 멈춤", color = BoxColors.Red, fontSize = 13.sp)
                if (s.demo) Text("시연 모드 (목표·제한 시간이 실제와 달라요)", color = BoxColors.Purple, fontSize = 12.sp)
                val ago = (now - s.receivedAt) / 1000
                if (ago > 10) Text("마지막으로 받은 지 ${ago}초 지났어요", color = BoxColors.Red, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TempRow(s: BoxStatus, cfg: BoxConfig?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(Modifier.weight(1f), "식품칸", "${c1(s.berryC)}℃", BoxColors.Red,
            cfg?.let { "목표 ${c1(it.targetC)}℃" } ?: "")
        InfoCard(Modifier.weight(1f), "냉기실", "${c1(s.coldC)}℃", BoxColors.Blue,
            "팬 ${if (s.fanOn) "켜짐" else "꺼짐"} · 뚜껑 ${if (s.lidOpen) "열림" else "닫힘"}")
    }
}

@Composable
private fun InfoCard(modifier: Modifier, title: String, value: String, color: Color, sub: String) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = BoxColors.Muted, fontSize = 13.sp)
            Text(value, color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = BoxColors.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EtaCard(s: BoxStatus, cfg: BoxConfig?) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("목표 온도까지 남은 시간", color = BoxColors.Muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(etaText(s.etaUsed), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = BoxColors.Purple)
                Spacer(Modifier.width(10.dp))
                Text(s.methodText, fontSize = 13.sp, color = BoxColors.Muted, modifier = Modifier.padding(bottom = 6.dp))
            }
            if (s.etaUsed >= 0 && s.level != Level.DONE) {
                val arrive = s.receivedAt + (s.etaUsed * 60000).toLong()
                Text("도착 예상 ${clock(arrive)} (수확 후 ${Math.round(s.elapsedMin + s.etaUsed)}분째)", fontSize = 14.sp)
            } else if (s.etaUsed == -2.0) {
                Text("이대로는 목표 온도에 닿지 않을 것 같아요. 아이스팩 상태를 확인해 주세요.", fontSize = 14.sp, color = BoxColors.Red)
            }
            if (cfg != null) {
                val frac = (s.elapsedMin / cfg.deadlineMin).coerceIn(0.0, 1.0).toFloat()
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { frac }, modifier = Modifier.fillMaxWidth(),
                    color = if (s.elapsedMin > cfg.deadlineMin) BoxColors.Red else BoxColors.Purple,
                    trackColor = BoxColors.Line,
                )
                val deadlineAt = s.receivedAt + ((cfg.deadlineMin - s.elapsedMin) * 60000).toLong()
                Text(
                    "제한 ${Math.round(cfg.deadlineMin)}분 중 ${elapsedText(s.elapsedSec)} 지남 · 마감 ${clock(deadlineAt)}",
                    fontSize = 13.sp, color = BoxColors.Muted,
                )
            }
            HorizontalDivider(color = BoxColors.Line)
            Text("AI ${etaText(s.etaAi)} · 뉴턴 계산식 ${etaText(s.etaNewton)}", fontSize = 13.sp, color = BoxColors.Muted)
            if (cfg?.modelFromSimulation == true) {
                Text("지금 박스의 AI 모델은 시뮬레이션으로 미리 학습한 거예요 (실측 아님).", fontSize = 12.sp, color = BoxColors.Purple)
            }
        }
    }
}

private fun elapsedText(sec: Long) = "${sec / 60}분 ${sec % 60}초"

@Composable
private fun ChartCard(pts: List<Point>, cfg: BoxConfig?) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(BoxColors.Red, "식품칸"); Legend(BoxColors.Blue, "냉기실")
                Legend(BoxColors.Green, "목표"); Legend(BoxColors.Gray, "제한 시간")
            }
            Spacer(Modifier.height(6.dp))
            TempChart(
                points = pts,
                targetC = cfg?.targetC ?: 1.0,
                deadlineMin = cfg?.deadlineMin ?: 60.0,
                modifier = Modifier.fillMaxWidth().height(240.dp),
            )
            Text("빨간 띠 = 뚜껑이 열린 구간", fontSize = 11.sp, color = BoxColors.Muted)
        }
    }
}

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 12.dp, height = 3.dp).background(c))
        Spacer(Modifier.width(4.dp)); Text(label, fontSize = 11.sp, color = BoxColors.Muted)
    }
}

@Composable
private fun ActionCard(cfg: BoxConfig?, connected: Boolean, onReset: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("박스 조작", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReset, enabled = connected && cfg?.remoteCommandOn == true) { Text("새 딸기 시작") }
                OutlinedButton(onClick = { BoxRepository.send("h") }, enabled = connected) { Text("최근 10분 다시 받기") }
            }
            if (cfg != null && !cfg.remoteCommandOn) {
                Text("박스 펌웨어의 BLE_PIN이 000000이라 원격 명령이 꺼져 있어요. 보기만 할 수 있어요.", fontSize = 12.sp, color = BoxColors.Muted)
            } else {
                Text("새 딸기를 넣었을 때만 눌러요. 박스 시간이 0분으로 돌아가고 새 기록이 시작돼요.", fontSize = 12.sp, color = BoxColors.Muted)
            }
        }
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 딸기 시작") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("지금까지의 기록은 저장되고, 박스 시간이 0분부터 다시 시작돼요. 박스에 정한 6자리 PIN을 넣어 주세요.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter { it.isDigit() }.take(6) },
                    label = { Text("PIN 6자리") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onOk(pin) }, enabled = pin.length == 6) { Text("시작") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
