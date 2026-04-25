package com.githubcontrol.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/**
 * Turn a Throwable into a human-readable message.
 *
 * GitHub returns rich JSON error bodies on 4xx/5xx (`{"message":"...","errors":[{"resource":"...","field":"...","code":"..."}],"documentation_url":"..."}`).
 * Retrofit hides those behind `HttpException` whose default `message` is just `HTTP 422`,
 * which is exactly what users were seeing in the app. This pulls the underlying details out.
 */
fun friendlyGhError(t: Throwable): String {
    if (t !is HttpException) return t.message ?: t::class.java.simpleName

    val code = t.code()
    val body = runCatching { t.response()?.errorBody()?.string().orEmpty() }.getOrNull().orEmpty()
    val parsed = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()

    val mainMessage = parsed?.get("message")?.jsonPrimitive?.contentOrNull
    val fieldErrors: List<String> = parsed?.get("errors")?.let { el ->
        runCatching {
            el.jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                val field = o["field"]?.jsonPrimitive?.contentOrNull
                val code2 = o["code"]?.jsonPrimitive?.contentOrNull
                val msg = o["message"]?.jsonPrimitive?.contentOrNull
                listOfNotNull(field, code2, msg).joinToString(": ").ifEmpty { null }
            }
        }.getOrDefault(emptyList())
    }.orEmpty()

    val pieces = buildList {
        add("HTTP $code")
        mainMessage?.let { add(it) }
        if (fieldErrors.isNotEmpty()) add(fieldErrors.joinToString("; "))
    }
    return pieces.joinToString(" — ")
}

private fun JsonObject.optString(key: String): String? =
    get(key)?.jsonPrimitive?.contentOrNull
