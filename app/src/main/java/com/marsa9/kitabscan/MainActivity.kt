
package com.marsa9.kitabscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val engine by lazy {
        OcrEngine(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {

                var pages by remember {
                    mutableStateOf(PageStore.list(this@MainActivity))
                }

                var ocrText by remember { mutableStateOf("") }
                var isProcessing by remember { mutableStateOf(false) }

                val scope = rememberCoroutineScope()

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->

                    val scanResult =
                        GmsDocumentScanningResult.fromActivityResultIntent(result.data)

                    val uris = scanResult?.pages?.map { it.imageUri }
                        ?: emptyList()

                    if (uris.isNotEmpty()) {
                        PageStore.addAll(this@MainActivity, uris)
                        pages = PageStore.list(this@MainActivity)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    Text(
                        text = "MARSA KitabScan",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Text(
                        text = "Pemindai kitab dan pengenalan teks Arab",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            val options =
                                GmsDocumentScannerOptions.Builder()
                                    .setGalleryImportAllowed(true)
                                    .setPageLimit(20)
                                    .setResultFormats(
                                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
                                    )
                                    .setScannerMode(
                                        GmsDocumentScannerOptions.SCANNER_MODE_FULL
                                    )
                                    .build()

                            GmsDocumentScanning.getClient(options)
                                .getStartScanIntent(this@MainActivity)
                                .addOnSuccessListener {
                                    launcher.launch(
                                        IntentSenderRequest.Builder(it).build()
                                    )
                                }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan halaman")
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Halaman tersimpan",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            "${pages.size}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(pages) { index, file ->

                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween
                                ) {

                                    Text("Halaman ${index + 1}")

                                    Row {
                                        TextButton(
                                            enabled = !isProcessing,
                                            onClick = {
                                                isProcessing = true
                                                ocrText =
                                                    "Sedang membaca halaman ${index + 1}..."

                                                scope.launch {
                                                    ocrText =
                                                        withContext(Dispatchers.IO) {
                                                            runCatching {
                                                                engine.recognizePage(file)
                                                            }.getOrElse {
                                                                "Gagal OCR: ${it.message}"
                                                            }
                                                        }

                                                    isProcessing = false
                                                }
                                            }
                                        ) {
                                            Text("OCR")
                                        }

                                        TextButton(
                                            enabled = !isProcessing,
                                            onClick = {
                                                PageStore.delete(file)
                                                pages = PageStore.list(
                                                    this@MainActivity
                                                )
                                            }
                                        ) {
                                            Text("Hapus")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Text(
                        "Hasil OCR",
                        style = MaterialTheme.typography.titleMedium
                    )

                    CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Rtl
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            tonalElevation = 2.dp,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = ocrText.ifBlank {
                                        "Hasil pembacaan teks Arab akan muncul di sini."
                                    },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
