package com.veinticinco.loteriacubana

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Fondo = Color(0xFFF1F5F8)
private val Tinta = Color(0xFF263746)
private val Suave = Color(0xFF718096)
private val Azul = Color(0xFF648CA6)

private enum class Pantalla { INICIO, BILLETERA }
private enum class OperacionBilletera { DEPOSITO, RETIRO }

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
                abrirBilletera = { pantalla = Pantalla.BILLETERA }
            )
            Pantalla.BILLETERA -> Billetera(
                volver = { pantalla = Pantalla.INICIO }
            )
        }
    }
}

@Composable
private fun Inicio(abrirBilletera: () -> Unit) {
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
                Text("Mi billetera", color = Tinta, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Consulta tu saldo y gestiona depósitos o retiros cuando el servicio esté habilitado.", color = Suave, fontSize = 13.sp)
                Button(onClick = abrirBilletera, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("Abrir billetera")
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
            OutlinedButton(
                onClick = { operacion = OperacionBilletera.RETIRO },
                modifier = Modifier.weight(1f)
            ) { Text("Retirar") }
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
                Text(
                    "Las operaciones aún no están activas. No se enviará dinero ni se realizará ningún cargo desde esta pantalla.",
                    color = Suave,
                    fontSize = 12.sp
                )
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
