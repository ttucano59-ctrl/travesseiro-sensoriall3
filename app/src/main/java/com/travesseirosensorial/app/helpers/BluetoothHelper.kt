package com.travesseirosensorial.app.helpers

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.concurrent.thread

class BluetoothHelper(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothHelper"
        val GENERIC_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val GENERIC_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    }

    var onDeviceFound: ((device: BluetoothDevice) -> Unit)? = null
    var onConnected: ((device: BluetoothDevice) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onDataReceived: ((raw: String) -> Unit)? = null

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private var bleScanner: BluetoothLeScanner? = null
    private var scanning = false
    private var scanCallback: ScanCallback? = null

    private var bluetoothGatt: BluetoothGatt? = null

    private var socketThread: Thread? = null
    private var btSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w(TAG, "Bluetooth adapter não disponível/ativado")
            return
        }
        bleScanner = bluetoothAdapter!!.bluetoothLeScanner
        if (bleScanner == null) {
            Log.w(TAG, "BLE scanner não disponível, talvez dispositivo não suporte BLE")
            return
        }

        if (scanning) return

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(GENERIC_SERVICE_UUID)).build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    onDeviceFound?.invoke(device)
                    stopScan()
                }
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { r -> r.device?.let { onDeviceFound?.invoke(it) } }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "Scan failed: $errorCode")
            }
        }

        try {
            bleScanner?.startScan(filters, settings, scanCallback)
            scanning = true
        } catch (ex: SecurityException) {
            Log.e(TAG, "startScan: permissões faltando", ex)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (ex: Exception) {
            Log.w(TAG, "stopScan exception: $${ex.message}")
        }
        scanning = false
        scanCallback = null
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice) {
        stopScan()
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (ex: Exception) {
            Log.e(TAG, "connectDevice failed: $${ex.message}")
            connectClassic(device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectClassic(device: BluetoothDevice) {
        thread {
            try {
                val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                btSocket = device.createRfcommSocketToServiceRecord(sppUuid)
                btSocket?.connect()
                inputStream = btSocket?.inputStream
                outputStream = btSocket?.outputStream
                onConnected?.invoke(device)
                val buffer = ByteArray(1024)
                while (btSocket?.isConnected == true) {
                    val read = inputStream?.read(buffer) ?: -1
                    if (read > 0) {
                        val raw = String(buffer, 0, read)
                        onDataReceived?.invoke(raw)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Classic connect error: $${e.message}")
                disconnectClassic()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        disconnectClassic()
    }

    private fun disconnectClassic() {
        try { inputStream?.close() } catch (ignored: Exception) {}
        try { outputStream?.close() } catch (ignored: Exception) {}
        try { btSocket?.close() } catch (ignored: Exception) {}
        btSocket = null
        socketThread = null
        onDisconnected?.invoke()
    }

    fun cleanup() {
        stopScan()
        bluetoothGatt?.close()
        bluetoothGatt = null
        disconnectClassic()
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(command: String) {
        try {
            outputStream?.let {
                it.write((command + "\n").toByteArray())
                it.flush()
                return
            }
        } catch (ex: Exception) {
            Log.w(TAG, "sendCommand SPP failed: $${ex.message}")
        }

        bluetoothGatt?.let { gatt ->
            val service = gatt.services.firstOrNull { svc -> svc.uuid == GENERIC_SERVICE_UUID }
                ?: gatt.services.firstOrNull()
            val char = service?.characteristics?.firstOrNull { c ->
                (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                        (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            }
            if (char != null) {
                char.setValue(command.toByteArray())
                gatt.writeCharacteristic(char)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT connected, discovering services")
                onConnected?.invoke(gatt.device)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "GATT disconnected")
                onDisconnected?.invoke()
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Service discovery failed: $status")
                return
            }
            val service = gatt.services.firstOrNull { it.uuid == GENERIC_SERVICE_UUID } ?: gatt.services.firstOrNull()
            val char = service?.characteristics?.firstOrNull { (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 }
            if (char != null) {
                gatt.setCharacteristicNotification(char, true)
                val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                }
            } else {
                val readable = service?.characteristics?.firstOrNull { (it.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0 }
                readable?.let { gatt.readCharacteristic(it) }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.getStringValue(0) ?: String(characteristic.value ?: byteArrayOf())
            onDataReceived?.invoke(data)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val data = characteristic.getStringValue(0) ?: String(characteristic.value ?: byteArrayOf())
                onDataReceived?.invoke(data)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.i(TAG, "Characteristic write status: $status")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.i(TAG, "Descriptor write status: $status")
        }
    }
}
