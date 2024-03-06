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

@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "RestrictedApi")

package com.farmerbb.notepad.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.farmerbb.notepad.ui.routes.NotepadComposeAppRoute
import com.farmerbb.notepad.viewmodel.NotepadViewModel
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.FSAFActivityCallbacks
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket


class NotepadActivity : ComponentActivity(), FSAFActivityCallbacks {
    private val vm: NotepadViewModel by viewModel()
    private val fileChooser: FileChooser = get()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val userName = vm.getUserName()
            if (userName.isEmpty()) {
                startActivity(Intent(this@NotepadActivity, LoginActivity::class.java))
            }
        }
        fileChooser.setCallbacks(this)
        setContent {
            NotepadComposeAppRoute(onLogout = {
                startActivity(Intent(this@NotepadActivity, LoginActivity::class.java))
            })
        }
        var serverSocket: ServerSocket

        Thread {
            try {
                serverSocket = ServerSocket(8080)
                while (true) {
                    // 等待客户端连接
                    val socket: Socket = serverSocket.accept()
                    // 处理请求
                    print(socket)
                    // 关闭连接
                    socket.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onStart() {
        super.onStart()
        vm.deleteDraft()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            vm.saveDraft()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fileChooser.removeCallbacks()
    }

    override fun fsafStartActivityForResult(intent: Intent, requestCode: Int) {
        when (intent.action) {
            Intent.ACTION_OPEN_DOCUMENT -> intent.type = "text/plain"
            Intent.ACTION_OPEN_DOCUMENT_TREE -> intent.removeExtra(Intent.EXTRA_LOCAL_ONLY)
        }

        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        fileChooser.onActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val from = intent?.extras?.getString("from") ?: ""
        if (from == "login") {
            finish()
        }
    }
}