## SimpleDownloader - Complete Guide

[![](https://jitpack.io/v/simpledownloader/SimpleDownloader.svg)](https://jitpack.io/#simpledownloader/SimpleDownloader)
> Jeet - Creator and maintainer.

**Table of Contents**

1. [Overview](#overview) 
2. Installation
3. Quick Start
4. Core Concepts
5. API Reference
6. Advanced Usage
7. Best Practices
8. FAQ

### Overview

SimpleDownloader is an Android download library that handles everything from basic file downloads to complex scenarios with multiple concurrent downloads, automatic retry, network awareness, database persistence, and many more.

**Key Features**

- ✅ Pure Android SDK (only OkHttp as dependency)
- ✅ Scoped Storage support - DocumentFile API
- ✅ Database persistence - Survives app restarts
- ✅ Auto file naming - Extract from URL or timestamp
- ✅ ETA calculation - Smooth speed averaging with stall detection
- ✅ Priority system - NEXT, HIGH, NORMAL, LOW
- ✅ Network awareness - Auto-pause on network loss
- ✅ Wi-Fi only mode - Save mobile data
- ✅ Queue locking - Lock downloads in queue position
- ✅ Resumable downloads - Range header support
- ✅ Rich callbacks - 13 lifecycle events
- ✅ Batch operations - Pause/resume/remove by status or priority
- ✅ Context listener cleanup - Prevent memory leaks
- ✅ Configuration cloning - Reuse previous config with withConfig()
- ✅ And many more


### Installation

**Add JitPack repository**

```gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

**Add dependency**

```gradle
dependencies {
    implementation 'com.github.simpledownloader:SimpleDownloader:1.0.0-beta3'
}
```

**Required imports**

```java
// Import based on what your project needs
import com.jeet.simpledownloader.SimpleDownloader;
import com.jeet.simpledownloader.DownloadTask;
import com.jeet.simpledownloader.Status;
import com.jeet.simpledownloader.Priority;
import com.jeet.simpledownloader.FileName;
import com.jeet.simpledownloader.MimeType;
import com.jeet.simpledownloader.TaskDatabase;

// Or import all
import com.jeet.simpledownloader.*;
```

**Optional dependency**

SimpleDownloader uses [AndroidX DocumentFile](https://developer.android.com/jetpack/androidx/releases/documentfile) and [OkHttp](https://github.com/square/okhttp). If it doesn't get pulled automatically, add:
- `implementation "androidx.documentfile:documentfile:1.0.1"`
- `implementation "com.squareup.okhttp3:okhttp:4.12.0"`

### Quick Start

**1. Initialization & Configuration (Optional)**

```java
public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize
        SimpleDownloader.with(this)
        .setMaxConcurrent(3)
        .enableRetryOnNetworkGain(true)
        .enableHistory(true);
        
    }
}
```

**2. Basic Download**

```java
SimpleDownloader.with(context)
    .setOutput(downloadsUri, "file.pdf", "application/pdf")
    .setFileUrl("https://example.com/file.pdf")
    .startDownload();
```

**3. With Listener**

```java
SimpleDownloader.with(context)
    .setOutput(downloadsUri, FileName.AUTO, MimeType.AUTO)
    .setFileUrl(url)
    .addListener(new SimpleDownloader.Listener() {
        @Override
        public void onProgress(long id, int progress, long speed, long eta, DownloadTask task) {
                progressBar.setProgress(progress);
                textView.setText(progress + "% - " + formatSpeed(speed));
        }
        
        @Override
        public void onComplete(long id, Uri uri, DownloadTask task) {
            Toast.makeText(context, "Download complete!", Toast.LENGTH_SHORT).show();
        }
        
        @Override
        public void onError(long id, Uri uri, Exception e, DownloadTask task) {
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    })
    .startDownload();
```

### Core Concepts

**DownloadTask Object**

The DownloadTask object represents a single download and provides access to all download information:

```java
DownloadTask task = SimpleDownloader.getTask(id);

// Download info
task.getId(); // return long
task.getFileUrl(); // return String
task.getFileName(); // return String
task.getMimeType(); // return String
task.getOutputFileUri(); // return Uri
task.getOutputFile(); // return DocumentFile

// Progress
task.getProgress(); // return int (0-100)
task.getDownloadedBytes(); // return long
task.getTotalBytes(); // return long
task.getSpeed(); // return long
task.getEtaMs(); // return long (millisecond)
task.getCreatedAt(); // return long

// Configuration
task.getUserAgent(); // return String
task.getHeaders(); // return Map<String, String>
task.getCookies(); // return String
task.isWifiOnly(); // return boolean
task.getBufferSize(); // return int
task.getProgressInterval(); // return int
task.getConnectTimeout(); // return int
task.getReadTimeout(); // return int
task.getMaxRetries(); // return int
task.getPriority(); // return Priority
task.getStatus(); // return Status

// State checks
task.isQueued(); // return boolean
task.isPaused(); // return boolean
task.isActive(); // return boolean
task.isLockedInQueue(); // return boolean
task.isDeleteOnRemoval(); // return boolean

// Control
task.pause();
task.resume();
task.cancel();
task.requeue();
task.remove();
task.retry();
task.setPriority(Priority.HIGH);
task.setLockedInQueue(true);
task.setDeleteOnRemoval(true);
```

**Status Enum**

```java
QUEUED // Waiting in queued
CONNECTING // Connecting to server
DOWNLOADING // downloading
STARTING // Starting
PAUSED // Download paused
CANCELLED // Cancelled by user
WAITING_FOR_NETWORK // Waiting for network
RETRYING // Retrying after failure
COMPLETED // Successfully finished
FAILED // Download failed
```

**Priority Enum**

```java
NEXT // Highest priority - goes to front of queue
HIGH // High priority, processes early
NORMAL // Default priority
LOW // Lowest priority

// usage
Priority.HIGH
```

**FileName Enum**

```java
AUTO // Extract filename from URL
TIME_MILLIS // Generate timestamp-based name (yyyyMMdd_HHmmss)

// usage
FileName.AUTO
```

**MimeType Enum**

```java
AUTO // Detect from URL

// usage
MimeType.AUTO
```

**Network Type Constants**

```java
SimpleDownloader.NETWORK_TYPE_NONE
SimpleDownloader.NETWORK_TYPE_UNKNOWN
SimpleDownloader.NETWORK_TYPE_WIFI
SimpleDownloader.NETWORK_TYPE_CELLULAR
SimpleDownloader.NETWORK_TYPE_ETHERNET
SimpleDownloader.NETWORK_TYPE_BLUETOOTH
SimpleDownloader.NETWORK_TYPE_VPN
SimpleDownloader.NETWORK_TYPE_USB
SimpleDownloader.NETWORK_TYPE_ROAMING
```

### API Reference

**Initialization & Configuration**

```java
SimpleDownloader.with(Context) // Initialize library (returns builder)
SimpleDownloader.withConfig(Context) // Clone previous configuration for new download
SimpleDownloader.setMaxConcurrent(int) // Set maximum parallel downloads
SimpleDownloader.enableRetryOnNetworkGain(boolean) // Auto-resume when network returns
SimpleDownloader.enableHistory(boolean) // Keep completed downloads in database
SimpleDownloader.enableSorting(boolean) // Enable/disable auto-sorting of task list
SimpleDownloader.setDownloadOnSlotFree(boolean) // Auto-start next queued task when slot free
SimpleDownloader.releaseCallbacks(Context) // Remove all listeners for context
SimpleDownloader.releaseCallbacks(id, Context) // Remove specific listener
```

**Builder Methods (chainable)**

```java
SimpleDownloader.with(context)
.setOutput(Uri, String, String) // Save to folder with custom name and MIME
.setOutput(Uri, FileName, MimeType) // Auto-generate name and MIME
.setOutput(Uri, String, MimeType) // Custom name, auto MIME
.setOutput(Uri, FileName, String) // Auto name, custom MIME
.overwrite(Uri) // Overwrite existing file
.setFileUrl(String) // Download URL (required)
.setUserAgent(String) // Custom User-Agent
.setHeader(String, String) // Add HTTP header
.setHeaders(Map) // Add multiple HTTP headers
.setCookies(String) // Add cookies
.setId(long) // Custom download ID
.setRetryCount(int) // Retry attempts on failure
.setConnectTimeout(int) // Connection timeout (ms)
.setReadTimeout(int) // Read timeout (ms)
.setProgressInterval(long) // Progress callback interval (ms)
.setBufferSize(int) // Download buffer size (bytes)
.setPriority(Priority) // Download priority
.wifiOnly(boolean) // Wi-Fi only mode
.setLockedInQueue(boolean) // Lock download in queue position
.setDeleteOnRemoval(boolean) // Delete file when download removed
.addListener(Listener) // Add callback listener
.startDownload() // Start the download (returns DownloadTask)
.getTask() // Get last created task
```

**Global Control Methods**

```java
SimpleDownloader.pause() // Pause all downloads
SimpleDownloader.pause(long) // Pause specific download
SimpleDownloader.pause(Priority) // Pause all downloads with priority
SimpleDownloader.resume() // Resume all downloads
SimpleDownloader.resume(long) //Resume specific download
SimpleDownloader.resume(Priority) // Resume all downloads with priority
SimpleDownloader.cancel() // Cancel all downloads
SimpleDownloader.cancel(long) // Cancel specific download
SimpleDownloader.remove() // Remove all downloads from registry and database
SimpleDownloader.remove(long) // Remove specific download
SimpleDownloader.remove(Status) // Remove all downloads with status
SimpleDownloader.remove(Priority) // Remove all downloads with priority
SimpleDownloader.requeue() // Move all downloads to back of queue
SimpleDownloader.requeue(long) // Move specific download to back of queue
SimpleDownloader.retry() // Retry all failed downloads
SimpleDownloader.retry(long) // Retry specific failed download
SimpleDownloader.setPriority(long, Priority) // Change priority after start
SimpleDownloader.setLockedInQueue(long, boolean) // Lock/unlock download in queue
SimpleDownloader.setDeleteOnRemoval(long, boolean) // Set delete on removal flag
```

**Global query Methods**

```java
SimpleDownloader.getTask(long) // DownloadTask Get DownloadTask by ID
SimpleDownloader.getTasks() // ArrayList<DownloadTask> Get all tasks (auto-sorted)
SimpleDownloader.getTasks(Status) // ArrayList<DownloadTask> Get tasks by status
SimpleDownloader.getTasks(Priority) // ArrayList<DownloadTask> Get tasks by priority
SimpleDownloader.getTasks(String) // ArrayList<DownloadTask> Get tasks by MIME type
SimpleDownloader.getCreatedAt(long) // long Get creation timestamp
SimpleDownloader.getOutputFileUri(long) // Uri Get output URI by ID
SimpleDownloader.getTotalCount() // int Total downloads count
SimpleDownloader.getQueuedCount() // int Queued downloads count
SimpleDownloader.getActiveCount() // int Active downloads count
SimpleDownloader.getPausedCount() // int Paused downloads count
SimpleDownloader.getOccupiedCount() // int Occupied slots count
SimpleDownloader.isDownloading() // boolean Any download active?
SimpleDownloader.isDownloading(long) // boolean Specific download active?
SimpleDownloader.hasTask(long) // boolean Check if download exists
SimpleDownloader.hasTask(String) // boolean Check if URL exists
```

**Database Methods (via TaskDatabase)**

```java
SimpleDownloader.with(context)
.TaskDatabase.loadTaskData(long) // Load download by ID
.TaskDatabase.loadTasksData() // Load all downloads from database
.TaskDatabase.loadTasksData(Status) // Load downloads by status
.TaskDatabase.loadTasksData(Priority) // Load downloads by priority
.TaskDatabase.loadTasksData(String) // Load downloads by MIME type

TaskDatabase.removeTasksData() // Clear/Remove all task Data fram database
TaskDatabase.removeTaskData(long) // Clear/Remove specific task data from database by ID
```

**Listener Interface (default methods)**

```java
@Override
public void onStart(long id, DownloadTask task) {} // Download started
@Override
public void onQueued(long id, int queuePosition, boolean lockedInQueue, DownloadTask task) {} // Added to queue
@Override
public void onProgress(long id, int progress, long speedPerSec, long etaMs, DownloadTask task) {} // Progress update
@Override
public void onPaused(long id, DownloadTask task) {} // Download paused
@Override
public void onResumed(long id, DownloadTask task) {} // Download resumed
@Override
public void onCancelled(long id, DownloadTask task) {} // Download cancelled
@Override
public void onComplete(long id, Uri outputFileUri, DownloadTask task) {} // Download completed
@Override
public void onError(long id, Uri outputFileUri, Exception error, DownloadTask task) {} // Error occurred
@Override
public void onRemoved(long id, boolean deleteOnRemoval, DownloadTask task) {} // Download removed
@Override
public void onRetrying(long id, int attempt, int maxAttempts, DownloadTask task) {} // Retry attempt
@Override
public void onWaitingForNetwork(long id, int networkType, DownloadTask task) {} // Waiting for network
@Override
public void onStatusChanged(long id, Status status, DownloadTask task) {} // Status changed
@Override
public void onLoadDatabase(long id, int progress, DownloadTask task) {} // Restored from database
```

### Advanced Usage

**1. Auto File Naming**

```java
// Auto-extract filename from URL
SimpleDownloader.with(context)
    .setOutput(downloadsUri, FileName.AUTO, MimeType.AUTO)

// Timestamp-based naming (ex. 20240331_143022.pdf)
SimpleDownloader.with(context)
    .setOutput(downloadsUri, FileName.TIME_MILLIS, MimeType.AUTO)
```

**2. Clone Configuration with `withConfig()`**

```java
// Set configuration once
SimpleDownloader.with(context)
    .setOutput(uri, FileName.AUTO, MimeType.AUTO)
    .setPriority(Priority.HIGH)
    .wifiOnly(true)
    .setRetryCount(3)

// Reuse same config for multiple downloads
SimpleDownloader.withConfig(context)
    .setFileUrl(url2)
    .startDownload();

SimpleDownloader.withConfig(context)
    .setFileUrl(url3)
    .setPriority(Priority.NORMAL) // overwrite the configuration
    .startDownload();
```

**3. Restore Downloads After App Restart**

```java
public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
SimpleDownloader.with(this).TaskDatabase.loadTasksData();
        SimpleDownloader.resume();
    }
}
```

**4. Lock Downloads in Queue Position**


```java
SimpleDownloader.setLockedInQueue(downloadId, true);
task.setLockedInQueue();

