package com.politex.adminplus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import com.politex.owner.OwnerGoldMenuCard
import com.politex.owner.OwnerMenuExtras
import com.politex.owner.OwnerRoot
import com.politex.ui.dataeditor.TeacherEditorListScreen
import com.politex.ui.dataeditor.TeacherEditorScreen

object AdminPlusNav {
    val showDataTools = mutableStateOf(false)
    val editingTeacher = mutableStateOf<String?>(null)

    fun openDataTools() {
        showDataTools.value = true
    }

    fun back() {
        if (editingTeacher.value != null) {
            editingTeacher.value = null
        } else {
            showDataTools.value = false
        }
    }

    fun reset() {
        showDataTools.value = false
        editingTeacher.value = null
    }
}

@Composable
fun AdminPlusRoot(content: @Composable () -> Unit) {
    val showDataTools by AdminPlusNav.showDataTools
    val editingTeacher by AdminPlusNav.editingTeacher
    BackHandler(enabled = showDataTools) { AdminPlusNav.back() }
    OwnerRoot {
        val teacher = editingTeacher
        when {
            showDataTools && teacher != null ->
                TeacherEditorScreen(
                    teacherName = teacher,
                    onBack = { AdminPlusNav.editingTeacher.value = null },
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
    }
}
