package com.lquiroz.flab.launcher

import android.app.Application
import android.graphics.Rect
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the app catalogue across the configuration changes a fold produces. */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LauncherAppsRepository(application, viewModelScope)

    val catalogue: StateFlow<LauncherCatalogue> = repository.catalogue

    private val _homePresses = MutableStateFlow(0)

    /** Bumped on every Home press while already home, so the pager can return to the first page. */
    val homePresses: StateFlow<Int> = _homePresses.asStateFlow()

    fun onHomePressed() {
        _homePresses.value += 1
    }

    fun launch(entry: LauncherEntry, sourceBounds: Rect?, options: Bundle?) =
        repository.launch(entry, sourceBounds, options)

    fun openAppDetails(entry: LauncherEntry) = repository.openAppDetails(entry)

    override fun onCleared() {
        repository.release()
        super.onCleared()
    }
}
