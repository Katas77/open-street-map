package com.example.open_street_map

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Загружаем настройки OSMDroid
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))

        // Устанавливаем пути для кэша
        val basePath = getExternalFilesDir(null) ?: filesDir
        Configuration.getInstance().apply {
            osmdroidBasePath = basePath
            osmdroidTileCache = File(basePath, "tile")
            // Устанавливаем User-Agent (важно)
            userAgentValue = "com.example.open_street_map"
        }
    }
}
