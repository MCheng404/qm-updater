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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

	companion object {
		const val MIN_QUERY_LENGTH = 2
	}

	private val state = MutableStateFlow<SearchUiState>(SearchUiState.Empty)
	private var job: Job? = null
	private var lastQuery = ""
	private var lastProgressAt = 0L
	private var lastProgressValue = -1L

	fun state(): StateFlow<SearchUiState> = state

	/** Single entry point: both debounce and IME submit go through here. */
	fun search(raw: String) {
		val text = raw.trim()
		job?.cancel()
		if (text.length < MIN_QUERY_LENGTH) {
			lastQuery = ""
			state.value = SearchUiState.Empty
			badger.changeSearchBadge("")
			return
		}

		lastQuery = text
		val startedAt = System.currentTimeMillis()
		state.value = SearchUiState.Loading(text)
		badger.changeSearchBadge("")
		job = viewModelScope.launch(Dispatchers.IO) {
			searchRepository.search(text).collect { result ->
				val elapsed = System.currentTimeMillis() - startedAt
				result.onSuccess { outcome ->
					state.value = SearchUiState.Success(
						updates = outcome.updates,
						query = text,
						elapsedMs = elapsed,
						failedSources = outcome.failedSources
					)
					badger.changeSearchBadge(outcome.updates.size.toString())
				}.onFailure { error ->
					badger.changeSearchBadge("!")
					state.value = SearchUiState.Error(text, error.message)
				}
			}
		}
	}

	fun retry() = search(lastQuery)

	override fun onInstallingChanged(id: Int, installing: Boolean) {
		state.value = state.value.withUpdates(
			state.value.mutableUpdates().setIsInstalling(id, installing)
		)
	}

	override fun onInstallProgress(progress: AppInstallProgress) {
		// Match UpdatesViewModel: avoid recomposing the whole result list per data chunk.
		val now = System.currentTimeMillis()
		val total = state.value.updates().firstOrNull { it.id == progress.id }?.total ?: 0L
		val value = progress.progress ?: 0L
		val advancedEnough = total <= 0L ||
			value - lastProgressValue > total / 100 ||
			value >= total
		if (now - lastProgressAt < 200L && !advancedEnough) return
		lastProgressAt = now
		lastProgressValue = value
		state.value = state.value.withUpdates(state.value.mutableUpdates().setProgress(progress))
	}

	override fun onInstalled(id: Int) {
		state.value = state.value.withUpdates(state.value.mutableUpdates().removeId(id))
	}

	override fun onInstallFailed(id: Int) {
		state.value = state.value.withUpdates(
			state.value.mutableUpdates().setIsInstalling(id, false)
		)
	}
}
