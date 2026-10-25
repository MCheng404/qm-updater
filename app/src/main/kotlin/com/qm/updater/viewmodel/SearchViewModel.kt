package com.qm.updater.viewmodel

import androidx.lifecycle.viewModelScope
import com.qm.updater.data.ui.AppInstallProgress
import com.qm.updater.data.ui.SearchUiState
import com.qm.updater.data.ui.removeId
import com.qm.updater.data.ui.setIsInstalling
import com.qm.updater.data.ui.setProgress
import com.qm.updater.repository.SearchRepository
import com.qm.updater.util.Badger
import com.qm.updater.util.Downloader
import com.qm.updater.util.InstallLog
import com.qm.updater.util.SessionInstaller
import com.qm.updater.util.SnackBar
import com.qm.updater.util.Stringer
import com.qm.updater.util.UpdatesNotification
import com.qm.updater.prefs.Prefs
import com.qm.updater.util.launchWithMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex


class SearchViewModel(
	private val searchRepository: SearchRepository,
	private val badger: Badger,
	downloader: Downloader,
	installer: SessionInstaller,
	prefs: Prefs,
	snackBar: SnackBar,
	stringer: Stringer,
	installLog: InstallLog,
	notification: UpdatesNotification
) : InstallViewModel(downloader, installer, prefs, snackBar, stringer, installLog, notification) {

	private val mutex = Mutex()
	private val state = MutableStateFlow<SearchUiState>(SearchUiState.Empty)
	private var job: Job? = null

	fun state(): StateFlow<SearchUiState> = state

	/** 搜索词变化时取消上一次请求，避免旧结果覆盖新结果。 */
	fun search(text: String) {
		job?.cancel()
		if (text.isBlank()) {
			state.value = SearchUiState.Empty
			badger.changeSearchBadge("")
			return
		}
		job = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
			state.value = SearchUiState.Loading
			badger.changeSearchBadge("")
			searchRepository.search(text).collect {
				it.onSuccess { apps ->
					state.value = SearchUiState.Success(apps)
					badger.changeSearchBadge(apps.size.toString())
				}.onFailure {
					badger.changeSearchBadge("!")
					state.value = SearchUiState.Error
				}
			}
		}
	}

	override fun onInstallingChanged(id: Int, installing: Boolean) {
		state.value = SearchUiState.Success(
			state.value.mutableUpdates().setIsInstalling(id, installing)
		)
	}

	override fun onInstallProgress(progress: AppInstallProgress) {
		state.value = SearchUiState.Success(state.value.mutableUpdates().setProgress(progress))
	}

	override fun onInstalled(id: Int) {
		state.value = SearchUiState.Success(state.value.mutableUpdates().removeId(id))
	}

	override fun onInstallFailed(id: Int) {
		state.value = SearchUiState.Success(state.value.mutableUpdates().setIsInstalling(id, false))
	}
}
