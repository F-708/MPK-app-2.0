package com.politex.owner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import com.politex.ui.theme.ColorAccentGoldBg
import com.politex.ui.theme.ColorAccentGoldBright
import com.politex.ui.theme.ColorAccentGoldMuted
import com.politex.ui.theme.ColorOnAccentGold
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

@Composable
fun OwnerGoldMenuCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorAccentGoldBg,
        border = BorderStroke(1.dp, ColorAccentGold),
        modifier =
        Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                Modifier
                    .size(36.dp)
                    .background(ColorAccentGold.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ColorAccentGoldBright,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style =
                        TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ColorAccentGoldBright,
                        ),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorAccentGold,
                    ) {
                        Text(
                            text = badge,
                            style =
                            TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                color = ColorOnAccentGold,
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style =
                    TextStyle(
                        fontSize = 12.sp,
                        color = ColorAccentGoldMuted,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ColorAccentGoldBright,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun OwnerMenuExtras() {
    OwnerGoldMenuCard(
        icon = Icons.Default.VpnKey,
        title = "КОД ДОСТУПА",
        subtitle = "Показать код для входа в версию для администрации",
        badge = "OWNER",
        testTag = "owner_access_code_menu",
        onClick = { OwnerNav.showAccessCode.value = true },
    )
}
