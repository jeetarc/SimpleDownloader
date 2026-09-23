package app.jeetarc.simpledownloader;

import android.app.Application
import android.content.Context

import com.jeet.simpledownloader.SimpleDownloader

class App : Application() {
	private lateinit var downloader: SimpleDownloader
	
	override fun onCreate() {
		super.onCreate()
		applicationContextRef = applicationContext
		
		downloader = SimpleDownloader.Builder(applicationContext)
		.setMaxConcurrent(3)
		.enableForeground(true)
		.enableNotifications(true)
		.enableHistory(true)
		.setConnectTimeout(15000)
		.setReadTimeout(15000)
		.setRetryCount(3)
		.setAutoRestore(true)
		.enableSorting(true)
		.build()
	}
	
	fun getDownloader(): SimpleDownloader {
		return downloader
	}
	
	companion object {
		private lateinit var applicationContextRef: Context
		fun getContext(): Context {
			return applicationContextRef
		}
	}
}
