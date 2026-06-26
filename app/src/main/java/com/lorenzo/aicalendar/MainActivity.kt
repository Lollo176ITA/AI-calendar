package com.lorenzo.aicalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.lorenzo.aicalendar.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                TodayScreen()
            }
        }
    }
}

/**
 * Placeholder "Oggi" screen (slice 1) — proves the Material 3 Expressive theme,
 * scaffold and FAB render on device. Real agenda content arrives in a later slice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Oggi") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* Quick-Add arrives in a later slice */ }) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Nessun evento per oggi.\nTocca + per aggiungerne uno.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayScreenPreview() {
    AppTheme(dynamicColor = false) { TodayScreen() }
}
