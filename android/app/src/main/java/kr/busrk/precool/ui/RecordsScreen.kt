package kr.busrk.precool.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kr.busrk.precool.data.BoxRepository
import kr.busrk.precool.data.Prefs
import kr.busrk.precool.data.RunInfo
import kr.busrk.precool.data.RunStore
import java.util.Locale

@Composable
fun RecordsScreen() {
    val ctx = LocalContext.current
    val store = remember { RunStore(ctx) }
    val sp = remember { Prefs.of(ctx) }
    val active by BoxRepository.activeRunFile.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var runs by remember { mutableStateOf<List<RunInfo>>(emptyList()) }
    var memoFor by remember { mutableStateOf<RunInfo?>(null) }
    var deleteFor by remember { mutableStateOf<RunInfo?>(null) }

    // 기록 중인 회차가 늘어나는 것을 보여주려고 10초마다 새로 읽어요
    LaunchedEffect(refresh) {
        while (true) { runs = store.list(); delay(10_000) }
    }

    fun share(r: RunInfo) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", r.file)
        val memo = sp.getString(Prefs.memoKey(r.file.name), "") ?: ""
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "예냉박스 실험 기록 ${r.title}")
            .putExtra(Intent.EXTRA_TEXT, "예냉박스 실험 기록 ${r.title}\n실험 조건: ${memo.ifBlank { "(메모 없음)" }}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "기록 보내기"))
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Text("실험 기록", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "박스와 연결된 동안 2초마다 저장돼요. CSV를 PC로 보내서 AI 시뮬레이터 \"실험 기록 → CSV 불러오기\"로 " +
                        "AI를 다시 학습할 수 있어요. 회차마다 실험 조건(바깥 온도, 딸기 양, 아이스팩 개수)을 메모해 두세요.",
                    color = BoxColors.Muted, fontSize = 13.sp,
                )
            }
        }
        if (runs.isEmpty()) item { Text("아직 기록이 없어요. 현황 탭에서 박스를 연결해 보세요.", color = BoxColors.Muted) }
        items(runs, key = { it.file.name }) { r ->
            val isActive = r.file.name == active
            val memo = sp.getString(Prefs.memoKey(r.file.name), "") ?: ""
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(r.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (isActive) Text("기록 중", color = BoxColors.Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "길이 ${Math.round(r.minutes)}분 · 시작 ${fmt(r.startC)}℃ · 최저 ${fmt(r.minC)}℃ · ${r.samples}칸",
                        color = BoxColors.Muted, fontSize = 13.sp,
                    )
                    if (memo.isNotBlank()) Text("조건: $memo", fontSize = 13.sp)
                    Row {
                        TextButton(onClick = { share(r) }) { Text("보내기") }
                        TextButton(onClick = { memoFor = r }) { Text("조건 메모") }
                        TextButton(onClick = { deleteFor = r }, enabled = !isActive) { Text("삭제", color = if (isActive) BoxColors.Gray else BoxColors.Red) }
                    }
                }
            }
        }
    }

    memoFor?.let { r ->
        var text by remember(r) { mutableStateOf(sp.getString(Prefs.memoKey(r.file.name), "") ?: "") }
        AlertDialog(
            onDismissRequest = { memoFor = null },
            title = { Text("실험 조건 메모") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(200) },
                    label = { Text("예: 바깥 25℃, 딸기 500g, 아이스팩 2개") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    sp.edit().putString(Prefs.memoKey(r.file.name), text.trim()).apply()
                    memoFor = null; refresh++
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { memoFor = null }) { Text("취소") } },
        )
    }

    deleteFor?.let { r ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("기록 삭제") },
            text = { Text("${r.title} 기록을 지울까요? 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    r.file.delete(); sp.edit().remove(Prefs.memoKey(r.file.name)).apply()
                    deleteFor = null; refresh++
                }) { Text("삭제", color = BoxColors.Red) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("취소") } },
        )
    }
}

private fun fmt(v: Double?) = v?.let { String.format(Locale.US, "%.1f", it) } ?: "-"
