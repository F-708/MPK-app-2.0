package com.politex.ui.screens.materials

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.util.bouncyClickable

/**
 * M2/M4/M5: общие куски просмотрщиков — тёмная шапка (фирменный ColorTopBar, как в остальном
 * приложении), иконка материала по расширению и «честная плашка» для неподдерживаемых форматов.
 */

/** M4: иконка материала по расширению. */
fun materialIconFor(item: MaterialItemEntity): ImageVector = when {
    item.ext == "pdf" -> Icons.Default.PictureAsPdf
    item.ext == "doc" || item.ext == "docx" -> Icons.Default.Description
    item.ext == "xls" || item.ext == "xlsx" -> Icons.Default.TableChart
    item.ext == "txt" || item.ext == "md" || item.ext == "csv" || item.ext == "log" -> Icons.AutoMirrored.Filled.TextSnippet
    item.imagePaths.isNotEmpty() -> Icons.Default.Image
    item.ext.isEmpty() -> Icons.AutoMirrored.Filled.TextSnippet
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/** M4: цвет иконки по типу — PDF красный, Word синий, таблицы зелёные, изображения фиолетовые. */
fun materialIconTint(item: MaterialItemEntity): Color = when {
    item.ext == "pdf" -> Color(0xFFDC2626)
    item.ext == "doc" || item.ext == "docx" -> Color(0xFF0072CE)
    item.ext == "xls" || item.ext == "xlsx" -> Color(0xFF16A34A)
    item.imagePaths.isNotEmpty() -> Color(0xFF8B5CF6)
    else -> ColorBrandBlue
}

/**
 * Тёмная шапка просмотрщика: назад + заголовок + действия. Хитбокс кнопки назад 48dp.
 * `onBack = null` — шапка без возврата (не используется, оставлено для симметрии).
 */
@Composable
fun MaterialViewerTopBar(
    title: String,
    testTag: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorTopBar,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${testTag}_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White
                    )
                }
            }
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack == null) 12.dp else 0.dp),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            )
            actions()
        }
    }
}

/** M2: маленькая кнопка-действие в тёмной шапке (хитбокс 44dp). */
@Composable
fun ViewerAction(
    icon: ImageVector,
    description: String,
    testTag: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (active) ColorBrandBlue else Color.Transparent,
        modifier = Modifier
            .size(44.dp)
            .bouncyClickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * M2: плашка «встроенный просмотрщик этот формат не умеет». Не молчаливая пустота и не краш:
 * материал сохранён, пользователь понимает, что делать дальше.
 *
 * ponytail: .doc (бинарный OLE2) и .xls/.xlsx попадают сюда. Честная поддержка = Apache POI
 * ≈ +10 МБ к APK, при том что в колледже раздают в основном .docx/.pdf. Подключать POI,
 * только если заказчик принесёт реальный поток .doc-файлов.
 */
@Composable
fun UnsupportedFormatPlaque(ext: String, fileName: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, ColorBorderLight),
            color = ColorBgMain,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("unsupported_plaque")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Формат .${ext.ifBlank { "?" }} не поддерживается встроенным просмотрщиком",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColorTextTitle)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (ext) {
                        "doc" -> "Конвертируйте файл в .docx (Word → «Сохранить как» → .docx) и загрузите заново: " +
                            ".docx читается прямо в приложении, и по нему работает поиск."
                        "xls", "xlsx" -> "Таблицы Excel открываются только внешним приложением. " +
                            "Файл сохранён в материальной базе и никуда не денется."
                        else -> "Файл сохранён в материальной базе. Внутри приложения читаются PDF, DOCX, " +
                            "TXT и наборы изображений."
                    },
                    style = TextStyle(fontSize = 12.sp, color = ColorTextMuted)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = fileName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ColorTextBody)
                )
            }
        }
    }
}

/**
 * M5: плашка про PDF в результатах поиска.
 *
 * ponytail: извлечение текста из PDF = pdfbox ≈ +5 МБ к APK. Пока PDF ищется по названию,
 * а внутри — закладками и номером страницы; объясняем это пользователю прямо в выдаче.
 */
@Composable
fun PdfSearchHintPlaque(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, ColorBorderLight),
        color = ColorBgMain,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("pdf_search_hint")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "PDF не ищется по тексту — используйте закладки и номер страницы. По названию PDF находится.",
                style = TextStyle(fontSize = 11.sp, color = ColorTextMuted)
            )
        }
    }
}

/** M4: строка-карточка в стиле проекта (Surface + RoundedCornerShape(2.dp) + тонкая граница). */
@Composable
fun MaterialCard(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = 44.dp)
        .let { if (testTag != null) it.testTag(testTag) else it }
        .let { if (onClick != null) it.bouncyClickable(onClick = onClick) else it }
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, ColorBorderLight),
        color = ColorBgMain,
        modifier = base
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) { content() }
    }
}
