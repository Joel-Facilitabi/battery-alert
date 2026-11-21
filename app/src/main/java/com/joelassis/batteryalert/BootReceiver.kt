package com.joelassis.batteryalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Religa o monitoramento ao reiniciar o celular e depois de instalar uma versão nova
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val enabled = prefs.getBoolean(BatteryMonitorService.PREF_SERVICE_ENABLED, false)

            if (enabled) {
                val serviceIntent = Intent(context, BatteryMonitorService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
