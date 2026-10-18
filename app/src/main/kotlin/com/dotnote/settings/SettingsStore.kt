/**
 * Loads and saves [Settings] with Jetpack DataStore (a small file in app storage, written safely).
 *
 * Read the current value with [settings] (a `Flow` that updates on every change) and change it
 * with [update]. DataStore makes sure two updates never overwrite each other.
 */
package com.dotnote.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

class SettingsStore(
    context: Context,
) {
    private val dataStore: DataStore<Settings> = context.applicationContext.settingsDataStore

    /** The settings, updated whenever they change. */
    val settings: Flow<Settings> = dataStore.data

    /** The settings right now (waits for the first read). */
    suspend fun current(): Settings = dataStore.data.first()

    /** Changes the settings: [transform] gets the current value and returns the new one. */
    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.updateData { transform(it) }
    }
}

/** How [Settings] are turned into bytes and back. Unknown fields are ignored (newer versions). */
private object SettingsSerializer : Serializer<Settings> {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override val defaultValue = Settings()

    override suspend fun readFrom(input: InputStream): Settings =
        try {
            json.decodeFromString(Settings.serializer(), input.readBytes().decodeToString())
        } catch (error: SerializationException) {
            // DataStore calls its corruption handler for this (see below), which starts fresh.
            throw CorruptionException("Settings file is invalid", error)
        }

    override suspend fun writeTo(
        t: Settings,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(Settings.serializer(), t).toByteArray())
    }
}

// `by dataStore(...)` creates one DataStore per process for this file name.
private val Context.settingsDataStore: DataStore<Settings> by dataStore(
    fileName = "settings.json",
    serializer = SettingsSerializer,
    corruptionHandler =
        androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { error ->
            Log.w("SettingsStore", "Settings were unreadable; starting with defaults", error)
            Settings()
        },
)
