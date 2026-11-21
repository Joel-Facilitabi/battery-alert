package com.joelassis.batteryalert

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.joelassis.batteryalert.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            toggleService(true)
        } else {
            Toast.makeText(
                this,
                "Permissão de notificação necessária para alertas",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        updateBatteryInfo()
    }

    override fun onResume() {
        super.onResume()
        updateBatteryInfo()
        ensureServiceRunning()
    }

    // Se o monitoramento está marcado como ativo, garante que o serviço está rodando
    // (o Android encerra o serviço quando o app é atualizado)
    private fun ensureServiceRunning() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(BatteryMonitorService.PREF_SERVICE_ENABLED, false)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        startForegroundService(Intent(this, BatteryMonitorService::class.java))
    }

    private fun setupUI() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val threshold = prefs.getInt(BatteryMonitorService.PREF_THRESHOLD, BatteryMonitorService.DEFAULT_THRESHOLD)
        val enabled = prefs.getBoolean(BatteryMonitorService.PREF_SERVICE_ENABLED, false)

        // Configurar SeekBar
        binding.seekBarThreshold.progress = threshold
        binding.textThresholdValue.text = "$threshold%"

        binding.seekBarThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(5, 95)
                binding.textThresholdValue.text = "$value%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = (seekBar?.progress ?: BatteryMonitorService.DEFAULT_THRESHOLD).coerceIn(5, 95)
                prefs.edit().putInt(BatteryMonitorService.PREF_THRESHOLD, value).apply()

                // Reset do alerta para reavaliar com novo limite
                prefs.edit().putBoolean(BatteryMonitorService.PREF_ALERT_SHOWN, false).apply()

                if (prefs.getBoolean(BatteryMonitorService.PREF_SERVICE_ENABLED, false)) {
                    // Reiniciar serviço com novo limite
                    restartService()
                }
            }
        })

        // Configurar botão de ativar/desativar
        binding.switchEnable.isChecked = enabled
        updateButtonState(enabled)

        binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            toggleService(isChecked)
            updateButtonState(isChecked)
        }

        setupUpperLimit()
        setupWhatsapp()
    }

    private fun setupUpperLimit() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val upper = prefs.getInt(BatteryMonitorService.PREF_UPPER_THRESHOLD, BatteryMonitorService.DEFAULT_UPPER)

        binding.switchUpper.isChecked = prefs.getBoolean(BatteryMonitorService.PREF_UPPER_ENABLED, false)
        binding.seekBarUpper.progress = upper
        binding.textUpperValue.text = "$upper%"

        binding.switchUpper.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(BatteryMonitorService.PREF_UPPER_ENABLED, isChecked)
                .putBoolean(BatteryMonitorService.PREF_HIGH_SHOWN, false)
                .apply()
        }

        binding.seekBarUpper.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textUpperValue.text = "${progress.coerceIn(50, 100)}%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = (seekBar?.progress ?: BatteryMonitorService.DEFAULT_UPPER).coerceIn(50, 100)
                prefs.edit()
                    .putInt(BatteryMonitorService.PREF_UPPER_THRESHOLD, value)
                    .putBoolean(BatteryMonitorService.PREF_HIGH_SHOWN, false)
                    .apply()
            }
        })
    }

    private fun setupWhatsapp() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        binding.editPhone.setText(prefs.getString(WhatsAppSender.PREF_WA_PHONE, ""))
        binding.editApiKey.setText(prefs.getString(WhatsAppSender.PREF_WA_APIKEY, ""))
        binding.switchWhatsapp.isChecked = prefs.getBoolean(WhatsAppSender.PREF_WA_ENABLED, false)

        binding.switchWhatsapp.setOnCheckedChangeListener { _, isChecked ->
            saveWhatsappFields()
            prefs.edit().putBoolean(WhatsAppSender.PREF_WA_ENABLED, isChecked).apply()
        }

        binding.buttonHowTo.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WhatsAppSender.SETUP_URL)))
        }

        binding.buttonTest.setOnClickListener {
            saveWhatsappFields()
            binding.buttonTest.isEnabled = false
            binding.buttonTest.text = "Enviando..."
            WhatsAppSender.send(
                this,
                "✅ *Battery Alert* configurado! Você vai receber os avisos de bateria por aqui."
            ) { ok, message ->
                binding.buttonTest.isEnabled = true
                binding.buttonTest.text = "Salvar e testar"
                if (ok) {
                    binding.switchWhatsapp.isChecked = true
                    prefs.edit().putBoolean(WhatsAppSender.PREF_WA_ENABLED, true).apply()
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveWhatsappFields() {
        // Mantém só dígitos e o "+" do número
        val phone = binding.editPhone.text?.toString().orEmpty().filter { it.isDigit() || it == '+' }
        val apiKey = binding.editApiKey.text?.toString()?.trim().orEmpty()
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(WhatsAppSender.PREF_WA_PHONE, phone)
            .putString(WhatsAppSender.PREF_WA_APIKEY, apiKey)
            .apply()
    }

    override fun onPause() {
        super.onPause()
        saveWhatsappFields()
    }

    private fun toggleService(enable: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        if (enable) {
            // Verificar permissão de notificação (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }

            prefs.edit().putBoolean(BatteryMonitorService.PREF_SERVICE_ENABLED, true).apply()
            val intent = Intent(this, BatteryMonitorService::class.java)
            startForegroundService(intent)
            Toast.makeText(this, "Monitoramento ativado ✓", Toast.LENGTH_SHORT).show()
        } else {
            prefs.edit().putBoolean(BatteryMonitorService.PREF_SERVICE_ENABLED, false).apply()
            val intent = Intent(this, BatteryMonitorService::class.java)
            stopService(intent)
            Toast.makeText(this, "Monitoramento desativado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restartService() {
        val intent = Intent(this, BatteryMonitorService::class.java)
        stopService(intent)
        startForegroundService(intent)
    }

    private fun updateButtonState(enabled: Boolean) {
        binding.switchEnable.text = if (enabled) "Monitoramento Ativo" else "Monitoramento Inativo"
    }

    private fun updateBatteryInfo() {
        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val percent = (level * 100) / scale
            val isCharging = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0

            binding.textBatteryLevel.text = "$percent%"
            binding.textChargingStatus.text = if (isCharging) "Carregando" else "Descarregando"

            // Cor do texto de acordo com o nível
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val threshold = prefs.getInt(BatteryMonitorService.PREF_THRESHOLD, BatteryMonitorService.DEFAULT_THRESHOLD)

            val color = when {
                percent <= threshold -> getColor(R.color.battery_low)
                percent <= 50 -> getColor(R.color.battery_medium)
                else -> getColor(R.color.battery_good)
            }
            binding.textBatteryLevel.setTextColor(color)
        }
    }
}
