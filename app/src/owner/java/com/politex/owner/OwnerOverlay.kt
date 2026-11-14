package com.politex.owner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.BuildConfig
import com.politex.ui.theme.ColorAccentGold
import com.politex.ui.theme.ColorAccentGoldBright
import com.politex.ui.theme.ColorAccentGoldMuted
import com.politex.ui.util.bouncyClickable

object OwnerNav {
    val showAccessCode = mutableStateOf(false)
}

@Composable
fun AccessGateWrapper(
    gateRequired: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var unlocked by remember {
        mutableStateOf(!gateRequired || AccessUnlockStore.isUnlocked(context))
    }
    if (unlocked) {
        content()
    } else {
        AccessGateScreen(onUnlocked = { unlocked = true })
    }
}

@Composable
fun OwnerRoot(content: @Composable () -> Unit) {
    val showCode by OwnerNav.showAccessCode
    BackHandler(enabled = showCode) { OwnerNav.showAccessCode.value = false }
    AccessGateWrapper(gateRequired = BuildConfig.FLAVOR == "admin") {
        if (showCode) {
            AccessCodeScreen(onBack = { OwnerNav.showAccessCode.value = false })
        } else {
            content()
        }
    }
}

/**
 * Строка owner-меню: та же геометрия, что у обычной SettingsRow (56dp, иконка 22dp
 * без плитки, заголовок 14sp), но с золотым акцентом. Раньше была «жирной»:
 * плитка 36dp + заголовок 15sp + крупный бейдж — заказчик попросил тоньше.
 */
@Composable
fun OwnerGoldMenuCard(
    icon: ImageVector,
    title: String,
    badge: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ColorAccentGold,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = ColorAccentGoldBright,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = badge,
            style =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 0.6.sp,
                color = ColorAccentGold,
            ),
            maxLines = 1,
            softWrap = false,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ColorAccentGoldMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun OwnerMenuExtras() {
    // Карточка кода нужна только там, где гейт реально есть (admin).
    // В adminplus гейт отключён заказчиком (2026-11-12) — карточка была бы
    // мёртвым UI: открывала бы экран кода, который никогда не спрашивают.
    if (BuildConfig.FLAVOR == "admin") {
        OwnerGoldMenuCard(
            icon = Icons.Default.VpnKey,
            title = "КОД ДОСТУПА",
            badge = "OWNER",
            testTag = "owner_access_code_menu",
            onClick = { OwnerNav.showAccessCode.value = true },
        )
    }
}
