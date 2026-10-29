package com.example.sreproductor.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sreproductor.ui.theme.SReproductorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SReproductorTheme {
                val viewModel: DownloadViewModel = viewModel()
                val state by viewModel.uiState.collectAsState()
                val createDocument = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("*/*"),
                    onResult = { uri -> if (uri != null) viewModel.downloadTo(uri) }
                )

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DownloadScreen(
                        state = state,
                        onUrlChange = viewModel::onUrlChange,
                        onDownloadClick = viewModel::checkUrl,
                        onConfirmDownload = {
                            createDocument.launch(state.suggestedFileName)
                            viewModel.dismissConfirmation()
                        },
                        onDismissConfirmation = viewModel::dismissConfirmation,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadScreen(
    state: DownloadUiState,
    onUrlChange: (String) -> Unit,
    onDownloadClick: () -> Unit,
    onConfirmDownload: () -> Unit,
    onDismissConfirmation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Descargar música", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Ingresa el enlace directo a un archivo de audio o video que tengas permiso para descargar.")
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = state.url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Enlace directo al archivo") },
            placeholder = { Text("https://servidor.com/video.mp4") },
            singleLine = true,
            enabled = !state.isBusy
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDownloadClick,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Descargar archivo")
        }
        if (state.isBusy) {
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text(state.statusMessage)
        } else if (state.statusMessage.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(state.statusMessage)
        }
    }

    if (state.showConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissConfirmation,
            title = { Text("Archivo disponible") },
            text = { Text("Se encontró ${state.suggestedFileName}. ¿Quieres elegir dónde guardarlo?") },
            confirmButton = {
                TextButton(onClick = onConfirmDownload) { Text("Elegir ubicación") }
            },
            dismissButton = {
                TextButton(onClick = onDismissConfirmation) { Text("Cancelar") }
            }
        )
    }
}
