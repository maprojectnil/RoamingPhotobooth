package com.example.roamingphotobooth.template

import android.content.ContentValues.TAG
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.roamingphotobooth.ui.theme.RoamingPhotoboothTheme

class TemplateEditorActivity : ComponentActivity() {

    private val viewModel: TemplateEditorViewModel by viewModels()
    private lateinit var frameFileManager: FrameFileManager
    private lateinit var templateStorage: TemplateStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        frameFileManager = FrameFileManager(this)
        templateStorage = TemplateStorage(this)

        setContent {
            RoamingPhotoboothTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EditorContent()
                }
            }
        }
    }

    @Composable
    private fun EditorContent() {
        var showEditor by remember { mutableStateOf(false) }
        var templates by remember { mutableStateOf(templateStorage.loadAllTemplates()) }

        val pickImageLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { handleImagePicked(it) }
        }

        if (showEditor) {
            // TemplateEditorScreen sudah mengurus seluruh layout (workspace + panel
            // kontrol, termasuk tombol pilih/ganti bingkai) — selalu tampil landscape
            // penuh layar (dipaksa lewat AndroidManifest), tanpa Column pembungkus lagi.
            TemplateEditorScreen(
                viewModel = viewModel,
                onPickFrameClick = { pickImageLauncher.launch("image/png") },
                onSaveClick = {
                    saveTemplate()
                    templates = templateStorage.loadAllTemplates()
                    showEditor = false
                    viewModel.reset()
                }
            )
        } else {
            TemplateListScreen(
                templates = templates,
                frameFileManager = frameFileManager,
                onTemplateSelected = { template ->
                    // Kirim balik ID template yang dipilih ke MainActivity
                    val resultIntent = android.content.Intent()
                    resultIntent.putExtra("selected_template_id", template.id)
                    setResult(RESULT_OK, resultIntent)
                    finish()
                },
                onCreateNewClick = {
                    viewModel.reset()
                    showEditor = true
                },
                onDeleteClick = { template ->
                    templateStorage.deleteTemplate(template.id)
                    templates = templateStorage.loadAllTemplates()
                }
            )
        }
    }

    private fun handleImagePicked(uri: Uri) {
        val path = frameFileManager.importFrameFromUri(uri) ?: return
        val dimensions = frameFileManager.getImageDimensions(path) ?: return
        val bitmap = frameFileManager.loadBitmap(path) ?: return

        viewModel.setFrame(path, bitmap, dimensions.first, dimensions.second)

        // Default: mulai dengan 1 slot supaya user langsung lihat contoh
        if (viewModel.slots.isEmpty()) {
            viewModel.setSlotCount(1)
        }
    }

    private fun saveTemplate() {
        val template = viewModel.buildTemplate()
        if (template == null) {
            android.widget.Toast.makeText(this, "Gagal simpan: pastikan bingkai & minimal 1 slot ada", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        templateStorage.saveTemplate(template)
        android.widget.Toast.makeText(this, "Template tersimpan!", android.widget.Toast.LENGTH_SHORT).show()
    }
}