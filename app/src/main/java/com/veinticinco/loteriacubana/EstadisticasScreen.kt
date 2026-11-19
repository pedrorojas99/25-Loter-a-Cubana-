package com.veinticinco.loteriacubana

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
import com.veinticinco.loteriacubana.data.ResultadoPublico
import com.veinticinco.loteriacubana.data.SupabaseRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val StatsInk = Color(0xFF263746)
private val StatsMuted = Color(0xFF718096)
private val StatsBlue = Color(0xFF648CA6)
private val StatsBg = Color(0xFFF1F5F8)

private data class ConteoNumero(val numero: String, val veces: Int, val ultimaFecha: String)

@Composable
fun EstadisticasScreen() {
    var registros by remember { mutableStateOf<List<ResultadoPublico>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var periodo by remember { mutableStateOf("30 días") }
    var turno by remember { mutableStateOf("Ambos") }
    var modalidad by remember { mutableStateOf("Fijo") }

    fun cargar() {
        cargando = true
        error = null
        SupabaseRepository.cargarResultados { resultado ->
            resultado.onSuccess {
                registros = it.filter { row ->
                    row.juego == "PICK_3" && row.numero.matches(Regex("\\d{3}")) &&
                        (row.turno == "MIDDAY" || row.turno == "EVENING")
                }
                cargando = false
            }.onFailure {
                error = it.message ?: "No se pudieron cargar las estadísticas."
                cargando = false
            }
        }
    }

    LaunchedEffect(Unit) { cargar() }

    val fechasOrdenadas = registros.map { it.fecha }.distinct().sorted()
    val fechaMaxima = fechasOrdenadas.lastOrNull()
    val limite = when (periodo) {
        "7 días" -> 7
        "30 días" -> 30
        "90 días" -> 90
        else -> 0
    }
    val fechasIncluidas = remember(fechasOrdenadas, periodo) {
        if (limite == 0 || fechaMaxima == null) fechasOrdenadas.toSet()
        else {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val cal = Calendar.getInstance()
            val maxDate = runCatching { parser.parse(fechaMaxima) }.getOrNull()
            if (maxDate == null) fechasOrdenadas.toSet() else {
                cal.time = maxDate
                cal.add(Calendar.DAY_OF_YEAR, -(limite - 1))
                val start = parser.format(cal.time)
                fechasOrdenadas.filter { it >= start }.toSet()
            }
        }
    }
    val filtrados = registros.filter { row ->
        row.fecha in fechasIncluidas &&
            (turno == "Ambos" || (turno == "Mediodía" && row.turno == "MIDDAY") ||
                (turno == "Noche" && row.turno == "EVENING"))
    }
    val numeros = filtrados.mapNotNull { row ->
        when (modalidad) {
            "Fijo" -> row.numero.takeLast(2)
            else -> row.numero
        }.takeIf { it.isNotBlank() }
    }
    val conteos = numeros.groupingBy { it }.eachCount()
    val total = numeros.size
    val ranking = conteos.entries
        .map { entry ->
            val last = filtrados.filter { row ->
                (if (modalidad == "Fijo") row.numero.takeLast(2) else row.numero) == entry.key
            }.maxOfOrNull { it.fecha } ?: ""
            ConteoNumero(entry.key, entry.value, last)
        }
        .sortedWith(compareByDescending<ConteoNumero> { it.veces }.thenBy { it.numero })
        .take(10)
    val fechaInicial = fechasIncluidas.minOrNull()
    val fechaFinal = fechasIncluidas.maxOrNull()

    Column(
        Modifier.fillMaxSize().background(StatsBg).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Estadísticas", color = StatsInk, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("Análisis basado en resultados oficiales guardados", color = StatsMuted, fontSize = 12.sp)
            }
            TextButton(onClick = { cargar() }, enabled = !cargando) { Text("Actualizar", color = StatsBlue) }
        }

        StatsSelector("Modalidad", listOf("Fijo", "Centena"), modalidad) { modalidad = it }
        StatsSelector("Período", listOf("7 días", "30 días", "90 días", "Todo"), periodo) { periodo = it }
        StatsSelector("Sorteos", listOf("Ambos", "Mediodía", "Noche"), turno) { turno = it }

        when {
            cargando -> Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = StatsBlue, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Calculando con el historial oficial…", color = StatsMuted, fontSize = 13.sp)
                }
            }
            error != null -> Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No se pudieron cargar los datos", color = StatsInk, fontWeight = FontWeight.Bold)
                    Text(error!!, color = StatsMuted, fontSize = 13.sp)
                    Button(onClick = { cargar() }) { Text("Reintentar") }
                }
            }
            total == 0 -> Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Text("No hay sorteos verificados para los filtros seleccionados.", Modifier.padding(18.dp), color = StatsMuted)
            }
            else -> {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Resumen del período", color = StatsInk, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatsMetric("Resultados", total.toString())
                            StatsMetric("Números distintos", conteos.size.toString())
                            StatsMetric("Fechas", fechasIncluidas.size.toString())
                        }
                        Text("Desde ${fechaInicial ?: "—"} hasta ${fechaFinal ?: "—"}", color = StatsMuted, fontSize = 11.sp)
                        Text(if (modalidad == "Fijo") "Fijo: se analizan las dos últimas cifras de cada Pick 3." else "Centena: se analiza el Pick 3 completo, conservando ceros iniciales.", color = StatsMuted, fontSize = 12.sp)
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Números más repetidos", color = StatsInk, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Ordenados por apariciones en el período elegido", color = StatsMuted, fontSize = 11.sp)
                        ranking.forEachIndexed { index, item ->
                            val pct = item.veces * 100f / total
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}.", color = StatsMuted, modifier = Modifier.width(24.dp), fontSize = 12.sp)
                                    Text(item.numero, color = StatsBlue, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                    Text("${item.veces} veces · ${String.format(Locale.US, "%.1f", pct)}%", color = StatsInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp), color = StatsBlue, trackColor = StatsBg)
                                Text("Última aparición: ${item.ultimaFecha}", color = StatsMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }

                if (modalidad == "Centena") {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("Frecuencia por posición", color = StatsInk, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Cuántas veces apareció cada dígito en cada posición del Pick 3.", color = StatsMuted, fontSize = 11.sp)
                            listOf("Centena" to 0, "Decena" to 1, "Unidad" to 2).forEach { (label, pos) ->
                                val digits = filtrados.groupingBy { it.numero[pos].toString() }.eachCount()
                                Text(label, color = StatsInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    (0..9).forEach { digit ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(digit.toString(), color = StatsBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text((digits[digit.toString()] ?: 0).toString(), color = StatsMuted, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text("Estadísticas descriptivas del historial. Las frecuencias pasadas no predicen ni garantizan premios futuros.", color = StatsMuted, fontSize = 11.sp)
    }
}

@Composable
private fun StatsSelector(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = StatsInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option, fontSize = 11.sp) })
            }
        }
    }
}

@Composable
private fun StatsMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = StatsBlue, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, color = StatsMuted, fontSize = 10.sp)
    }
}
