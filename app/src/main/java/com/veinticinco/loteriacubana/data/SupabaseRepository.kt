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
    private const val PAGE_SIZE = 1000

    // Consulta la vista diaria agrupada de Pick 3 para el historial compacto.
    fun cargarHistorialDiario(fecha: String? = null, callback: (Result<List<ResultadoPublico>>) -> Unit) {
        Thread {
            try {
                var endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') +
                    "/rest/v1/lottery_daily_history?select=draw_date,midday_number,evening_number,midday_fireball,evening_fireball"
                if (!fecha.isNullOrBlank()) endpoint += "&draw_date=eq.$fecha"
                endpoint += "&order=draw_date.desc&limit=1000"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                    setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_PUBLISHABLE_KEY}")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    if (status !in 200..299) throw IllegalStateException("Supabase respondió HTTP $status")
                    val array = JSONArray(body)
                    val rows = mutableListOf<ResultadoPublico>()
                    for (index in 0 until array.length()) {
                        val row = array.getJSONObject(index)
                        val drawDate = row.optString("draw_date")
                        val midday = row.optString("midday_number").takeIf { it.isNotBlank() && it != "null" }
                        val evening = row.optString("evening_number").takeIf { it.isNotBlank() && it != "null" }
                        if (midday != null) rows += ResultadoPublico(drawDate, "PICK_3", "MIDDAY", midday, row.optString("midday_fireball").takeIf { it.isNotBlank() && it != "null" })
                        if (evening != null) rows += ResultadoPublico(drawDate, "PICK_3", "EVENING", evening, row.optString("evening_fireball").takeIf { it.isNotBlank() && it != "null" })
                    }
                    mainHandler.post { callback(Result.success(rows)) }
                } finally { connection.disconnect() }
            } catch (error: Exception) { mainHandler.post { callback(Result.failure(error)) } }
        }.start()
    }
    // Historial completo desde 2025, con filtro opcional por fecha.
    // La paginación evita truncar los resultados al límite REST de una respuesta.
    fun cargarResultados(fecha: String? = null, callback: (Result<List<ResultadoPublico>>) -> Unit) {
        Thread {
            val allRows = mutableListOf<ResultadoPublico>()
            var from = 0
            try {
                while (true) {
                    var endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') +
                        "/rest/v1/lottery_draws?select=draw_date,game,period,winning_number,fireball" +
                        "&verified=eq.true"
                    endpoint += if (!fecha.isNullOrBlank()) "&draw_date=eq.$fecha" else "&draw_date=gte.2025-01-01"
                    endpoint += "&order=draw_date.desc,published_at.desc,game.asc,period.asc&limit=$PAGE_SIZE"

                    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15000
                        readTimeout = 20000
                        setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                        setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_PUBLISHABLE_KEY}")
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Range-Unit", "items")
                        setRequestProperty("Range", "$from-${from + PAGE_SIZE - 1}")
                    }
                    try {
                        val status = connection.responseCode
                        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                        val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        if (status !in 200..299) throw IllegalStateException("Supabase respondió HTTP $status")
                        val array = JSONArray(body)
                        for (index in 0 until array.length()) {
                            val row = array.getJSONObject(index)
                            allRows += ResultadoPublico(
                                fecha = row.optString("draw_date"),
                                juego = row.optString("game"),
                                turno = row.optString("period"),
                                numero = row.optString("winning_number"),
                                fireball = row.optString("fireball").takeIf { it.isNotBlank() && it != "null" }
                            )
                        }
                        if (array.length() < PAGE_SIZE) break
                        from += PAGE_SIZE
                    } finally {
                        connection.disconnect()
                    }
                }
                mainHandler.post { callback(Result.success(allRows)) }
            } catch (error: Exception) {
                mainHandler.post { callback(Result.failure(error)) }
            }
        }.start()
    }
}
