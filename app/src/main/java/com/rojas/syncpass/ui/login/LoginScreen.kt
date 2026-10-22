package com.rojas.syncpass.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rojas.syncpass.ui.components.AuthCard
import com.rojas.syncpass.ui.theme.SyncPassTheme

// LoginScreen = wrapper con estado: conecta ViewModel con UI. Sin dibujar directo.
// @param viewModel se obtiene con viewModel() = el sistema crea UNA instancia por pantalla
// atada al ciclo de vida (sobrevive a rotación). Nunca construir con LoginViewModel() a mano:
// eso rompe el scope y en preview ni hay ViewModelStoreOwner (warning 'Constructing a view model').
// @param onLoginSuccess callback al padre (navegar a Home).
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    onLoginSuccess: (token: String) -> Unit = {}
) {
    // collectAsState() = suscribe el StateFlow; cada emisión recompone con el estado nuevo.
    // 'by' delega para usar 'state.username' directo en vez de 'state.value.username'.
    val state by viewModel.uiState.collectAsState()
    LoginContent(
        state = state,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePassword = viewModel::onTogglePasswordVisibility,
        onLoginClick = viewModel::onLoginClick,
        onErrorShown = viewModel::onErrorShown,
        onLoginSuccess = onLoginSuccess
    )
}

// LoginContent = UI tonta y testeable: solo dibuja según 'state', sin ViewModel ni red.
// Todos los eventos entran como lambdas. Es lo que usa el preview con datos ficticios,
// así ningún @Preview construye un ViewModel (eso dispara el warning de Compose).
@Composable
fun LoginContent(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onLoginClick: () -> Unit,
    onErrorShown: () -> Unit,
    onLoginSuccess: (token: String) -> Unit = {}
) {
    // SnackbarHostState en remember = sobrevive a recomposiciones (no a rotación; el error vive en ViewModel).
    val snackbar = remember { SnackbarHostState() }

    // LaunchedEffect(key) = efecto que se relanza cuando cambia la key. Aquí muestra el Snackbar
    // una vez por cada loginError nuevo y luego avisa onErrorShown() para limpiarlo y no repetirlo.
    LaunchedEffect(state.loginError) {
        state.loginError?.let {
            snackbar.showSnackbar(it)
            onErrorShown()
        }
    }
    // Cuando isSuccess pasa a true, notifica una vez al padre para navegar. No navega la Content directo.
    LaunchedEffect(state.isSuccess, state.authToken) {
        val token = state.authToken
        if (state.isSuccess && !token.isNullOrBlank()) onLoginSuccess(token)
    }

    // Column raíz = centra el Card en pantalla con margen exterior de 16dp.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuthCard(
            title = "SyncPass",
            subtitle = "Inicia sesión para acreditar asistencia"
        ) {
            // --- Todo lo de abajo es el 'children' (content: ColumnScope) del Card ---
            // Gracias al receptor ColumnScope podríamos usar Modifier.weight()/align() aquí si hiciera falta.

            // OutlinedTextField usuario: controlado (value viene del state, onValueChange va al ViewModel).
            // singleLine + isError/supportingText = error bajo el campo. ImeAction.Next = pasa al siguiente campo.
            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = { Text("Usuario o correo") }, // label flotante M3, no placeholder en duro
                singleLine = true,
                isError = state.usernameError != null,
                supportingText = { state.usernameError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email, // teclado con @ para una mano en terreno
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth() // fillMaxWidth dentro del ColumnScope del Card
            )

            // OutlinedTextField contraseña: visualTransformation oculta/muestra según isPasswordVisible.
            // trailingIcon = IconButton con ojo + contentDescription accesible (lector de pantalla).
            // KeyboardActions(onDone) = tecla Done del teclado dispara login, igual que el botón.
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text("Contraseña") },
                singleLine = true,
                isError = state.passwordError != null,
                supportingText = { state.passwordError?.let { Text(it) } },
                visualTransformation = if (state.isPasswordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(), // bullets •
                trailingIcon = {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            imageVector = if (state.isPasswordVisible)
                                Icons.Filled.VisibilityOff
                            else
                                Icons.Filled.Visibility,
                            contentDescription = if (state.isPasswordVisible)
                                "Ocultar contraseña" else "Mostrar contraseña"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onLoginClick() }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Button Ingresar: enabled = canSubmit (derivado en UiState) evita taps inválidos.
            // height(48.dp) = mínimo táctil caso §3.3/§5.1 para operar con una mano.
            // Si isLoading muestra CircularProgressIndicator en vez de texto (feedback inmediato).
            Button(
                onClick = onLoginClick,
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (state.isLoading) {
                    // Progress dentro del botón con color onPrimary para contraste sobre el primario M3.
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Ingresar")
                }
            }
        }
        // SnackbarHost = punto donde LaunchedEffect muestra el snackbar de loginError (red/credenciales).
        SnackbarHost(hostState = snackbar)
    }
}

// Preview SIN ViewModel: usa LoginContent con estado ficticio. Construir LoginViewModel() aquí
// dispara 'Constructing a view model in a composable' porque el preview no tiene scope de lifecycle
// y el ViewModel quedaría sin ViewModelStore. Así se previsualiza igual sin ese warning.
@Preview(showBackground = true)
@Composable
private fun LoginContentPreview() {
    SyncPassTheme {
        LoginContent(
            state = LoginUiState(username = "juan_perez", password = "Password123"),
            onUsernameChange = {},
            onPasswordChange = {},
            onTogglePassword = {},
            onLoginClick = {},
            onErrorShown = {}
        )
    }
}