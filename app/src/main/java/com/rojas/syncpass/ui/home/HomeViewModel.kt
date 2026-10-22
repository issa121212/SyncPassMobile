package com.rojas.syncpass.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider // <- import agregado para la Factory
import com.rojas.syncpass.core.jwt.JwtDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// class : ViewModel con parámetro token: una instancia por Home, sobrevive a rotación.
// Es class (no object) porque cada login tiene un token distinto; un object filtraría usuarios.
class HomeViewModel(accessToken: String) : ViewModel() {
    // Backing property igual que en login: privada mutable, pública solo lectura.
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Se decodifica una vez al crear. Sin corrutina: es CPU local, no red.
        JwtDecoder.decode(accessToken)
            .onSuccess { user -> _uiState.update { it.copy(user = user) } }
            .onFailure { _uiState.update { it.copy(decodeError = "Sesión inválida. Vuelve a ingresar.") } }
    }
}

// ---- Va en el MISMO archivo HomeViewModel.kt, debajo del ViewModel (no en HomeScreen.kt,
// no en MainActivity): la factory se define donde vive lo que construye. Quien la USA es
// HomeScreen.kt con viewModel(factory = HomeViewModelFactory(accessToken)) (ver Paso 4).
// class : ViewModelProvider.Factory porque DEBE guardar el token y crear ViewModels.
// No es object: cada Home tiene su token. No es data class: no se compara, solo construye.
class HomeViewModelFactory(private val accessToken: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") // seguro: esta factory solo se usa con HomeViewModel
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(accessToken) as T // aquí SÍ usamos el constructor con parámetro
    }
}