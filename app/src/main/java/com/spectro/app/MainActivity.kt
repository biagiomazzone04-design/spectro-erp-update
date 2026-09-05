package com.spectro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.spectro.app.ui.SpectroApp
import com.spectro.app.ui.SpectroViewModel

class MainActivity : ComponentActivity() {
    private val vm: SpectroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SpectroApp(vm) }
    }
}
