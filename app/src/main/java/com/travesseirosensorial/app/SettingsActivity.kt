package com.travesseirosensorial.app

import android.app.Activity
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.travesseirosensorial.app.models.UserSettings

class SettingsActivity : AppCompatActivity() {

    private lateinit var sbSensitivity: SeekBar
    private lateinit var spMassageType: Spinner
    private lateinit var etAutoBpm: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private var settings: UserSettings = UserSettings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sbSensitivity = findViewById(R.id.sb_sensitivity)
        spMassageType = findViewById(R.id.sp_massage_type)
        etAutoBpm = findViewById(R.id.et_auto_bpm)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)

        val extras = intent
        val passed = extras.getSerializableExtra("userSettings") as? UserSettings
        if (passed != null) {
            settings = passed
        }

        sbSensitivity.progress = settings.touchSensitivity
        etAutoBpm.setText(settings.autoActivateBpmLimit.toString())

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            UserSettings.MassageType.values().map { it.displayName }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spMassageType.adapter = adapter
        spMassageType.setSelection(settings.massageType.ordinal)

        btnSave.setOnClickListener {
            settings.touchSensitivity = sbSensitivity.progress
            settings.massageType = UserSettings.MassageType.values()[spMassageType.selectedItemPosition]
            val bpmLimit = etAutoBpm.text.toString().toIntOrNull() ?: 0
            settings.autoActivateBpmLimit = bpmLimit

            val result = intent
            result.putExtra("userSettings", settings)
            setResult(Activity.RESULT_OK, result)
            finish()
        }

        btnCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
}
