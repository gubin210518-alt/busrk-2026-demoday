package kr.busrk.precool.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * 예냉박스 한 대와의 블루투스(BLE) 연결.
 * - 연결 → 서비스 찾기 → 알림 3개 켜기 → 설정·상태 읽기 → 최근 10분 기록 요청
 * - 블루투스는 한 번에 한 가지 일만 할 수 있어서, 일을 줄 세워 하나씩 처리해요(ops).
 * - 사용자가 끊지 않았는데 끊기면 2초 뒤 자동으로 다시 연결해요.
 * 권한 확인은 화면(MainActivity)에서 미리 해요.
 */
@SuppressLint("MissingPermission")
class BoxBle(private val ctx: Context, private val listener: Listener) {

    interface Listener {
        fun onLinkUp()
        fun onLinkDown(willRetry: Boolean)
        fun onError(message: String)
        fun onStatus(bytes: ByteArray)
        fun onConfig(bytes: ByteArray)
        fun onHist(bytes: ByteArray)
        fun onReply(text: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private var device: BluetoothDevice? = null
    private var gatt: BluetoothGatt? = null
    private var userClosed = false
    private var chCmd: BluetoothGattCharacteristic? = null

    // 줄 세운 블루투스 작업. 각 작업은 시작에 성공하면 true를 돌려줘요.
    private val ops = ArrayDeque<(BluetoothGatt) -> Boolean>()
    private var opBusy = false
    private val opTimeout = Runnable { opDone() }

    fun connect(address: String): Boolean {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) { listener.onError("블루투스가 꺼져 있어요"); return false }
        device = try { adapter.getRemoteDevice(address) } catch (e: IllegalArgumentException) { null }
        if (device == null) { listener.onError("박스 주소가 올바르지 않아요"); return false }
        userClosed = false
        open(autoConnect = false)
        return true
    }

    private fun open(autoConnect: Boolean) {
        val d = device ?: return
        gatt = d.connectGatt(ctx, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
    }

    /** 사용자가 끊을 때 */
    fun close() {
        userClosed = true
        main.removeCallbacksAndMessages(null)
        ops.clear(); opBusy = false
        gatt?.let { it.disconnect(); it.close() }
        gatt = null; chCmd = null
    }

    /** 명령 글자 보내기 (예: "h", "r123456") */
    fun send(text: String): Boolean {
        val c = chCmd
        if (gatt == null || c == null) return false
        val data = text.toByteArray(Charsets.US_ASCII)
        main.post { enqueue { g -> write(g, c, data) } }
        return true
    }

    // ---------------- 작업 줄 ----------------
    private fun enqueue(op: (BluetoothGatt) -> Boolean) { ops.addLast(op); next() }

    private fun next() {
        if (opBusy) return
        val g = gatt ?: return
        while (ops.isNotEmpty()) {
            val op = ops.removeFirst()
            if (op(g)) { opBusy = true; main.postDelayed(opTimeout, 3000); return }
        }
    }

    private fun opDone() {
        main.removeCallbacks(opTimeout)
        opBusy = false
        next()
    }

    @Suppress("DEPRECATION")
    private fun enableNotify(g: BluetoothGatt, ch: BluetoothGattCharacteristic): Boolean {
        if (!g.setCharacteristicNotification(ch, true)) return false
        val d = ch.getDescriptor(BoxProtocol.CCCD) ?: return false
        val v = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(d, v) == BluetoothStatusCodes.SUCCESS
        } else {
            d.value = v
            g.writeDescriptor(d)
        }
    }

    @Suppress("DEPRECATION")
    private fun write(g: BluetoothGatt, ch: BluetoothGattCharacteristic, data: ByteArray): Boolean {
        val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(ch, data, type) == BluetoothStatusCodes.SUCCESS
        } else {
            ch.writeType = type
            ch.value = data
            g.writeCharacteristic(ch)
        }
    }

    private fun deliver(uuid: java.util.UUID, value: ByteArray) {
        when (uuid) {
            BoxProtocol.STATUS -> listener.onStatus(value)
            BoxProtocol.CONFIG -> listener.onConfig(value)
            BoxProtocol.HIST -> listener.onHist(value)
            BoxProtocol.CMD -> listener.onReply(String(value, Charsets.US_ASCII))
        }
    }

    // ---------------- 블루투스 콜백 (다른 스레드에서 와서 main으로 넘겨요) ----------------
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (g != gatt) return@post
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    listener.onLinkUp()
                    main.postDelayed({ gatt?.discoverServices() }, 300)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    g.close(); gatt = null; chCmd = null
                    ops.clear(); opBusy = false; main.removeCallbacks(opTimeout)
                    val retry = !userClosed
                    listener.onLinkDown(retry)
                    if (retry) main.postDelayed({ if (!userClosed) open(autoConnect = true) }, 2000)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                if (g != gatt) return@post
                val svc = g.getService(BoxProtocol.SERVICE)
                if (svc == null) {
                    listener.onError("예냉박스가 아니거나 펌웨어가 블루투스판(precool_ai_ble)이 아니에요")
                    close(); return@post
                }
                val st = svc.getCharacteristic(BoxProtocol.STATUS)
                val cfg = svc.getCharacteristic(BoxProtocol.CONFIG)
                val cmd = svc.getCharacteristic(BoxProtocol.CMD)
                val hist = svc.getCharacteristic(BoxProtocol.HIST)
                chCmd = cmd
                if (st != null) enqueue { enableNotify(it, st) }
                if (cmd != null) enqueue { enableNotify(it, cmd) }
                if (hist != null) enqueue { enableNotify(it, hist) }
                if (cfg != null) enqueue { it.readCharacteristic(cfg) }
                if (st != null) enqueue { it.readCharacteristic(st) }
                if (cmd != null) enqueue { write(it, cmd, "h".toByteArray()) }   // 최근 10분 기록 받기
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            main.post { opDone() }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            main.post { opDone() }
        }

        // 안드로이드 13 이상
        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            val copy = value.copyOf()
            main.post { if (status == BluetoothGatt.GATT_SUCCESS) deliver(ch.uuid, copy); opDone() }
        }

        // 안드로이드 12 이하
        @Deprecated("Android 12 이하용")
        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION") val copy = ch.value?.copyOf() ?: ByteArray(0)
            main.post { if (status == BluetoothGatt.GATT_SUCCESS) deliver(ch.uuid, copy); opDone() }
        }

        // 안드로이드 13 이상
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            val copy = value.copyOf()
            main.post { deliver(ch.uuid, copy) }
        }

        // 안드로이드 12 이하
        @Deprecated("Android 12 이하용")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") val copy = ch.value?.copyOf() ?: return
            main.post { deliver(ch.uuid, copy) }
        }
    }
}
