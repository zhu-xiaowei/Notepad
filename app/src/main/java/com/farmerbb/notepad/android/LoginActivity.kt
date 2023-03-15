/* Copyright 2021 Braden Farmer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.farmerbb.notepad.android

import android.content.Intent
import android.os.Bundle
import android.os.Process.killProcess
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.farmerbb.notepad.R
import com.farmerbb.notepad.ui.routes.LoginRoute
import com.farmerbb.notepad.viewmodel.NotepadViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.system.exitProcess

class LoginActivity : ComponentActivity() {
    private val vm: NotepadViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val onLogin: (userName: String) -> Unit = {
                if (it.isEmpty()) {
                    Toast.makeText(this, R.string.user_name_is_null, Toast.LENGTH_SHORT).show()
                } else {
                    vm.saveUserName(userName = it)
                    vm.saveUserId()
                    startActivity(Intent(this, NotepadActivity::class.java))
                    finish()
                }
            }
            val skip: () -> Unit = {
                finish()
            }
            LoginRoute(onLogin = onLogin, skip = skip)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        var intent = Intent(this, NotepadActivity::class.java)
        intent.putExtra("from", "login")
        startActivity(intent)
        finish()
    }
}