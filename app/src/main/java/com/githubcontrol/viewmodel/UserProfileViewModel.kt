package com.githubcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.GhRepo
import com.githubcontrol.data.api.GhUser
import com.githubcontrol.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserProfileState(
    val loading: Boolean = false,
    val user: GhUser? = null,
    val repos: List<GhRepo> = emptyList(),
    val page: Int = 1,
    val endReached: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val repo: GitHubRepository
) : ViewModel() {
    private val _state = MutableStateFlow(UserProfileState())
    val state: StateFlow<UserProfileState> = _state

    fun load(login: String) {
        if (login.isBlank()) return
        viewModelScope.launch {
            _state.value = UserProfileState(loading = true)
            try {
                val u = repo.user(login)
                val rs = repo.userRepos(login, page = 1)
                _state.value = UserProfileState(
                    loading = false,
                    user = u,
                    repos = rs,
                    page = 1,
                    endReached = rs.size < 30,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = t.message)
            }
        }
    }

    fun loadMore(login: String) {
        val s = _state.value
        if (s.loading || s.endReached || s.user == null) return
        viewModelScope.launch {
            _state.value = s.copy(loading = true)
            try {
                val next = s.page + 1
                val more = repo.userRepos(login, page = next)
                _state.value = s.copy(
                    loading = false,
                    repos = (s.repos + more).distinctBy { it.id },
                    page = next,
                    endReached = more.size < 30,
                )
            } catch (t: Throwable) {
                _state.value = s.copy(loading = false, error = t.message)
            }
        }
    }
}
