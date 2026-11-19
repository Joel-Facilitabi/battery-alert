package com.joelassis.batteryalert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager

class BatteryMonitorService : Service() {

    companion object {
        const val CHANNEL_ID_SERVICE = "battery_monitor_service"
        const val CHANNEL_ID_ALERT = "battery_alert"
        const val SERVICE_NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2
        const val DEFAULT_THRESHOLD = 30
        const val PREF_THRESHOLD = "battery_threshold"
        const val PREF_SERVICE_ENABLED = "service_enabled"
        const val PREF_ALERT_SHOWN = "alert_shown"
    }

    private var batteryReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(SERVICE_NOTIFICATION_ID, createServiceNotification())
        registerBatteryReceiver()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        batteryReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Canal silencioso para o serviço em segundo plano
        val serviceChannel = NotificationChannel(
            CHANNEL_ID_SERVICE,
            "Monitoramento de Bateria",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notificação do serviço de monitoramento em segundo plano"
            setShowBadge(false)
        }
        manager.createNotificationChannel(serviceChannel)

        // Canal com som e vibração para o alerta
        val alertChannel = NotificationChannel(
            CHANNEL_ID_ALERT,
            "Alerta de Bateria Baixa",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerta quando a bateria está com carga baixa"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(alertChannel)
    }

    private fun createServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val threshold = prefs.getInt(PREF_THRESHOLD, DEFAULT_THRESHOLD)

        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("Monitorando bateria")
            .setContentText("Alerta configurado para $threshold%")
            .setSmallIcon(R.drawable.ic_battery)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun registerBatteryReceiver() {
        batteryReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    val percent = (level * 100) / scale
                    val isCharging = intent.getIntExtra(
                        BatteryManager.EXTRA_PLUGGED, 0
                    ) != 0

                    checkBatteryLevel(percent, isCharging)
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun checkBatteryLevel(percent: Int, isCharging: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val threshold = prefs.getInt(PREF_THRESHOLD, DEFAULT_THRESHOLD)
        val alertShown = prefs.getBoolean(PREF_ALERT_SHOWN, false)

        if (percent <= threshold && !isCharging && !alertShown) {
            showBatteryAlert(percent)
            prefs.edit().putBoolean(PREF_ALERT_SHOWN, true).apply()
        } else if (percent > threshold || isCharging) {
            // Reset do alerta quando carregar ou subir acima do limite
            dismissAlert()
            prefs.edit().putBoolean(PREF_ALERT_SHOWN, false).apply()
        }

        // Atualiza notificação do serviço com nível atual
        updateServiceNotification(percent, threshold)
    }

    private fun showBatteryAlert(percent: Int) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acorda a tela
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "BatteryAlert:WakeLock"
        )
        wakeLock.acquire(10_000L) // 10 segundos

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setContentTitle("⚡ Bateria Baixa!")
            .setContentText("Bateria em $percent% — Conecte o carregador para usar o BYD!")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Sua bateria está em $percent%.\n\nConecte o carregador para garantir que você consiga ligar o BYD!"))
            .setSmallIcon(R.drawable.ic_battery_alert)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun dismissAlert() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(ALERT_NOTIFICATION_ID)
    }

    private fun updateServiceNotification(currentPercent: Int, threshold: Int) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("Bateria: $currentPercent%")
            .setContentText("Alerta configurado para $threshold%")
            .setSmallIcon(R.drawable.ic_battery)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(SERVICE_NOTIFICATION_ID, notification)
    }
}
