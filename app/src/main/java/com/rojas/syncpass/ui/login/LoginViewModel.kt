package com.rojas.syncpass.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rojas.syncpass.data.remote.RetrofitClient
import com.rojas.syncpass.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// LoginViewModel = dueño del estado. La UI observa, el ViewModel decide.
// Es 'class : ViewModel()' (no object/data class) para que el sistema cree UNA instancia por
// pantalla y la conserve ante rotación, con viewModelScope que se cancela solo al salir.
class LoginViewModel(
    // repository por constructor con default = permite pasar un fake en tests sin tocar producción.
    private val repository: AuthRepository = AuthRepository(RetrofitClient.authApi)
) : ViewModel() {

    // CON guion (_uiState) = PRIVADO y MUTABLE: solo el ViewModel puede escribir.
    // MutableStateFlow guarda el LoginUiState actual y notifica a cada update.
    private val _uiState = MutableStateFlow(LoginUiState())
    // SIN guion (uiState) = PÚBLICO y SOLO LECTURA: la UI observa pero no puede mutar directo.
    // asStateFlow() expone la vista inmutable. Patrón backing property: escritura adentro, lectura afuera.
    // Así se cumple UDF: eventos suben (funciones onX), estado baja (uiState).
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // onUsernameChange = evento de cada tecla en el campo usuario.
    // Guarda el texto nuevo y LIMPIA usernameError/loginError para que el error desaparezca al corregir.
    // Por qué update+copy (ver nota abajo): crea un estado NUEVO inmutable en vez de mutar el actual.
    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, usernameError = null, loginError = null) }
    }

    // onPasswordChange = idem para clave. Limpia passwordError/loginError al escribir de nuevo.
    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, loginError = null) }
    }

    // onTogglePasswordVisibility = invierte el flag con !it.isPasswordVisible (toma el valor actual 'it').
    // Solo toca ese campo; copy conserva todo lo demás.
    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    // onLoginClick = valida y luego llama a red. Flujo: lee snapshot, valida, loading, request, success/error.
    fun onLoginClick() {
        val current = _uiState.value // foto actual para validar sin suscribirse
        val usernameError = if (current.username.isBlank()) "Ingresa tu usuario o correo" else null
        val passwordError = if (current.password.length < 6) "Mínimo 6 caracteres" else null
        if (usernameError != null || passwordError != null) {
            // Hay errores de formulario: los publica y NO llama a red (return temprano).
            _uiState.update { it.copy(usernameError = usernameError, passwordError = passwordError) }
            return
        }
        // Sin errores: marca loading para deshabilitar botón (canSubmit) y mostrar spinner.
        _uiState.update { it.copy(isLoading = true, loginError = null) }
        // viewModelScope.launch = corrutina atada al ViewModel: no bloquea UI y se cancela al salir.
        viewModelScope.launch {
            val result = repository.login(current.username, current.password)
            result
                .onSuccess { response ->
                    // Antes: it.copy(isLoading = false, isSuccess = true)
                    // Ahora: además guarda el token para llevarlo a Home (memoria; DataStore va en 03).
                    _uiState.update {
                        it.copy(isLoading = false, isSuccess = true, authToken = response.access_token)
                    }
                }
                .onFailure { e ->
                    // Mensaje genérico al usuario (no expone 401/timeout ni datos sensibles).
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginError = "No se pudo iniciar sesión. Revisa tus datos o tu conexión."
                        )
                    }
                }
        }
    }

    // onErrorShown = la Screen lo llama tras mostrar el Snackbar para limpiar loginError y no repetirlo.
    fun onErrorShown() {
        _uiState.update { it.copy(loginError = null) }
    }
}
// Por qué SIEMPRE 'update { it.copy(...) }' y no mutar directo:
// 1) LoginUiState es inmutable (val): no puedes hacer 'state.username = x', no compila. Solo copy crea otro.
// 2) 'update { }' hace lectura+escritura ATÓMICA y thread-safe: 'it' es el valor más reciente,
//    evita carreras si dos corrutinas actualizan a la vez (vs '_uiState.value = ...' que puede pisarse).
// 3) Cada update emite un OBJETO NUEVO por StateFlow -> Compose detecta cambio y recompone.
//    Mutar por dentro no emitiría nada y la UI quedaría desactualizada.
// En corto: update = forma segura de publicar, copy = forma de crear el estado siguiente sin romper el actual.