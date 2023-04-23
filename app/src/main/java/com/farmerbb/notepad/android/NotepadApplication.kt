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

package com.farmerbb.notepad.android

//import android.util.Log
//import com.amazonaws.solution.clickstream.ClickstreamAnalytics
//import com.amplifyframework.AmplifyException

import android.app.Application
import com.bytedance.applog.AppLog
import com.bytedance.applog.InitConfig
import com.bytedance.applog.util.UriConstants
import com.farmerbb.notepad.di.notepadModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin


class NotepadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@NotepadApplication)
            modules(notepadModule)
        }

//        try {
//            ClickstreamAnalytics.init(this)
//            ClickstreamAnalytics.getClickStreamConfiguration().withLogEvents(true)
//            Log.i("Notepad", "Initialized ClickstreamAnalytics")
//        } catch (error: AmplifyException) {
//            Log.e("Notepad", "Could not initialize ClickstreamAnalytics", error)
//        }

        val config = InitConfig("yourAPPID", "yourCHANNEL")
        // 设置数据上送地址
        // 设置数据上送地址
        config.setUriConfig(UriConstants.DEFAULT)
        config.isAutoTrackEnabled = true // 全埋点开关，true开启，false关闭
        config.isLogEnable = true // true:开启日志，参考4.3节设置logger，false:关闭日志
        AppLog.setEncryptAndCompress(false) // 加密开关，true开启，false关闭
        AppLog.init(this, config)
    }
}