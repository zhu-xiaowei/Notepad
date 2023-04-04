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

import android.app.Application
//import android.util.Log
//import com.amazonaws.solution.clickstream.ClickstreamAnalytics
//import com.amplifyframework.AmplifyException
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
    }
}