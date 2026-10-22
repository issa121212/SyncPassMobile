package com.rojas.syncpass.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rojas.syncpass.ui.theme.SyncPassTheme

/**
 * Card reutilizable para auth. El formulario se pasa como [content] (children).
 * No sabe nada de login: solo layout + estilo.
 *
 * @param title Título principal del card (obligatorio, ej: 'SyncPass').
 * @param modifier Modifier externo para que el padre controle tamaño/posición.
 *  Va primero entre los opcionales por convención Compose. Se encadena con
 *  fillMaxWidth() interno: el padre puede sumar padding, click, etc. sin romper el ancho total.
 * @param subtitle Texto secundario opcional (nullable + default null = si no lo pasas, no se dibuja).
 * @param content Slot del formulario hijo. Tipo '@Composable ColumnScope.() -> Unit' significa:
 *  1) '@Composable' = es UI, se recompone cuando cambia su estado.
 *  2) '() -> Unit' = es una función sin retorno, solo dibuja (como children de React).
 *  3) 'ColumnScope.()' = función de extensión con receptor ColumnScope: dentro del bloque
 *     puedes usar Modifier.weight(), align(), etc. como si estuvieras dentro del Column
 *     interno del Card. Sin ese receptor el hijo no podría repartir espacio ni alinearse.
 */
@Composable
// Convención Compose: 'modifier' es el primer parámetro opcional (después de los obligatorios).
// Si lo pones después de otro opcional como 'subtitle', el linter avisa
// 'Modifier parameter should be the first optional parameter'.
fun AuthCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // Card M3 = contenedor elevado con forma, color y sombra del tema (se adapta a claro/oscuro solo).
    Card(
        modifier = modifier.fillMaxWidth(), // respeta el modifier del padre + ocupa todo el ancho
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // sombra sutil
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh // color M3, no hex en duro
        )
    ) {
        // Column interno = apila título, subtítulo y formulario con ritmo vertical.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp), // respiro interno fijo para uso en terreno con una mano
            horizontalAlignment = Alignment.CenterHorizontally, // centra título/subtítulo
            verticalArrangement = Arrangement.spacedBy(12.dp) // espacio uniforme entre hijos
        ) {
            // Text título con estilo tipográfico del tema (headlineSmall), hereda color onSurface.
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
            // Subtítulo solo si viene no-nulo y no-blanco: evita hueco vacío cuando no se usa.
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant // gris legible en ambas apariencias
                )
            }
            Spacer(Modifier.height(4.dp)) // aire extra entre encabezado y formulario
            content() // <- aquí se dibuja el formulario hijo dentro del ColumnScope
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AuthCardPreview() {
    SyncPassTheme {
        AuthCard(
            title = "SyncPass",
            subtitle = "Vista previa con contenido ficticio"
        ) {
            Text("Aquí va el formulario hijo")
        }
    }
}