package kr.busrk.precool.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow

/** 주변의 예냉박스 찾기 (12초 동안). 이름이 BUSRK-로 시작하거나 예냉박스 서비스 번호가 있으면 보여줘요. */
@SuppressLint("MissingPermission")
object BoxScanner {
    data class Found(val name: String, val address: String, val rssi: Int)

    val found = MutableStateFlow<List<Found>>(emptyList())
    val scanning = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private val main = Handler(Looper.getMainLooper())
    private var callback: ScanCallback? = null
    private var appCtx: Context? = null

    fun start(ctx: Context) {
        stop()
        appCtx = ctx.applicationContext
        val scanner = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        if (scanner == null) { error.value = "블루투스를 켜 주세요"; return }
        found.value = emptyList(); error.value = null
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) { main.post { add(result) } }
            override fun onBatchScanResults(results: MutableList<ScanResult>) { main.post { results.forEach { add(it) } } }
            override fun onScanFailed(errorCode: Int) {
                main.post { scanning.value = false; error.value = "찾기 실패 (오류 $errorCode). 잠시 뒤 다시 해 보세요" }
            }
        }
        callback = cb
        scanning.value = true
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, cb)
        } catch (e: SecurityException) {
            scanning.value = false; error.value = "주변 기기 권한이 필요해요"; return
        }
        main.postDelayed({ stop() }, 12_000)
    }

    private fun add(r: ScanResult) {
        val uuids = r.scanRecord?.serviceUuids
        val name = r.scanRecord?.deviceName ?: try { r.device.name } catch (e: SecurityException) { null }
        val isBox = uuids?.any { it.uuid == BoxProtocol.SERVICE } == true ||
            name?.startsWith(BoxProtocol.NAME_PREFIX) == true
        if (!isBox) return
        val item = Found(name ?: "예냉박스", r.device.address, r.rssi)
        found.value = (found.value.filter { it.address != item.address } + item).sortedByDescending { it.rssi }
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        val cb = callback ?: run { scanning.value = false; return }
        try {
            appCtx?.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (e: Exception) { /* 블루투스가 꺼진 경우 등 */ }
        callback = null
        scanning.value = false
    }
}
