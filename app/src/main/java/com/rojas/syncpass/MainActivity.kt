package com.rojas.syncpass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.rojas.syncpass.ui.home.HomeScreen
import com.rojas.syncpass.ui.login.LoginScreen
import com.rojas.syncpass.ui.theme.SyncPassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SyncPassTheme {
                // rememberSaveable mantiene el token ante rotaciones de pantalla
                var token: String? by rememberSaveable { mutableStateOf<String?>(null) }
                val currentToken: String? = token

                if (currentToken == null) {
                    // Si no hay token, muestra la pantalla de Login y captura el token al tener éxito
                    LoginScreen(
                        onLoginSuccess = { t -> token = t }
                    )
                } else {
                    // Si hay token, navega a la Home pasándole el token y permitiendo cerrar sesión
                    HomeScreen(
                        accessToken = currentToken,
                        onLogout = { token = null }
                    )
                }
            }
        }
    }
}