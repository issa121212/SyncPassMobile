package com.rojas.syncpass.ui.home


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rojas.syncpass.core.jwt.SessionUser
import com.rojas.syncpass.ui.components.AuthCard
import com.rojas.syncpass.ui.theme.SyncPassTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Stateful: pide el ViewModel CON su factory (lleva el token). Sin factory crashea al entrar.
// Ver HomeViewModelFactory en Paso 3 y nota de la corrección.
@Composable
fun HomeScreen(accessToken: String, onLogout: () -> Unit = {}) {
    val viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = HomeViewModelFactory(accessToken)
    )
    val state by viewModel.uiState.collectAsState()
    HomeContent(state = state, onLogout = onLogout)
}

// Stateless: dibuja según HomeUiState. Es lo que usa el preview con datos ficticios.
@Composable
fun HomeContent(state: HomeUiState, onLogout: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuthCard(title = "Bienvenido") {
            // Todo esto es el children (ColumnScope) del Card, igual que en login.
            val user = state.user
            if (user != null) {
                // Text con username + id: lo que el JWT dice que eres.
                Text("Usuario: ${user.username}", style = MaterialTheme.typography.titleMedium)
                Text("ID (sub): ${user.sub}", style = MaterialTheme.typography.bodyMedium)
                // roles: lista del claim. En MVP como texto; luego como chips/filtros.
                Text(
                    "Roles: ${user.roles.joinToString()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Emisor: ${user.iss}", style = MaterialTheme.typography.bodySmall)
                // iat/exp vienen en epoch-segundos: se formatean a fecha legible solo para mostrar.
                Text("Emitido: ${formatEpoch(user.iat)}", style = MaterialTheme.typography.bodySmall)
                Text("Expira: ${formatEpoch(user.exp)}", style = MaterialTheme.typography.bodySmall)
                if (user.isExpired) {
                    Text("Sesión expirada, vuelve a ingresar.", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Text(state.decodeError ?: "Cargando sesión...")
            }
            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar sesión")
            }
        }
    }
}

// Helper solo de presentación: epoch-segundos -> fecha local. No es lógica de negocio.
private fun formatEpoch(epochSeconds: Long): String {
    if (epochSeconds <= 0) return "-"
    val fmt = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
    return fmt.format(Date(epochSeconds * 1000))
}

// Preview SIN token real ni ViewModel: SessionUser ficticio directo (caso §5.1).
@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    SyncPassTheme {
        HomeContent(
            state = HomeUiState(
                user = SessionUser(
                    username = "claudiodcv",
                    roles = listOf("superadmin"),
                    iss = "https://app.claudioviajando.com",
                    sub = "1",
                    iat = 1791481004,
                    exp = 1791484604
                )
            )
        )
    }
}