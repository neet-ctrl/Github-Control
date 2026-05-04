package com.githubcontrol.utils

import android.content.Context
import android.net.Uri
import com.githubcontrol.data.auth.Account
import com.githubcontrol.data.auth.AccountManager
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Settings + accounts export / import.
 *
 * Version 2: tokens ARE included so that importing on a new device automatically
 * signs in all backed-up accounts without asking the user to re-paste PATs.
 * Accounts that already exist on the importing device (matched by id) are left
 * untouched — their local token / validation history is kept.
 *
 * SECURITY: the exported JSON file contains live PATs. Keep it safe.
 */
object SettingsBackup {

    @Serializable
    data class AccountStub(
        val id: String,
        val login: String,
        val name: String? = null,
        val avatarUrl: String? = null,
        val tokenType: String? = null,
        val scopes: List<String> = emptyList(),
        val token: String = "",
        val email: String? = null,
        val tokenExpiry: String? = null
    )

    @Serializable
    data class Backup(
        val version: Int = 2,
        val createdAt: Long = System.currentTimeMillis(),
        val appVersion: String = BuildInfo.VERSION_NAME,
        // Appearance
        val theme: String = "system",
        val accent: String = "blue",
        val dynamicColor: Boolean = false,
        val amoled: Boolean = false,
        val fontScale: Float = 1.0f,
        val monoFontScale: Float = 1.0f,
        val density: String = "comfortable",
        val cornerRadius: Int = 14,
        val terminalTheme: String = "github-dark",
        // Behavior
        val biometricEnabled: Boolean = false,
        val autoLockMinutes: Int = 5,
        val dangerousMode: Boolean = false,
        val authorName: String? = null,
        val authorEmail: String? = null,
        // Accounts — tokens included from v2 onward
        val accounts: List<AccountStub> = emptyList()
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun snapshot(am: AccountManager): Backup = Backup(
        theme           = am.themeFlow.first(),
        accent          = am.accentColorFlow.first(),
        dynamicColor    = am.dynamicColorFlow.first(),
        amoled          = am.amoledFlow.first(),
        fontScale       = am.fontScaleFlow.first(),
        monoFontScale   = am.monoFontScaleFlow.first(),
        density         = am.densityFlow.first(),
        cornerRadius    = am.cornerRadiusFlow.first(),
        terminalTheme   = am.terminalThemeFlow.first(),
        biometricEnabled = am.biometricEnabledFlow.first(),
        autoLockMinutes = am.autoLockMinutesFlow.first(),
        dangerousMode   = am.dangerousModeFlow.first(),
        authorName      = am.authorNameFlow.first(),
        authorEmail     = am.authorEmailFlow.first(),
        accounts        = am.accountsBlocking().map {
            AccountStub(
                id          = it.id,
                login       = it.login,
                name        = it.name,
                avatarUrl   = it.avatarUrl,
                tokenType   = it.tokenType,
                scopes      = it.scopes,
                token       = it.token,
                email       = it.email,
                tokenExpiry = it.tokenExpiry
            )
        }
    )

    fun encode(b: Backup): String = json.encodeToString(b)
    fun decode(text: String): Backup = json.decodeFromString(text)

    fun writeToUri(ctx: Context, uri: Uri, text: String) {
        ctx.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) }
    }

    fun readFromUri(ctx: Context, uri: Uri): String? =
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }

    /**
     * Apply a backup to [am].
     *
     * - All appearance / behavior settings are always overwritten.
     * - For accounts: any account whose [id] is NOT already present is added
     *   automatically using the stored token. Accounts that already exist on
     *   this device are left untouched (their local state is preserved).
     *
     * Returns the number of accounts that were newly added.
     */
    suspend fun apply(am: AccountManager, b: Backup): Int {
        // Appearance
        am.setTheme(b.theme)
        am.setAccent(b.accent)
        am.setDynamicColor(b.dynamicColor)
        am.setAmoled(b.amoled)
        am.setFontScale(b.fontScale)
        am.setMonoFontScale(b.monoFontScale)
        am.setDensity(b.density)
        am.setCornerRadius(b.cornerRadius)
        am.setTerminalTheme(b.terminalTheme)
        am.setBiometric(b.biometricEnabled)
        am.setAutoLockMinutes(b.autoLockMinutes)
        am.setDangerous(b.dangerousMode)
        am.setAuthor(b.authorName, b.authorEmail)

        // Accounts — skip ones that are already present
        val existing = am.accountsBlocking().map { it.id }.toSet()
        var added = 0
        for (stub in b.accounts) {
            if (stub.id in existing) continue
            if (stub.token.isBlank()) continue
            val account = Account(
                id              = stub.id,
                login           = stub.login,
                avatarUrl       = stub.avatarUrl ?: "",
                name            = stub.name,
                email           = stub.email,
                token           = stub.token,
                scopes          = stub.scopes,
                tokenType       = stub.tokenType,
                tokenExpiry     = stub.tokenExpiry,
                lastValidatedAt = 0L
            )
            am.addOrReplaceAccount(account, makeActive = false)
            added++
        }
        return added
    }
}
