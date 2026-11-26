package app.jeetarc.simpledownloader

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.jeetarc.simpledownloader.SimpleDownloader

class App : Application() {
    private lateinit var downloader: SimpleDownloader

    override fun onCreate() {
        super.onCreate()
        mAppContext = applicationContext
        
            downloader = SimpleDownloader.Builder(applicationContext)
                .setMaxConcurrent(3)
                .enableForeground(true)
                .enableNotifications(true)
                .enableHistory(true)
                .setConnectTimeout(15000)
                .setReadTimeout(15000)
                .setRetryCount(3)
                .setHoldSlotOnPause(true)
                .setAutoRestore(true)
                .enableSorting(true)
                .build()

            downloader.setDeleteOnRemoval(true)
    }

    fun getDownloader(): SimpleDownloader {
        return downloader
    }

    companion object {
        private lateinit var mAppContext: Context
        fun getContext(): Context = mAppContext

        fun showToast(msg: String) {
            Toast.makeText(mAppContext, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
