## SimpleDownloader - Guide

[![](https://jitpack.io/v/jeetarc/SimpleDownloader.svg)](https://jitpack.io/#jeetarc/SimpleDownloader)

> A simple API for smart and modern downloads.

**Table of Contents**

1. [Overview](#overview)
2. [Installation](#installation)
3. [Quick Start](#quick-start)
4. [API Reference](#api-reference)
5. [Advanced Usage](#advanced-usage)
6. [Best Practices](#best-practices)
7. [FAQ](#faq)
8. [Support](#support)

### Overview

SimpleDownloader is an Android file download library built for fast and secure downloads on modern Android devices. It supports multiple concurrent downloads, queue management, pause/resume/retry/cancel/remove/requeue controls, priority handling, smart network handling, scoped storage, persistence across app restart, speed, ETA, rich listener callbacks, and many more.

**Key Features**

- ✅ Modern Android download manager library
- ✅ Android 11+ to latest version support
- ✅ Pause, smart resume, cancel, retry, remove, and requeue downloads
- ✅ Queue system with priority, locking, and concurrent download control
- ✅ handle authentication via `.setHeaders(...)`, `.setCookies(...)`
- ✅ Network-aware downloading with Wi-Fi-only mode and auto resume on network back
- ✅ Database persistence for restoring tasks after app restart
- ✅ Auto file naming, MIME detection, speed, ETA, and progress tracking
- ✅ Rich listener callbacks with lifecycle, active-state, and status events
- ✅ Detailed error handling with `DownloadException`, `RetryPolicy` configuration
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
    implementation 'com.github.jeetarc:SimpleDownloader:1.0.0-beta'
}
```

**Required imports**

```java
// Import based on what your project needs
import com.jeet.simpledownloader.SimpleDownloader;
import com.jeet.simpledownloader.DownloadTask;
import com.jeet.simpledownloader.DownloadException;
import com.jeet.simpledownloader.RetryPolicy;
import com.jeet.simpledownloader.Status;
import com.jeet.simpledownloader.Priority;
import com.jeet.simpledownloader.FileName;
import com.jeet.simpledownloader.MimeType;

// Or import all
import com.jeet.simpledownloader.*;
```

**Optional dependency**

SimpleDownloader uses AndroidX DocumentFile and OkHttp. If they don't get pulled automatically, add:

- `implementation "androidx.documentfile:documentfile:1.0.1"`
- `implementation "com.squareup.okhttp3:okhttp:4.12.0"`

### Quick Start

**1. Initialization & Configuration (Optional)**

```java
public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        SimpleDownloader.with(this)
            .setMaxConcurrent(3)
            .enableRetryOnNetworkGain(true)
            .enableHistory(true)
            .setDownloadOnSlotFree(true);
    }
}
```

**2. Basic Download**

```java
SimpleDownloader.with(context)
    .setOutput(downloadsUri, "file.pdf", "application/pdf")
    .setFileUrl("https://example.com/file.pdf")
    .startDownload();

// Auto File Name & MIME
SimpleDownloader.with(context)
    .setOutput(downloadsUri, FileName.AUTO, MimeType.AUTO)
    .setFileUrl(url)
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
        public void onError(long id, Uri uri, Exception error, DownloadTask task) {
            Toast.makeText(context, "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    })
    .startDownload();
```

### API Reference

**Builder Methods (chainable)**

```java
SimpleDownloader
    .with(context)
    .withConfig(context) // Reuse Configuration
    .setOutput(Uri, String, String) // Save to folder with custom name and MIME
    .setOutput(Uri, FileName, MimeType) // Auto-generate name and MIME
    .setOutput(Uri, String, MimeType) // Custom name, auto MIME
    .setOutput(Uri, FileName, String) // Auto name, custom MIME
    .overwrite(Uri) // Overwrite existing file
    .setFileUrl(String) // Download URL (required)
    .setUserAgent(String) // Custom User-Agent
    .setHeader(String, String) // Add HTTP header
    .setHeaders(Map<String, String>) // Add multiple HTTP headers
    .setCookies(String) // Add cookies
    .setId(long) // Custom download ID
    .setRetryCount(int) // Retry attempts on failure
    .setRetryPolicy(RetryPolicy) // Custom retry policy
    .setConnectTimeout(int) // Connection timeout in ms
    .setReadTimeout(int) // Read timeout in ms
    .setProgressInterval(long) // Progress callback interval in ms
    .setBufferSize(int) // Download buffer size in bytes
    .setPriority(Priority) // Download priority
    .wifiOnly(boolean) // Wi-Fi only mode
    .setLockedInQueue(boolean) // Lock download in queue
    .setDeleteOnRemoval(boolean) // Delete file when task removed
    .addListener(Listener) // Add callback listener
    .startDownload() // Start download and return DownloadTask
    .getTask() // Get last created task
```

**DownloadTask Info Methods**

```java
DownloadTask task = SimpleDownloader.getTask(id);

// Basic info
task.getId() // long
task.getFileUrl() // String
task.getFileName() // String
task.getMimeType() // String
task.getOutputFileUri() // Uri
task.getOutputFile() // DocumentFile
task.getCreatedAt() // long

// Progress
task.getProgress() // int 0-100
task.getDownloadedBytes() // long
task.getTotalBytes() // long
task.getSpeed() // long bytes/sec
task.getEtaMs() // long milliseconds

// Config info
task.getUserAgent() // String
task.getHeaders() // Map<String, String>
task.getCookies() // String
task.isWifiOnly() // boolean
task.getBufferSize() // int
task.getProgressInterval() // long
task.getConnectTimeout() // int
task.getReadTimeout() // int
task.getMaxRetries() // int
task.getPriority() // Priority
task.getStatus() // Status
task.getTreeUri() // Uri
task.getOverwriteUri() // Uri
task.isDeleteOnRemoval() // boolean
task.isLockedInQueue() // boolean
```

**DownloadTask State Methods**

```java
task.isQueued() // boolean
task.isPaused() // boolean
task.isActive() // boolean
task.isWaitingForNetwork() // boolean
task.isFinished() // boolean completed/failed/cancelled
task.getError() // Exception final error after FAILED
```

**DownloadTask Control Methods**

```java
task.pause()
task.resume()
task.cancel()
task.requeue()
task.remove()
task.retry()
task.forceDownload()
task.wifiOnly(boolean)
task.setPriority(Priority)
task.setLockedInQueue(boolean)
task.setDeleteOnRemoval(boolean)
task.addListener(SimpleDownloader.Listener)
task.removeListener(SimpleDownloader.Listener)
task.releaseCallbacks()
```

**Initialization & Global Configuration**

```java
SimpleDownloader.with(Context) // Initialize library and return builder
          .setMaxConcurrent(int) // Set maximum parallel downloads
          .enableRetryOnNetworkGain(boolean) // Auto-resume when network returns
          .enableHistory(boolean) // Keep completed/cancelled/failed tasks in database/list
          .enableSorting(boolean) // Enable/disable auto-sorting of task list
          .setDownloadOnSlotFree(boolean) // Auto-start queued task when slot becomes free
SimpleDownloader.releaseCallbacks(Context) // Remove listeners attached by this context
```

**Network APIs**

```java
SimpleDownloader.isNetworkAvailable() // boolean Current network available?
SimpleDownloader.getNetworkType() // int Current network type
SimpleDownloader.getNetworkTypeName() // String Current network type name
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

**Global Control Methods**

```java
SimpleDownloader.pause() // Pause all downloads
SimpleDownloader.pause(long) // Pause specific download
SimpleDownloader.pause(Priority) // Pause downloads by priority

SimpleDownloader.resume() // Resume all downloads
SimpleDownloader.resume(long) // Resume specific download
SimpleDownloader.resume(Priority) // Resume downloads by priority

SimpleDownloader.cancel() // Cancel all downloads
SimpleDownloader.cancel(long) // Cancel specific download

SimpleDownloader.remove() // Remove all tasks
SimpleDownloader.remove(long) // Remove specific task
SimpleDownloader.remove(Status) // Remove tasks by status
SimpleDownloader.remove(Priority) // Remove tasks by priority

SimpleDownloader.requeue() // Requeue all possible tasks
SimpleDownloader.requeue(long) // Requeue specific task

SimpleDownloader.retry() // Retry all failed tasks
SimpleDownloader.retry(long) // Retry specific failed task

SimpleDownloader.forceDownload(long) // Force queued task to start
SimpleDownloader.setPriority(long, Priority) // Change priority after start
SimpleDownloader.setLockedInQueue(long, boolean) // Lock/unlock queued task
SimpleDownloader.setDeleteOnRemoval(long, boolean) // Set delete on removal flag
SimpleDownloader.wifiOnly(long, boolean) // Change Wi-Fi-only mode after start
```

**Global Query Methods**

```java
SimpleDownloader.getTask(long) // DownloadTask by ID
SimpleDownloader.getTasks() // ArrayList<DownloadTask> all tasks
SimpleDownloader.getTasks(Status) // ArrayList<DownloadTask> by status
SimpleDownloader.getTasks(Priority) // ArrayList<DownloadTask> by priority
SimpleDownloader.getTasks(String) // ArrayList<DownloadTask> by MIME type

SimpleDownloader.getCreatedAt(long) // long
SimpleDownloader.getOutputFileUri(long) // Uri
SimpleDownloader.getTotalCount() // int
SimpleDownloader.getQueuedCount() // int
SimpleDownloader.getActiveCount() // int
SimpleDownloader.getPausedCount() // int
SimpleDownloader.getOccupiedCount() // int

SimpleDownloader.isDownloading() // boolean any active download?
SimpleDownloader.isDownloading(long) // boolean specific active download?
SimpleDownloader.hasTask(long) // boolean check task by ID
SimpleDownloader.hasTask(String) // boolean check task by URL
```

**Database Methods (via TaskDatabase)**

```java
SimpleDownloader downloader = SimpleDownloader.with(context)
           .setMaxConcurrent(3);

downloader.TaskDatabase.loadTaskData(long) // Load task by ID
downloader.TaskDatabase.loadTasksData() // Load all tasks
downloader.TaskDatabase.loadTasksData(Status) // Load tasks by status
downloader.TaskDatabase.loadTasksData(Priority) // Load tasks by priority
downloader.TaskDatabase.loadTasksData(String) // Load tasks by MIME type

downloader.TaskDatabase.removeTaskData(long) // Remove database task by ID
downloader.TaskDatabase.removeTasksData() // Clear all database tasks
```

**Listener Interface**

Overwrite only the callbacks you whant.

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
public void onError(long id, Uri outputFileUri, Exception error, DownloadTask task) {} // Final error after retries

@Override
public void onRemoved(long id, boolean deleteOnRemoval, DownloadTask task) {} // Download removed

@Override
public void onRetrying(long id, int attempt, int maxAttempts, DownloadTask task) {} // Retry attempt

@Override
public void onWaitingForNetwork(long id, int networkType, DownloadTask task) {} // Waiting for network

@Override
public void onActiveChanged(long id, boolean isActive, DownloadTask task) {} // Active/non-active changed

@Override
public void onLifecycleChanged(long id, int lifecycle, DownloadTask task) {} // Lifecycle started/ended

@Override
public void onStatusChanged(long id, Status status, DownloadTask task) {} // Status changes updates

@Override
public void onLoadDatabase(long id, int progress, DownloadTask task) {} // when Restored from database
```

**Lifecycle Constants**

```java
DownloadTask.LIFECYCLE_STARTED // 1
DownloadTask.LIFECYCLE_ENDED // 0
```

**Status Enum**

```java
STARTING // Starting
QUEUED // Waiting in queue
CONNECTING // Connecting to server
DOWNLOADING // Downloading
PAUSED // Paused by user
CANCELLED // Cancelled by user
WAITING_FOR_NETWORK // Waiting for network
RETRYING // Retrying after failure
COMPLETED // Successfully finished
FAILED // Download failed
```

**Priority Enum**

```java
NEXT // Highest priority - starts next when possible
HIGH // High priority
NORMAL // Default priority
LOW // Lowest priority
```

**FileName Enum**

```java
AUTO // create automatically
TIME_MILLIS // Generate current timestamp based name
```

**MimeType Enum**

```java
AUTO // Detect automatically
```

**RetryPolicy**

```java
RetryPolicy.ofAttempts(int maxRetries) // Create retry policy from retry attempts
RetryPolicy policy = RetryPolicy.ofAttempts(3);

RetryPolicy policy = RetryPolicy.builder()
    .maxRetries(5)
    .initialDelayMs(1000)   // Start with 1 second
    .multiplier(2.0)         // Double each time
    .maxDelayMs(30000)       // Cap at 30 seconds
    .build();

```

**DownloadException types**

```java
NETWORK_LOST
TIMEOUT
DNS_ERROR
SSL_ERROR
HTTP_ERROR
ENOSPC
FILE_ERROR
OUTPUT_URI_INVALID
STORAGE_PERMISSION_DENIED
RANGE_NOT_SUPPORTED
EMPTY_RESPONSE
CANCELLED
UNKNOWN
```
**DownloadException info methods**

```java
DownloadException error = task.getError();
error.getType();       // DownloadException.Type
error.getCode();       // HTTP status code or -1
error.isRetryable();   // boolean
error.getMessage();    // Clean readable message
error.toString();      // Full error
error.getCause();      // Original raw exception if available
```

### Advanced Usage

**Auto File Naming**

```java
// Auto-extract filename from URL
SimpleDownloader.with(context)
    .setOutput(downloadsUri, FileName.AUTO, MimeType.AUTO)
    .setFileUrl(url)
    .startDownload();

// Timestamp-based naming
SimpleDownloader.with(context)
    .setOutput(downloadsUri, FileName.TIME_MILLIS, MimeType.AUTO)
    .setFileUrl(url)
    .startDownload();
```

**Reuse Configuration with `withConfig()`**

```java
SimpleDownloader base = SimpleDownloader.with(context)
    .setOutput(downloadsUri, FileName.AUTO, MimeType.AUTO)
    .setCookies(cookies)
    .setRetryCount(3)
    .wifiOnly(true);

base.withConfig(context)
    .setFileUrl(url1)
    .startDownload();

base.withConfig(context)
    .setFileUrl(url2)
    .wifiOnly(false) // overwrite reused config
    .startDownload();
```

**Retry Policy Setup**

```java
RetryPolicy policy = RetryPolicy.builder()
    .maxRetries(5)
    .initialDelayMs(1000)   // Start with 1 second
    .multiplier(2.0)         // Double each time
    .maxDelayMs(30000)       // Cap at 30 seconds
    .build();

SimpleDownloader.with(context)
    .setRetryPolicy(policy) // apply
    .setFileUrl(url)
```

**Lock Downloads in Queue**

```java
SimpleDownloader.setLockedInQueue(downloadId, true);

// Or using task
task.setLockedInQueue(true);

// Using builder
SimpleDownloader.with(context)
    .setLockedInQueue(true)
    .startDownload();
```

**Download with Custom Headers & Cookies**

```java
SimpleDownloader.with(context)
    .setHeader("Authorization", "Bearer " + token)
    .setCookies("sessionId=" + sessionId)
    .setFileUrl(url)
    .startDownload();

Map<String, String> headers = new HashMap<>();
headers.put("Key1", "Value1");
headers.put("Key2", "Value2");

SimpleDownloader.with(context)
    .setHeaders(headers)
    .setFileUrl(url)
    .startDownload();
```

**Handle `onActiveChanged(...)`**

`isActive=true` when task becomes active, like connecting/downloading/retrying/etc.
`isActive=false` when task leaves active running state, like pause/cancel/etc.

```java
@Override
public void onActiveChanged(long id, boolean isActive, DownloadTask task) {
    if (isActive) {
        // Task became active
    } else {
        // Task became inactive
    }
}
```

**Handle `onLifecycleChanged(...)`**

`DownloadTask.LIFECYCLE_STARTED` means the task entered its running lifecycle.
`DownloadTask.LIFECYCLE_ENDED` means the task ended completely (complete, error, cancel, remove, etc).

```java
@Override
public void onLifecycleChanged(long id, int lifecycle, DownloadTask task) {

    if (lifecycle == DownloadTask.LIFECYCLE_STARTED) {
        // Task lifecycle started
        acquiredWakelock(); // example

    } else if (lifecycle == DownloadTask.LIFECYCLE_ENDED) {
        // Task lifecycle ended
        releaseWakelock(); // example
    }
}
```

**DownloadException handling**

```java

// Usage
@Override
public void onError(long id, Uri outputFileUri, Exception error, DownloadTask task) {
    if (error instanceof DownloadException) {
        DownloadException e = (DownloadException) error;
        
        DownloadException.Type type = e.getType();
        int code = e.getCode();
        boolean retryable = e.isRetryable();
        String message = e.getMessage();
        Throwable cause = e.getCause();
        
        Log.e("Download", e.toString());
        
        if (retryable) {
            retryButton.setVisibility(View.VISIBLE);
        }
        
        errorText.setText(message);
    }
}
```

**Batch Operations**

```java
// Pause all video downloads
for (DownloadTask task : SimpleDownloader.getTasks()) {
    if (task.getMimeType() != null && task.getMimeType().startsWith("video/")) {
        task.pause();
    }
}

// Remove completed downloads
SimpleDownloader.remove(Status.COMPLETED);

// Remove failed downloads
SimpleDownloader.remove(Status.FAILED);

// Requeue all possible tasks
SimpleDownloader.requeue();
```

**Force a Queued Download**

```java
SimpleDownloader.forceDownload(downloadId);

// Or using task
task.forceDownload();
```

**Disable Auto-Start on Free Slot**

```java
SimpleDownloader.with(context)
    .setMaxConcurrent(3)
    .setDownloadOnSlotFree(false);

// Later enable again
SimpleDownloader.with(context)
    .setDownloadOnSlotFree(true);
```

### Best Practices

**1. Initialize in Application**

```java
public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        SimpleDownloader.with(this)
            .setMaxConcurrent(3)
            .enableRetryOnNetworkGain(true)
            .enableSorting(true)
            .setDownloadOnSlotFree(true);
    }
}
```

**2. Clean Up Listeners (mandatory to avoid memory leaks)**

```java
@Override
protected void onDestroy() {
    super.onDestroy();

    SimpleDownloader.releaseCallbacks(this);

//Or using task
task.releaseCallbacks() // clear all listeners of this task across activities
}
```

**3. Use `withConfig()` for Multiple Downloads**

```java
SimpleDownloader base = SimpleDownloader.with(context)
    .setOutput(uri, FileName.AUTO, MimeType.AUTO)
    .setRetryCount(3);

base.withConfig(context)
    .setFileUrl(url2)
    .startDownload();
```

**4. Take persistable permissions**

```java
intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);

// inside onActivityResult
if (treeUri != null) {
    int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                   | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

    getContentResolver().takePersistableUriPermission(treeUri, flags);
}
```

**5. Use Auto File Naming (if name/mime type unknown)**

```java
.setOutput(uri, FileName.AUTO, MimeType.AUTO)

// or
.setOutput(uri, "video1.mp4", MimeType.AUTO)
```

**6. Handle Waiting for Network**

```java
@Override
public void onWaitingForNetwork(long id, int networkType, DownloadTask task) {
    if (networkType == SimpleDownloader.NETWORK_TYPE_NONE) {
        textView.setText("Waiting for network...");
    } else {
        textView.setText("Waiting for preferred network...");
    }
}
```

### FAQ

**Q: How do I get the download ID after starting?**

```java
DownloadTask task = SimpleDownloader.with(context)
    .setOutput(uri, "file1.zip", "application/zip")
    .setFileUrl(url)
    .startDownload();

long id = task.getId();
```

**Q: What is MIME type? How do I get it?**

> MIME type tells Android what kind of file it is, like PDF, image, video, audio, APK, ZIP, etc.

> Examples of MIME types:
`application/pdf`, `image/jpeg`, `image/png`, `video/mp4`, `audio/mpeg`, `application/zip`, `application/vnd.android.package-archive`, etc.

> You can provide it. if it's unknown, SimpleDownloader can detect it for you, just use `MimeType.AUTO`:
```java
SimpleDownloader.with(context)
    .setOutput(uri, name, MimeType.AUTO)
    .setFileUrl(url)
    .startDownload();
```

**Q: What is the difference between `onActiveChanged()` and `onLifecycleChanged()`?**

> `onActiveChanged()` tells you when a task enters or leaves an active running state like `CONNECTING`, `DOWNLOADING`, or `RETRYING`. It can fire multiple times for the same task during pause, resume, retry, network changes, etc.

> `onLifecycleChanged()` tells you when the full task lifecycle starts and ends.
It fires only once for start and once for end.

**Q: What is `withConfig()`?**

> `withConfig()` clones the current builder configuration into a new builder. You can reuse output, headers, cookies, retry, priority, and other settings, then overwrite only the fields you need.

**Q: How do I get the tree URI for output?**

> Tree URI is the folder permission URI returned by Android’s folder picker. SimpleDownloader uses it with `setOutput(...)` to create files inside the selected folder.

> a) Open the folder picker (via `ACTION_OPEN_DOCUMENT_TREE`):
```java
Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
startActivityForResult(intent, 101);
```

> b) Handle the result in `onActivityResult(...)`:
```java
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
        Uri treeUri = data.getData();
        if (treeUri != null) {
            int flags = data.getFlags() & (
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
            getContentResolver().takePersistableUriPermission(treeUri, flags);

            // optionally Save this treeUri string in SharedPreferences for later use
            getSharedPreferences("app", MODE_PRIVATE)
                     .edit().putString("saved_tree_uri", treeUri.toString())
                     .apply();
        }
    }
}
```

> c) Use it with `.setOutput(uri, ...)`:
```java
String savedUri = getSharedPreferences("app", MODE_PRIVATE).getString("saved_tree_uri", null);
if (savedUri != null) {
    Uri treeUri = Uri.parse(savedUri);
    SimpleDownloader.with(context)
        .setOutput(treeUri, "video1.mp4", MimeType.AUTO) // here
        .setFileUrl(url)
        .startDownload();
}
```

**Q: How do I check if a URL already exists?**

```java
boolean exists = SimpleDownloader.hasTask(url);
```

**Q: How do I get all completed downloads?**

```java
ArrayList<DownloadTask> completed = SimpleDownloader.getTasks(Status.COMPLETED);
```

**Q: Does SimpleDownloader work on Android 10+?**

> Yes. It uses DocumentFile API for Scoped Storage support.

**Q: How do I handle authentication?**

```java
.setHeader("Authorization", "Bearer " + token)
.setCookies("session=" + sessionId)
```

**Q: What's the difference between `setOutput()` and `overwrite()`?**

> `setOutput()` creates a new file inside a selected folder. `overwrite()` writes directly into an existing file URI.

**Q: How do I change priority after download started?**

```java
SimpleDownloader.setPriority(id, Priority.HIGH);

// Or
task.setPriority(Priority.HIGH);
```

**Q: Can I have multiple listeners?**

> Yes. You can attach multiple listeners to a task.

**Q: What happens when network disconnects?**

> Downloads move to `WAITING_FOR_NETWORK`. If retry on network gain is enabled (enabled by default), they resume when network becomes available.

**Q: What is NEXT priority?**

> `NEXT` is the highest priority. It makes a task start before HIGH, NORMAL, and LOW tasks when a slot becomes free. use it when you want a task needed to be start next after a slot become free.

```java
SimpleDownloader.setPriority(id, Priority.NEXT);

// Or
task.setPriority(Priority.NEXT);
```

**Q: How to delete a file**

```java
SimpleDownloader.setDeleteOnRemoval(downloadId, true).remove(downloadId);

// using task
task.setDeleteOnRemoval(true).remove();
```

### Support

GitHub Issues: https://github.com/jeetarc/SimpleDownloader/issues

[Jeet](https://github.com/jeet-012) - Creator and maintainer.

If you find this library useful, please consider starring it on GitHub! ⭐
