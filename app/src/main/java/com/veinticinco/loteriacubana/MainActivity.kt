package com.veinticinco.loteriacubana

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Fondo = Color(0xFFF1F5F8)
private val Tinta = Color(0xFF263746)
private val Suave = Color(0xFF718096)
private val Azul = Color(0xFF648CA6)

private enum class Pantalla { INICIO, BILLETERA, JUGAR }
private enum class OperacionBilletera { DEPOSITO, RETIRO }
private enum class TipoJugada(val etiqueta: String, val cifras: Int) {
    NUMERO("Número", 2), CENTENA("Centena", 3)
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
    var pantalla by remember { mutableStateOf(Pantalla.INICIO) }
    Surface(Modifier.fillMaxSize(), color = Fondo) {
        when (pantalla) {
            Pantalla.INICIO -> Inicio(
                abrirBilletera = { pantalla = Pantalla.BILLETERA },
                abrirJugar = { pantalla = Pantalla.JUGAR }
            )
            Pantalla.BILLETERA -> Billetera(volver = { pantalla = Pantalla.INICIO })
            Pantalla.JUGAR -> Jugar(volver = { pantalla = Pantalla.INICIO })
        }
    }
}

@Composable
private fun Inicio(abrirBilletera: () -> Unit, abrirJugar: () -> Unit) {
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
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Jugar número o centena", color = Tinta, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Prepara tus selecciones para Pick 2 o Pick 3.", color = Suave, fontSize = 13.sp)
                Button(onClick = abrirJugar, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("Crear boleto")
                }
                Text("Modo de prueba: no se reciben apuestas ni dinero.", color = Suave, fontSize = 11.sp)
            }
        }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mi billetera", color = Tinta, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("La billetera permanecerá desactivada hasta integrar un proveedor de pagos autorizado.", color = Suave, fontSize = 13.sp)
                OutlinedButton(onClick = abrirBilletera, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("Ver billetera")
                }
            }
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
                Text("Los resultados oficiales aparecerán aquí cuando se conecte y verifique la fuente de Florida Lottery.", color = Suave, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("Aún no hay resultados cargados", color = Azul, fontWeight = FontWeight.SemiBold)
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
    val cifras = tipo.cifras

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = volver) { Text("← Volver al inicio") }
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
                    OutlinedButton(onClick = { boleto.clear(); aviso = "Boleto de prueba limpiado." }, modifier = Modifier.fillMaxWidth()) {
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
private fun Billetera(volver: () -> Unit) {
    var operacion by remember { mutableStateOf(OperacionBilletera.DEPOSITO) }
    var cantidad by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = volver) { Text("← Volver al inicio") }
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
