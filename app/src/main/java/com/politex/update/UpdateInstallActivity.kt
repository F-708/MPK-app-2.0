package com.politex.update

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

class UpdateInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_APK_PATH)
        val file = path?.let(::File)
        if (file == null || !UpdateDownloader.apkIsValid(this, file)) {
            Toast.makeText(this, "Файл обновления не найден", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingFile = file
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        launchInstaller(file)
    }

    override fun onResume() {
        super.onResume()
        val file = pendingFile
        pendingFile = null
        if (file != null) launchInstaller(file) else finish()
    }

    private fun launchInstaller(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.updateprovider", file)
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            Toast.makeText(this, "Не удалось открыть установщик", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_APK_PATH = "extra_apk_path"
        private var pendingFile: File? = null
    }
}
