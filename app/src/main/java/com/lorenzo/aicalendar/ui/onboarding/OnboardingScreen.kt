@file:OptIn(ExperimentalMaterial3Api::class)

package com.lorenzo.aicalendar.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorenzo.aicalendar.ui.profile.ProfileForm

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Benvenuto") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                text = "Raccontaci qualcosa di te. Potrai modificarlo quando vuoi dalle Impostazioni.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            ProfileForm(
                profile = profile,
                onChange = viewModel::update,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = viewModel::finish, modifier = Modifier.fillMaxWidth()) {
                Text("Inizia")
            }
        }
    }
}
