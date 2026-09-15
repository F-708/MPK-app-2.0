package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.viewmodel.AppTab

/**
 * Фирменная шапка приложения «МПК Расписание» по официальному Style Guide.
 */
@Composable
fun MainTopBar(
    currentGroup: String,
    isSyncing: Boolean,
    hasSyncError: Boolean = false,
    onGroupChanged: (String) -> Unit,
    onSyncClicked: () -> Unit,
    onSelectTab: ((AppTab) -> Unit)? = null,
    onOpenCalendarArchive: (() -> Unit)? = null,
    onOpenPasteDialog: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    MainHeaderBanner(
        currentGroup = currentGroup,
        isSyncing = isSyncing,
        hasSyncError = hasSyncError,
        onGroupChanged = onGroupChanged,
        onSyncClicked = onSyncClicked,
        onSelectTab = onSelectTab,
        onOpenCalendarArchive = onOpenCalendarArchive,
        onOpenPasteDialog = onOpenPasteDialog,
        modifier = modifier
    )
}
