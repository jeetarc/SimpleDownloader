package app.jeetarc.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.app.Application
import android.net.Uri
import android.widget.Toast

import com.jeet.simpledownloader.DownloadTask
import com.jeet.simpledownloader.SimpleDownloader

class App : Application() {
	private lateinit var downloader: SimpleDownloader
	
	override fun onCreate() {
		super.onCreate()
		
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
        
		downloader.setDeleteOnRemoval(true)
        
		downloader.addListener(object : SimpleDownloader.Listener() {
			override fun onComplete(outputUri: Uri, task: DownloadTask) {
				showToast("Download complete!")
			}
			
			override fun onError(outputUri: Uri, err: Exception, task: DownloadTask) {
                showToast("Download failed: " + err.message)
			}
		})
	}
	
	fun getDownloader(): SimpleDownloader {
		return downloader
	}
	
    private fun showToast(message: String) {
		Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
	}
}
