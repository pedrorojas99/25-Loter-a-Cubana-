package com.veinticinco.loteriacubana

import android.os.Bundle
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
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
import com.veinticinco.loteriacubana.data.SupabaseRepository
import com.veinticinco.loteriacubana.data.AuthRepository
import com.veinticinco.loteriacubana.data.UserSession

private val Fondo = Color(0xFFF1F5F8)
private val Tinta = Color(0xFF263746)
private val Suave = Color(0xFF718096)
private val Azul = Color(0xFF648CA6)

private enum class Pantalla { INICIO, ESTADISTICAS, HISTORIAL, BILLETERA, JUGAR }
private enum class OperacionBilletera { DEPOSITO, RETIRO }
private enum class TipoJugada(val etiqueta: String, val cifras: Int) {
    NUMERO("Número", 2), CENTENA("Centena", 3)
}
private enum class Turno(val etiqueta: String) { MEDIODIA("Mediodía"), NOCHE("Noche") }

private data class JugadaGuardada(val fecha: String, val selecciones: List<String>)

private fun preferenciasJugada(context: Context): SharedPreferences =
    context.getSharedPreferences("25_loteria_mis_jugadas", Context.MODE_PRIVATE)

private fun guardarBoleto(context: Context, selecciones: List<String>) {
    val prefs = preferenciasJugada(context)
    val registros = JSONArray(prefs.getString("boletos", "[]") ?: "[]")
    val nuevo = JSONObject().apply {
        put("fecha", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()))
        put("selecciones", JSONArray(selecciones))
    }
    registros.put(nuevo)
    prefs.edit().putString("boletos", registros.toString()).apply()
}

private fun cargarBoletos(context: Context): List<JugadaGuardada> {
    val registros = JSONArray(preferenciasJugada(context).getString("boletos", "[]") ?: "[]")
    return (0 until registros.length()).mapNotNull { index ->
        val item = registros.optJSONObject(index) ?: return@mapNotNull null
        val nums = item.optJSONArray("selecciones") ?: JSONArray()
        val selecciones = (0 until nums.length()).mapNotNull { nums.optString(it).takeIf { value -> value.isNotBlank() } }
        if (selecciones.isEmpty()) null else JugadaGuardada(item.optString("fecha"), selecciones)
    }.reversed()
}

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
                        NavigationBarItem(selected = pantalla == Pantalla.HISTORIAL, onClick = { pantalla = Pantalla.HISTORIAL }, icon = { Icon(Icons.Filled.History, contentDescription = "Historial") }, label = { Text("Historial") })
                        NavigationBarItem(selected = pantalla == Pantalla.BILLETERA, onClick = { pantalla = Pantalla.BILLETERA }, icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Billetera") }, label = { Text("Billetera") })
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (pantalla) {
                        Pantalla.INICIO -> Inicio(abrirJugar = { pantalla = Pantalla.JUGAR })
                        Pantalla.ESTADISTICAS -> Estadisticas()
                        Pantalla.HISTORIAL -> Historial()
                        Pantalla.BILLETERA -> Billetera()
                        Pantalla.JUGAR -> Jugar(volver = { pantalla = Pantalla.INICIO })
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
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }

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
                Text(if (creandoCuenta) "Crear cuenta" else "Iniciar sesión", color = Tinta, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Tu cuenta guarda tus jugadas y tu historial.", color = Suave, fontSize = 13.sp)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; mensaje = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre de usuario") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; mensaje = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                if (creandoCuenta) {
                    Text("Usa 3–20 caracteres para el usuario y una contraseña de al menos 8 caracteres.", color = Suave, fontSize = 12.sp)
                }
                if (mensaje != null) Text(mensaje!!, color = if (mensaje!!.startsWith("Cuenta creada")) Azul else Color(0xFFB45309), fontSize = 13.sp)
                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            mensaje = "Escribe tu nombre de usuario y contraseña."
                        } else {
                            cargando = true
                            if (creandoCuenta) {
                                AuthRepository.register(context, username, password) { result ->
                                    cargando = false
                                    result.onSuccess {
                                        creandoCuenta = false
                                        password = ""
                                        mensaje = "Cuenta creada. Ya puedes iniciar sesión."
                                    }.onFailure { mensaje = it.message ?: "No se pudo crear la cuenta." }
                                }
                            } else {
                                AuthRepository.login(context, username, password) { result ->
                                    cargando = false
                                    result.onSuccess(onAuthenticated)
                                        .onFailure { mensaje = "Usuario o contraseña incorrectos, o no hay conexión." }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !cargando
                ) {
                    if (cargando) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(if (creandoCuenta) "Crear cuenta" else "Entrar")
                }
                TextButton(
                    onClick = {
                        creandoCuenta = !creandoCuenta
                        mensaje = null
                        password = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (creandoCuenta) "Ya tengo una cuenta · Iniciar sesión" else "No tengo cuenta · Registrarme")
                }
            }
        }
        Text("La sesión se guarda cifrada en este teléfono.", Modifier.padding(top = 18.dp), color = Suave, fontSize = 12.sp)
    }
}

