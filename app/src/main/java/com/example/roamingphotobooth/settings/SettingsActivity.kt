package com.example.roamingphotobooth.settings

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.roamingphotobooth.template.FrameFileManager
import com.example.roamingphotobooth.template.TemplateEditorScreen
import com.example.roamingphotobooth.template.TemplateEditorViewModel
import com.example.roamingphotobooth.template.TemplateListScreen
import com.example.roamingphotobooth.template.TemplateStorage
import com.example.roamingphotobooth.ui.theme.RoamingPhotoboothTheme

/**
 * Layar Settings yang dibuka dari tombol ⚙️ di HomeScreen. Berisi dropdown
 * menu di kiri atas (lihat [SettingsNavDropdown]) dengan pilihan:
 *
 *  - Frame Editor: TemplateEditorScreen yang SUDAH ADA (buat/edit bingkai) —
 *    dipakai apa adanya, cuma dipindah ke sini dari alur lama
 *    (EXTRA_START_IN_EDITOR di TemplateEditorActivity).
 *  - Frame List: TemplateListScreen yang SUDAH ADA, dipanggil dengan
 *    `onDeleteTemplate` terisi supaya muncul tombol hapus — TIDAK ada
 *    perubahan pada fungsi/tampilan TemplateListScreen itu sendiri untuk
 *    pemanggil lain (lihat komentar di file itu). Tap kartu (bukan tombol
 *    hapus) membuka template tsb di Frame Editor untuk diedit.
 *  - Appearance: layar baru untuk ubah background Home/Mode Select,
 *    warna tombol & aksen, dan teks tombol — lihat [AppearanceScreen].
 *  - Printer: <-- BARU: cari & pilih Print Server (mDNS) — lihat
 *    [PrinterSettingsScreen]. Sebelumnya ini dilakukan di FinalResultScreen
 *    tiap kali mau print; sekarang cukup sekali di sini, tersimpan lewat
 *    PrintServerRepository dan dipakai lagi di FinalResultScreen.
 *
 * TemplateEditorActivity (alur pilih-template saat mulai sesi booth) TIDAK
 * disentuh sama sekali oleh activity ini.
 */
class SettingsActivity : ComponentActivity() {

    private val viewModel: TemplateEditorViewModel by viewModels()
    private lateinit var frameFileManager: FrameFileManager
    private lateinit var templateStorage: TemplateStorage
    private lateinit var mediaFileManager: MediaFileManager
    private lateinit var appearanceStorage: AppearanceStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        frameFileManager = FrameFileManager(this)
        templateStorage = TemplateStorage(this)
        mediaFileManager = MediaFileManager(this)
        appearanceStorage = AppearanceStorage(this)

        setContent {
            RoamingPhotoboothTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsHubContent()
                }
            }
        }
    }

    @Composable
    private fun SettingsHubContent() {
        var section by remember { mutableStateOf(SettingsSection.APPEARANCE) }
        var templates by remember { mutableStateOf(templateStorage.loadAllTemplates()) }
        // true selama Frame Editor sedang mengedit template yang dipilih dari
        // Frame List (lihat handleEditFromList) — dipakai supaya tombol Simpan
        // di editor menimpa template yang sama, bukan selalu bikin baru.
        var editingTemplateId by remember { mutableStateOf<String?>(null) }

        val pickImageLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { handleFramePicked(it) }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: tombol kembali + dropdown menu (di kiri, sesuai permintaan).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF16181D))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                IconButton(onClick = { finish() }) {
                    Text(text = "←", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                Column {
                    Text(
                        text = "Pengaturan",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF9AA0AC)
                    )
                    SettingsNavDropdown(
                        selected = section,
                        onSelect = {
                            section = it
                            if (it != SettingsSection.FRAME_EDITOR) {
                                editingTemplateId = null
                            }
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF16181D), Color(0xFF1F2229)))
                    )
            ) {
                when (section) {
                    SettingsSection.FRAME_EDITOR -> TemplateEditorScreen(
                        viewModel = viewModel,
                        onPickFrameClick = { pickImageLauncher.launch("image/png") },
                        onSaveClick = {
                            saveTemplate(editingTemplateId)
                            templates = templateStorage.loadAllTemplates()
                            editingTemplateId = null
                            viewModel.reset()
                            section = SettingsSection.FRAME_LIST
                        }
                    )

                    SettingsSection.FRAME_LIST -> TemplateListScreen(
                        templates = templates,
                        frameFileManager = frameFileManager,
                        onTemplateSelected = { template ->
                            handleEditFromList(template)
                            editingTemplateId = template.id
                            section = SettingsSection.FRAME_EDITOR
                        },
                        onDeleteTemplate = { template ->
                            frameFileManager.deleteFrameFile(template.framePngPath)
                            templateStorage.deleteTemplate(template.id)
                            templates = templateStorage.loadAllTemplates()
                        }
                    )

                    SettingsSection.APPEARANCE -> AppearanceScreen(
                        initialSettings = remember { appearanceStorage.load() },
                        mediaFileManager = mediaFileManager,
                        onSave = { updated ->
                            appearanceStorage.save(updated)
                            android.widget.Toast.makeText(
                                this@SettingsActivity,
                                "Tampilan tersimpan!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )

                    // <-- BARU: cari & pilih Print Server sekarang dilakukan di sini
                    // (Pengaturan > Printer), bukan lagi di FinalResultScreen. Screen ini
                    // self-contained — baca/simpan sendiri lewat PrintServerRepository,
                    // tidak butuh state apa pun dari SettingsActivity.
                    SettingsSection.PRINTER -> PrinterSettingsScreen()
                }
            }
        }
    }

    /** Muat template yang dipilih dari Frame List ke dalam ViewModel Frame Editor. */
    private fun handleEditFromList(template: com.example.roamingphotobooth.template.PhotoTemplate) {
        val bitmap = frameFileManager.loadBitmap(template.framePngPath) ?: return
        viewModel.templateName.value = template.name
        viewModel.setFrame(template.framePngPath, bitmap, template.frameWidthPx, template.frameHeightPx)
        viewModel.slots.clear()
        viewModel.slots.addAll(template.slots)
    }

    private fun handleFramePicked(uri: Uri) {
        val path = frameFileManager.importFrameFromUri(uri) ?: return
        val dimensions = frameFileManager.getImageDimensions(path) ?: return
        val bitmap = frameFileManager.loadBitmap(path) ?: return

        viewModel.setFrame(path, bitmap, dimensions.first, dimensions.second)

        if (viewModel.slots.isEmpty()) {
            viewModel.setSlotCount(1)
        }
    }

    private fun saveTemplate(editingTemplateId: String?) {
        val built = viewModel.buildTemplate()
        if (built == null) {
            android.widget.Toast.makeText(this, "Gagal simpan: pastikan bingkai & minimal 1 slot ada", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        // Kalau sedang edit template yang sudah ada (dari Frame List), timpa
        // dengan ID yang sama supaya tidak bikin duplikat entry baru.
        val template = if (editingTemplateId != null) {
            built.copy(id = editingTemplateId)
        } else {
            built
        }
        templateStorage.saveTemplate(template)
        android.widget.Toast.makeText(this, "Template tersimpan!", android.widget.Toast.LENGTH_SHORT).show()
    }
}
