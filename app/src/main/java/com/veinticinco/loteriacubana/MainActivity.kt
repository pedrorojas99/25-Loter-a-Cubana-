package com.veinticinco.loteriacubana

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veinticinco.loteriacubana.data.ResultadoPublico
import com.veinticinco.loteriacubana.data.JugadaRemota
import com.veinticinco.loteriacubana.data.SupabaseRepository
import com.veinticinco.loteriacubana.data.AuthRepository
import com.veinticinco.loteriacubana.data.UserSession

private val Fondo = Color(0xFFF1F5F8)
private val Tinta = Color(0xFF263746)
private val Suave = Color(0xFF718096)
private val Azul = Color(0xFF648CA6)

private enum class Pantalla { INICIO, ESTADISTICAS, HISTORIAL, BILLETERA, JUGAR, SOPORTE }

private const val CORREO_SOPORTE = "25loteriacubana@gmail.com"

private fun abrirSoporte(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$CORREO_SOPORTE")
        putExtra(Intent.EXTRA_SUBJECT, "Soporte - 25 Lotería Cubana")
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Enviar correo a soporte"))
    } catch (_: Exception) {
        Toast.makeText(context, "No hay una aplicación de correo configurada en este teléfono.", Toast.LENGTH_LONG).show()
    }
}
private enum class OperacionBilletera { DEPOSITO, RETIRO }
private enum class TipoJugada(val etiqueta: String, val cifras: Int) {
    NUMERO("Fijo", 2), CENTENA("Centena", 3)
}
private enum class Turno(val etiqueta: String) { MEDIODIA("Mediodía"), NOCHE("Noche") }


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Azul, background = Fondo, surface = Color.White)) {
                Aplicacion()
            }
        }
    }
}

@Composable
private fun Aplicacion() {
    val context = LocalContext.current
    var session by remember { mutableStateOf<UserSession?>(null) }
    var checkingSession by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        AuthRepository.restore(context) {
            session = it
            checkingSession = false
        }
    }

    when {
        checkingSession -> Box(Modifier.fillMaxSize().background(Fondo), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Azul)
        }
        session == null -> PantallaAcceso(onAuthenticated = { session = it })
        BuildConfig.ADMIN_APP -> AdminSupportPanel(session!!, cerrarSesion = {
            val current = session
            if (current != null) AuthRepository.logout(context, current) { session = null }
        })
        else -> {
            var pantalla by remember { mutableStateOf(Pantalla.INICIO) }
            Scaffold(
                containerColor = Fondo,
                topBar = {
                    Row(
                        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hola, ${session!!.username}", Modifier.weight(1f), color = Tinta, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { pantalla = Pantalla.SOPORTE }) {
                            Text("Soporte", color = Azul)
                        }
                        TextButton(onClick = {
                            val current = session
                            if (current != null) AuthRepository.logout(context, current) { session = null }
                        }) { Text("Cerrar sesión", color = Azul) }
                    }
                },
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(selected = pantalla == Pantalla.INICIO || pantalla == Pantalla.JUGAR, onClick = { pantalla = Pantalla.INICIO }, icon = { Icon(Icons.Filled.Home, contentDescription = "Inicio") }, label = { Text("Inicio") })
                        NavigationBarItem(selected = pantalla == Pantalla.ESTADISTICAS, onClick = { pantalla = Pantalla.ESTADISTICAS }, icon = { Icon(Icons.Filled.BarChart, contentDescription = "Estadísticas") }, label = { Text("Estadísticas") })
                        NavigationBarItem(selected = pantalla == Pantalla.HISTORIAL, onClick = { pantalla = Pantalla.HISTORIAL }, icon = { Icon(Icons.Filled.History, contentDescription = "Historial") }, label = { Text("Historial de números") })
                        NavigationBarItem(selected = pantalla == Pantalla.BILLETERA, onClick = { pantalla = Pantalla.BILLETERA }, icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Billetera") }, label = { Text("Billetera") })
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (pantalla) {
                        Pantalla.INICIO -> Inicio(abrirJugar = { pantalla = Pantalla.JUGAR })
                        Pantalla.ESTADISTICAS -> Estadisticas()
                        Pantalla.HISTORIAL -> Historial(session!!)
                        Pantalla.BILLETERA -> Billetera()
                        Pantalla.JUGAR -> Jugar(session!!, volver = { pantalla = Pantalla.INICIO })
                        Pantalla.SOPORTE -> SoporteChat(session!!)
                    }
                }
            }
        }
    }
}

