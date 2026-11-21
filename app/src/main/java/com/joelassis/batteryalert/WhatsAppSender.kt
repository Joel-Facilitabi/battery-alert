package com.joelassis.batteryalert

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Envia mensagens de WhatsApp para o próprio usuário pelo serviço gratuito CallMeBot.
 * O usuário ativa o serviço uma vez no WhatsApp e recebe uma chave (APIKey) pessoal.
 */
object WhatsAppSender {

    const val PREF_WA_ENABLED = "wa_enabled"
    const val PREF_WA_PHONE = "wa_phone"
    const val PREF_WA_APIKEY = "wa_apikey"
    const val SETUP_URL = "https://www.callmebot.com/blog/free-api-whatsapp-messages/"

    fun isEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(PREF_WA_ENABLED, false) &&
                !prefs.getString(PREF_WA_PHONE, "").isNullOrBlank() &&
                !prefs.getString(PREF_WA_APIKEY, "").isNullOrBlank()
    }

    fun send(context: Context, text: String, onResult: ((Boolean, String) -> Unit)? = null) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val phone = prefs.getString(PREF_WA_PHONE, "")?.trim().orEmpty()
        val apiKey = prefs.getString(PREF_WA_APIKEY, "")?.trim().orEmpty()

        if (phone.isEmpty() || apiKey.isEmpty()) {
            onResult?.invoke(false, "Preencha o número e a chave do WhatsApp")
            return
        }

        Thread {
            var ok = false
            var message = ""
            for (attempt in 1..3) {
                try {
                    val url = URL(
                        "https://api.callmebot.com/whatsapp.php" +
                                "?phone=" + URLEncoder.encode(phone, "UTF-8") +
                                "&apikey=" + URLEncoder.encode(apiKey, "UTF-8") +
                                "&text=" + URLEncoder.encode(text, "UTF-8")
                    )
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 20_000
                    val code = conn.responseCode
                    val stream = if (code < 400) conn.inputStream else conn.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty().lowercase()
                    conn.disconnect()

                    if (code == 200 && !body.contains("invalid")) {
                        ok = true
                        message = "Mensagem enviada. Confira seu WhatsApp."
                        break
                    }
                    if (body.contains("apikey") || body.contains("invalid")) {
                        message = "Chave (APIKey) inválida para esse número"
                        break // não adianta tentar de novo
                    }
                    message = "Falha ao enviar (código $code)"
                } catch (e: Exception) {
                    message = "Sem conexão com a internet"
                }
                if (attempt < 3) Thread.sleep(20_000)
            }
            onResult?.let { callback ->
                Handler(Looper.getMainLooper()).post { callback(ok, message) }
            }
        }.start()
    }
}
