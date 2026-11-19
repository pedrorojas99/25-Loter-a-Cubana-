package com.veinticinco.loteriacubana.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.veinticinco.loteriacubana.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UserSession(val username: String, val accessToken: String, val refreshToken: String)

object AuthRepository {
    private const val PREFS = "secure_25_user_session"
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun securePrefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREFS,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    private fun request(path: String, method: String, body: JSONObject, bearer: String? = null): JSONObject {
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = if (response.isBlank()) JSONObject() else JSONObject(response)
            if (status !in 200..299) {
                val message = json.optString("msg").ifBlank {
                    json.optString("message").ifBlank { json.optString("error_description").ifBlank { json.optString("error") } }
                }
                throw IllegalStateException(message.ifBlank { "Error de conexión ($status)" })
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    fun register(context: Context, username: String, password: String, callback: (Result<String>) -> Unit) {
        Thread {
            try {
                val result = request(
                    "/functions/v1/register-user",
                    "POST",
                    JSONObject().put("username", username.trim()).put("password", password)
                )
                mainHandler.post { callback(Result.success(result.optString("message", "Cuenta creada. Ya puedes iniciar sesión."))) }
            } catch (e: Exception) {
                mainHandler.post { callback(Result.failure(e)) }
            }
        }.start()
    }

    fun login(context: Context, username: String, password: String, callback: (Result<UserSession>) -> Unit) {
        Thread {
            try {
                val normalized = username.trim().lowercase()
                val email = "$normalized@accounts.25loteria.invalid"
                val result = request(
                    "/auth/v1/token?grant_type=password",
                    "POST",
                    JSONObject().put("email", email).put("password", password)
                )
                val session = UserSession(
                    username = result.optJSONObject("user")?.optJSONObject("user_metadata")?.optString("username")?.takeIf { !it.isNullOrBlank() } ?: username.trim(),
                    accessToken = result.getString("access_token"),
                    refreshToken = result.getString("refresh_token")
                )
                save(context, session)
                mainHandler.post { callback(Result.success(session)) }
            } catch (e: Exception) {
                mainHandler.post { callback(Result.failure(e)) }
            }
        }.start()
    }

    fun restore(context: Context, callback: (UserSession?) -> Unit) {
        Thread {
            val prefs = securePrefs(context)
            val refresh = prefs.getString("refresh_token", null)
            val username = prefs.getString("username", null)
            val restored = if (refresh.isNullOrBlank() || username.isNullOrBlank()) null else try {
                val result = request(
                    "/auth/v1/token?grant_type=refresh_token",
                    "POST",
                    JSONObject().put("refresh_token", refresh)
                )
                UserSession(username, result.getString("access_token"), result.getString("refresh_token")).also {
                    save(context, it)
                }
            } catch (_: Exception) {
                clear(context)
                null
            }
            mainHandler.post { callback(restored) }
        }.start()
    }

    fun logout(context: Context, session: UserSession, callback: () -> Unit) {
        Thread {
            try {
                request("/auth/v1/logout", "POST", JSONObject(), session.accessToken)
            } catch (_: Exception) {
                // La sesión local se elimina incluso si no hay conexión.
            } finally {
                clear(context)
                mainHandler.post(callback)
            }
        }.start()
    }

    private fun save(context: Context, session: UserSession) {
        securePrefs(context).edit()
            .putString("username", session.username)
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .apply()
    }

    private fun clear(context: Context) {
        securePrefs(context).edit().clear().apply()
    }
}
