# SimpleDownloader

[![JitPack](https://jitpack.io/v/jeetarc/SimpleDownloader.svg)](https://jitpack.io/#jeetarc/SimpleDownloader)
[![GitHub release](https://img.shields.io/github/v/release/jeetarc/SimpleDownloader?include_prereleases)](https://github.com/jeetarc/SimpleDownloader/releases)
![Android API](https://img.shields.io/badge/Android-API%2021%2B-3DDC84?logo=android&logoColor=white)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

SimpleDownloader is an Android download library. It handles the parts that usually make downloading difficult: queues, concurrent downloads, pause and resume, unstable networks, scoped storage, task persistence, foreground, notifications, progress updates, etc.

A simple API:

```java
DownloadTask task = SimpleDownloader.getInstance(context)
    .startDownload(DownloadRequest.from(folderPath, FileName.AUTO, fileUrl));
```

## Features

- Multi-download management
- Multiple downloads with queue and priority support
- automatic download concurrency
- Pause, resume, cancel, retry, remove, requeue, and force download
- Resume using HTTP range requests
- Network loss handling and Wi-Fi-only downloads
- Task persistence and restoration with filters.
- Complete built-in storage support for filesystem paths, SAF/document-tree output, and MediaStore output.
- Built-in Subfolder support
- Built-in file overwrite support
- Automatic file name and MIME type resolution
- Progress, speed, ETA, status, and lifecycle callbacks
- Progress, completion, and error notifications with actions, thumbnails, and more.
- Optional foreground execution
- Custom OkHttpClient support
- Custom task Comparator
- RetryPolicy, timeouts, headers, cookies, checksums and many more
- Minimum Android version: API 21

## Installation

Add JitPack to your repositories:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
}
```

Add SimpleDownloader to your app module:

```gradle
dependencies {
    implementation "com.github.jeetarc:SimpleDownloader:1.0.0"
}
```

SimpleDownloader is built with Java 8 and compileSdk 35.

## Setup

If you enable notifications, add:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```
On Android 13 and newer, request this permission at runtime.

If you enable `enableForeground(true)`, also add:
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```
Foreground mode automatically enables notifications. You can also use `enableNotifications(true)` without foreground mode.

If using normal filesystem path, add:
``` XML
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```
and request the permission at runtime.
Storage permissions are not required when saving to an app-specific folder or using folderUri via MediaStore or Storage Access Framework.

> All other required permissions and components are added by default.

## Quick start

**1. Get a SimpleDownloader instance**

```java
// get the default instance (simple)
SimpleDownloader downloader = SimpleDownloader.getInstance(context);

// Or create your own instance
SimpleDownloader downloader = new SimpleDownloader.Builder(context)
    .setMaxConcurrent(3)
    .setRetryCount(3)
    .enableHistory(true)
    .enableForeground(true)
    .setAutoRestore(true)
    .build();
```

**2. Start downloads using `downloader`**

SimpleDownloader has three built-in storage modes.

 a) Download into a filesystem folder:

```java
DownloadRequest request = DownloadRequest.from(folderPath, FileName.AUTO, fileUrl);
DownloadTask task = downloader.startDownload(request);
```

b) Download into a MediaStore collection (Android 10+):

```java
DownloadRequest request = DownloadRequest.from(MediaStore.Downloads.EXTERNAL_CONTENT_URI, FileName.AUTO, fileUrl);
DownloadTask task = downloader.startDownload(request);
```

You can also use other MediaStore collections:

```java
MediaStore.Images.Media.EXTERNAL_CONTENT_URI
MediaStore.Video.Media.EXTERNAL_CONTENT_URI
MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
```

c) Download into a selected folder via SAF/document-tree:

```java
DownloadRequest request = DownloadRequest.from(treeUri, FileName.AUTO, fileUrl);
DownloadTask task = downloader.startDownload(request);
```

Keep the URI permission:

```java
int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
getContentResolver().takePersistableUriPermission(treeUri, flags);
```

**You can also use a custom file name:** 
```java
DownloadRequest request = DownloadRequest.from(folderPath, "example.mp4", fileUrl);
```

> SimpleDownloader creates a new file inside each output folder. If the same name already exists, it creates a unique name automatically.

**Overwrite a file:**

a) Using a DocumentFile URI or specific MediaStore item URI

```java
DownloadRequest request = DownloadRequest.fromOverwrite(fileUri, fileUrl);
DownloadTask task = downloader.startDownload(request);
```

b) Using a file path:

```java
DownloadRequest request = DownloadRequest.fromOverwrite(file.getAbsolutePath(), fileUrl);
DownloadTask task = downloader.startDownload(request);
```

**Start multiple downloads:**

You can start several downloads together:

```java
List<DownloadRequest> requests = new ArrayList<>();
requests.add(DownloadRequest.from(folderUri, "one.mp4", url1));
requests.add(DownloadRequest.from(folderUri, "two.mp4", url2));
requests.add(DownloadRequest.from(folderUri, "three.mp4", url3));

List<DownloadTask> tasks = downloader.startDownloads(requests);
```

## SimpleDownloader

Create an independent downloader with a builder:

```java
SimpleDownloader downloader = new SimpleDownloader.Builder(context)
    .setMaxConcurrent(3)
    .setRetryCount(3)
    .enableHistory(true)
    .setAutoRestore(true)
    .build();
```

Available `SimpleDownloader.Builder` settings:
```java
.setOwnerId(String ownerId)
.setHttpClient(OkHttpClient client)
.setMaxConcurrent(int max)
.enableHistory(boolean enable)
.enableForeground(boolean enable)
.enableNotifications(boolean enable)
.setNotification(DownloadNotification notification)
.setProgressInterval(long ms)
.setConnectTimeout(int ms)
.setReadTimeout(int ms)
.setBufferSize(int bytes)
.setRetryCount(int count)
.setRetryPolicy(RetryPolicy retryPolicy)
.enableResumeOnNetworkGain(boolean enable)
.setAutoRestore(boolean enable)
.restoreTasks()
.restoreTasks(TaskField<?> field, Object value)
.enableSorting(boolean enable)
.setTaskComparator(Comparator<DownloadTask> comparator)
```

You can also use the default-owner downloader:

```java
SimpleDownloader downloader = SimpleDownloader.getInstance(context);
```
The default instance has a simple configuration. It is recommended to build your own instance for full features.
- `builder(context)` creates a new independent instance.
- `getInstance(context)` returns the application's default-owner instance.

### Owner ID

Use an owner ID when you want separate download profiles.

```java
SimpleDownloader downloader = SimpleDownloader.builder(context)
    .setOwnerId("media_downloads")
    .build();
```
Tasks restored by this downloader belong to its owner ID.

### Controls

Control a task through the downloader by ID:

```java
downloader.pause(id);
downloader.resume(id);
downloader.cancel(id);
downloader.retry(id);
downloader.requeue(id);
downloader.remove(id);
downloader.forceDownload(id);
```

Control multiple tasks:

```java
downloader.pauseAll();
downloader.resumeAll();
downloader.cancelAll();
downloader.retryAll();
downloader.requeueAll();
downloader.removeAll();
```

Update a task by ID:

```java
downloader.setWifiOnly(id, true);
downloader.setLockedInQueue(id, true);
downloader.setDeleteOnRemoval(id, true);
```

Find tasks:

```java
DownloadTask task = downloader.getTask(id);
List<DownloadTask> all = downloader.getTasks();
List<DownloadTask> completedTasks = downloader.getTasks(TaskField.STATUS, Status.COMPLETED);
DownloadTask task = downloader.getTask(TaskField.FILE_NAME, fileName);
```
`getTask(TaskField, value)` returns the latest matching task, or `null` when no task matches.
You can filter the task list by any supported `TaskField`.

Check task counts and state:

```java
downloader.getTotalCount();
downloader.getActiveCount();
downloader.getQueuedCount();
downloader.getOccupiedCount();
downloader.getEffectiveMaxConcurrent();
downloader.isDownloading();
downloader.isDownloading(id);
downloader.hasTask(id);
downloader.hasTask(fileUrl);
```

Common request fields can be set directly to the SimpleDownloader instance:
```java
.setSubFolder(String value)
.setHeaders(Map<String,String> headers)
.addHeader(String key, String value)
.setUserAgent(String value)
.setCookies(String value)
.setWifiOnly(boolean enable)
.setDeleteOnRemoval(boolean enable)
```
You can overwrite SimpleDownloader request fields by setting them again for each request using `DownloadRequest.builder()`

Downloader configuration and state can be read back with:

```java
downloader.getOwnerId();
downloader.getRetryPolicy();
downloader.getConnectTimeout();
downloader.getReadTimeout();
downloader.getProgressInterval();
downloader.getBufferSize();
downloader.getMaxConcurrent();
downloader.getEffectiveMaxConcurrent();
downloader.getSubFolder();
downloader.getHeaders();
downloader.getUserAgent();
downloader.getCookies();
downloader.isWifiOnlyDefault();
downloader.isDeleteOnRemovalDefault();
downloader.areNotificationsEnabled();
downloader.isForegroundEnabled();
downloader.isAdaptiveConcurrencyEnabled();
```

More:
- [Callbacks and Listeners](#Callbacks-and-Listeners)
- [Restore tasks](#Restore-tasks-&-Database)
- [Foreground and Notifications](#Foreground-and-Notifications)
- [Cleanup](#Cleanup)

## DownloadRequest

`DownloadRequest` is the description of a single download.

Shortcut factory methods:

```java
DownloadRequest.from(folderUri, fileName, fileUrl)
DownloadRequest.from(folderUri, FileName.AUTO, fileUrl)
DownloadRequest.from(folderPath, fileName, fileUrl)
DownloadRequest.from(folderPath, FileName.AUTO, fileUrl)
DownloadRequest.fromOverwrite(fileUri, fileUrl)
DownloadRequest.fromOverwrite(filePath, fileUrl)
```

Use the builder when you need more control.

```java
DownloadRequest request = DownloadRequest.builder()
    .setFileUrl(fileUrl)
    .setOutput(folderUri, FileName.AUTO)
    .addHeader("Authorization", "Bearer " + token)
    .setPriority(Priority.HIGH)
    .build();
```

Available request settings:

```java
.setId(long id)
.setFileUrl(String fileUrl)
.setOutput(Uri folderUri, String fileName)
.setOutput(Uri folderUri, FileName fileName)
.setOutput(String folderPath, String fileName)
.setOutput(String folderPath, FileName fileName)
.overwrite(String outputPath)
.overwrite(Uri fileUri)
.setSubFolder(String subFolder)
.setMimeType(String mimeType)
.setMimeType(MimeType mimeType)
.setUserAgent(String userAgent)
.addHeader(String key, String value)
.setHeaders(Map<String, String> headers)
.setCookies(String cookies)
.setChecksum(String algorithm, String checksum)
.setPriority(Priority priority)
.setWifiOnly(boolean wifiOnly)
.setLockedInQueue(boolean enable)
.setDeleteOnRemoval(boolean enable)
```
More:
- [Authorize via headers, cookies](#Authorization)
- [Set Subfolder](#Subfolder)
- [FileName and MimeType modes](#FileName-and-MimeType-modes)
- [Checksums](#Checksums)


## DownloadTask
`DownloadTask` is a live task object and provides access to its state, output, configuration, listeners, controls, etc.

### Control a task

```java
task.pause();
task.resume();
task.cancel();
task.retry();
task.requeue();
task.remove();
task.forceDownload();

// Change some task settings after creation:
task.setWifiOnly(true);
task.setLockedInQueue(true);
task.setDeleteOnRemoval(true);
```

Note:
- `cancel()` stops the task and deletes its output.
- `remove()` removes the task from SimpleDownloader register.
- `remove()` deletes the output only when `setDeleteOnRemoval(true)` is enabled.
- `forceDownload()` starts a queued task even when it is locked. It doesn't care about the concurrency limit

### Task information

```java
task.getId();
task.getFileUrl();
task.getFileName();
task.getMimeType();

task.getOutputUri();
task.getOutputFile();
task.getOutputDocumentFile();
task.getOutputFolderUri();
task.getOutputFolderPath();
task.getSubFolderPath();
task.getOutputPath();
task.getOverwriteUri();

task.getProgress();
task.getDownloadedBytes();
task.getTotalBytes();
task.getSpeed();
task.getEtaMs();

task.getStatus();
task.getPriority();
task.getError();
task.getCreatedAt();
task.getMaxRetryCount();

task.canPause();
task.canResume();
task.canRetry();

task.isActive();
task.isQueued();
task.isPaused();
task.isWaitingForNetwork();
task.isFinished();
task.isOccupiedSlot();
```

More:
- [Task status](#Task-status)
- [Understand TaskField](#TaskField)
- [Task Listeners](#DownloadTask.Listener)
- [Error handling](#Error-handling)

## Callbacks and Listeners
Use listeners and the observer to listen for download updates. All callbacks run on the main thread and are optional.

### SimpleDownloader.Listener

`SimpleDownloader.Listener` receives updates from every task owned by that downloader:

```java
SimpleDownloader.Listener listener = new SimpleDownloader.Listener() {
    @Override
    public void onProgress(long id, int progress, long speed, long etaMs, DownloadTask task) {
        progressBar.setProgress(progress);
        speedText.setText(Formator.formatSpeed(speed));
        etaText.setText(Formator.formatEta(etaMs));
    }

    @Override
    public void onComplete(long id, Uri outputUri, DownloadTask task) {
        // The download finished successfully.
    }

    @Override
    public void onError(long id, Uri outputUri, Exception error, DownloadTask task) {
        // The download failed.
    }
};

downloader.addListener(listener);
```

Available callbacks are:

```java
onStart(long id, DownloadTask task) {}
onQueued(long id, int position, DownloadTask task) {}
onProgress(long id, int progress, long speed, long etaMs, DownloadTask task) {}
onPaused(long id, DownloadTask task) {}
onResumed(long id, DownloadTask task) {}
onCancelled(long id, DownloadTask task) {}
onComplete(long id, Uri outputUri, DownloadTask task) {}
onError(long id, Uri outputUri, Exception error, DownloadTask task) {}
onRemoved(long id, boolean outputDeleted, DownloadTask task) {}
onRetry(long id, int attempt, DownloadTask task) {}
onWaitingForNetwork(long id, int networkType, DownloadTask task) {}
onStatusChanged(long id, Status status, DownloadTask task) {}
onActiveChanged(long id, boolean isActive, DownloadTask task) {}
onLifecycleChanged(long id, int lifecycle, DownloadTask task) {}
```

Remove listeners when they are no longer needed:

```java
downloader.removeListener(listener);
downloader.removeAllListeners();
```

### DownloadTask.Listener
`DownloadTask.Listener` receives updates only for the task it attached to.

```java
DownloadTask.Listener taskListener = new DownloadTask.Listener() {
    @Override
    public void onProgress(int progress, long speed, long etaMs) {
        // Updates for this task only.
    }

    @Override
    public void onComplete(Uri outputUri) {
        // This task finished.
    }
};

task.addListener(taskListener);
```

Available callbacks are:

```java
onStart()
onQueued(int position)
onProgress(int progress, long speed, long etaMs)
onPaused()
onResumed()
onCancelled()
onComplete(Uri outputUri)
onError(Uri outputUri, Exception error)
onRemoved(boolean outputDeleted)
onRetry(int attempt)
onWaitingForNetwork(int networkType)
onStatusChanged(Status status)
onActiveChanged(boolean isActive)
onLifecycleChanged(int lifecycle)
```
Remove listeners when they are no longer needed:

```java
task.removeListener(taskListener);
task.removeAllListeners();
```

> `onStart()` can run again when a task starts after resume or retry. Use `onLifecycleChanged()` when you need to know the beginning or end of the full task lifecycle.

### TaskListObaerver
`TaskListObserver` observes the full task list. Use it when showing all downloads in a list, RecyclerView, etc.

```java
TaskListObserver observer = new TaskListObserver() {
    @Override
    public void onTasksChanged(int size, List<DownloadTask> tasks) {
        // The list order changed.
    }

    @Override
    public void onTaskUpdated(long id, DownloadTask task) {
        // Update only the matching item.
    }
};

downloader.addObserver(observer);
```
The list passed to `onTasksChanged()` is an unmodifiable snapshot. The `DownloadTask` objects inside it are still live and can update.

Release it when not needed:

```java
downloader.removeObserver(observer);
```
**Full cleanup:**
- `SimpleDownloader.Listener`
- `DownloadTask.Listener`
- `TaskListObserver`

```java
downloader.releaseAllCallbacks();
```

## Restore tasks & Database

Restore methods are configured on the `SimpleDownloader.Builder`.
Only one restore mode can be configured on a builder:

- `setAutoRestore(true)`
- `restoreTasks()`
- `restoreTasks(field, value)`


### Automatic Restore

Enable automatic restoration of previously saved download tasks:

```java
SimpleDownloader downloader = SimpleDownloader.builder(context)
    .setAutoRestore(true)
    .build();
```

When enabled, SimpleDownloader restores the saved tasks for this owner and automatically resumes eligible tasks. Auto restore is disabled by default.


### Explicit restore

Restore all saved tasks

```java
SimpleDownloader downloader = new SimpleDownloader.Builder(context)
    .enableHistory(true)
    .restoreTasks()
    .build();
```

Restored active downloads are restored as paused. Resume the tasks you want to continue:

```java
downloader.resumeAll();
```

### Restore matching tasks

Restore only tasks matching a field and value:

```java
SimpleDownloader downloader = new SimpleDownloader.Builder(context)
    .restoreTasks(TaskField.STATUS, Status.PAUSED)
    .build();
```

Finished tasks are kept in the database only when history is enabled:

```java
SimpleDownloader downloader = SimpleDownloader.builder(context)
   .enableHistory(true)
   .built();
```

### Clear database

Clear the persisted data for the tasks form the database or reset the internal data table. 
This does not delete the downloaded file or remove the task from in-memory registry.

```java
SimpleDownloader.database().deleteForTask(taskId);
SimpleDownloader.database().deleteForOwner(ownerId);
SimpleDownloader.database().deleteForDefaultOwner();
SimpleDownloader.database().deleteForAll();
SimpleDownloader.database().resetTasksTable();
```

> Read the Javadoc for the methods before using. These can cause unwanted tasks data lose.

## Queue and concurrency

By default, SimpleDownloader uses automatic concurrency. It starts with 1 and can increase up to 10 slots based on download speed.

Automatic concurrency is enabled with `setMaxConcurrent(0)` or when it is not set.

Use a fixed number of concurrent downloads when needed:

```java
SimpleDownloader downloader = SimpleDownloader.builder(context)
    .setMaxConcurrent(3)
    .build();
```

There is also a global concurrency cap shared by downloader instances:

```java
SimpleDownloader.setGlobalConcurrent(5);
```
Use `0` to disable the global cap.

Stop queued tasks from starting automatically when a slot becomes free:

```java
downloader.setDownloadOnSlotFree(false);
```

Lock a queued task:

```java
task.setLockedInQueue(true);

// or
downloader.setLockedInQueue(id);
```

## Task Priorities

```java
Priority.HIGH
Priority.NORMAL
Priority.LOW

// HIGH > NORMAL > LOW
```

## Task status

Available statuses:
```java
Status.STARTING
Status.QUEUED
Status.CONNECTING
Status.DOWNLOADING
Status.PAUSED
Status.CANCELLED
Status.WAITING_FOR_NETWORK
Status.RETRYING
Status.COMPLETED
Status.FAILED
```

You can also use:
```java
status.getCode();
status.isActive();
status.isFinished();
```

## Retry policy

The default retry policy allows one automatic retry. Configure it when needed:

```java
RetryPolicy retryPolicy = RetryPolicy.builder()
    .maxRetryCount(3)
    .initialDelayMs(750)
    .multiplier(2.0)
    .maxDelayMs(30_000)
    .build();

SimpleDownloader downloader = SimpleDownloader.builder(context)
    .setRetryPolicy(retryPolicy)
    .build();
```

Or simply set the retry count:

```java
SimpleDownloader.builder(context)
    .setRetryCount(3)
    .build();
```

```java
RetryPolicy retryPolicy = RetryPolicy.ofAttempts(3);
```

Read the configured retry policy when needed:
```java
retryPolicy.getMaxRetryCount();
retryPolicy.getInitialDelayMs();
retryPolicy.getMaxDelayMs();
retryPolicy.getMultiplier();
```

## MIME Type

A MIME type is used to identify the format of a file. SimpleDownloader resolves it automatically, but you can set it explicitly:

```java
DownloadRequest request = DownloadRequest.builder()
    .setOutput(folderUri, "manual.pdf")
    .setMimeType("application/pdf")
    .setFileUrl(fileUrl)
    .build();
```

Or use automatic resolution:

```java
.setMimeType(MimeType.AUTO)
```

Resolve the MIME type from the final file name only:

```java
.setMimeType(MimeType.FROM_NAME)
```

## FileName and MimeType modes

```java
FileName.AUTO
FileName.TIME_BASED
```

```java
MimeType.AUTO
MimeType.FROM_NAME
```

- `FileName.AUTO` uses the URL, response headers, file extension, and content type to resolve the name.
- `FileName.TIME_BASED` creates a time-based file name.
- `MimeType.AUTO` uses the URL, response headers, file extension, and content type to resolve the MIME type.
- `MimeType.FROM_NAME` resolves it from the file name.

## Subfolder

A subfolder is an optional folder inside the main output folder where you want the downloaded file to be saved.

```java
DownloadRequest request = DownloadRequest.builder()
    .setOutput(folderUri, FileName.AUTO)
    .setSubFolder("App")
    .setFileUrl(fileUrl)
    .build();
```

Subfolders can be nested, for example `"App/videos"`. They work with filesystem paths, SAF/document-tree output, and MediaStore output.

## Authorization 
Authorize via headers, cookies, and a user agent.

```java
DownloadRequest request = DownloadRequest.builder()
    .setOutput(folderUri, FileName.AUTO)
    .setFileUrl(fileUrl)
    .setUserAgent(userAgent)
    .addHeader("Authorization", "Bearer " + token)
    .addHeader("Referer", pageUrl)   // or .setHeaders(headersMap);
    .setCookies("session=" + sessionId)
    .setWifiOnly(true)
    .build();
```

**You can set common authorization on the `downloader` directly:**

```java
Map<String, String> headers = new HashMap<>();
headers.put("Authorization", "Bearer " + token);
headers.put("Referer", pageUrl);
downloader.setHeaders(headers);

//Or add a single common header:
downloader.addHeader("Authorization", "Bearer " + token);
```

Set a common user agent or cookies:

```java
downloader.setUserAgent(userAgent);
downloader.setCookies(cookies);
```

## Network

```java
boolean available = downloader.isNetworkAvailable();
int networkType = downloader.getNetworkType();
```

Network constants:

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

By default, waiting tasks automatically resume when the network becomes available. Disable this when needed:

```java
downloader.enableResumeOnNetworkGain(false);
```

You can set Wi-Fi-only downloader too:

```java
downloader.setWifiOnly(true);
```
## Foreground and Notifications

Run tasks using a foreground service:

```java
SimpleDownloader downloader = SimpleDownloader.builder(context)
    .enableForeground(true)
    .build();
```
Make sure declare permissions in manufast. View [Setup guide](#Setup).

Notifications are disabled by default. Foreground mode automatically enables notifications.
- Foreground mode cannot be enabled without notifications.
- Notifications can be enabled without foreground mode.

```java
DownloadNotification notification = new DownloadNotification()
    .setChannelId("downloads")
    .setChannelName("Downloads")
    .setSmallIcon(R.drawable.ic_download)
    .setCompleteIcon(R.drawable.ic_download_done)
    .setErrorIcon(R.drawable.ic_download_error)
    .setColorAccent(0xFF0087E5);

SimpleDownloader downloader = SimpleDownloader.builder(context)
    .enableNotifications(true)
    .setNotification(notification)
    .build();
```

`DownloadNotification` configuration is optional. When notifications are enabled without a custom configuration, the default configuration is used.

Available notification configuration includes:

```java
.setChannelId(String channelId)
.setChannelName(String channelName)
.setChannelDescription(String channelDescription)
.setChannelImportance(int channelImportance)
.setForegroundNotificationId(int foregroundNotificationId)
.setSmallIcon(int smallIcon)
.setCompleteIcon(int completeIcon)
.setErrorIcon(int errorIcon)
.setColorAccent(int colorAccent)
.setColorAccentResource(int colorAccentRes)
.clearColorAccent()
.setColorized(boolean colorized)
.setSoundEnabled(boolean soundEnabled)
.setSound(Uri soundUri)
.setVibrationEnabled(boolean vibrationEnabled)
.setVibrationPattern(long[] vibrationPattern)
.setLockscreenVisibility(int visibility)
.setThumbnail(Bitmap bitmap)
.setThumbnailUrl(String url)
.setThumbnailUrl(String url, Map<String, String> headers)
.clearThumbnail()
.setShowThumbnail(boolean showThumbnail)
.setShowPauseAction(boolean showPauseAction)
.setShowCancelAction(boolean showCancelAction)
.setShowRetryAction(boolean showRetryAction)
.setShowCompleteNotification(boolean showCompleteNotification)
.setShowErrorNotification(boolean showErrorNotification)
.setNotificationUpdateInterval(long millis)
```

SimpleDownloader has a built-in thumbnail system for notifications. It can generate thumbnails from supported video, image, audio, APK, and PDF files.

You can also set a thumbnail directly:

```java
notification.setThumbnail(bitmap);

// Or load one from a URL:
notification.setThumbnailUrl(thumbnailUrl, thumbHeaders);
```
Pass `null` for headers when they are not needed.

## Checksums

Verify the completed file with algorithms supported by `MessageDigest`, such as SHA-256, SHA-1, or MD5:

```java
DownloadRequest request = DownloadRequest.builder()
    .setChecksum("SHA-256", expectedChecksum)
    .setOutput(folderUri, FileName.AUTO)
    .setFileUrl(fileUrl)
    .build();
```

## Error handling

Download failures are reported as `DownloadException` when the library can classify the failure:

```java
@Override
public void onError(Uri outputUri, Exception error) {
    if (!(error instanceof DownloadException)) return;

    DownloadException failure = (DownloadException) error;

    DownloadException.Type type = failure.getType();
    int httpCode = failure.getCode();
    boolean retryable = failure.isRetryable();
    Throwable cause = failure.getCause();
}
```

Error types:

```java
DownloadException.Type.NETWORK_LOST
DownloadException.Type.TIMEOUT
DownloadException.Type.DNS_ERROR
DownloadException.Type.SSL_ERROR
DownloadException.Type.HTTP_ERROR
DownloadException.Type.ENOSPC
DownloadException.Type.FILE_ERROR
DownloadException.Type.STORAGE_PERMISSION_DENIED
DownloadException.Type.OUTPUT_INVALID
DownloadException.Type.RANGE_NOT_SUPPORTED
DownloadException.Type.EMPTY_RESPONSE
DownloadException.Type.CHECKSUM_FAILED
DownloadException.Type.CANCELLED
DownloadException.Type.UNKNOWN
```

For HTTP errors, `getCode()` contains the HTTP status code. For errors without an HTTP status, the code is not an HTTP status code.

## Custom HTTP client

Use your own `OkHttpClient`:

```java
OkHttpClient client = new OkHttpClient.Builder()
    .followRedirects(true)
    .build();

SimpleDownloader downloader = SimpleDownloader.builder(context)
    .setHttpClient(client)
    .build();
```

The HTTP client cannot be replaced while a worker is running or already scheduled.

## Custom task list order

Use a custom comparator for the task list:

```java
SimpleDownloader downloader = SimpleDownloader.builder(context)
    .setTaskComparator(myComparator)
    .build();
```

Sorting is enabled by default. Disable it when needed:

```java
downloader.enableSorting(false);
```

## TaskField

`TaskField` is used to tell SimpleDownloader which property of a download task you want to filter or search by.

For example:
```java
List<DownloadTask> tasks = downloader.getTasks(TaskField.STATUS, Status.COMPLETED);
```
This means: get tasks whose STATUS is COMPLETED. 

- field = WHAT should I check?
- value = WHAT should it equal?

Available TaskFields:

```java
ID
FILE_URL
STATUS
PRIORITY
MIME_TYPE
FILE_NAME
CREATED_AT
WIFI_ONLY
PROGRESS
BYTES_DOWNLOADED
TOTAL_BYTES
OUTPUT_URI
OUTPUT_PATH
OVERWRITE_URI
OVERWRITE_PATH
OUTPUT_FOLDER_URI
OUTPUT_FOLDER_PATH
SUB_FOLDER_PATH
DELETE_ON_REMOVAL
LOCKED_IN_QUEUE
```

## Utilities

```java
String size = Formator.formatBytes(bytes);
String speed = Formator.formatSpeed(bytesPerSecond);
String eta = Formator.formatEta(etaMs);
String ratio = Formator.formatRatio(part, total);
```

`TypeResolver` is used internally, but it is also available for resolving file extensions and MIME types for you.

```java
String extension = TypeResolver.getExtension(fileName);
String mime = TypeResolver.getMimeFromName(fileName);
String mimeFromUrl = TypeResolver.getMimeFromUrl(fileUrl);
```

## Cleanup

When listeners and observers are owned by an Activity or Fragment, release them from the same downloader instance:

```java
@Override
protected void onDestroy() {
    downloader.releaseAllCallbacks();
    super.onDestroy();
}
```

You can also remove a specific listener or observer with `removeListener()` and `removeObserver()`.

Call `shutdown()` only when you intentionally want to stop the downloader and release its workers, network callbacks, HTTP resources, thumbnails, and other runtime resources:

```java
downloader.shutdown();
```

For the default-owner instance You can also use:

```java
SimpleDownloader.shutdownDefault();
```

You do not need to call `shutdown()` normally or when an Activity is destroyed. Do not use a shut down downloader again.

## License

```license
   Copyright (C) 2026 Jeet / Jeetarc

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```

## Support

Found a problem or have a suggestion? Open an issue:
https://github.com/jeetarc/SimpleDownloader/issues

Copyright © 2026 Jeet / Jeetarc.
