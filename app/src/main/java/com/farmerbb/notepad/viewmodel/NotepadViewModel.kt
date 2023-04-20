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

@file:OptIn(FlowPreview::class)

package com.farmerbb.notepad.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
//import com.amazonaws.solution.clickstream.ClickstreamAnalytics
//import com.amazonaws.solution.clickstream.ClickstreamEvent
//import com.amazonaws.solution.clickstream.ClickstreamUserAttribute
import com.farmerbb.notepad.R
import com.farmerbb.notepad.data.NotepadRepository
import com.farmerbb.notepad.data.PreferenceManager.Companion.prefs
import com.farmerbb.notepad.model.*
import com.farmerbb.notepad.usecase.ArtVandelay
import com.farmerbb.notepad.usecase.SystemTheme
import com.farmerbb.notepad.usecase.Toaster
import com.farmerbb.notepad.utils.checkForUpdates
import com.farmerbb.notepad.utils.safeGetOrDefault
import com.farmerbb.notepad.utils.showShareSheet
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import de.schnettler.datastore.manager.DataStoreManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import okio.buffer
import okio.sink
import okio.source
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class NotepadViewModel(
    private val context: Application,
    private val repo: NotepadRepository,
    val dataStoreManager: DataStoreManager,
    private val toaster: Toaster,
    private val artVandelay: ArtVandelay,
    systemTheme: SystemTheme
) : ViewModel() {

    private val _noteState = MutableStateFlow(Note())
    val noteState: StateFlow<Note> = _noteState
    lateinit var firebaseAnalytics: FirebaseAnalytics

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text

    private val selectedNotes = mutableMapOf<Long, Boolean>()
    private val _selectedNotesFlow = MutableSharedFlow<Map<Long, Boolean>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val selectedNotesFlow: SharedFlow<Map<Long, Boolean>> = _selectedNotesFlow

    val noteMetadata get() = prefs.sortOrder.flatMapConcat(repo::noteMetadataFlow)
    val prefs = dataStoreManager.prefs(viewModelScope, systemTheme)

    private val _savedDraftId = MutableStateFlow<Long?>(null)
    val savedDraftId: StateFlow<Long?> = _savedDraftId
    private var savedDraftIdJob: Job? = null
    private var isEditing = false


    /*********************** Event record ***********************/
    fun userLogin(userName: String) = viewModelScope.launch(Dispatchers.IO) {
        dataStoreManager.editPreference(
            key = PrefKeys.userName,
            newValue = userName
        )
        val userId = UUID.randomUUID().toString()
        dataStoreManager.editPreference(
            key = PrefKeys.userId,
            newValue = userId
        )
        /**
         * following code is for record user login event.
         */
//        val userAttribute = ClickstreamUserAttribute.Builder()
//            .userId(userId)
//            .add("_user_name", userName)
//            .build()
//        ClickstreamAnalytics.addUserAttributes(userAttribute)
//        ClickstreamAnalytics.recordEvent("user_login")
        firebaseAnalytics.setUserId(userId)
        firebaseAnalytics.setUserProperty("_user_name", userName)
        firebaseAnalytics.logEvent("user_login", null)
    }

    fun addButtonClick() {
        /**
         * following code is for record add_button_click event.
         */
//        ClickstreamAnalytics.recordEvent("add_button_click")
        firebaseAnalytics.logEvent("add_button_click", null)
    }

    fun saveNote(
        id: Long,
        text: String,
        onSuccess: (Long) -> Unit = {}
    ) = viewModelScope.launch(Dispatchers.IO) {
        text.checkLength {
            repo.saveNote(id, text) {
                toaster.toast(R.string.note_saved)
                onSuccess(it)
            }
        }
        /**
         * following code is for record note_create event.
         */
        if (id == -1L) {
//            ClickstreamAnalytics.recordEvent("note_create")
            firebaseAnalytics.logEvent("note_create", null)
        }
    }

    fun shareNote(id: Long = -1, text: String) = viewModelScope.launch {
        text.checkLength {
            context.showShareSheet(text)
        }
        /**
         * following code is for record note_share event.
         */
//        val event = ClickstreamEvent.builder()
//            .name("note_share")
//            .add("note_id", id.toInt())
//            .build()
//        ClickstreamAnalytics.recordEvent(event)
        firebaseAnalytics.logEvent("note_share") {
            param("note_id", id)
        }
    }

    fun exportNote(
        id: Long = -1,
        metadata: NoteMetadata,
        text: String,
        filenameFormat: FilenameFormat
    ) = viewModelScope.launch {
        text.checkLength {
            artVandelay.exportSingleNote(
                metadata,
                filenameFormat,
                { saveExportedNote(it, text) }
            ) {
                viewModelScope.launch {
                    toaster.toast(R.string.note_exported_to)
                }
            }
        }
        /**
         * following code is for record note_export event.
         */
//        val event = ClickstreamEvent.builder()
//            .name("note_export")
//            .add("note_id", id.toInt())
//            .build()
//        ClickstreamAnalytics.recordEvent(event)
        firebaseAnalytics.logEvent("note_export") {
            param("note_id", id)
        }
    }

    fun printNote(id: Long) {
        /**
         * following code is for record note_print event.
         */
//        val event = ClickstreamEvent.builder()
//            .name("note_print")
//            .add("note_id", id.toInt())
//            .build()
//        ClickstreamAnalytics.recordEvent(event)
        firebaseAnalytics.logEvent("note_print") {
            param("note_id", id)
        }
    }

    fun logout() = viewModelScope.launch(Dispatchers.IO) {
        dataStoreManager.editPreference(
            key = PrefKeys.userId,
            newValue = ""
        )
        dataStoreManager.editPreference(
            key = PrefKeys.userName,
            newValue = ""
        )
    }


    /*********************** UI Operations ***********************/

    fun setText(text: String) {
        _text.value = text
    }

    fun clearNote() {
        _noteState.value = Note()
        _text.value = ""
    }

    fun toggleSelectedNote(id: Long) {
        selectedNotes[id] = !selectedNotes.safeGetOrDefault(id, false)
        _selectedNotesFlow.tryEmit(selectedNotes.filterValues { it })
    }

    fun clearSelectedNotes() {
        selectedNotes.clear()
        _selectedNotesFlow.tryEmit(emptyMap())
    }

    fun selectAllNotes(notes: List<NoteMetadata>) {
        notes.forEach {
            selectedNotes[it.metadataId] = true
        }

        _selectedNotesFlow.tryEmit(selectedNotes.filterValues { it })
    }

    fun showToast(@StringRes text: Int) = viewModelScope.launch {
        toaster.toast(text)
    }

    fun showToastIf(
        condition: Boolean,
        @StringRes text: Int,
        block: () -> Unit
    ) = viewModelScope.launch {
        toaster.toastIf(condition, text, block)
    }


    fun checkForUpdates() = context.checkForUpdates()

    fun setIsEditing(value: Boolean) {
        isEditing = value
    }

    /*********************** Database Operations ***********************/

    fun getSavedDraftId() {
        savedDraftIdJob = viewModelScope.launch(Dispatchers.IO) {
            repo.savedDraftId.collect { id ->
                _savedDraftId.value = id

                if (id != -1L) {
                    toaster.toast(R.string.draft_restored)
                }

                savedDraftIdJob?.cancel()
            }
        }
    }

    fun getNote(id: Long?) = viewModelScope.launch(Dispatchers.IO) {
        id?.let {
            _noteState.value = repo.getNote(it)

            if (text.value.isEmpty()) {
                _text.value = with(noteState.value) {
                    draftText.ifEmpty { text }
                }
            }
        } ?: run {
            _noteState.value = Note()
        }
    }

    fun deleteSelectedNotes(
        onSuccess: () -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        selectedNotes.filterValues { it }.keys.let { ids ->
            repo.deleteNotes(ids.toList()) {
                clearSelectedNotes()

                val toastId = when (ids.size) {
                    1 -> R.string.note_deleted
                    else -> R.string.notes_deleted
                }

                toaster.toast(toastId)
                onSuccess()
            }
        }
    }

    fun deleteNote(
        id: Long,
        onSuccess: () -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        repo.deleteNote(id) {
            toaster.toast(R.string.note_deleted)
            onSuccess()
        }
    }

    fun saveDraft(
        onSuccess: suspend () -> Unit = { toaster.toast(R.string.draft_saved) }
    ) {
        val draftText = text.value
        if (!isEditing || draftText.isEmpty()) return

        if (noteState.value.text == draftText) {
            viewModelScope.launch { onSuccess() }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            with(noteState.value) {
                repo.saveNote(id, text, date, draftText) { newId ->
                    getNote(newId)
                    onSuccess()
                }
            }
        }
    }

    fun deleteDraft() = viewModelScope.launch(Dispatchers.IO) {
        with(noteState.value) {
            when {
                text.isEmpty() -> repo.deleteNote(id)
                !isEditing -> repo.saveNote(id, text, date)
            }
        }
    }

    /*********************** Preference Operations ***********************/

    fun firstRunComplete() = viewModelScope.launch(Dispatchers.IO) {
        dataStoreManager.editPreference(
            key = PrefKeys.FirstRun,
            newValue = 1
        )
    }

    fun firstViewComplete() = viewModelScope.launch(Dispatchers.IO) {
        dataStoreManager.editPreference(
            key = PrefKeys.FirstLoad,
            newValue = 1
        )
    }

    fun doubleTapMessageShown() = viewModelScope.launch(Dispatchers.IO) {
        toaster.toast(R.string.double_tap)

        dataStoreManager.editPreference(
            key = PrefKeys.ShowDoubleTapMessage,
            newValue = false
        )
    }

    /*********************** Import / Export ***********************/

    fun importNotes() = artVandelay.importNotes(::saveImportedNote) { size ->
        val toastId = when (size) {
            1 -> R.string.note_imported_successfully
            else -> R.string.notes_imported_successfully
        }

        viewModelScope.launch {
            toaster.toast(toastId)
        }
    }

    fun exportNotes(
        metadata: List<NoteMetadata>,
        filenameFormat: FilenameFormat
    ) = viewModelScope.launch(Dispatchers.IO) {
        val hydratedNotes = repo.getNotes(
            metadata.filter {
                selectedNotes.safeGetOrDefault(it.metadataId, false)
            }
        ).also {
            clearSelectedNotes()
        }

        if (hydratedNotes.size == 1) {
            val note = hydratedNotes.first()
            exportNote(note.id, note.metadata, note.text, filenameFormat)
            return@launch
        }

        artVandelay.exportNotes(
            hydratedNotes,
            filenameFormat,
            ::saveExportedNote,
            ::clearSelectedNotes
        ) {
            viewModelScope.launch {
                toaster.toast(R.string.notes_exported_to)
            }
        }
    }


    private fun saveImportedNote(
        input: InputStream
    ) = viewModelScope.launch(Dispatchers.IO) {
        input.source().buffer().use {
            val text = it.readUtf8()
            if (text.isNotEmpty()) {
                repo.saveNote(text = text)
            }
        }
    }

    private fun saveExportedNote(
        output: OutputStream,
        text: String
    ) = viewModelScope.launch(Dispatchers.IO) {
        output.sink().buffer().use {
            it.writeUtf8(text)
        }
    }

    @SuppressLint("Recycle")
    fun loadFileFromIntent(
        intent: Intent,
        onLoad: (String?) -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        intent.data?.let { uri ->
            val input = context.contentResolver.openInputStream(uri) ?: run {
                onLoad(null)
                return@launch
            }

            input.source().buffer().use {
                val text = it.readUtf8()
                withContext(Dispatchers.Main) {
                    onLoad(text)
                }
            }
        } ?: onLoad(null)
    }


    suspend fun getUserName(): String {
        return dataStoreManager.getPreference(Prefs.UserName)
    }

    /*********************** Miscellaneous ***********************/

    private suspend fun String.checkLength(
        onSuccess: suspend () -> Unit
    ) = when (length) {
        0 -> toaster.toast(R.string.empty_note)
        else -> onSuccess()
    }

}

val viewModelModule = module {
    viewModel {
        NotepadViewModel(
            context = androidApplication(),
            repo = get(),
            dataStoreManager = get(),
            toaster = get(),
            artVandelay = get(),
            systemTheme = get()
        )
    }
}