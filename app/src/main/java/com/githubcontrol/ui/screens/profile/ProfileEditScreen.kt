package com.githubcontrol.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.repository.GitHubRepository
import com.githubcontrol.ui.components.EmbeddedTerminal
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.utils.Logger
import com.githubcontrol.utils.friendlyGhError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
                form.value = ProfileForm(loading = false, error = friendlyGhError(t))
            }
        }
    }
    fun update(transform: (ProfileForm) -> ProfileForm) { form.value = transform(form.value) }

    /**
     * Build a `PATCH /user` body that contains *only* the fields the user
     * touched. We send empty strings (not null) for cleared text fields so
     * GitHub actually clears them — the previous code stripped null fields
     * via `explicitNulls = false`, which made "clear bio and save" silently
     * do nothing and was one of the causes of the 422.
     */
    private fun buildPatchBody(f: ProfileForm): JsonObject = buildJsonObject {
        val o = f.original
        if (f.name != o.name)       put("name",             JsonPrimitive(f.name))
        if (f.email != o.email)     put("email",            JsonPrimitive(f.email))
        if (f.blog != o.blog)       put("blog",             JsonPrimitive(f.blog))
        if (f.bio != o.bio)         put("bio",              JsonPrimitive(f.bio))
        if (f.company != o.company) put("company",          JsonPrimitive(f.company))
        if (f.location != o.location) put("location",       JsonPrimitive(f.location))
        if (f.twitter != o.twitter) put("twitter_username", JsonPrimitive(f.twitter))
        if (f.hireable != o.hireable) put("hireable",       JsonPrimitive(f.hireable))
    }

    fun save() {
        val f = form.value
        val body = buildPatchBody(f)
        if (body.isEmpty()) {
            form.value = f.copy(message = "No changes to save.")
            return
        }
        viewModelScope.launch {
            form.value = f.copy(saving = true, error = null, message = null)
            try {
                repo.updateMe(body)
                Logger.i("Profile", "saved profile updates: keys=${body.keys}")
                form.value = f.copy(
                    saving = false, message = "Saved.",
                    original = ProfileSnapshot(
                        f.name, f.email, f.blog, f.bio, f.company, f.location, f.twitter, f.hireable
                    )
                )
            } catch (t: Throwable) {
                Logger.e("Profile", "save failed", t)
                form.value = f.copy(saving = false, error = friendlyGhError(t))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(onBack: () -> Unit, vm: ProfileEditViewModel = hiltViewModel()) {
    val f by vm.form.collectAsState()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Edit profile") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { pad ->
        // Render loading and ready states as separate sibling branches so that
        // when `loading` flips false Compose doesn't crash with the
        // "Start/end imbalance" runtime error (an early `return@Column` after
        // emitting the spinner reliably triggers that bug on Android 15).
        if (f.loading) {
            Column(Modifier.padding(pad).padding(12.dp).fillMaxSize()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        } else {
            Column(
                Modifier.padding(pad).padding(12.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(f.hireable, { v -> vm.update { it.copy(hireable = v) } })
                        Spacer(Modifier.width(8.dp)); Text("Available for hire")
                    }
                }
                Button(onClick = { vm.save() }, enabled = !f.saving, modifier = Modifier.fillMaxWidth()) {
                    if (f.saving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    else { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(6.dp)); Text("Save changes") }
                }
                EmbeddedTerminal(section = "Profile")
            }
        }
    }
}
