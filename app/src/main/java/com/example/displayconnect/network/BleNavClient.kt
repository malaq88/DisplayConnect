package com.example.displayconnect.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.example.displayconnect.models.ConnectionState
import com.example.displayconnect.protocol.NavMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BLE UART (Nordic NUS) client for JSON navigation updates to the ESP32 CYD.
 * Framing: each JSON message ends with '\n'; large payloads are chunked to fit MTU.
 */
@SuppressLint("MissingPermission")
class BleNavClient(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var writeChunkSize = DEFAULT_CHUNK
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var scanJob: Job? = null
    private val shouldReconnect = AtomicBoolean(false)
    private val writeMutex = Mutex()

    private var targetAddress: String = ""

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BleDeviceItem>>(emptyList())
    val scannedDevices: StateFlow<List<BleDeviceItem>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val deviceMap = ConcurrentHashMap<String, BleDeviceItem>()

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun startScan(durationMs: Long = SCAN_DURATION_MS) {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || !isBluetoothEnabled()) {
            _isScanning.value = false
            return
        }

        stopScanInternal()
        deviceMap.clear()
        _scannedDevices.value = emptyList()
        _isScanning.value = true

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (_: SecurityException) {
            try {
                scanner.startScan(null, settings, scanCallback)
            } catch (_: SecurityException) {
                _isScanning.value = false
                return
            }
        }

        scanJob = scope.launch {
            delay(durationMs)
            stopScanInternal()
        }
    }

    fun stopScan() {
        stopScanInternal()
    }

    private fun stopScanInternal() {
        scanJob?.cancel()
        scanJob = null
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
            // ignore
        }
        _isScanning.value = false
    }

    fun connect(address: String, name: String = "") {
        disconnect(manual = false)
        targetAddress = address
        shouldReconnect.set(true)
        openGatt()
    }

    fun disconnect(manual: Boolean = true) {
        if (manual) {
            shouldReconnect.set(false)
        }
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        stopScanInternal()
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
            // ignore
        }
        gatt = null
        rxCharacteristic = null
        writeChunkSize = DEFAULT_CHUNK
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun sendNavMessage(json: String): Boolean {
        if (_connectionState.value != ConnectionState.CONNECTED) return false
        val characteristic = rxCharacteristic ?: return false
        val gattLocal = gatt ?: return false
        val payload = (json.trimEnd() + "\n").toByteArray(Charsets.UTF_8)

        scope.launch {
            writeMutex.withLock {
                writeInChunks(gattLocal, characteristic, payload)
            }
        }
        return true
    }

    fun release() {
        disconnect(manual = true)
        scope.cancel()
    }

    private fun openGatt() {
        if (targetAddress.isBlank()) return
        val device = try {
            adapter?.getRemoteDevice(targetAddress)
        } catch (_: IllegalArgumentException) {
            null
        } ?: run {
            _connectionState.value = ConnectionState.ERROR
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(appContext, false, gattCallback)
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect.get()) return
        reconnectJob?.cancel()
        _connectionState.value = ConnectionState.RECONNECTING
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (shouldReconnect.get() && isActive) {
                openGatt()
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                delay(HEARTBEAT_INTERVAL_SEC * 1000)
                sendNavMessage(NavMessage.heartbeat())
            }
        }
    }

    private fun writeInChunks(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray
    ) {
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + writeChunkSize, payload.size)
            val chunk = payload.copyOfRange(offset, end)
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    chunk,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                ) == 0
            } else {
                @Suppress("DEPRECATION")
                run {
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    characteristic.value = chunk
                    gatt.writeCharacteristic(characteristic)
                }
            }
            if (!ok) break
            offset = end
            Thread.sleep(8)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            val name = result.scanRecord?.deviceName
                ?: device.name
                ?: ""
            val hasNus = result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true
            val nameMatch = name.contains(DEVICE_NAME_HINT, ignoreCase = true)
            if (!hasNus && !nameMatch && name.isNotBlank()) {
                return
            }
            if (!hasNus && !nameMatch && name.isBlank()) {
                // Service filter scan already matched; keep unnamed devices
            }
            val displayName = name.ifBlank { "CYD $address" }
            deviceMap[address] = BleDeviceItem(address, displayName, result.rssi)
            _scannedDevices.value = deviceMap.values.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.CONNECTING
                gatt.requestMtu(REQUESTED_MTU)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                heartbeatJob?.cancel()
                rxCharacteristic = null
                try {
                    gatt.close()
                } catch (_: Exception) {
                }
                this@BleNavClient.gatt = null
                if (shouldReconnect.get()) {
                    scheduleReconnect()
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeChunkSize = (mtu - 3).coerceIn(20, 500)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.ERROR
                scheduleReconnect()
                return
            }
            val service = gatt.getService(SERVICE_UUID)
            val rx = service?.getCharacteristic(RX_UUID)
            val tx = service?.getCharacteristic(TX_UUID)
            if (rx == null || tx == null) {
                _connectionState.value = ConnectionState.ERROR
                scheduleReconnect()
                return
            }
            rxCharacteristic = rx

            gatt.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(cccd)
                    }
                }
            }

            _connectionState.value = ConnectionState.CONNECTED
            startHeartbeat()
        }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val TX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val DEVICE_NAME_HINT = "DisplayConnect"
        private const val REQUESTED_MTU = 512
        private const val DEFAULT_CHUNK = 180
        private const val HEARTBEAT_INTERVAL_SEC = 15L
        private const val RECONNECT_DELAY_MS = 3000L
        private const val SCAN_DURATION_MS = 10_000L
    }
}
