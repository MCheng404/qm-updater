package com.qm.updater.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qm.updater.R
import com.qm.updater.data.ui.SearchUiState
import com.qm.updater.ui.component.AppList
import com.qm.updater.ui.component.EmptyState
import com.qm.updater.ui.component.ErrorState
import com.qm.updater.ui.component.LoadingList
import com.qm.updater.ui.component.UpdateGroupCard
import com.qm.updater.ui.component.groupByPackagePreservingOrder
import com.qm.updater.viewmodel.SearchViewModel
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SEARCH_DEBOUNCE_MS = 600L
private const val TYPE_META = "search-meta"
private const val TYPE_CARD = "search-card"

@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    val state = viewModel.state().collectAsStateWithLifecycle().value
    val handler = LocalUriHandler.current
    val keyboard = LocalSoftwareKeyboardController.current

    var query by rememberSaveable { mutableStateOf("") }
    var submitToken by rememberSaveable { mutableIntStateOf(0) }
    var lastToken by rememberSaveable { mutableIntStateOf(0) }

    // One entry point for debounce and IME submit. IME increments the token so
    // the search starts immediately; plain typing still waits for a pause.
    LaunchedEffect(query, submitToken) {
        val text = query.trim()
        if (text.length < SearchViewModel.MIN_QUERY_LENGTH) {
            viewModel.search("")
            return@LaunchedEffect
        }
        val immediate = submitToken != lastToken
        lastToken = submitToken
        if (!immediate) delay(SEARCH_DEBOUNCE_MS)
        if (text == query.trim()) viewModel.search(text)
    }

    Column {
        SmallTopAppBar(title = stringResource(R.string.tab_search))

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            label = stringResource(R.string.search_hint),
            useLabelAsPlaceholder = true,
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = MiuixIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = {
                        query = ""
                        keyboard?.hide()
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = stringResource(R.string.clear_search),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                submitToken++
            })
        )

        when (state) {
            is SearchUiState.Empty -> EmptyState(stringResource(R.string.search_hint_long))

            is SearchUiState.Loading -> Column {
                Text(
                    text = stringResource(R.string.search_searching, state.query),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LinearProgressIndicator(
                    progress = null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
                LoadingList(modifier = Modifier.weight(1f))
            }

            is SearchUiState.Error -> ErrorState(
                text = stringResource(R.string.something_went_wrong),
                detail = state.message,
                onRetry = { viewModel.retry() }
            )

            is SearchUiState.Success -> {
                if (state.updates.isEmpty()) {
                    EmptyState(
                        text = stringResource(R.string.no_search_results),
                        detail = stringResource(R.string.search_no_results_detail, state.query)
                    )
                } else {
                    val groups = remember(state.updates) {
                        state.updates.groupByPackagePreservingOrder()
                    }
                    AppList {
                        item(key = "search-meta", contentType = TYPE_META) {
                            Text(
                                text = stringResource(
                                    R.string.search_result_meta,
                                    groups.size,
                                    state.elapsedMs / 1000f
                                ),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        if (state.failedSources > 0) {
                            item(key = "search-partial", contentType = TYPE_META) {
                                Text(
                                    text = stringResource(R.string.search_partial_error, state.failedSources),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                )
                            }
                        }
                        items(
                            items = groups,
                            key = { it.first().packageName },
                            contentType = { TYPE_CARD }
                        ) { candidates ->
                            UpdateGroupCard(
                                candidates = candidates,
                                onInstall = { update -> viewModel.install(update, handler) }
                            )
                        }
                    }
                }
            }
        }
    }
}
