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
data class RegistrationResult(val username: String, val recoveryCode: String)

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
        val connection = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
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
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = if (response.isBlank()) JSONObject() else JSONObject(response)
            if (status !in 200..299) {
                val message = json.optString("msg").ifBlank {
                    json.optString("message").ifBlank {
                        json.optString("error_description").ifBlank { json.optString("error") }
                    }
                }
                throw IllegalStateException(message.ifBlank { "Error de conexión ($status)" })
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    fun register(context: Context, username: String, email: String, password: String, callback: (Result<RegistrationResult>) -> Unit) {
        Thread {
            try {
                val result = request(
                    "/functions/v1/register-user",
                    "POST",
                    JSONObject()
                        .put("username", username.trim())
                        .put("email", email.trim().lowercase())
                        .put("password", password)
                )
                val code = result.optString("recovery_code")
                if (code.isBlank()) throw IllegalStateException("La cuenta se creó, pero no recibimos el código de recuperación. Contacta con soporte antes de continuar.")
                val registration = RegistrationResult(
                    result.optString("username", username.trim()),
                    code
                )
                mainHandler.post { callback(Result.success(registration)) }
            } catch (e: Exception) {
                mainHandler.post { callback(Result.failure(e)) }
            }
        }.start()
    }

    fun login(context: Context, email: String, password: String, callback: (Result<UserSession>) -> Unit) {
        Thread {
            try {
                val result = request(
                    "/auth/v1/token?grant_type=password",
                    "POST",
                    JSONObject().put("email", email.trim()).put("password", password)
                )
                val user = result.optJSONObject("user")
                val session = UserSession(
                    username = user?.optJSONObject("user_metadata")?.optString("username")?.takeIf { !it.isNullOrBlank() }
                        ?: email.substringBefore("@"),
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

    fun sendPasswordRecoveryCode(email: String, callback: (Result<String>) -> Unit) {
        Thread {
            try {
                request(
                    "/auth/v1/recover",
                    "POST",
                    JSONObject().put("email", email.trim().lowercase())
                )
                mainHandler.post {
                    callback(Result.success("Si el correo está registrado, recibirás un código de 6 cifras. Revisa también Spam."))
                }
            } catch (e: Exception) {
                mainHandler.post { callback(Result.failure(e)) }
            }
        }.start()
    }

    fun resetPasswordWithEmailCode(
        email: String,
        code: String,
        newPassword: String,
        callback: (Result<String>) -> Unit
    ) {
        Thread {
            try {
                val verified = request(
                    "/auth/v1/verify",
                    "POST",
                    JSONObject()
                        .put("email", email.trim().lowercase())
                        .put("token", code.filter { it.isDigit() })
                        .put("type", "recovery")
                )
                val accessToken = verified.optString("access_token")
                if (accessToken.isBlank()) {
                    throw IllegalStateException("El código no es válido o ha caducado. Solicita uno nuevo.")
                }

                request(
                    "/auth/v1/user",
                    "PUT",
                    JSONObject().put("password", newPassword),
                    accessToken
                )

                try {
                    request("/auth/v1/logout", "POST", JSONObject(), accessToken)
                } catch (_: Exception) {
                    // La contraseña ya quedó actualizada; el cierre de la sesión temporal es secundario.
                }

                mainHandler.post {
                    callback(Result.success("Contraseña actualizada. Ya puedes iniciar sesión con tu nueva contraseña."))
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback(Result.failure(IllegalStateException(
                        e.message?.takeIf { it.isNotBlank() } ?: "No se pudo recuperar la cuenta. Revisa el código e inténtalo de nuevo."
                    )))
                }
            }
        }.start()
    }

    fun restore(context: Context, callback: (UserSession?) -> Unit) {
        Thread {
            val prefs = securePrefs(context)
            val refresh = prefs.getString("refresh_token", null)
            val cachedAccess = prefs.getString("access_token", null)
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
                UserSession(username, cachedAccess.orEmpty(), refresh)
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
                mainHandler.post { callback() }
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
