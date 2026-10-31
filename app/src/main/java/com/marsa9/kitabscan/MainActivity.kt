package com.marsa9.kitabscan

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var pages by remember { mutableStateOf<List<Uri>>(emptyList()) }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    val res = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                    pages = res?.pages?.map { it.imageUri } ?: emptyList()
                }

                val preview = remember(pages) {
                    pages.firstOrNull()?.let { uri ->
                        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Kitab Scan", style = MaterialTheme.typography.headlineMedium)

                    Button(onClick = {
                        val options = GmsDocumentScannerOptions.Builder()
                            .setGalleryImportAllowed(true)
                            .setPageLimit(20)
                            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                            .build()
                        GmsDocumentScanning.getClient(options)
                            .getStartScanIntent(this@MainActivity)
                            .addOnSuccessListener {
                                launcher.launch(IntentSenderRequest.Builder(it).build())
                            }
                    }) { Text("Scan halaman") }

                    Text("Halaman: ${pages.size}")

                    preview?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Halaman pertama",
                            modifier = Modifier.fillMaxWidth().height(400.dp)
                        )
                    }
                }
            }
        }
    }
}