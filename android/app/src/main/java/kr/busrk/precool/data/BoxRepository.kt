package kr.busrk.precool.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kr.busrk.precool.ble.BoxConfig
import kr.busrk.precool.ble.BoxStatus

/** 그래프·기록에 쓰는 한 시점 */
data class Point(
    val tMin: Double,          // 박스 기준 경과 시간(분)
    val berryC: Double?,
    val coldC: Double?,
    val lidOpen: Boolean,
    val fanOn: Boolean,
    val level: Int = -1,
    val etaUsed: Double = -1.0,
    val method: Char = '-',
    val etaAi: Double = -1.0,
    val etaNewton: Double = -1.0,
)

/** 연결 상태 */
sealed interface Link {
    data object Idle : Link
    data class Connecting(val name: String) : Link
    data class Connected(val name: String) : Link
    data class Reconnecting(val name: String) : Link
    data class Failed(val message: String) : Link
}

/**
 * 앱 전체가 함께 보는 상태 저장소.
 * 블루투스 서비스(BoxService)가 값을 넣고, 화면이 값을 읽어요.
 */
object BoxRepository {
    val link = MutableStateFlow<Link>(Link.Idle)
    val status = MutableStateFlow<BoxStatus?>(null)
    val config = MutableStateFlow<BoxConfig?>(null)
    val points = MutableStateFlow<List<Point>>(emptyList())
    val activeRunFile = MutableStateFlow<String?>(null)

    private val _replies = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val replies: SharedFlow<String> = _replies
    fun postReply(text: String) { _replies.tryEmit(text) }

    /** 서비스가 연결되어 있을 때만 채워져요. 박스로 명령 글자를 보내요. */
    @Volatile
    var commandSender: ((String) -> Boolean)? = null

    fun send(cmd: String): Boolean = commandSender?.invoke(cmd) ?: false

}

/** 휴대폰에만 저장되는 간단한 설정 */
object Prefs {
    const val ALERT_RISK = "alert_risk"
    const val ALERT_LATE = "alert_late"
    const val ALERT_DONE = "alert_done"
    const val ALERT_SENSOR = "alert_sensor"
    const val ALERT_LID = "alert_lid"
    const val ALERT_DISCONNECT = "alert_disconnect"
    const val LAST_ADDRESS = "last_address"
    const val LAST_NAME = "last_name"
    const val RUN_FILE = "run_file"
    const val RUN_ELAPSED = "run_elapsed"
    const val RUN_WALL = "run_wall"

    fun of(ctx: Context): SharedPreferences = ctx.getSharedPreferences("busrk", Context.MODE_PRIVATE)
    fun memoKey(fileName: String) = "memo_$fileName"
}
