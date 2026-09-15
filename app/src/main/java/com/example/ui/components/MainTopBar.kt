package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Фирменная шапка приложения «МПК Расписание» с официальным баннером.
 */
@Composable
fun MainTopBar(
    currentGroup: String,
    isSyncing: Boolean,
    hasSyncError: Boolean = false,
    onGroupChanged: (String) -> Unit,
    onSyncClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    MainHeaderBanner(
        currentGroup = currentGroup,
        isSyncing = isSyncing,
        hasSyncError = hasSyncError,
        onGroupChanged = onGroupChanged,
        onSyncClicked = onSyncClicked,
        modifier = modifier
    )
}

