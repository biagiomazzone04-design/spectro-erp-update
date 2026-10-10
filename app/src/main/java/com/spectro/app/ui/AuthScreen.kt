package com.spectro.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectro.app.R
import com.spectro.app.auth.AuthState
import com.spectro.app.ui.theme.SpectroPalette

@Composable
fun AuthScreen(vm: SpectroViewModel, state: AuthState, p: SpectroPalette) {
    Box(
        Modifier.fillMaxSize().background(p.background).systemBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            AuthState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.spectro_header_logo), "Spectro", Modifier.size(150.dp, 110.dp))
                CircularProgressIndicator(color = p.primary)
                Spacer(Modifier.height(14.dp))
                Text("Verifica accesso sicuro…", color = p.textMuted)
            }
            AuthState.SignedOut -> LoginCard(vm, p)
            is AuthState.ConfigurationRequired -> MessageCard(
                icon = Icons.Rounded.CloudOff,
                title = "Collegamento Supabase da completare",
                message = "Progetto: ${state.projectUrl}\n\nLa build non contiene una chiave Supabase publishable valida. Ricompila l’app con SPECTRO_SUPABASE_ANON_KEY oppure aggiorna la configurazione pubblica del progetto.",
                p = p
            )
            is AuthState.AwaitingAccess -> MessageCard(
                icon = Icons.Rounded.HourglassTop,
                title = "Accesso in attesa",
                message = state.message,
                p = p,
                primaryLabel = "Ricontrolla permessi",
                onPrimary = vm::retryAccess,
                tertiaryLabel = "Configura la prima azienda",
                onTertiary = vm::bootstrapOrganization,
                secondaryLabel = "Esci",
                onSecondary = vm::signOut
            )
            is AuthState.SignedIn -> Unit
        }
    }
}

@Composable
private fun LoginCard(vm: SpectroViewModel, p: SpectroPalette) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    val message = vm.authMessage

    LaunchedEffect(message) {
        if (message != null) {
            // Keep the message until the user changes mode or retries.
        }
    }

    Column(
        Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(R.drawable.spectro_header_logo), "Spectro", Modifier.size(155.dp, 112.dp))
        Text("SPECTRO", color = p.primary, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Text("ERP CLOUD", color = p.text, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(Modifier.height(26.dp))
        SpectroCard(p = p) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = p.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(if (register) "Richiedi un account" else "Accedi al gestionale", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("Stesso account su Android, Windows e Mac", color = p.textMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            if (register) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; vm.consumeAuthMessage() },
                    label = { Text("Nome e cognome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; vm.consumeAuthMessage() },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; vm.consumeAuthMessage() },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = if (it.startsWith("Richiesta")) p.success else p.warning, fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
            GradientButton(if (register) "Invia richiesta" else "Accedi", p) {
                if (register) vm.signUp(email, password, displayName) else vm.signIn(email, password)
            }
            TextButton(onClick = { register = !register; vm.consumeAuthMessage() }, Modifier.fillMaxWidth()) {
                Text(if (register) "Ho già un account" else "Non hai un account? Richiedi accesso")
            }
        }
    }
}

@Composable
private fun MessageCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    p: SpectroPalette,
    primaryLabel: String? = null,
    onPrimary: () -> Unit = {},
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    tertiaryLabel: String? = null,
    onTertiary: () -> Unit = {}
) {
    SpectroCard(p = p, modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Icon(icon, null, tint = p.primary, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        Text(message, color = p.textMuted)
        primaryLabel?.let {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onPrimary, Modifier.fillMaxWidth()) { Text(it) }
        }
        tertiaryLabel?.let {
            OutlinedButton(onClick = onTertiary, Modifier.fillMaxWidth()) { Text(it) }
        }
        secondaryLabel?.let {
            TextButton(onClick = onSecondary, Modifier.fillMaxWidth()) { Text(it, color = Color.White) }
        }
    }
}
