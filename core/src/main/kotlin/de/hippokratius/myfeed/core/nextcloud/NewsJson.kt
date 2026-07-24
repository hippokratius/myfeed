package de.hippokratius.myfeed.core.nextcloud

import java.io.InputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * JSON-Konfiguration für die News-API: tolerant gegenüber unbekannten Feldern
 * (API wächst), Zahlen in Anführungszeichen (ältere Server liefern lastModified
 * teils als String) und null für Felder mit Default.
 */
object NewsJson {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun parseFolders(stream: InputStream): List<NewsFolder> =
        json.decodeFromStream<NewsFoldersResponse>(stream).folders

    @OptIn(ExperimentalSerializationApi::class)
    fun parseFeeds(stream: InputStream): NewsFeedsResponse =
        json.decodeFromStream(stream)

    @OptIn(ExperimentalSerializationApi::class)
    fun parseItems(stream: InputStream): List<NewsItem> =
        json.decodeFromStream<NewsItemsResponse>(stream).items

    @OptIn(ExperimentalSerializationApi::class)
    fun parseVersion(stream: InputStream): NewsVersion =
        json.decodeFromStream(stream)

    @OptIn(ExperimentalSerializationApi::class)
    fun parseFolder(stream: InputStream): NewsFolder? =
        json.decodeFromStream<NewsFoldersResponse>(stream).folders.firstOrNull()
}
