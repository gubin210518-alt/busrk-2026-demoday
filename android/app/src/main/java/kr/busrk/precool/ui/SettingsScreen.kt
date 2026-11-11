package kr.busrk.precool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.busrk.precool.ble.BoxProtocol
import kr.busrk.precool.data.BoxRepository
import kr.busrk.precool.data.Prefs
import java.util.Locale

@Composable
fun SettingsScreen() {
    val cfg by BoxRepository.config.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("설정", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Section("알림") {
                AlertSwitch(Prefs.ALERT_RISK, "늦을 위험", "제한 시간 안에 못 식을 것 같을 때")
                AlertSwitch(Prefs.ALERT_LATE, "제한 시간 지남", "제한 시간이 지났는데 아직 못 식었을 때")
                AlertSwitch(Prefs.ALERT_DONE, "예냉 완료", "식품칸이 목표 온도에 닿았을 때")
                AlertSwitch(Prefs.ALERT_LID, "뚜껑 열림", "뚜껑이 30초 넘게 열려 있을 때")
                AlertSwitch(Prefs.ALERT_SENSOR, "센서 오류", "온도를 못 읽을 때")
                AlertSwitch(Prefs.ALERT_DISCONNECT, "연결 끊김", "블루투스 연결이 끊겼을 때")
                Text("휴대폰 설정에서 이 앱의 알림 권한이 꺼져 있으면 알림이 오지 않아요.", color = BoxColors.Muted, fontSize = 12.sp)
            }
        }
        item {
            Section("연결된 박스 정보") {
                val c = cfg
                if (c == null) Text("박스를 연결하면 보여요.", color = BoxColors.Muted)
                else {
                    InfoLine("목표 온도", String.format(Locale.US, "%.1f℃", c.targetC))
                    InfoLine("제한 시간", "${Math.round(c.deadlineMin)}분")
                    InfoLine("동작 모드", if (c.demo) "시연 모드" else "실사용")
                    InfoLine("AI 모델", if (c.modelFromSimulation) "시뮬레이션 학습 (실측 아님)" else "실측 기록으로 학습")
                    InfoLine("원격 명령", if (c.remoteCommandOn) "켜짐 (PIN 필요)" else "꺼짐 (보기 전용)")
                    InfoLine("기록 간격", "${c.historySec}초")
                    InfoLine("통신 형식", "v${c.protoVersion}")
                    if (c.protoVersion != BoxProtocol.PROTO_VERSION) {
                        Text("앱과 펌웨어의 통신 형식 버전이 달라요. 둘 다 최신으로 맞춰 주세요.", color = BoxColors.Red, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Section("보안과 개인정보") {
                Text(
                    "• 이 앱은 인터넷을 쓰지 않아요. 온도 기록은 휴대폰 안에만 저장되고, \"보내기\"를 누를 때만 밖으로 나가요.\n" +
                        "• 개인정보와 위치는 모으지 않아요. (안드로이드 11 이하에서 위치 권한은 블루투스 검색 때문에만 필요해요)\n" +
                        "• 블루투스 내용은 암호화하지 않아서, 약 10m 안에 있는 사람은 온도 값을 볼 수 있어요.\n" +
                        "• 그래서 박스를 바꾸는 명령(새 딸기 시작)은 6자리 PIN이 있어야 하고, 5번 틀리면 1분 동안 잠겨요. " +
                        "기록 삭제 같은 명령은 블루투스로 받지 않아요.",
                    fontSize = 13.sp,
                )
            }
        }
        item {
            Section("이 앱") {
                InfoLine("이름", "부스럭 예냉박스 1.0")
                InfoLine("만든 팀", "TEAM 부스럭 (신정고 매니악코더)")
                Text("예측은 참고 표시예요. 품질·출하 판단은 사람이 해요.", color = BoxColors.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun AlertSwitch(key: String, title: String, sub: String) {
    val ctx = LocalContext.current
    val sp = remember { Prefs.of(ctx) }
    var on by remember { mutableStateOf(sp.getBoolean(key, true)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(sub, color = BoxColors.Muted, fontSize = 12.sp)
        }
        Switch(checked = on, onCheckedChange = { on = it; sp.edit().putBoolean(key, it).apply() })
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, color = BoxColors.Muted, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}