// Using builder
SimpleDownloader.with(context)
    .setLockedInQueue(true)
    .startDownload();
```

**5. Wi-Fi Only Downloads**

```java
SimpleDownloader.with(context)
    .wifiOnly(true)  // Will pause on mobile data
    .startDownload();
```

**6. Priority Management**

```java
SimpleDownloader.with(context)
    .setPriority(Priority.NEXT)
    .startDownload();

// also later
task.setPriority(priority);
SimpleDownloader.setPriority(id,priority);

```

**7. Retry Failed Downloads**

```java
// In your error handling
@Override
public void onError(long id, Uri uri, 
        SimpleDownloader.retry(id);
}

SimpleDownloader.retry(); // Or retry all failed
task.retry(); // retry specific task
```

**8. Download with Custom Headers & Cookies**

```java
// Single header
SimpleDownloader.with(context)
    .setHeader("Authorization", "Bearer " + token)
    .setCookies("sessionId=" + sessionId)
    .startDownload();

// Multiple headers
headers.put("Key1", "Value1");
headers.put("Key2", "Value2");
SimpleDownloader.with(context)
    .setHeaders(headers)
    .startDownload();
```

**9. Foreground Service Integration**

```java
public class DownloadService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();
        
        SimpleDownloader.with(this)
        .setMaxConcurrent(3)
        .TaskDatabase.loadTasksData(); // Restore downloads
        
        // Start foreground
        startForeground(1001, createNotification());
        
        // Listen for updates
        SimpleDownloader.with(this).addListener(new SimpleDownloader.Listener() {
            @Override
            public void onProgress(long id, int progress, long speed, long eta, DownloadTask task) {
                updateNotification(task.getFileName(), progress);
            }
            
            @Override
            public void onComplete(long id, Uri uri, DownloadTask task) {
                updateNotification(task.getFileName() + " complete!", 100);
            }
            
            @Override
            public void onWaitingForNetwork(long id, int networkType, DownloadTask task) {
                updateNotification("Waiting for network...", task.getProgress());
            }
        });
    }
}
```

**10. Batch Operations**

```java
// Pause all video downloads
for (DownloadTask task : SimpleDownloader.getTasks()) {
    if (task.getMimeType().startsWith("video/")) {
        task.pause();
    }
}

