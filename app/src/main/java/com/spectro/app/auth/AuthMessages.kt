package com.spectro.app.auth

import java.util.Locale

internal fun localizedAuthError(message: String, status: Int): String {
    val normalized = message.trim().lowercase(Locale.ROOT)
    return when {
        "email not confirmed" in normalized ->
            "Email non ancora confermata. Apri l’ultimo link ricevuto oppure richiedine uno nuovo."
        "invalid login credentials" in normalized ->
            "Email o password non corrette. Se non hai ancora confermato l’email, richiedi un nuovo link."
        "email rate limit exceeded" in normalized || "over_email_send_rate_limit" in normalized ->
            "Hai richiesto troppi link in poco tempo. Attendi alcuni minuti e riprova."
        "user already registered" in normalized ->
            "Questo indirizzo è già registrato. Accedi oppure usa “Reinvia email di conferma”."
        "signup is disabled" in normalized ->
            "La creazione di nuovi account è temporaneamente disabilitata."
        "email address" in normalized && "invalid" in normalized ->
            "L’indirizzo email non è valido."
        message.isNotBlank() -> message
        else -> "Servizio cloud non disponibile ($status)."
    }
}
