package com.politex.ui.screens.materials

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.politex.util.PendingMaterial

object ExternalMaterialIntake {

    var pending by mutableStateOf<PendingMaterial?>(null)

    fun fromIntent(
        resolver: ContentResolver,
        intent: Intent,
    ): PendingMaterial? {
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_SEND -> {
                val uri = intent.data ?: intent.streamUri() ?: intent.clipDataUri() ?: return null
                if (uri.scheme != "content" && uri.scheme != "file") return null
                val mime = intent.type ?: runCatching { resolver.getType(uri) }.getOrNull()
                return if (mime?.startsWith("image/") == true) {
                    PendingMaterial.Images(listOf(uri))
                } else {
                    PendingMaterial.SingleFile(uri)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.streamUris()?.filter { it.scheme == "content" || it.scheme == "file" }
                return if (uris.isNullOrEmpty()) null else PendingMaterial.Images(uris)
            }
            else -> return null
        }
    }

    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun Intent.streamUris(): List<Uri>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }

    private fun Intent.clipDataUri(): Uri? =
        clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.uri
}
