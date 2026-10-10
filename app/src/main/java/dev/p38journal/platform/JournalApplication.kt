package dev.p38journal.platform

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class JournalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        Notifications.schedule(this)
    }
}