// Remove all completed downloads
SimpleDownloader.remove(Status.COMPLETED);

// Remove all failed downloads
SimpleDownloader.remove(Status.FAILED);

// Requeue all paused downloads
SimpleDownloader.requeue();
```

**11. History Mode (default disabled)**

```java
SimpleDownloader.enableHistory(true);
```
**12. Delete on Removal (default disabled)**

```java
SimpleDownloader.setDeleteOnRemoval(downloadId, true); // Delete file when removing
SimpleDownloader.remove(downloadId);  // File deleted

// using task
task.setDeleteOnRemoval(true)
task.remove();
// Or chainable
task.setDeleteOnRemoval(true).remove();

// Or on builder
SimpleDownloader.with(context)
    .setDeleteOnRemoval(true)
    .startDownload();
```

### Best Practices

**1. Initialize in Application**

```java
public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // One-time initialization
        SimpleDownloader.with(this)
        .setMaxConcurrent(3)
        .enableRetryOnNetworkGain(true)
        .enableSorting(true); // default enabled
    }
}
```

**2. Clean Up Listeners**

```java
@Override
protected void onDestroy() {
    super.onDestroy();
    // Prevent memory leaks
    SimpleDownloader.releaseCallbacks(this);
}
```

**3. Use `withConfig()` for Multiple Downloads**

```java
// Instead of repeating config
SimpleDownloader.withConfig(context)
    .setFileUrl(url2)
    .startDownload();
