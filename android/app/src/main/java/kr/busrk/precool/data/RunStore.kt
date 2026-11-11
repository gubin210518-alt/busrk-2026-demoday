package kr.busrk.precool.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 한 회차 기록 요약 */
data class RunInfo(
    val file: File,
    val title: String,
    val minutes: Double,
    val startC: Double?,
    val minC: Double?,
    val samples: Int,
)

/**
 * 실험 기록을 CSV 파일로 저장해요. (앱 내부 저장공간 /files/runs)
 * 첫 5칸은 AI 시뮬레이터 "실험 기록 → CSV 불러오기"와 같은 형식이라, 그대로 AI 재학습에 쓸 수 있어요.
 */
class RunStore(ctx: Context) {
    val dir: File = File(ctx.filesDir, "runs").apply { mkdirs() }

    companion object {
        const val HEADER = "min,berry_c,cold_c,lid_open,cooling,level,eta_used,method,eta_ai,eta_newton"
        private val nameFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        private val titleFmt = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
    }

    fun newFile(): File {
        val f = File(dir, "run_${nameFmt.format(Date())}.csv")
        f.writeText(HEADER + "\n")
        return f
    }

    private fun num(v: Double?, digits: Int): String =
        if (v == null || v.isNaN()) "nan" else String.format(Locale.US, "%.${digits}f", v)

    fun line(p: Point): String = listOf(
        num(p.tMin, 3), num(p.berryC, 2), num(p.coldC, 2),
        if (p.lidOpen) "1" else "0", if (p.fanOn) "1" else "0",
        p.level.toString(), num(p.etaUsed, 1), p.method.toString(), num(p.etaAi, 1), num(p.etaNewton, 1),
    ).joinToString(",")

    fun append(f: File, p: Point) {
        if (!f.exists()) f.writeText(HEADER + "\n")   // 기록 화면에서 지웠어도 다시 만들어요
        f.appendText(line(p) + "\n")
    }

    fun rewrite(f: File, pts: List<Point>) {
        f.writeText(HEADER + "\n" + pts.joinToString("") { line(it) + "\n" })
    }

    fun load(f: File): List<Point> {
        if (!f.exists()) return emptyList()
        return f.readLines().drop(1).mapNotNull { row ->
            val c = row.split(",")
            if (c.size < 5) return@mapNotNull null
            val t = c[0].toDoubleOrNull() ?: return@mapNotNull null
            Point(
                tMin = t,
                berryC = c[1].toDoubleOrNull()?.takeIf { !it.isNaN() },
                coldC = c[2].toDoubleOrNull()?.takeIf { !it.isNaN() },
                lidOpen = c[3] == "1",
                fanOn = c[4] == "1",
                level = c.getOrNull(5)?.toIntOrNull() ?: -1,
                etaUsed = c.getOrNull(6)?.toDoubleOrNull() ?: -1.0,
                method = c.getOrNull(7)?.firstOrNull() ?: '-',
                etaAi = c.getOrNull(8)?.toDoubleOrNull() ?: -1.0,
                etaNewton = c.getOrNull(9)?.toDoubleOrNull() ?: -1.0,
            )
        }
    }

    fun list(): List<RunInfo> =
        (dir.listFiles { f -> f.name.endsWith(".csv") } ?: emptyArray())
            .sortedByDescending { it.name }
            .map { f ->
                val pts = load(f)
                val bs = pts.mapNotNull { it.berryC }
                val title = try {
                    titleFmt.format(nameFmt.parse(f.name.removePrefix("run_").removeSuffix(".csv"))!!)
                } catch (e: Exception) { f.name }
                RunInfo(
                    file = f,
                    title = title,
                    minutes = if (pts.size >= 2) pts.last().tMin - pts.first().tMin else 0.0,
                    startC = bs.firstOrNull(),
                    minC = bs.minOrNull(),
                    samples = pts.size,
                )
            }
}
