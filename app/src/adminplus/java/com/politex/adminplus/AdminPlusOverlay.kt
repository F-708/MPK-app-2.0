package com.politex.adminplus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import com.politex.adminplus.publish.PatSetupScreen
import com.politex.owner.AccessGateWrapper
import com.politex.owner.OwnerGoldMenuCard
import com.politex.owner.OwnerMenuExtras
import com.politex.owner.OwnerRoot
import com.politex.ui.dataeditor.TeacherEditorListScreen
import com.politex.ui.dataeditor.TeacherEditorScreen

object AdminPlusNav {
    val showDataTools = mutableStateOf(false)
    val editingTeacher = mutableStateOf<String?>(null)
    val showPatSetup = mutableStateOf(false)

    fun openDataTools() {
        showDataTools.value = true
    }

    fun openPatSetup() {
        showPatSetup.value = true
    }

    fun back() {
        when {
            showPatSetup.value -> showPatSetup.value = false
            editingTeacher.value != null -> editingTeacher.value = null
            else -> showDataTools.value = false
        }
    }

    fun reset() {
        showDataTools.value = false
        editingTeacher.value = null
        showPatSetup.value = false
    }
}

@Composable
fun AdminPlusRoot(content: @Composable () -> Unit) {
    // P1-fix: adminplus несёт редактор данных и PAT публикации, поэтому код доступа
    // требуется всегда, а не только в admin (owner-код проверяет FLAVOR == "admin").
    // Гейт стоит снаружи OwnerRoot: закрывает и меню, и редактор, и экран кода владельца.
    AccessGateWrapper(gateRequired = AdminPlusAccess.GATE_REQUIRED) {
        val showDataTools by AdminPlusNav.showDataTools
        val editingTeacher by AdminPlusNav.editingTeacher
        val showPatSetup by AdminPlusNav.showPatSetup
        BackHandler(enabled = showDataTools || showPatSetup) { AdminPlusNav.back() }
        OwnerRoot {
            val teacher = editingTeacher
            when {
                showPatSetup ->
                    PatSetupScreen(
                        onBack = { AdminPlusNav.showPatSetup.value = false },
                    )
                showDataTools && teacher != null ->
                    TeacherEditorScreen(
                        teacherName = teacher,
                        onBack = { AdminPlusNav.editingTeacher.value = null },
                        onOpenTokenSetup = { AdminPlusNav.openPatSetup() },
                    )
                showDataTools ->
                    TeacherEditorListScreen(
                        onBack = { AdminPlusNav.showDataTools.value = false },
                        onEditTeacher = { name -> AdminPlusNav.editingTeacher.value = name },
                    )
                else -> content()
            }
        }
    }
}

@Composable
fun AdminPlusMenuExtras() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OwnerMenuExtras()
        OwnerGoldMenuCard(
            icon = Icons.Default.Edit,
            title = "ИНСТРУМЕНТЫ ДАННЫХ",
            badge = "OWNER",
            testTag = "adminplus_data_tools_menu",
            onClick = { AdminPlusNav.openDataTools() },
        )
        OwnerGoldMenuCard(
            icon = Icons.Default.CloudUpload,
            title = "ТОКЕН ПУБЛИКАЦИИ",
            badge = "OWNER",
            testTag = "adminplus_pat_menu",
            onClick = { AdminPlusNav.openPatSetup() },
        )
    }
}