@Composable
private fun Inicio(abrirJugar: () -> Unit) {
    var resultados by remember { mutableStateOf<List<ResultadoPublico>?>(null) }
    var errorConexion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        SupabaseRepository.cargarResultados { respuesta ->
            respuesta.onSuccess { resultados = it; errorConexion = null }
                .onFailure { errorConexion = "No se pudo conectar. Se intentará de nuevo al abrir la pantalla." }
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
            Sorteo("Pick 2", Modifier.weight(1f))
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
private fun Jugar(volver: () -> Unit) {
    var tipo by remember { mutableStateOf(TipoJugada.NUMERO) }
    var turno by remember { mutableStateOf(Turno.MEDIODIA) }
    var numero by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf("") }
    val boleto = remember { mutableStateListOf<String>() }
    val context = LocalContext.current
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
                Text(if (tipo == TipoJugada.NUMERO) "Número de 2 cifras (Pick 2)" else "Centena de 3 cifras (Pick 3)", color = Suave, fontSize = 13.sp)
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
                            guardarBoleto(context, boleto.toList())
                            boleto.clear()
                            aviso = "Boleto guardado en Mis jugadas."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Guardar boleto en Mis jugadas") }
                    OutlinedButton(onClick = { boleto.clear(); aviso = "Boleto limpiado." }, modifier = Modifier.fillMaxWidth()) {
                        Text("Vaciar boleto")
                    }
                }
            }
        }
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Fondo)) {
            Text(
                "Importante: este boleto solo guarda selecciones en esta pantalla. No es una apuesta válida, no se envía a Florida Lottery y no mueve dinero. Las apuestas reales requieren autorización y un proveedor legal.",
                Modifier.padding(14.dp), color = Suave, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun Estadisticas() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Estadísticas", color = Tinta, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Análisis de Pick 2 y Pick 3 de Miami", color = Suave)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Frecuencia de números", color = Tinta, fontWeight = FontWeight.Bold)
                Text("Las frecuencias, números más repetidos y menos frecuentes aparecerán cuando se cargue el historial oficial.", color = Suave, fontSize = 13.sp)
                Text("Aún no hay suficientes resultados verificados.", color = Azul, fontWeight = FontWeight.SemiBold)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tendencias", color = Tinta, fontWeight = FontWeight.Bold)
                Text("Se mostrarán estadísticas descriptivas; no garantizan resultados futuros ni premios.", color = Suave, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Historial() {
    val context = LocalContext.current
    var seccion by remember { mutableStateOf("Sorteos") }
    val jugadas = remember { cargarBoletos(context) }
    var resultados by remember { mutableStateOf<List<ResultadoPublico>?>(null) }
    var errorConexion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        SupabaseRepository.cargarResultados { respuesta ->
            respuesta.onSuccess { resultados = it; errorConexion = null }
                .onFailure { errorConexion = "No se pudieron cargar los sorteos. Comprueba tu conexión a Internet." }
        }
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Historial", color = Tinta, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = seccion == "Sorteos", onClick = { seccion = "Sorteos" }, label = { Text("Sorteos") })
            FilterChip(selected = seccion == "Mis jugadas", onClick = { seccion = "Mis jugadas" }, label = { Text("Mis jugadas") })
        }
        if (seccion == "Sorteos") {
            Text("Resultados verificados de Florida Lottery · Miami", color = Suave)
            when {
                errorConexion != null -> Text(errorConexion!!, color = Suave, fontSize = 13.sp)
                resultados == null -> Text("Cargando historial desde la nube…", color = Suave, fontSize = 13.sp)
                resultados!!.isEmpty() -> Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("La conexión está lista", color = Tinta, fontWeight = FontWeight.Bold)
                        Text("Todavía no hay sorteos verificados en la base de datos. Cuando se publiquen y se sincronicen, aparecerán aquí.", color = Suave, fontSize = 13.sp)
                    }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    resultados!!.forEach { ResultadoFila(it) }
                }
            }
        } else {
            Text("Boletos guardados en este teléfono", color = Suave)
            if (jugadas.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Text("Todavía no tienes boletos guardados. Crea un boleto desde Inicio y pulsa «Guardar boleto en Mis jugadas».", Modifier.padding(18.dp), color = Suave, fontSize = 13.sp)
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${jugadas.size} boletos guardados", color = Azul, fontWeight = FontWeight.SemiBold)
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

@Composable
private fun ResultadoFila(resultado: ResultadoPublico) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text((if (resultado.juego == "PICK_2") "Pick 2" else "Pick 3") + " · " +
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
private fun Sorteo(nombre: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(nombre, color = Azul, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Mediodía y noche", color = Suave, fontSize = 12.sp)
            Text("Pendiente de conexión", color = Suave, fontSize = 11.sp)
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
