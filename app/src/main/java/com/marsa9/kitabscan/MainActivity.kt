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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var pages by remember { mutableStateOf(PageStore.list(this@MainActivity)) }

var ocrInfo by remember { mutableStateOf("") }
val scope = rememberCoroutineScope()

val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
) { result ->
    val res = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
    val uris = res?.pages?.map { it.imageUri } ?: emptyList()
    if (uris.isNotEmpty()) {
        PageStore.addAll(this@MainActivity, uris)
        pages = PageStore.list(this@MainActivity)
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

Button(onClick = {
    ocrInfo = "Memuat model..."
    scope.launch {
        ocrInfo = withContext(Dispatchers.IO) {
            runCatching { OcrEngine(applicationContext).info() }
                .getOrElse { "Gagal: ${it.message}" }
        }
    }
}) { Text("Cek model OCR") }

Text(ocrInfo)

                    Text("Halaman tersimpan: ${pages.size}")

LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    itemsIndexed(pages) { index, file ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Halaman ${index + 1}")
            TextButton(onClick = {
                PageStore.delete(file)
                pages = PageStore.list(this@MainActivity)
            }) { Text("Hapus") }
        }
    }
}
                }
            }
        }
    }
}