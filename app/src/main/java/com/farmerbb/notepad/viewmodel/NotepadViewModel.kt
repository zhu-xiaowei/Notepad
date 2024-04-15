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

    companion object {
        var allEventNumber = 0
    }

    /*********************** Event record ***********************/

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
            val timestamp = System.currentTimeMillis().toString()
            val uuid = UUID.randomUUID().toString()
            firebaseAnalytics.logEvent("note_create") {
                param("event_timestamp", timestamp)
                param("event_uuid", uuid)
            }
            addEventNumber()
        }
    }

    fun shareNote(id: Long = -1, text: String) = viewModelScope.launch {
        text.checkLength {
            context.showShareSheet(text)
        }
        /**
         * following code is for record note_share event.
         */
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()
        firebaseAnalytics.logEvent("note_share") {
            param("note_id", id)
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
    }


    fun printNote(id: Long) {
        /**
         * following code is for record note_print event.
         */
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()

        firebaseAnalytics.logEvent("note_print") {
            param("note_id", id)
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
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
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()

        firebaseAnalytics.logEvent("note_export") {
            param("note_id", id)
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
    }

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
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()

        firebaseAnalytics.setUserId(userId)
        firebaseAnalytics.setUserProperty("_user_name", userName)
        firebaseAnalytics.logEvent("user_login") {
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
    }

    fun addButtonClick() {
        /**
         * following code is for record add_button_click event.
         */
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()
        firebaseAnalytics.logEvent("add_button_click") {
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
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
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()

        firebaseAnalytics.logEvent("logout") {
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        firebaseAnalytics.setUserId(null)
        addEventNumber()
    }


    /*********************** UI Operations ***********************/

    fun setText(text: String) {
        _text.value = text
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()
        firebaseAnalytics.logEvent("setText") {
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
    }

    fun clearNote() {
        _noteState.value = Note()
        _text.value = ""
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()
        firebaseAnalytics.logEvent("clearNote") {
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
    }

    fun toggleSelectedNote(id: Long) {
        selectedNotes[id] = !selectedNotes.safeGetOrDefault(id, false)
        _selectedNotesFlow.tryEmit(selectedNotes.filterValues { it })
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()

        firebaseAnalytics.logEvent("toggleSelectedNote") {
            param("note_id", id)
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
    }

    fun clearSelectedNotes() {
        selectedNotes.clear()
        _selectedNotesFlow.tryEmit(emptyMap())
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()
        firebaseAnalytics.logEvent("clearSelectedNotes") {
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
    }

    fun selectAllNotes(notes: List<NoteMetadata>) {
        notes.forEach {
            selectedNotes[it.metadataId] = true
        }
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()
        firebaseAnalytics.logEvent("selectAllNotes") {
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
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
                val timestamp = System.currentTimeMillis().toString()
                val uuid = UUID.randomUUID().toString()
                firebaseAnalytics.logEvent("deleteSelectedNotes") {
                    param("event_timestamp", timestamp)
                    param("event_uuid", uuid)
                }
                addEventNumber()
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
            val timestamp = System.currentTimeMillis().toString()
            val uuid = UUID.randomUUID().toString()
            firebaseAnalytics.logEvent("deleteNote") {
                param("event_timestamp", timestamp)
                param("event_uuid", uuid)
            }
            addEventNumber()
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

                    val timestamp = System.currentTimeMillis().toString()
                    val uuid = UUID.randomUUID().toString()
                    firebaseAnalytics.logEvent("saveDraft") {
                        param("event_timestamp", timestamp)
                        param("event_uuid", uuid)
                    }
                    addEventNumber()
                    onSuccess()
                }
            }
        }
    }

    fun deleteDraft() = viewModelScope.launch(Dispatchers.IO) {
        with(noteState.value) {
            when {
                text.isEmpty() -> {
                    repo.deleteNote(id)
                }

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
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()
        firebaseAnalytics.logEvent("importNotes") {
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
        viewModelScope.launch {
            toaster.toast(toastId)
        }
    }

    fun exportNotes(
        metadata: List<NoteMetadata>,
        filenameFormat: FilenameFormat
    ) = viewModelScope.launch(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()
        firebaseAnalytics.logEvent("exportNotes") {
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
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
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()
        firebaseAnalytics.logEvent("saveImportedNote") {
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
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
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()
        firebaseAnalytics.logEvent("saveExportedNote") {
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
        output.sink().buffer().use {
            it.writeUtf8(text)
        }
    }

    @SuppressLint("Recycle")
    fun loadFileFromIntent(
        intent: Intent,
        onLoad: (String?) -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis().toString()
        val uuid = UUID.randomUUID().toString()
        firebaseAnalytics.logEvent("loadFileFromIntent") {
            param("event_timestamp", timestamp)
            param("event_uuid", uuid)
        }
        addEventNumber()
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

    suspend fun getAllEventNumber(): Int {
        return dataStoreManager.getPreference(Prefs.AllEventNumber)
    }

    /*********************** Miscellaneous ***********************/

    private suspend fun String.checkLength(
        onSuccess: suspend () -> Unit
    ) = when (length) {
        0 -> toaster.toast(R.string.empty_note)
        else -> onSuccess()
    }

    fun addEventNumber() = viewModelScope.launch(Dispatchers.IO) {
        allEventNumber += 1
        dataStoreManager.editPreference(
            key = PrefKeys.allEventNumber,
            newValue = allEventNumber
        )
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