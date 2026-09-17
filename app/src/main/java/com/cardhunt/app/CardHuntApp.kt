package com.cardhunt.app

import android.app.Application
import com.cloudinary.Configuration
import com.cloudinary.android.MediaManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import org.osmdroid.config.Configuration.getInstance as OsmConfig
import java.io.File
import javax.inject.Inject
import com.cardhunt.app.data.local.TokenStore

@HiltAndroidApp
class CardHuntApp : Application() {

    @Inject lateinit var tokenStore: TokenStore

    override fun onCreate() {
        super.onCreate()

        // osmdroid: private-storage tile cache → no WRITE_EXTERNAL_STORAGE permission needed
        OsmConfig().apply {
            load(this@CardHuntApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }

        // Cloudinary SDK (Req 1)
        MediaManager.init(
            this,
            Configuration.Builder().setCloudName(BuildConfig.CLOUDINARY_CLOUD_NAME).build()
        )

        // Synchronously hydrate the JWT cache so the OkHttp interceptor and
        // the auth gate can read it without suspend calls. Small one-time read.
        runBlocking { tokenStore.warmUp() }
    }
}