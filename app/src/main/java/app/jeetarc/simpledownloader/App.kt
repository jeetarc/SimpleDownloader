package app.jeetarc.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.app.Application
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
	}
	
	fun getDownloader(): SimpleDownloader {
		return downloader
	}
}
