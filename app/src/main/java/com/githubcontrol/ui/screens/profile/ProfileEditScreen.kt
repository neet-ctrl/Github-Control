package com.githubcontrol.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.UpdateUserRequest
import com.githubcontrol.data.repository.GitHubRepository
import com.githubcontrol.ui.components.EmbeddedTerminal
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Snapshot of the values returned by /user, used to compute a minimal PATCH on save. */
data class ProfileSnapshot(
    val name: String = "", val email: String = "", val blog: String = "",
    val bio: String = "", val company: String = "", val location: String = "",
    val twitter: String = "", val hireable: Boolean = false
)

data class ProfileForm(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val name: String = "", val email: String = "", val blog: String = "",
    val bio: String = "", val company: String = "", val location: String = "",
    val twitter: String = "", val hireable: Boolean = false,
    val original: ProfileSnapshot = ProfileSnapshot(),
    val message: String? = null, val error: String? = null
)

@HiltViewModel
class ProfileEditViewModel @Inject constructor(private val repo: GitHubRepository) : ViewModel() {
    val form = MutableStateFlow(ProfileForm())
    init {
        viewModelScope.launch {
            try {
                val u = repo.api.me()
                val snap = ProfileSnapshot(
                    name = u.name.orEmpty(), email = u.email.orEmpty(),
                    blog = u.blog.orEmpty(), bio = u.bio.orEmpty(),
                    company = u.company.orEmpty(), location = u.location.orEmpty(),
                    twitter = u.twitterUsername.orEmpty(), hireable = u.hireable ?: false
                )
                form.value = ProfileForm(
                    loading = false,
                    name = snap.name, email = snap.email, blog = snap.blog, bio = snap.bio,
                    company = snap.company, location = snap.location, twitter = snap.twitter,
                    hireable = snap.hireable, original = snap
                )
            } catch (t: Throwable) {
                form.value = ProfileForm(loading = false, error = t.message)
            }
        }
    }
    fun update(transform: (ProfileForm) -> ProfileForm) { form.value = transform(form.value) }
    fun save() {
        val f = form.value
        // Send only what actually changed so we don't trip GitHub's "you are not allowed to
        // change `hireable`" or similar restrictions when the field is left untouched.
        val o = f.original
        // GitHub treats explicit "" as "clear this field", but rejects null on some fields with 422.
        // Send the actual edited string (including "") for any field the user touched; leave others as null.
        val req = UpdateUserRequest(
            name = if (f.name != o.name) f.name else null,
            email = if (f.email != o.email) f.email else null,
            blog = if (f.blog != o.blog) f.blog else null,
            bio = if (f.bio != o.bio) f.bio else null,
            company = if (f.company != o.company) f.company else null,
            location = if (f.location != o.location) f.location else null,
            twitterUsername = if (f.twitter != o.twitter) f.twitter else null,
            hireable = if (f.hireable != o.hireable) f.hireable else null
        )
        if (req.name == null && req.email == null && req.blog == null && req.bio == null
            && req.company == null && req.location == null && req.twitterUsername == null
            && req.hireable == null) {
            form.value = f.copy(message = "No changes to save.")
            return
        }
        viewModelScope.launch {
            form.value = f.copy(saving = true, error = null, message = null)
            try {
                repo.updateMe(req)
                Logger.i("Profile", "saved profile updates (changed-fields-only)")
                form.value = f.copy(
                    saving = false, message = "Saved.",
                    original = ProfileSnapshot(
                        f.name, f.email, f.blog, f.bio, f.company, f.location, f.twitter, f.hireable
                    )
                )
            } catch (t: Throwable) {
                Logger.e("Profile", "save failed", t)
                form.value = f.copy(saving = false, error = t.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(onBack: () -> Unit, vm: ProfileEditViewModel = hiltViewModel()) {
    val f by vm.form.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Edit profile") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(12.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (f.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
            f.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            f.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            GhCard {
                Text("Public profile", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(f.name, { v -> vm.update { it.copy(name = v) } }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(f.email, { v -> vm.update { it.copy(email = v) } }, label = { Text("Public email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(f.blog, { v -> vm.update { it.copy(blog = v) } }, label = { Text("Website") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(f.company, { v -> vm.update { it.copy(company = v) } }, label = { Text("Company") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(f.location, { v -> vm.update { it.copy(location = v) } }, label = { Text("Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(f.twitter, { v -> vm.update { it.copy(twitter = v) } }, label = { Text("Twitter / X username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(f.bio, { v -> vm.update { it.copy(bio = v) } }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(f.hireable, { v -> vm.update { it.copy(hireable = v) } })
                    Spacer(Modifier.width(8.dp)); Text("Available for hire")
                }
            }
            Button(onClick = { vm.save() }, enabled = !f.saving, modifier = Modifier.fillMaxWidth()) {
                if (f.saving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                else { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(6.dp)); Text("Save changes") }
            }
            }
            EmbeddedTerminal(section = "Profile")
        }
    }
}
