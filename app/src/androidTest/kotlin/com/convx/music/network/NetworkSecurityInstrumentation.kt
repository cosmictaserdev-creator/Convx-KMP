package com.convx.music.network

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.security.NetworkSecurityPolicy
import androidx.room.testing.MigrationTestHelper
import com.convx.music.artistvideo.ArtistVideoCanvasProvider
import com.convx.music.canvas.AppleMusicCatalog
import com.convx.music.db.InternalDatabase
import com.convx.music.db.MIGRATION_39_40
import com.music.lastfm.LastFM
import com.music.lrclib.LrcLib
import com.music.spotify.Spotify
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Runs against the installed debug app, whose Android trust policy JVM tests cannot exercise. */
class NetworkSecurityInstrumentation : Instrumentation() {
    private var proofMode: String? = null

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        proofMode = arguments?.getString("mode")
        start()
    }

    override fun onStart() {
        if (proofMode == "schema-migration") {
            runSchemaMigrationProof()
            return
        }

        val report = StringBuilder()
        try {
            val policy = NetworkSecurityPolicy.getInstance()
            check(policy.isCleartextTrafficPermitted("localhost"))
            check(!policy.isCleartextTrafficPermitted("open.spotify.com"))
            runBlocking {
                val targets = listOf(
                    Triple(Spotify, "getGqlClient", "https://api-partner.spotify.com/"),
                    Triple(LrcLib, "getClient", "https://lrclib.net/"),
                    Triple(AppleMusicCatalog, "getClient", "https://amp-api.music.apple.com/"),
                    Triple(LastFM, "getClient", "https://ws.audioscrobbler.com/2.0/"),
                    Triple(ArtistVideoCanvasProvider, "getClient", "https://www.apple.com/"),
                )
                for ((provider, getter, url) in targets) {
                    val method = provider.javaClass.declaredMethods.first {
                        it.name == getter || it.name.startsWith(getter + "$")
                    }.apply { isAccessible = true }
                    val client = method.invoke(provider) as HttpClient
                    check(client.engine.javaClass.simpleName == "OkHttpEngine") {
                        "${provider.javaClass.simpleName} must use Android-aware TLS"
                    }
                    withTimeout(20_000) {
                        val response = client.get(url) { expectSuccess = false }
                        // Any HTTP response, including an unauthenticated 401/404, proves TLS completed.
                        response.bodyAsText()
                        report.appendLine("${provider.javaClass.simpleName}: HTTP ${response.status.value}")
                    }
                }
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report.toString()) })
        } catch (failure: Exception) {
            finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("stream", report.toString() + "FAILED: ${failure.javaClass.simpleName}: ${failure.message}")
            })
        }
    }

    private fun runSchemaMigrationProof() {
        val databaseName = "schema-39-to-40-proof.db"
        val report = StringBuilder()
        try {
            targetContext.deleteDatabase(databaseName)
            val helper = MigrationTestHelper(this, InternalDatabase::class.java)
            helper.createDatabase(databaseName, 39).use { oldDatabase ->
                oldDatabase.execSQL(
                    """
                    INSERT INTO song (id, title, duration, liked, totalPlayTime)
                    VALUES ('kmp-migration-proof', 'Preserved song', 210000, 1, 321)
                    """.trimIndent()
                )
            }

            helper.runMigrationsAndValidate(databaseName, 40, true, MIGRATION_39_40).use { migratedDatabase ->
                migratedDatabase.query(
                    "SELECT title, liked, totalPlayTime, isPodcastEpisode FROM song WHERE id = 'kmp-migration-proof'"
                ).use { cursor ->
                    check(cursor.moveToFirst()) { "Song row disappeared during migration" }
                    check(cursor.getString(0) == "Preserved song") { "Song title changed during migration" }
                    check(cursor.getInt(1) == 1) { "Liked state changed during migration" }
                    check(cursor.getLong(2) == 321L) { "Play time changed during migration" }
                    check(cursor.getInt(3) == 0) { "New podcast flag did not default to false" }
                }
            }
            report.appendLine("Schema 39→40 preserved library data and initialized isPodcastEpisode=false")
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report.toString()) })
        } catch (failure: Throwable) {
            report.appendLine("FAILED: ${failure.javaClass.simpleName}: ${failure.message}")
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", report.toString()) })
        } finally {
            targetContext.deleteDatabase(databaseName)
        }
    }
}