@Composable
private fun PantallaAcceso(onAuthenticated: (UserSession) -> Unit) {
    val context = LocalContext.current
    var creandoCuenta by remember { mutableStateOf(false) }
    var recuperando by remember { mutableStateOf(false) }
    var codigoEnviado by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var nuevaPassword by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }

    fun solicitarCodigo() {
        cargando = true
        AuthRepository.sendPasswordRecoveryCode(email) { result ->
            cargando = false
            result.onSuccess {
                codigoEnviado = true
                mensaje = "Si el correo está registrado, recibirás un código de 8 cifras. Revisa Spam."
            }.onFailure {
                mensaje = it.message ?: "No se pudo enviar el código. Inténtalo más tarde."
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Fondo).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("25", color = Azul, fontSize = 58.sp, fontWeight = FontWeight.ExtraBold)
        Text("LOTERÍA CUBANA", color = Tinta, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(22.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    when {
                        recuperando && codigoEnviado -> "Restablecer contraseña"
                        recuperando -> "Recuperar cuenta"
                        creandoCuenta -> "Crear cuenta"
                        else -> "Iniciar sesión"
                    },
                    color = Tinta, fontSize = 22.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    when {
                        recuperando -> "Te enviaremos un código temporal de 8 cifras al correo de tu cuenta. Solo podrás usarlo una vez."
                        creandoCuenta -> "Crea tu cuenta con un correo al que tengas acceso y una contraseña segura."
                        else -> "Inicia sesión con tu correo y contraseña desde cualquier teléfono."
                    },
                    color = Suave, fontSize = 13.sp
                )

                if (creandoCuenta && !recuperando) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; mensaje = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombre de usuario") },
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim(); mensaje = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Correo electrónico") },
                    placeholder = { Text("tunombre@correo.com") },
                    singleLine = true,
                    enabled = !(recuperando && codigoEnviado),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                if (recuperando && codigoEnviado) {
                    OutlinedTextField(
                        value = codigo,
                        onValueChange = { codigo = it.filter(Char::isDigit).take(8); mensaje = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Código de 8 cifras enviado a tu correo") },
                        placeholder = { Text("00000000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = nuevaPassword,
                        onValueChange = { nuevaPassword = it; mensaje = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nueva contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                } else if (!recuperando) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; mensaje = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }

                if (creandoCuenta && !recuperando) {
                    Text("Usuario: 3–20 caracteres. Contraseña: al menos 8 caracteres.", color = Suave, fontSize = 12.sp)
                }

                if (mensaje != null) Text(
                    mensaje!!,
                    color = if (mensaje!!.contains("actualizada") || mensaje!!.contains("creó") || mensaje!!.contains("registrada")) Azul else Color(0xFFB45309),
                    fontSize = 13.sp
                )

                Button(
                    onClick = {
                        val correoValido = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
                        when {
                            !correoValido -> mensaje = "Escribe un correo electrónico válido."
                            recuperando && !codigoEnviado -> solicitarCodigo()
                            recuperando && (codigo.length != 8 || nuevaPassword.length < 8) ->
                                mensaje = "Introduce el código de 8 cifras y una contraseña de al menos 8 caracteres."
                            recuperando -> {
                                cargando = true
                                AuthRepository.resetPasswordWithEmailCode(email, codigo, nuevaPassword) { result ->
                                    cargando = false
                                    result.onSuccess {
                                        recuperando = false
                                        codigoEnviado = false
                                        codigo = ""
                                        nuevaPassword = ""
                                        password = ""
                                        mensaje = it
                                    }.onFailure { mensaje = it.message ?: "No se pudo recuperar la cuenta." }
                                }
                            }
                            creandoCuenta && (username.length !in 3..20 || !username.matches(Regex("[A-Za-z0-9_.]+")) || password.length < 8) ->
                                mensaje = "Revisa el usuario y usa una contraseña de al menos 8 caracteres."
                            creandoCuenta -> {
                                cargando = true
                                AuthRepository.register(context, username, email, password) { result ->
                                    result.onSuccess {
                                        AuthRepository.login(context, email, password) { loginResult ->
                                            cargando = false
                                            loginResult.onSuccess(onAuthenticated)
                                                .onFailure { mensaje = "La cuenta se creó, pero no pudimos iniciar sesión automáticamente. Comprueba tu conexión e inténtalo." }
                                        }
                                    }.onFailure {
                                        cargando = false
                                        mensaje = it.message ?: "No se pudo crear la cuenta."
                                    }
                                }
                            }
                            password.isBlank() -> mensaje = "Escribe tu contraseña."
                            else -> {
                                cargando = true
                                AuthRepository.login(context, email, password) { result ->
                                    cargando = false
                                    result.onSuccess(onAuthenticated)
                                        .onFailure { mensaje = "No se pudo iniciar sesión. Comprueba el correo y la contraseña." }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !cargando
                ) {
                    if (cargando) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(
                        when {
                            recuperando && codigoEnviado -> "Cambiar contraseña"
                            recuperando -> "Enviar código a Gmail"
                            creandoCuenta -> "Crear cuenta"
                            else -> "Entrar"
                        }
                    )
                }

                if (recuperando && codigoEnviado) {
                    TextButton(
                        onClick = { if (!cargando) solicitarCodigo() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !cargando
                    ) { Text("Reenviar código") }
                }

                if (!recuperando && !creandoCuenta) {
                    TextButton(
                        onClick = { recuperando = true; codigoEnviado = false; mensaje = null; password = "" },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Olvidé mi contraseña") }
                }

                TextButton(
                    onClick = {
                        when {
                            recuperando -> { recuperando = false; codigoEnviado = false; codigo = ""; nuevaPassword = "" }
                            else -> creandoCuenta = !creandoCuenta
                        }
                        mensaje = null
                        password = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            recuperando -> "Volver al inicio de sesión"
                            creandoCuenta -> "Ya tengo una cuenta · Iniciar sesión"
                            else -> "No tengo cuenta · Registrarme"
                        }
                    )
                }
            }
        }
        Text("Tu sesión se guarda cifrada en este teléfono. Puedes usar tu correo para entrar desde otro móvil.", Modifier.padding(top = 18.dp), color = Suave, fontSize = 12.sp)
        TextButton(onClick = { abrirSoporte(context) }) {
            Text("Contactar soporte · $CORREO_SOPORTE", color = Azul, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Inicio(abrirJugar: () -> Unit) {
    var resultados by remember { mutableStateOf<List<ResultadoPublico>?>(null) }
    var errorConexion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        // Verifica los sorteos recientes al abrir Inicio y luego carga el historial guardado.
        SupabaseRepository.cargarUltimosDiezDias { actualizacion ->
            SupabaseRepository.cargarResultados { respuesta ->
                respuesta.onSuccess {
                    resultados = it
                    errorConexion = if (actualizacion.isFailure) "No se pudo verificar el sorteo más reciente; se muestran los resultados guardados." else null
                }.onFailure { errorConexion = "No se pudo conectar. Se intentará de nuevo al abrir la pantalla." }
            }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("25", color = Azul, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold)
                Text("LOTERÍA CUBANA", color = Tinta, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Resultados de Miami · Florida", color = Suave, fontSize = 13.sp)
            }
            Text("FL", Modifier.background(Color.White, RoundedCornerShape(14.dp)).padding(horizontal = 17.dp, vertical = 12.dp), color = Azul, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Text("Sorteos", color = Tinta, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Sorteo("Pick 3", Modifier.weight(1f))
        }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(18.dp)) {
                Text("Últimos resultados", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Tinta)
                Spacer(Modifier.height(8.dp))
                Spacer(Modifier.height(6.dp))
                when {
                    errorConexion != null -> Text(errorConexion!!, color = Suave, fontSize = 13.sp)
                    resultados == null -> Text("Conectando con la base de datos segura…", color = Suave, fontSize = 13.sp)
                    resultados!!.isEmpty() -> Text("Conexión con Supabase correcta. Todavía no hay resultados oficiales verificados guardados.", color = Azul, fontSize = 13.sp)
                    else -> resultados!!.take(4).forEach { ResultadoFila(it) }
                }
            }
        }
        Text("Explora", color = Tinta, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Seccion("Historial", "Consulta sorteos anteriores")
        Seccion("Estadísticas", "Frecuencias, repeticiones y tendencias")
        Seccion("Charada", "Números del 1 al 100 y sus significados")
        Text("Información estadística · Sin garantía de premios", color = Suave, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun Jugar(session: UserSession, volver: () -> Unit) {
    var tipo by remember { mutableStateOf(TipoJugada.NUMERO) }
    var turno by remember { mutableStateOf(Turno.MEDIODIA) }
    var numero by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf("") }
    var guardandoBoleto by remember { mutableStateOf(false) }
    val boleto = remember { mutableStateListOf<String>() }
    val cifras = tipo.cifras

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Crear boleto", color = Tinta, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Elige el tipo de jugada y el turno del sorteo de Miami.", color = Suave, fontSize = 14.sp)
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tipo de jugada", color = Tinta, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TipoJugada.values().forEach { opcion ->
                        if (tipo == opcion) {
                            Button(onClick = { tipo = opcion; numero = ""; aviso = "" }, modifier = Modifier.weight(1f)) { Text(opcion.etiqueta) }
                        } else {
                            OutlinedButton(onClick = { tipo = opcion; numero = ""; aviso = "" }, modifier = Modifier.weight(1f)) { Text(opcion.etiqueta) }
                        }
                    }
                }
                Text(if (tipo == TipoJugada.NUMERO) "Fijo: últimos 2 dígitos del Pick 3" else "Centena: los 3 dígitos del Pick 3", color = Suave, fontSize = 13.sp)
                OutlinedTextField(
                    value = numero,
                    onValueChange = { nuevo ->
                        if (nuevo.length <= cifras && nuevo.all { it in '0'..'9' }) {
                            numero = nuevo
                            aviso = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Escribe $cifras cifras") },
                    placeholder = { Text(if (cifras == 2) "Ej. 07" else "Ej. 025") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text("Los ceros iniciales se conservan.", color = Suave, fontSize = 12.sp)
                Divider(color = Fondo)
                Text("Turno", color = Tinta, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Turno.values().forEach { opcion ->
                        if (turno == opcion) {
                            Button(onClick = { turno = opcion }, modifier = Modifier.weight(1f)) { Text(opcion.etiqueta) }
                        } else {
                            OutlinedButton(onClick = { turno = opcion }, modifier = Modifier.weight(1f)) { Text(opcion.etiqueta) }
                        }
                    }
                }
                Button(
                    onClick = {
                        if (numero.length != cifras) aviso = "Debes escribir exactamente $cifras cifras."
                        else {
                            boleto.add("${tipo.etiqueta}: $numero · ${turno.etiqueta}")
                            numero = ""
                            aviso = "Selección añadida al boleto de prueba."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Añadir selección") }
                if (aviso.isNotBlank()) Text(aviso, color = Azul, fontSize = 12.sp)
            }
        }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tu boleto", color = Tinta, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (boleto.isEmpty()) {
                    Text("Todavía no has añadido selecciones.", color = Suave, fontSize = 13.sp)
                } else {
                    boleto.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}. $item", modifier = Modifier.weight(1f), color = Tinta, fontSize = 14.sp)
                            TextButton(onClick = { boleto.removeAt(index) }) { Text("Quitar") }
                        }
                    }
                    Button(
                        onClick = {
                            guardandoBoleto = true
                            SupabaseRepository.guardarBoleto(session, boleto.toList()) { result ->
                                guardandoBoleto = false
                                result.onSuccess {
                                    boleto.clear()
                                    aviso = "Boleto guardado en tu cuenta. Estará disponible al iniciar sesión en otro teléfono."
                                }.onFailure { aviso = it.message ?: "No se pudo guardar el boleto en la nube." }
                            }
                        },
                        enabled = !guardandoBoleto,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (guardandoBoleto) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Guardar boleto en Mis jugadas")
                    }
                    OutlinedButton(onClick = { boleto.clear(); aviso = "Boleto limpiado." }, modifier = Modifier.fillMaxWidth()) {
                        Text("Vaciar boleto")
                    }
                }
            }
        }
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Fondo)) {
            Text(
                "Importante: el boleto se guarda como historial personal en tu cuenta. No es una apuesta válida, no se envía a Florida Lottery y no mueve dinero. Las apuestas reales requieren autorización y un proveedor legal.",
                Modifier.padding(14.dp), color = Suave, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun Estadisticas() {
    EstadisticasScreen()
}

@Composable
private fun Historial(session: UserSession) {
    var seccion by remember { mutableStateOf("Sorteos") }
    val jugadas = remember { mutableStateListOf<JugadaRemota>() }
    var cargandoJugadas by remember { mutableStateOf(false) }
    var errorJugadas by remember { mutableStateOf<String?>(null) }
    var resultados by remember { mutableStateOf<List<ResultadoPublico>?>(null) }
    var errorConexion by remember { mutableStateOf<String?>(null) }
    var cargandoBusqueda by remember { mutableStateOf(false) }
    var tipoBusqueda by remember { mutableStateOf("Fijo") }
    var numeroBusqueda by remember { mutableStateOf("") }
    var coincidencias by remember { mutableStateOf<List<ResultadoPublico>?>(null) }
    var busquedaRealizada by remember { mutableStateOf(false) }
    var fechaBusqueda by remember { mutableStateOf("") }
    var fechaAplicada by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session.accessToken) {
        cargandoJugadas = true
        SupabaseRepository.cargarMisBoletos(session) { respuesta ->
            cargandoJugadas = false
            respuesta.onSuccess { lista -> jugadas.clear(); jugadas.addAll(lista); errorJugadas = null }
                .onFailure { errorJugadas = it.message ?: "No se pudieron cargar tus boletos." }
        }
        SupabaseRepository.cargarUltimosDiezDias { respuesta ->
            respuesta.onSuccess { resultados = it; errorConexion = null }
                .onFailure { errorConexion = "No se pudieron cargar los sorteos. Comprueba tu conexión a Internet." }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Historial de números", color = Tinta, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = seccion == "Sorteos", onClick = { seccion = "Sorteos" }, label = { Text("Sorteos") })
            FilterChip(selected = seccion == "Mis jugadas", onClick = { seccion = "Mis jugadas" }, label = { Text("Mis jugadas") })
        }
        if (seccion == "Sorteos") {
            Text("Resultados oficiales de Florida Lottery · Desde 2025", color = Suave)
            Text("Buscar por fecha", color = Tinta, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = fechaBusqueda,
                onValueChange = { valor -> if (valor.length <= 10 && valor.all { it.isDigit() || it == '-' }) fechaBusqueda = valor },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Fecha (AAAA-MM-DD)") },
                placeholder = { Text("2025-01-25") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val parsed = runCatching {
                        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                        val date = format.parse(fechaBusqueda)
                        require(date != null && format.format(date) == fechaBusqueda && fechaBusqueda >= "2025-01-01")
                        fechaBusqueda
                    }.getOrNull()
                    if (parsed != null) {
                        fechaAplicada = parsed
                        resultados = null
                        errorConexion = null
                        coincidencias = null
                        busquedaRealizada = false
                        SupabaseRepository.buscarResultadoOficial(parsed) { respuesta ->
                            respuesta.onSuccess { resultados = it; errorConexion = null }
                                .onFailure { errorConexion = "No se pudo buscar esa fecha. Comprueba tu conexión." }
                        }
                    } else errorConexion = "Introduce una fecha válida desde 2025-01-01 (AAAA-MM-DD)."
                }, modifier = Modifier.weight(1f)) { Text("Buscar fecha") }
                OutlinedButton(onClick = {
                    fechaBusqueda = ""
                    fechaAplicada = null
                    errorConexion = null
                    resultados = null
                    coincidencias = null
                    busquedaRealizada = false
                    SupabaseRepository.cargarUltimosDiezDias { respuesta ->
                        respuesta.onSuccess { resultados = it; errorConexion = null }
                            .onFailure { errorConexion = "No se pudieron cargar los sorteos. Comprueba tu conexión." }
                    }
                }, modifier = Modifier.weight(1f)) { Text("Últimos 10 días") }
            }

            Divider(color = Fondo)
            Text("Buscar historial de un número", color = Tinta, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Encuentra todas las fechas en que apareció desde el 1 de enero de 2025.", color = Suave, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tipoBusqueda == "Fijo", onClick = {
                    tipoBusqueda = "Fijo"; numeroBusqueda = ""; coincidencias = null; busquedaRealizada = false
                }, label = { Text("Fijo · 2 cifras") })
                FilterChip(selected = tipoBusqueda == "Centena", onClick = {
                    tipoBusqueda = "Centena"; numeroBusqueda = ""; coincidencias = null; busquedaRealizada = false
                }, label = { Text("Centena · 3 cifras") })
            }
            OutlinedTextField(
                value = numeroBusqueda,
                onValueChange = { valor ->
                    val max = if (tipoBusqueda == "Fijo") 2 else 3
                    if (valor.length <= max && valor.all { it in '0'..'9' }) {
                        numeroBusqueda = valor
                        coincidencias = null
                        busquedaRealizada = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (tipoBusqueda == "Fijo") "Escribe las 2 cifras" else "Escribe las 3 cifras") },
                placeholder = { Text(if (tipoBusqueda == "Fijo") "Ej. 25" else "Ej. 025") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Text("Los ceros iniciales se conservan. En Fijo se buscan los dos últimos dígitos del Pick 3.", color = Suave, fontSize = 11.sp)
            Button(
                onClick = {
                    val required = if (tipoBusqueda == "Fijo") 2 else 3
                    if (numeroBusqueda.length != required) {
                        errorConexion = "Escribe exactamente $required cifras para realizar la búsqueda."
                    } else {
                        cargandoBusqueda = true
                        errorConexion = null
                        resultados = null
                        coincidencias = null
                        busquedaRealizada = false
                        SupabaseRepository.cargarResultados { respuesta ->
                            cargandoBusqueda = false
                            respuesta.onSuccess { todos ->
                                val objetivo = numeroBusqueda
                                coincidencias = todos.filter { resultado ->
                                    if (tipoBusqueda == "Fijo") resultado.numero.takeLast(2) == objetivo
                                    else resultado.numero == objetivo
                                }.sortedWith(compareByDescending<ResultadoPublico> { it.fecha }.thenBy { it.turno })
                                busquedaRealizada = true
                            }.onFailure {
                                errorConexion = "No se pudo consultar el historial completo. Comprueba tu conexión."
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !cargandoBusqueda
            ) {
                if (cargandoBusqueda) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Buscar todas las fechas")
            }
            if (cargandoBusqueda) Text("Consultando el historial completo desde 2025…", color = Suave, fontSize = 13.sp)
            if (busquedaRealizada) {
                val lista = coincidencias.orEmpty()
                Text("${lista.size} coincidencias para $tipoBusqueda $numeroBusqueda", color = Azul, fontWeight = FontWeight.Bold)
                if (lista.isEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Text("No se encontró ese número en los resultados verificados desde 2025.", Modifier.padding(16.dp), color = Suave, fontSize = 13.sp)
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        lista.forEach { resultado ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(resultado.fecha, color = Tinta, fontWeight = FontWeight.SemiBold)
                                        Text(if (resultado.turno == "MIDDAY") "Mediodía" else "Noche", color = Suave, fontSize = 12.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(resultado.numero, color = Azul, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                                        Text(if (tipoBusqueda == "Fijo") "Fijo ${resultado.numero.takeLast(2)}" else "Centena", color = Suave, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Divider(color = Fondo)
            Text("Resultados recientes", color = Tinta, fontWeight = FontWeight.SemiBold)
            when {
                errorConexion != null -> Text(errorConexion!!, color = Color(0xFFB45309), fontSize = 13.sp)
                resultados == null -> Text("Cargando historial desde la nube…", color = Suave, fontSize = 13.sp)
                resultados!!.isEmpty() -> Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sin resultados para mostrar", color = Tinta, fontWeight = FontWeight.Bold)
                        Text(if (fechaAplicada != null) "No hay resultados verificados para $fechaAplicada." else "Todavía no hay sorteos verificados guardados desde 2025.", color = Suave, fontSize = 13.sp)
                    }
                }
                else -> Column(
                    Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    resultados!!.groupBy { it.fecha }.forEach { (fecha, sorteos) -> ResultadoDiaCard(fecha, sorteos) }
                }
            }
        } else {
            Text("Boletos guardados en tu cuenta", color = Suave)
            when {
                cargandoJugadas -> CircularProgressIndicator(color = Azul)
                errorJugadas != null -> {
                    Text(errorJugadas!!, color = Color(0xFFB45309), fontSize = 13.sp)
                    OutlinedButton(onClick = {
                        cargandoJugadas = true
                        SupabaseRepository.cargarMisBoletos(session) { respuesta ->
                            cargandoJugadas = false
                            respuesta.onSuccess { lista -> jugadas.clear(); jugadas.addAll(lista); errorJugadas = null }
                                .onFailure { errorJugadas = it.message ?: "No se pudieron cargar tus boletos." }
                        }
                    }) { Text("Reintentar") }
                }
                jugadas.isEmpty() -> {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                        Text("Todavía no tienes boletos guardados. Crea un boleto desde Inicio y pulsa «Guardar boleto en Mis jugadas».", Modifier.padding(18.dp), color = Suave, fontSize = 13.sp)
                    }
                }
                else -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${jugadas.size} boletos guardados en la nube", color = Azul, fontWeight = FontWeight.SemiBold)
                        jugadas.forEachIndexed { index, jugada ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text("Boleto #${jugadas.size - index}", color = Tinta, fontWeight = FontWeight.Bold)
                                    Text(jugada.fecha, color = Suave, fontSize = 12.sp)
                                    jugada.selecciones.forEach { Text("• $it", color = Tinta, fontSize = 14.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultadoDiaCard(fecha: String, sorteos: List<ResultadoPublico>) {
    val midday = sorteos.firstOrNull { it.turno == "MIDDAY" }?.numero ?: "—"
    val evening = sorteos.firstOrNull { it.turno == "EVENING" }?.numero ?: "—"
    val displayDate = runCatching {
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val output = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        output.format(input.parse(fecha) ?: Date())
    }.getOrDefault(fecha)
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(displayDate, color = Tinta, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Sección del día", color = Suave, modifier = Modifier.weight(1f), fontSize = 14.sp)
                Text(midday, color = Azul, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            }
            Divider(color = Fondo)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Sección de la noche", color = Suave, modifier = Modifier.weight(1f), fontSize = 14.sp)
                Text(evening, color = Azul, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text("Los dos últimos dígitos son el número; los tres dígitos completos son la centena.", color = Suave, fontSize = 11.sp)
        }
    }
}
@Composable
private fun ResultadoFila(resultado: ResultadoPublico) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Pick 3 · " +
                    (if (resultado.turno == "MIDDAY") "Mediodía" else "Noche"),
                    color = Tinta, fontWeight = FontWeight.Bold)
                Text(resultado.fecha, color = Suave, fontSize = 12.sp)
            }
            Text(resultado.numero, color = Azul, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun Billetera() {
    var operacion by remember { mutableStateOf(OperacionBilletera.DEPOSITO) }
    var cantidad by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Mi billetera", color = Tinta, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Saldo disponible", color = Suave, fontSize = 13.sp)
                Text("No disponible", color = Tinta, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("El saldo se mostrará cuando se habilite una cuenta de pagos verificada.", color = Suave, fontSize = 12.sp)
            }
        }
        Text("Operaciones", color = Tinta, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { operacion = OperacionBilletera.DEPOSITO },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (operacion == OperacionBilletera.DEPOSITO) Azul else Color.White, contentColor = if (operacion == OperacionBilletera.DEPOSITO) Color.White else Azul)
            ) { Text("Depositar") }
            OutlinedButton(onClick = { operacion = OperacionBilletera.RETIRO }, modifier = Modifier.weight(1f)) { Text("Retirar") }
        }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (operacion == OperacionBilletera.DEPOSITO) "Solicitar depósito" else "Solicitar retiro", color = Tinta, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                OutlinedTextField(
                    value = cantidad,
                    onValueChange = { nuevo -> if (nuevo.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) cantidad = nuevo },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cantidad") },
                    placeholder = { Text("0.00") },
                    singleLine = true
                )
                Text("Las operaciones aún no están activas. No se enviará dinero ni se realizará ningún cargo desde esta pantalla.", color = Suave, fontSize = 12.sp)
                Button(onClick = { }, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text(if (operacion == OperacionBilletera.DEPOSITO) "Depósitos próximamente" else "Retiros próximamente")
                }
            }
        }
        Text("Seguridad: nunca compartas contraseñas, códigos de verificación ni datos completos de tarjetas en el chat.", color = Suave, fontSize = 12.sp)
    }
}


@Composable
private fun SoporteChat(session: UserSession) {
    val context = LocalContext.current
    var conversations by remember { mutableStateOf<List<com.veinticinco.loteriacubana.data.SupportConversation>>(emptyList()) }
    var selected by remember { mutableStateOf<com.veinticinco.loteriacubana.data.SupportConversation?>(null) }
    var messages by remember { mutableStateOf<List<com.veinticinco.loteriacubana.data.SupportMessage>>(emptyList()) }
    var subject by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadConversations() {
        busy = true
        SupabaseRepository.cargarConversacionesSoporte(session) { result ->
            busy = false
            result.onSuccess { conversations = it; error = null }
                .onFailure { error = it.message ?: "No se pudieron cargar tus conversaciones." }
        }
    }
    fun loadMessages(conversation: com.veinticinco.loteriacubana.data.SupportConversation) {
        selected = conversation
        busy = true
        SupabaseRepository.cargarMensajesSoporte(session, conversation.id) { result ->
            busy = false
            result.onSuccess { messages = it; error = null }
                .onFailure { error = it.message ?: "No se pudieron cargar los mensajes." }
        }
    }
    LaunchedEffect(Unit) { loadConversations() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Centro de soporte", color = Tinta, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Conversa directamente con nuestro equipo.", color = Suave, fontSize = 12.sp)
            }
            TextButton(onClick = { abrirSoporte(context) }) { Text("Correo", color = Azul) }
        }
        if (selected == null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nueva conversación", color = Tinta, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = subject, onValueChange = { subject = it.take(100) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("¿En qué podemos ayudarte?") },
                        placeholder = { Text("Ej. Problema al iniciar sesión") }, singleLine = true)
                    Button(onClick = {
                        busy = true
                        SupabaseRepository.crearConversacionSoporte(session, subject) { result ->
                            busy = false
                            result.onSuccess { subject = ""; loadMessages(it); loadConversations() }
                                .onFailure { error = it.message ?: "No se pudo abrir la conversación." }
                        }
                    }, enabled = !busy && subject.trim().length >= 3, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir chat")
                    }
                }
            }
            Text("Tus conversaciones", color = Tinta, fontWeight = FontWeight.Bold)
            if (busy && conversations.isEmpty()) CircularProgressIndicator(color = Azul)
            if (conversations.isEmpty() && !busy) Text("Todavía no tienes conversaciones. Abre una nueva para escribirnos.", color = Suave, fontSize = 13.sp)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                conversations.forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth(), onClick = { loadMessages(item) },
                        colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.subject, color = Tinta, fontWeight = FontWeight.SemiBold)
                                Text(if (item.status == "open") "Abierta" else "Cerrada", color = Suave, fontSize = 12.sp)
                            }
                            Text("Ver chat  ›", color = Azul, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selected = null; messages = emptyList(); loadConversations() }) { Text("‹ Conversaciones") }
                Text(selected!!.subject, Modifier.weight(1f), color = Tinta, fontWeight = FontWeight.SemiBold, maxLines = 1)
                TextButton(onClick = { selected?.let { loadMessages(it) } }) { Text("Actualizar") }
            }
            if (error != null) Text(error!!, color = Color(0xFFB45309), fontSize = 12.sp)
            Column(Modifier.weight(1f).fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (messages.isEmpty() && !busy) Text("Escribe tu primer mensaje para que nuestro equipo pueda ayudarte.", color = Suave, fontSize = 13.sp)
                messages.forEach { msg ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.senderRole == "user") Arrangement.End else Arrangement.Start) {
                        Column(Modifier.widthIn(max = 290.dp).background(
                            if (msg.senderRole == "user") Color(0xFFE7F0F6) else Fondo, RoundedCornerShape(14.dp)
                        ).padding(11.dp)) {
                            Text(if (msg.senderRole == "user") "Tú" else "Soporte", color = Azul, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(msg.body, color = Tinta, fontSize = 14.sp)
                            Text(msg.createdAt.replace("T", " ").take(16), color = Suave, fontSize = 9.sp)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = draft, onValueChange = { draft = it.take(2000) }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe tu mensaje…") }, maxLines = 4)
                Button(onClick = {
                    busy = true
                    SupabaseRepository.enviarMensajeSoporte(session, selected!!.id, draft) { result ->
                        busy = false
                        result.onSuccess { draft = ""; selected?.let { loadMessages(it) }; loadConversations() }
                            .onFailure { error = it.message ?: "No se pudo enviar el mensaje." }
                    }
                }, enabled = !busy && draft.trim().isNotEmpty()) { Text("Enviar") }
            }
            Text("No compartas contraseñas, códigos de recuperación ni datos bancarios.", color = Suave, fontSize = 11.sp)
        }
        if (error != null && selected == null) Text(error!!, color = Color(0xFFB45309), fontSize = 12.sp)
        if (busy && selected != null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Azul)
    }
}

@Composable
private fun AdminSupportPanel(session: UserSession, cerrarSesion: () -> Unit) {
    var conversations by remember { mutableStateOf<List<com.veinticinco.loteriacubana.data.SupportConversation>>(emptyList()) }
    var selected by remember { mutableStateOf<com.veinticinco.loteriacubana.data.SupportConversation?>(null) }
    var messages by remember { mutableStateOf<List<com.veinticinco.loteriacubana.data.SupportMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadInbox() {
        busy = true
        SupabaseRepository.cargarConversacionesSoporte(session, admin = true) { result ->
            busy = false
            result.onSuccess { conversations = it; error = null }
                .onFailure { error = it.message ?: "No se pudo cargar la bandeja de soporte." }
        }
    }
    fun loadThread(item: com.veinticinco.loteriacubana.data.SupportConversation) {
        selected = item
        busy = true
        SupabaseRepository.cargarMensajesSoporte(session, item.id) { result ->
            busy = false
            result.onSuccess { messages = it; error = null }
                .onFailure { error = it.message ?: "No se pudo cargar la conversación." }
        }
    }
    LaunchedEffect(Unit) { loadInbox() }

    Column(Modifier.fillMaxSize().background(Fondo).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("25 · Soporte Admin", color = Tinta, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Bandeja privada de atención al usuario", color = Suave, fontSize = 12.sp)
            }
            TextButton(onClick = cerrarSesion) { Text("Salir", color = Azul) }
        }
        if (selected == null) {
            Button(onClick = { loadInbox() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Actualizar bandeja") }
            if (conversations.isEmpty() && !busy) Text("No hay conversaciones o esta cuenta todavía no tiene permisos de administrador.", color = Suave, fontSize = 13.sp)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                conversations.forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth(), onClick = { loadThread(item) },
                        colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(item.subject, color = Tinta, fontWeight = FontWeight.SemiBold)
                            Text("Usuario: " + item.userId.take(8) + " · " + if (item.status == "open") "Abierta" else "Cerrada",
                                color = Suave, fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selected = null; messages = emptyList(); loadInbox() }) { Text("‹ Bandeja") }
                Text(selected!!.subject, Modifier.weight(1f), color = Tinta, fontWeight = FontWeight.SemiBold, maxLines = 1)
                TextButton(onClick = { selected?.let { loadThread(it) } }) { Text("Actualizar") }
            }
            Column(Modifier.weight(1f).fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                messages.forEach { msg ->
                    Column(Modifier.fillMaxWidth().background(
                        if (msg.senderRole == "admin") Color(0xFFE7F0F6) else Fondo, RoundedCornerShape(12.dp)
                    ).padding(10.dp)) {
                        Text(if (msg.senderRole == "admin") "Soporte" else "Usuario", color = Azul, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(msg.body, color = Tinta, fontSize = 14.sp)
                        Text(msg.createdAt.replace("T", " ").take(16), color = Suave, fontSize = 9.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = draft, onValueChange = { draft = it.take(2000) }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Responder al usuario…") }, maxLines = 4)
                Button(onClick = {
                    busy = true
                    SupabaseRepository.enviarMensajeSoporte(session, selected!!.id, draft, admin = true) { result ->
                        busy = false
                        result.onSuccess { draft = ""; selected?.let { loadThread(it) }; loadInbox() }
                            .onFailure { error = it.message ?: "No se pudo enviar la respuesta." }
                    }
                }, enabled = !busy && draft.trim().isNotEmpty()) { Text("Enviar") }
            }
        }
        if (error != null) Text(error!!, color = Color(0xFFB45309), fontSize = 12.sp)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Azul)
    }
}


@Composable
private fun Sorteo(nombre: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(nombre, color = Azul, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Mediodía y noche", color = Suave, fontSize = 12.sp)
            Text("Resultados oficiales de Florida Lottery", color = Suave, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Seccion(titulo: String, detalle: String) {
    Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
            Text(titulo, color = Tinta, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(detalle, color = Suave, fontSize = 12.sp)
        }
    }
}
