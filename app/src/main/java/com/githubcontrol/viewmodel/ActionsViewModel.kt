package com.githubcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.GhWorkflow
import com.githubcontrol.data.api.GhWorkflowRun
import com.githubcontrol.data.repository.GitHubRepository
import com.githubcontrol.utils.Logger
import com.githubcontrol.utils.friendlyGhError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class ActionsState(
    val loading: Boolean = false,
    val workflows: List<GhWorkflow> = emptyList(),
    val runs: List<GhWorkflowRun> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class ActionsViewModel @Inject constructor(private val repo: GitHubRepository) : ViewModel() {
    private val _state = MutableStateFlow(ActionsState())
    val state: StateFlow<ActionsState> = _state

    fun load(owner: String, name: String) {
        viewModelScope.launch {
            _state.value = ActionsState(loading = true)
            try {
                val w = repo.workflows(owner, name).workflows
                val r = repo.workflowRuns(owner, name).runs
                _state.value = ActionsState(loading = false, workflows = w, runs = r)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = friendlyGhError(t))
            }
        }
    }

    /**
     * Triggers a workflow_dispatch run for [id] on [ref].
     *
     * The previous implementation passed a `Map<String, Any>` to Retrofit, which
     * the kotlinx.serialization converter cannot handle (no serializer for `Any`)
     * — the request used to fail before ever hitting the network, which is why
     * the Run button looked dead. The repository now sends a proper JSON object,
     * so we simply translate the result into a clear UI message here.
     */
    fun dispatch(owner: String, name: String, id: Long, ref: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(message = null, error = null)
            try {
                val resp = repo.dispatchWorkflow(owner, name, id, ref)
                if (resp.isSuccessful) {
                    Logger.i("Actions", "dispatched workflow $id on $ref")
                    _state.value = _state.value.copy(message = "Workflow run requested on '$ref'.")
                } else {
                    val body = runCatching { resp.errorBody()?.string().orEmpty() }.getOrNull().orEmpty()
                    val ghMessage = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                    val msg = "Couldn't start the run (HTTP ${resp.code()})." +
                            (ghMessage?.let { " GitHub said: $it" } ?: "") +
                            " Make sure the workflow file has a `workflow_dispatch:` trigger and that '$ref' is a valid branch or tag."
                    Logger.w("Actions", "dispatch failed: ${resp.code()} body=$body")
                    _state.value = _state.value.copy(error = msg)
                }
            } catch (t: Throwable) {
                val base = if (t is HttpException) friendlyGhError(t) else t.message.orEmpty()
                Logger.e("Actions", "dispatch threw", t)
                _state.value = _state.value.copy(
                    error = "$base — make sure the workflow file has a `workflow_dispatch:` trigger and that '$ref' is a valid branch or tag."
                )
            } finally {
                load(owner, name)
            }
        }
    }
}
