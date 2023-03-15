package com.farmerbb.notepad.ui.routes

import android.annotation.SuppressLint
import androidx.compose.material.Scaffold
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.res.colorResource
import com.farmerbb.notepad.R
import com.farmerbb.notepad.ui.components.AppBarText
import com.farmerbb.notepad.ui.components.NotepadTheme
import com.farmerbb.notepad.ui.content.LoginScreen
import com.farmerbb.notepad.viewmodel.NotepadViewModel
import org.koin.androidx.compose.getViewModel

@Composable
fun LoginRoute(
    onLogin: (String) -> Unit,
    skip: () -> Unit
) {
    val vm: NotepadViewModel = getViewModel()
    val isLightTheme by vm.prefs.isLightTheme.collectAsState()
    val backgroundColorRes by vm.prefs.backgroundColorRes.collectAsState()
    val rtlLayout by vm.prefs.rtlLayout.collectAsState()

    NotepadTheme(isLightTheme, backgroundColorRes, rtlLayout) {
        LoginCompose(
            vm = vm,
            onLogin = onLogin,
            skip = skip
        )
    }
}

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
private fun LoginCompose(
    vm: NotepadViewModel = getViewModel(),
    onLogin: (String) -> Unit,
    skip: () -> Unit
) {
    val backgroundColorRes by vm.prefs.backgroundColorRes.collectAsState()


    Scaffold(
        backgroundColor = colorResource(id = backgroundColorRes),
        topBar = {
            TopAppBar(
                title = { AppBarText("Login") },
                backgroundColor = colorResource(id = R.color.primary)
            )
        },
        content = {
            LoginScreen(onLogin, skip)
        }
    )
}