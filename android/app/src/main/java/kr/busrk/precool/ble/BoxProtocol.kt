package kr.busrk.precool.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * 펌웨어 precool_ai_ble.ino 와 약속한 블루투스 데이터 형식.
 * 숫자 하나라도 바뀌면 펌웨어 쪽도 같이 바꿔야 해요.
 */
object BoxProtocol {
    val SERVICE: UUID = UUID.fromString("b5a10001-7c3e-4f2a-9d6b-1e8c0f4a2b70")
    val STATUS: UUID = UUID.fromString("b5a10002-7c3e-4f2a-9d6b-1e8c0f4a2b70")  // 상태 20바이트 (2초마다 알림)
    val CONFIG: UUID = UUID.fromString("b5a10003-7c3e-4f2a-9d6b-1e8c0f4a2b70")  // 설정 10바이트 (읽기)
    val CMD: UUID = UUID.fromString("b5a10004-7c3e-4f2a-9d6b-1e8c0f4a2b70")     // 명령 쓰기 / 결과 글자 알림
    val HIST: UUID = UUID.fromString("b5a10005-7c3e-4f2a-9d6b-1e8c0f4a2b70")    // 최근 10분 기록 14바이트씩
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")    // 알림 켜기 스위치(표준 번호)
    const val NAME_PREFIX = "BUSRK-"
    const val PROTO_VERSION = 1

    private fun le(b: ByteArray): ByteBuffer = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)

    /** 0.01℃ 단위 정수 → ℃. -32768은 "못 읽음" */
    private fun temp(v: Short): Double? = if (v == Short.MIN_VALUE) null else v / 100.0

    /** 0.1분 단위 정수 → 분. -1 = 모름, -2 = 못 닿음 */
    private fun eta(v: Short): Double = when (v.toInt()) {
        -10 -> -1.0
        -20 -> -2.0
        else -> v / 10.0
    }

    private fun u8(b: Byte): Int = b.toInt() and 0xFF

    fun parseStatus(b: ByteArray, now: Long = System.currentTimeMillis()): BoxStatus? {
        if (b.size < 20) return null
        val x = le(b)
        val flags = u8(b[11])
        val doneAt = u8(b[15])
        return BoxStatus(
            seq = x.getShort(0).toInt() and 0xFFFF,
            elapsedSec = x.getInt(2).toLong() and 0xFFFFFFFFL,
            berryC = temp(x.getShort(6)),
            coldC = temp(x.getShort(8)),
            level = Level.of(u8(b[10])),
            lidOpen = (flags and 1) != 0,
            fanOn = (flags and 2) != 0,
            demo = (flags and 4) != 0,
            aiHold = (flags and 8) != 0,
            done = (flags and 16) != 0,
            lidAlarm = (flags and 32) != 0,
            etaUsed = eta(x.getShort(12)),
            usedBy = u8(b[14]).toChar(),
            doneAtMin = if (doneAt == 255) null else doneAt,
            etaNewton = eta(x.getShort(16)),
            etaAi = eta(x.getShort(18)),
            receivedAt = now,
        )
    }

    fun parseConfig(b: ByteArray): BoxConfig? {
        if (b.size < 10) return null
        val x = le(b)
        return BoxConfig(
            targetC = x.getShort(0) / 100.0,
            deadlineMin = (x.getShort(2).toInt() and 0xFFFF) / 10.0,
            demo = u8(b[4]) == 1,
            modelFromSimulation = u8(b[5]) == 1,
            remoteCommandOn = u8(b[6]) == 1,
            protoVersion = u8(b[7]),
            historySec = x.getShort(8).toInt() and 0xFFFF,
        )
    }

    fun parseHist(b: ByteArray): HistPoint? {
        if (b.size < 14) return null
        val x = le(b)
        return HistPoint(
            index = x.getShort(0).toInt() and 0xFFFF,
            total = x.getShort(2).toInt() and 0xFFFF,
            tMin = (x.getInt(4).toLong() and 0xFFFFFFFFL) / 60000.0,
            berryC = temp(x.getShort(8)),
            coldC = temp(x.getShort(10)),
            clean = u8(b[12]) == 1,
        )
    }
}

/** 펌웨어 enum Level 과 같은 순서 */
enum class Level(val code: Int, val title: String, val sub: String) {
    COOLING(0, "식히는 중", "제한 시간 안에 될 것 같아요"),
    DONE(1, "완료", "목표 온도에 닿았어요"),
    LATE_RISK(2, "늦을 위험", "이대로면 제한 시간 안에 못 식어요"),
    LATE(3, "늦음", "제한 시간이 지났는데 아직 못 식었어요"),
    SENSOR_ERR(4, "센서 확인", "온도를 못 읽고 있어요. 팬을 멈췄어요");

    companion object {
        fun of(code: Int): Level = entries.firstOrNull { it.code == code } ?: SENSOR_ERR
    }
}

data class BoxStatus(
    val seq: Int,
    val elapsedSec: Long,
    val berryC: Double?,
    val coldC: Double?,
    val level: Level,
    val lidOpen: Boolean,
    val fanOn: Boolean,
    val demo: Boolean,
    val aiHold: Boolean,
    val done: Boolean,
    val lidAlarm: Boolean,
    val etaUsed: Double,
    val usedBy: Char,
    val doneAtMin: Int?,
    val etaNewton: Double,
    val etaAi: Double,
    val receivedAt: Long,
) {
    val elapsedMin: Double get() = elapsedSec / 60.0

    /** 상태 판단에 쓴 예측 방법 */
    val methodText: String
        get() = when (usedBy) {
            'A' -> if (aiHold) "AI (뚜껑·팬 직후라 직전 예측 유지)" else "AI 학습 모델"
            'N' -> "뉴턴 계산식"
            'L' -> "직선 추세"
            else -> "아직 계산 전 (2분 기록 필요)"
        }
}

data class BoxConfig(
    val targetC: Double,
    val deadlineMin: Double,
    val demo: Boolean,
    val modelFromSimulation: Boolean,
    val remoteCommandOn: Boolean,
    val protoVersion: Int,
    val historySec: Int,
)

data class HistPoint(
    val index: Int,
    val total: Int,
    val tMin: Double,
    val berryC: Double?,
    val coldC: Double?,
    val clean: Boolean,
)

/** 남은 시간 글자 */
fun etaText(e: Double): String = when {
    e == -2.0 -> "못 닿음"
    e < 0 -> "--"
    e < 1 -> "1분 안"
    else -> "${Math.round(e)}분"
}
