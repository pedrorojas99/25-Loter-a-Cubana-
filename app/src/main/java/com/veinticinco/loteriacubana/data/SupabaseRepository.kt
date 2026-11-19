package com.veinticinco.loteriacubana.data

import android.os.Handler
import android.os.Looper
import com.veinticinco.loteriacubana.BuildConfig
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class ResultadoPublico(
    val fecha: String,
    val juego: String,
    val turno: String,
    val numero: String,
    val fireball: String?
)

object SupabaseRepository {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun cargarResultados(callback: (Result<List<ResultadoPublico>>) -> Unit) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') +
                    "/rest/v1/lottery_draws?select=draw_date,game,period,winning_number,fireball" +
                    "&verified=eq.true&order=draw_date.desc,published_at.desc&limit=50"
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                    setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_PUBLISHABLE_KEY}")
                    setRequestProperty("Accept", "application/json")
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (status !in 200..299) {
                    throw IllegalStateException("Supabase respondió HTTP $status")
                }
                val array = JSONArray(body)
                val rows = (0 until array.length()).map { index ->
                    val row = array.getJSONObject(index)
                    ResultadoPublico(
                        fecha = row.optString("draw_date"),
                        juego = row.optString("game"),
                        turno = row.optString("period"),
                        numero = row.optString("winning_number"),
                        fireball = row.optString("fireball").takeIf { it.isNotBlank() && it != "null" }
                    )
                }
                mainHandler.post { callback(Result.success(rows)) }
            } catch (error: Exception) {
                mainHandler.post { callback(Result.failure(error)) }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }
}