```

**4. Handle Storage Permissions**

```java
// For Android 10+ (Scoped Storage)
Intent treeIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
startActivityForResult(treeIntent, 101);

@Override
protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
	super.onActivityResult(requestCode, resultCode, data);
	
	if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
		Uri treeUri = data.getData();
		
		if (treeUri != null) {
			int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			getContentResolver().takePersistableUriPermission(treeUri, flags);
		}
	}
}
// Use treeUri in setOutput()
```

**5. Use Auto File Naming**

```java
// Instead of:
.setOutput(uri, "file1.pdf", "application/pdf")
.setOutput(uri, "file2.pdf", "application/pdf")

// Use:
.setOutput(uri, FileName.AUTO, MimeType.AUTO)
// Or:
.setOutput(uri, "file1.pdf", MimeType.AUTO)
```

**6. Enable Sorting for Better UI Experience (default enabled)**

```java
SimpleDownloader.enableSorting(true);
// Tasks are automatically sorted by: Status → Priority → Creation Date
```

**7. Handle Waiting for Network State**

```java
@Override
public void onWaitingForNetwork(long id, int networkType, DownloadTask task) {
    if (networkType == SimpleDownloader.NETWORK_TYPE_NONE) {
        textView.setText("Waiting for network...");
    }
}
```

### FAQ

**Q: How do I get the download ID after starting?**

```java
DownloadTask task = SimpleDownloader.with(context)
    .setOutput(uri, FileName.AUTO, MimeType.AUTO)
    .setFileUrl(url)
    .startDownload();

