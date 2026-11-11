package kr.busrk.precool

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kr.busrk.precool.ble.BoxBle
import kr.busrk.precool.ble.BoxProtocol
import kr.busrk.precool.ble.BoxStatus
import kr.busrk.precool.ble.HistPoint
import kr.busrk.precool.ble.Level
import kr.busrk.precool.ble.etaText
import kr.busrk.precool.data.BoxRepository
import kr.busrk.precool.data.Link
import kr.busrk.precool.data.Point
import kr.busrk.precool.data.Prefs
import kr.busrk.precool.data.RunStore
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * 화면을 꺼도 박스와 연결을 유지하는 서비스.
 * 상단에 "연결 중" 알림이 떠 있는 동안 상태를 받고, 기록을 저장하고, 위험하면 알림을 보내요.
 */
class BoxService : Service(), BoxBle.Listener {

    companion object {
        private const val ACTION_CONNECT = "kr.busrk.precool.CONNECT"
        private const val ACTION_STOP = "kr.busrk.precool.STOP"
        private const val EXTRA_ADDRESS = "address"
        private const val EXTRA_NAME = "name"
        private const val CH_LIVE = "live"
        private const val CH_ALERT = "alert"
        private const val ID_LIVE = 1
        private const val ID_ALERT = 2

        fun connect(ctx: Context, address: String, name: String) {
            val i = Intent(ctx, BoxService::class.java).setAction(ACTION_CONNECT)
                .putExtra(EXTRA_ADDRESS, address).putExtra(EXTRA_NAME, name)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, BoxService::class.java).setAction(ACTION_STOP))
        }
    }

    private var ble: BoxBle? = null
    private var boxName = "예냉박스"
    private lateinit var store: RunStore

    // 지금 기록 중인 회차
    private var runFile: File? = null
    private var runLastElapsed = -1L
    private var runLastWall = 0L
    private var lastSeq = -1
    private var runChecked = false

    // 기록 받기
    private val hist = mutableListOf<HistPoint>()

    // 알림 판단
    private var prevLevel: Level? = null
    private var prevLidAlarm = false
    private var wasConnected = false
    private var lastLiveNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = RunStore(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_LIVE, "박스 연결 상태", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "예냉 경고·완료", NotificationManager.IMPORTANCE_HIGH))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return START_NOT_STICKY
                boxName = intent.getStringExtra(EXTRA_NAME) ?: "예냉박스"
                val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
                try {
                    ServiceCompat.startForeground(this, ID_LIVE, liveNotification("$boxName 연결 중…"), type)
                } catch (e: Exception) {
                    BoxRepository.link.value = Link.Failed("백그라운드 연결을 시작하지 못했어요. 블루투스 권한을 확인해 주세요")
                    stopSelf(); return START_NOT_STICKY
                }
                Prefs.of(this).edit().putString(Prefs.LAST_ADDRESS, address).putString(Prefs.LAST_NAME, boxName).apply()
                ble?.close()
                runChecked = false; lastSeq = -1; prevLevel = null; prevLidAlarm = false; wasConnected = false
                BoxRepository.link.value = Link.Connecting(boxName)
                val b = BoxBle(this, this)
                ble = b
                BoxRepository.commandSender = { cmd -> b.send(cmd) }
                if (!b.connect(address)) shutdown()
            }
            ACTION_STOP -> shutdown()
        }
        return START_NOT_STICKY
    }

    private fun shutdown() {
        ble?.close(); ble = null
        BoxRepository.commandSender = null
        if (BoxRepository.link.value !is Link.Failed) BoxRepository.link.value = Link.Idle
        BoxRepository.activeRunFile.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ble?.close(); ble = null
        BoxRepository.commandSender = null
        super.onDestroy()
    }

    // ---------------- 블루투스에서 온 소식 ----------------
    override fun onLinkUp() {
        wasConnected = true
        BoxRepository.link.value = Link.Connected(boxName)
        updateLive("$boxName 연결됨")
    }

    override fun onLinkDown(willRetry: Boolean) {
        BoxRepository.link.value = if (willRetry) Link.Reconnecting(boxName) else Link.Idle
        updateLive("$boxName 연결이 끊겨서 다시 찾는 중…")
        if (wasConnected && alertOn(Prefs.ALERT_DISCONNECT)) {
            alert("박스 연결이 끊겼어요", "블루투스 거리(약 10m)를 벗어났거나 박스 전원이 꺼졌을 수 있어요. 가까이 가면 자동으로 다시 연결해요.")
        }
        wasConnected = false
        runChecked = false
    }

    override fun onError(message: String) {
        BoxRepository.link.value = Link.Failed(message)
        shutdown()
    }

    override fun onConfig(bytes: ByteArray) {
        BoxProtocol.parseConfig(bytes)?.let { BoxRepository.config.value = it }
    }

    override fun onReply(text: String) {
        if (text == "OK h") hist.clear()
        BoxRepository.postReply(text)
    }

    override fun onStatus(bytes: ByteArray) {
        val s = BoxProtocol.parseStatus(bytes) ?: return
        if (s.seq == lastSeq) return              // 읽기와 알림으로 같은 값이 두 번 올 때
        lastSeq = s.seq
        BoxRepository.status.value = s
        record(s)
        checkAlerts(s)
        updateLive(liveText(s))
    }

    override fun onHist(bytes: ByteArray) {
        val h = BoxProtocol.parseHist(bytes) ?: return
        if (h.index == 0) hist.clear()
        hist.add(h)
        if (h.index == h.total - 1) mergeHistory()
    }

    // ---------------- 기록 ----------------
    private fun toPoint(s: BoxStatus) = Point(
        tMin = s.elapsedMin, berryC = s.berryC, coldC = s.coldC, lidOpen = s.lidOpen, fanOn = s.fanOn,
        level = s.level.code, etaUsed = s.etaUsed, method = s.usedBy, etaAi = s.etaAi, etaNewton = s.etaNewton,
    )

    /** 같은 회차인지: 박스 시간이 되돌아가지 않았고, 박스 시간과 휴대폰 시간이 같이 흘렀으면 같은 회차 */
    private fun sameRun(elapsed: Long, lastElapsed: Long, wall: Long, lastWall: Long): Boolean {
        if (lastElapsed < 0 || elapsed + 5 < lastElapsed) return false
        return abs((wall - lastWall) / 1000.0 - (elapsed - lastElapsed)) < 90
    }

    private fun record(s: BoxStatus) {
        val sp = Prefs.of(this)
        if (!runChecked) {
            // 앱을 다시 켰거나 다시 연결됐을 때: 저장해 둔 회차를 이어 쓸지 정해요
            val saved = sp.getString(Prefs.RUN_FILE, null)?.let { File(store.dir, it) }
            val ok = saved != null && saved.exists() &&
                sameRun(s.elapsedSec, sp.getLong(Prefs.RUN_ELAPSED, -1), s.receivedAt, sp.getLong(Prefs.RUN_WALL, 0))
            if (ok) {
                runFile = saved; runLastElapsed = sp.getLong(Prefs.RUN_ELAPSED, -1); runLastWall = sp.getLong(Prefs.RUN_WALL, 0)
                BoxRepository.points.value = store.load(saved!!)
                BoxRepository.activeRunFile.value = saved.name
            } else startRun()
            runChecked = true
        } else if (!sameRun(s.elapsedSec, runLastElapsed, s.receivedAt, runLastWall)) {
            startRun()                              // "새 딸기 시작"으로 박스 시간이 0분이 됨
        }
        val f = runFile ?: return
        val p = toPoint(s)
        store.append(f, p)
        BoxRepository.points.value = BoxRepository.points.value + p
        runLastElapsed = s.elapsedSec; runLastWall = s.receivedAt
        sp.edit().putString(Prefs.RUN_FILE, f.name).putLong(Prefs.RUN_ELAPSED, runLastElapsed)
            .putLong(Prefs.RUN_WALL, runLastWall).apply()
    }

    private fun startRun() {
        val f = store.newFile()
        runFile = f; runLastElapsed = -1; runLastWall = 0
        BoxRepository.points.value = emptyList()
        BoxRepository.activeRunFile.value = f.name
        prevLevel = null; prevLidAlarm = false
    }

    /** 연결 전 10분 기록을 그래프·파일 앞쪽에 채워 넣어요 (팬 켜짐+뚜껑 닫힘이 아니었던 칸은 팬 꺼짐으로 저장) */
    private fun mergeHistory() {
        val f = runFile ?: return
        val cur = BoxRepository.points.value
        val firstT = cur.firstOrNull()?.tMin ?: Double.MAX_VALUE
        val add = hist.filter { it.tMin < firstT - 0.05 }.sortedBy { it.tMin }.map {
            Point(tMin = it.tMin, berryC = it.berryC, coldC = it.coldC, lidOpen = false, fanOn = it.clean)
        }
        hist.clear()
        if (add.isEmpty()) return
        val merged = add + cur
        BoxRepository.points.value = merged
        store.rewrite(f, merged)
    }

    // ---------------- 알림 ----------------
    private fun alertOn(key: String) = Prefs.of(this).getBoolean(key, true)

    private fun checkAlerts(s: BoxStatus) {
        val p = prevLevel
        if (s.level != p) {
            val first = p == null
            when (s.level) {
                Level.LATE_RISK -> if (alertOn(Prefs.ALERT_RISK)) alert("늦을 위험!", "이대로면 제한 시간 안에 목표 온도까지 못 식어요. 아이스팩·뚜껑을 확인해 주세요. (${s.methodText})")
                Level.LATE -> if (alertOn(Prefs.ALERT_LATE)) alert("제한 시간 지남", "수확 후 제한 시간이 지났는데 아직 목표 온도가 아니에요.")
                Level.DONE -> if (!first && alertOn(Prefs.ALERT_DONE)) alert("예냉 완료", "식품칸이 목표 온도에 닿았어요." + (s.doneAtMin?.let { " (${it}분째)" } ?: ""))
                Level.SENSOR_ERR -> if (alertOn(Prefs.ALERT_SENSOR)) alert("센서 확인 필요", "온도센서를 못 읽어서 팬을 멈췄어요. 배선을 확인해 주세요.")
                Level.COOLING -> {}
            }
            prevLevel = s.level
        }
        if (s.lidAlarm && !prevLidAlarm && alertOn(Prefs.ALERT_LID)) {
            alert("뚜껑이 열려 있어요", "뚜껑이 30초 넘게 열려 있어요. 찬 공기가 빠져나가요.")
        }
        prevLidAlarm = s.lidAlarm
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun canNotify(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun alert(title: String, text: String) {
        if (!canNotify()) return
        val n = NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_box)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(this).notify(ID_ALERT, n) } catch (e: SecurityException) { }
    }

    private fun liveText(s: BoxStatus): String {
        val t = s.berryC?.let { String.format(Locale.US, "%.1f℃", it) } ?: "--℃"
        return "식품칸 $t · ${s.level.title} · 남은 ${etaText(s.etaUsed)}"
    }

    private fun liveNotification(text: String) = NotificationCompat.Builder(this, CH_LIVE)
        .setSmallIcon(R.drawable.ic_stat_box)
        .setContentTitle(boxName)
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openAppIntent())
        .build()

    private fun updateLive(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastLiveNotify < 1500) return
        lastLiveNotify = now
        if (!canNotify()) return
        try { NotificationManagerCompat.from(this).notify(ID_LIVE, liveNotification(text)) } catch (e: SecurityException) { }
    }
}
