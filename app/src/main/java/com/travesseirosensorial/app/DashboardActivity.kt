package com.travesseirosensorial.app

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.travesseirosensorial.app.helpers.BluetoothHelper
import com.travesseirosensorial.app.models.HealthData
import com.travesseirosensorial.app.models.PillowStatus
import com.travesseirosensorial.app.models.UserSettings

class DashboardActivity : AppCompatActivity() {

    private lateinit var btnConnect: Button
    private lateinit var btnActivate: Button
    private lateinit var btnDeactivate: Button
    private lateinit var btnSettings: Button
    private lateinit var tvBpm: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvBattery: TextView

    private lateinit var bluetoothHelper: BluetoothHelper

    private var userSettings = UserSettings()
    private var pillowStatus = PillowStatus()
    private var latestHealth = HealthData()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val requiredPermissions = mutableListOf<String>().apply {
        add(Manifest.permission.BLUETOOTH)
        add(Manifest.permission.BLUETOOTH_ADMIN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        btnConnect = findViewById(R.id.btn_connect)
        btnActivate = findViewById(R.id.btn_activate)
        btnDeactivate = findViewById(R.id.btn_deactivate)
        btnSettings = findViewById(R.id.btn_settings)
        tvBpm = findViewById(R.id.tv_bpm)
        tvStatus = findViewById(R.id.tv_status)
        tvBattery = findViewById(R.id.tv_battery)

        bluetoothHelper = BluetoothHelper(this)

        bluetoothHelper.onDeviceFound = { device ->
            mainHandler.post {
                showConnectDialog(device)
            }
        }

        bluetoothHelper.onConnected = { device ->
            pillowStatus.isConnected = true
            pillowStatus.connectedDeviceName = device.name ?: device.address
            updateStatusUI()
        }

        bluetoothHelper.onDisconnected = {
            pillowStatus.isConnected = false
            pillowStatus.connectedDeviceName = null
            pillowStatus.isActive = false
            updateStatusUI()
        }

        bluetoothHelper.onDataReceived = { raw ->
            parseIncomingData(raw)
        }

        btnConnect.setOnClickListener {
            if (!allPermissionsGranted()) {
                requestNecessaryPermissions()
                return@setOnClickListener
            }
            bluetoothHelper.startScan()
            showScanningToast()
        }

        btnActivate.setOnClickListener {
            if (!pillowStatus.isConnected) {
                showAlert("Dispositivo não conectado", "Conecte-se a um travesseiro antes de ativar.")
                return@setOnClickListener
            }
            bluetoothHelper.sendCommand("ACTIVATE")
            pillowStatus.isActive = true
            updateStatusUI()
        }

        btnDeactivate.setOnClickListener {
            if (!pillowStatus.isConnected) {
                showAlert("Dispositivo não conectado", "Conecte-se a um travesseiro antes de desativar.")
                return@setOnClickListener
            }
            bluetoothHelper.sendCommand("DEACTIVATE")
            pillowStatus.isActive = false
            updateStatusUI()
        }

        btnSettings.setOnClickListener {
            val i = Intent(this, SettingsActivity::class.java)
            i.putExtra("userSettings", userSettings)
            startActivityForResult(i, 1001)
        }

        updateStatusUI()
    }

    private fun showScanningToast() {
        showAlert("Procurando", "Procurando dispositivos BLE/BT próximos. Quando um dispositivo for encontrado, você será perguntado para conectar.")
    }

    private fun parseIncomingData(raw: String) {
        val tokens = raw.split(';')
        tokens.forEach { token ->
            val parts = token.split(':')
            if (parts.size >= 2) {
                val key = parts[0].trim().uppercase()
                val value = parts[1].trim()
                when (key) {
                    "BPM" -> {
                        val bpmVal = value.toIntOrNull() ?: 0
                        latestHealth.bpm = bpmVal
                        if (userSettings.autoActivateBpmLimit > 0 && bpmVal >= userSettings.autoActivateBpmLimit) {
                            bluetoothHelper.sendCommand("ACTIVATE")
                            pillowStatus.isActive = true
                        }
                    }
                    "BAT" -> {
                        val bat = value.toIntOrNull() ?: 0
                        pillowStatus.batteryLevel = bat
                    }
                    "STATUS" -> {
                        pillowStatus.isActive = value.equals("ACTIVE", ignoreCase = true)
                    }
                    "INTENSITY" -> {
                        pillowStatus.intensity = value.toIntOrNull() ?: pillowStatus.intensity
                    }
                }
            }
        }

        mainHandler.post {
            tvBpm.text = "${latestHealth.bpm} BPM"
            tvBattery.text = "${pillowStatus.batteryLevel}%"
            updateStatusUI()
        }
    }

    private fun updateStatusUI() {
        tvStatus.text = when {
            !pillowStatus.isConnected -> "Inativo (Desconectado)"
            pillowStatus.isActive -> "Ativo (${pillowStatus.intensity}%)"
            else -> "Conectado (Inativo)"
        }
        tvBpm.text = "${latestHealth.bpm} BPM"
        tvBattery.text = "${pillowStatus.batteryLevel}%"
    }

    private fun showConnectDialog(device: BluetoothDevice) {
        val name = device.name ?: device.address
        AlertDialog.Builder(this)
            .setTitle("Dispositivo encontrado")
            .setMessage("Conectar a $name ?")
            .setPositiveButton("Conectar") { _, _ ->
                bluetoothHelper.connectDevice(device)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAlert(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun allPermissionsGranted(): Boolean {
        return requiredPermissions.all { perm ->
            ActivityCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val granted = perms.entries.all { it.value == true }
            if (!granted) {
                showAlert("Permissões necessárias", "Permissões Bluetooth/Localização são necessárias para descobrir e conectar dispositivos.")
            }
        }

    private fun requestNecessaryPermissions() {
        requestPermissionsLauncher.launch(requiredPermissions)
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothHelper.cleanup()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && data != null) {
            val updated = data.getSerializableExtra("userSettings") as? UserSettings
            if (updated != null) {
                userSettings = updated
                bluetoothHelper.sendCommand("SENS:${userSettings.touchSensitivity}")
                bluetoothHelper.sendCommand("MODE:${userSettings.massageType.name}")
                bluetoothHelper.sendCommand("AUTOBPM:${userSettings.autoActivateBpmLimit}")
            }
        }
    }
}