long id = task.getId();  // Store this for later control
```

**Q: Can I pause/resume downloads after app restart?**

```java
// In Application onCreate
SimpleDownloader.with(this).TaskDatabase.loadTasksData();  // Restore
SimpleDownloader.resume();  // Resume all
```

**Q: How do I check if a URL was already downloaded?**

```java
boolean exists = SimpleDownloader.hasTask(url);
```

**Q: How do I get all completed downloads?**

```java
ArrayList<DownloadTask> completed = SimpleDownloader.getTasks(Status.COMPLETED);
```

**Q: Does SimpleDownloader work on Android 10+?**

> Yes! It uses DocumentFile API for full Scoped Storage support.

**Q: How do I handle authentication?**

```java
.setHeader("Authorization", "Bearer " + token)
.setCookies("session=" + sessionId)
```

**Q: What's the difference between setOutput and overwrite?**

> - `setOutput` - Creates new file in a folder
> - `overwrite` - Downloads directly into an existing file

**Q: How do I change priority after download started?**

```java
SimpleDownloader.setPriority(id, Priority.HIGH);
// or
task.setPriority(Priority.HIGH);
```

**Q: Can I have multiple listeners?**

> Yes! Add multiple listeners to the same downloader instance.

**Q: What happens when network disconnects?**

> Downloads auto-pause. Enable `enableRetryOnNetworkGain(true)`(enabled by default) to auto-resume when network returns.

**Q: What is NEXT priority?**

> NEXT is the highest priority that forces a download to start next immediately when a slot become free, bypassing the others priority entirely.
```java
SimpleDownloader.setPriority(id Priority.NEXT);
//or
task.setPriority(Priority.NEXT)
```

**Q: What is `withConfig()?`**

> `withConfig()` clones the configuration from the previous download. Set config once, reuse for multiple downloads.

**Q: How do I lock a download in queue position?**

```java
SimpleDownloader.setLockedInQueue(downloadId, true);
// Locked downloads stay at front of queue
```

**Q: How do I keep the file when removing from list?**

```java
SimpleDownloader.setDeleteOnRemoval(downloadId, false);
SimpleDownloader.remove(downloadId);  // File stays
```

### Support

GitHub Issues: https://github.com/simpledownloader/SimpleDownloader/issues

If you find this library useful, please consider starring it on GitHub! ⭐
