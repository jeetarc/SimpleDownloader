# SimpleDownloader

[![JitPack](https://jitpack.io/v/jeetarc/SimpleDownloader.svg)](https://jitpack.io/#jeetarc/SimpleDownloader)
[![GitHub release](https://img.shields.io/github/v/release/jeetarc/SimpleDownloader?include_prereleases)](https://github.com/jeetarc/SimpleDownloader/releases)
![Android API](https://img.shields.io/badge/Android-API%2021%2B-3DDC84?logo=android&logoColor=white)

SimpleDownloader is an Android download library project. It handles the parts that usually make downloading difficult: queues, concurrent downloads, pause and resume, unstable networks, scoped storage, task persistence, foreground, notifications, progress updates, etc.

The simple API:

```java
DownloadTask task = SimpleDownloader.with(context)
    .enableForeground(true)
    .setOutput(folderPath, FileName.AUTO) // Or .setOutput(folderUri, FileName)
    .setFileUrl(fileUrl)
    .startDownload();
```

## Features

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
    implementation "com.github.jeetarc:SimpleDownloader:1.0.0-beta.3"
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
Foreground mode can automatically enable notifications, also you can use `enableNotifications(true)`.

If using normal file path, add:
``` XML
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```
And request the permission at runtime.
Storage permissions are not needed when saving to app-specific folder or using folderUri via MediaStore or Storage Access Framework.

> All other permissions are added by default.

## Quick start

SimpleDownloader has three built-in storage modes.

**1. Download into a file system folder:**

```java
DownloadTask task = SimpleDownloader.with(context)
    .setOutput(folderPath, FileName.AUTO)
    .setFileUrl(fileUrl)
    .startDownload();
```

**2. Download into a MediaStore collection (Android 10+):**

```java
DownloadTask task = SimpleDownloader.with(context)
    .setOutput(MediaStore.Downloads.EXTERNAL_CONTENT_URI, FileName.AUTO)
    .setFileUrl(fileUrl)
    .startDownload();
```

You can also use other MediaStore collections:
```java
MediaStore.Images.Media.EXTERNAL_CONTENT_URI
MediaStore.Video.Media.EXTERNAL_CONTENT_URI
MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
```

**3. Download into a selected folder via SAF/document-tree:**

```java
DownloadTask task = SimpleDownloader.with(context)
    .setOutput(treeUri, FileName.AUTO)
    .setFileUrl("https://example.com/files/document.pdf")
    .startDownload();
```

Keep the URI permission:
```java
int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

getContentResolver().takePersistableUriPermission(treeUri, flags);
```

**Use a custom name if needed:**
```java
DownloadTask task = SimpleDownloader.with(context)
    .setOutput(folderUri, "example.mp4")
    .setFileUrl(fileUrl)
    .startDownload();
```

> SimpleDownloader creates a new file inside each folder. If a file with the same name already exists, it creates a unique name automatically.

**Overwrite a file:**

Use a DocumentFile URI or a specific MediaStore item URI:

```java
DownloadTask task = SimpleDownloader.with(context)
    .overwrite(fileUri)
    .setFileUrl(fileUrl)
    .startDownload();
```

Or use a file path:

```java
DownloadTask task = SimpleDownloader.with(context)
    .overwrite(outputFile.getAbsolutePath())
    .setFileUrl(fileUrl)
    .startDownload();
```

## Subfolder:
A subfolder is an optional folder inside the main output folder where you want the downloaded file to be saved.

```java
DownloadTask task = SimpleDownloader.with(context)
    .setOutput(folderUri, FileName.AUTO)
    .setSubFolder("app")
    .setFileUrl(fileUrl)
    .startDownload();
```

Subfolders can be nested, for example `"app/videos"`. They work with filesystem paths, SAF/document-tree output, and MediaStore output.

## Listener for download updates

All `DownloadListener` callbacks run on the main thread. Every method is optional.

```java
DownloadListener listener = new DownloadListener() {
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

DownloadTask task = SimpleDownloader.with(this)
    .setOutput(folderUri, FileName.AUTO)
    .setFileUrl(fileUrl)
    .addListener(listener)
    .startDownload();
```

Other callbacks include:

```java
onStart(long id, DownloadTask task) {}
onQueued(long id, int position, DownloadTask task) {}
onPaused(long id, DownloadTask task) {}
onResumed(long id, DownloadTask task) {}
onCancelled(long id, DownloadTask task) {}
onRemoved(long id, boolean outputDeleted, DownloadTask task) {}
onRetry(long id, int attempt, DownloadTask task) {}
onWaitingForNetwork(long id, int networkType, DownloadTask task) {}
onStatusChanged(long id, Status status, DownloadTask task) {}
onActiveChanged(long id, boolean isActive, DownloadTask task) {}
onLifecycleChanged(long id, int lifecycle, DownloadTask task) {}
```

`onStart()` can run again on resume and retry. Use `onLifecycleChanged()` to know the start or end of the full task lifecycle.

You can also add listeners directly on a task:

```java
task.addListener(listener);
task.removeListener(listener);
task.releaseCallbacks();
```

## Observe the task list

Use `TaskListObserver` when showing all downloads in a list, RecyclerView, etc.

```java
TaskListObserver observer = new TaskListObserver() {
    @Override
    public void onTasksChanged(List<DownloadTask> tasks) {
        // The list order changed
    }

    @Override
    public void onTaskUpdated(long id, DownloadTask task) {
        // Update only the matching item
    }
};

SimpleDownloader downloader = SimpleDownloader.with(this)
    .addObserver(observer);
```

The list passed to `onTasksChanged()` is an unmodifiable snapshot. `DownloadTask` objects inside it are live and can update.

Release the observer when not needed:

```java
SimpleDownloader.releaseObserver(observer);
```

## Control a task

```java
task.pause();
task.resume();
task.cancel();
task.retry();
task.requeue();
task.remove();
task.forceDownload();
```

Check:

```java
task.canPause();
task.canResume();
task.canRetry();
```

Change some task settings after creation:

```java
task.setPriority(Priority.HIGH);
task.setWifiOnly(true);
task.setLockedInQueue(true);
task.setDeleteOnRemoval(true);
```

Note:

- `cancel()` stops the task and deletes its output.
- `remove()` removes the task from SimpleDownloader register.
- `remove()` deletes the output only when `setDeleteOnRemoval(true)` is enabled.
- `forceDownload()` starts a queued task even when it is locked. It doesn't care about concurrency limit

## Task info

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

task.isActive();
task.isQueued();
task.isPaused();
task.isWaitingForNetwork();
task.isFinished();
task.isOccupiedSlot();
```

## Reuse a configured instance

You can configure a `SimpleDownloader` instance once inside `onCreate()` and reuse it:

```java
private SimpleDownloader downloader;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    downloader = SimpleDownloader.with(this)
        .setMaxConcurrent(3)
        .setConnectTimeout(30_000)
        .setReadTimeout(30_000)
        .setProgressInterval(300)
        .setBufferSize(16 * 1024)
        .enableHistory(true);
}

// You can configure all fields you want be the same across downloads.
```

Then use the configured instance whenever you start a download:

```java
DownloadTask task = downloader
    .setOutput(folderUri, fileName)
    .setMimeType(mimeType)
    .setFileUrl(fileUrl)
    .startDownload();
```

You can also use the same instance to restore tasks:

```java
List<DownloadTask> tasks = downloader.restoreTasks();
```

Other common settings, such as retry policy, user agent, priority, Wi-Fi-only mode, notifications, foreground execution, etc can also be configured on the same `downloader` instance, instead of being set again for every download.

> File URL, output destination, and custom ID should be set again before starting each task.


## Global controls

Control a task by ID:

```java
SimpleDownloader.pause(id);
SimpleDownloader.resume(id);
SimpleDownloader.cancel(id);
SimpleDownloader.retry(id);
SimpleDownloader.requeue(id);
SimpleDownloader.remove(id);
SimpleDownloader.forceDownload(id);
```

Control multiple tasks:

```java
SimpleDownloader.pauseAll();
SimpleDownloader.resumeAll();
SimpleDownloader.cancelAll();
SimpleDownloader.retryAll();
SimpleDownloader.requeueAll();
SimpleDownloader.removeAll();

SimpleDownloader.pause(Priority.LOW);
SimpleDownloader.resumeAll(Priority.HIGH);
SimpleDownloader.remove(Status.COMPLETED);
SimpleDownloader.remove(Priority.LOW);
```

get task from registry:

```java
DownloadTask task = SimpleDownloader.getTask(id);
List<DownloadTask> all = SimpleDownloader.getTasks();
List<DownloadTask> completed = SimpleDownloader.getTasks(TaskField.STATUS, Status.COMPLETED);
SimpleDownloader.getTask(TaskField.FILE_NAME, fileName) // returns latest matching single task, null if not found.

int total = SimpleDownloader.getTotalCount();
int active = SimpleDownloader.getActiveCount();
int queued = SimpleDownloader.getQueuedCount();
int occupied = SimpleDownloader.getOccupiedCount();
int concurrency = SimpleDownloader.getEffectiveMaxConcurrent();
```

Update a task by ID:

```java
SimpleDownloader.setPriority(id, Priority.NEXT);
SimpleDownloader.setWifiOnly(id, true);
SimpleDownloader.setLockedInQueue(id, true);
SimpleDownloader.setDeleteOnRemoval(id, true);
```

## Restore tasks
Restore methods immediately restore saved tasks using the downloader's current configuration.

> **Important:** Configure all downloader settings before calling any restore method.
> Restore methods should always be the **last configuration call**, otherwise restored tasks may not receive the configuration called after restore.

### Automatic Restore:

Enable automatic restoration of previously saved download tasks:

```java
SimpleDownloader downloader = SimpleDownloader.with(context)
    .setRetryPolicy(...)
    .enableNotifications(true)
    .enableForeground(true)
    .setConnectTimeout(30_000)
    .setReadTimeout(30_000)
    .setProgressInterval(300)
    .setNotification(...)
    .setAutoRestore(true); // Keep this last.
```
When enabled, SimpleDownloader automatically restores previously saved tasks and resumes eligible ones.
You do not need to call restoreTasks() separately when using auto restore.
Auto restore is disabled by default.

### Explicit restore when your app ready to show or continue downloads:

```java
restored = downloader.restoreTasks();
```

Active tasks are restored as paused. Resume the tasks you want to continue:

```java
SimpleDownloader.resumeAll();

//or
for (DownloadTask task : restored) {
     task.resume();
}
```

### Restore matching tasks:
`restoreTasks(...)` returns an empty list when no match:
```java
List<DownloadTask> paused = downloader.restoreTasks(TaskField.STATUS, Status.PAUSED);

List<DownloadTask> videos = downloader.restoreTasks(TaskField.MIME_TYPE, "video/mp4");

List<DownloadTask> matchingUrl = downloader.restoreTasks(TaskField.FILE_URL, fileUrl);
```

`restoreTask()` returns a single newest matching task, or `null` when no match:
```java
DownloadTask task = downloader.restoreTask(TaskField.FILE_URL, fileUrl);

if (task != null) task.resume();
```

Available fields:

```java
TaskField.ID
TaskField.FILE_URL
TaskField.STATUS
TaskField.PRIORITY
TaskField.MIME_TYPE
TaskField.FILE_NAME
TaskField.CREATED_AT
TaskField.WIFI_ONLY
TaskField.BUFFER_SIZE
TaskField.PROGRESS
TaskField.BYTES_DOWNLOADED
TaskField.TOTAL_BYTES
TaskField.OUTPUT_URI
TaskField.OUTPUT_PATH
TaskField.OVERWRITE_URI
TaskField.OVERWRITE_PATH
TaskField.OUTPUT_FOLDER_URI
TaskField.OUTPUT_FOLDER_PATH
TaskField.SUB_FOLDER_PATH
TaskField.DELETE_ON_REMOVAL
TaskField.LOCKED_IN_QUEUE
```

Finished tasks are kept in the database only when history is enabled:

```java
SimpleDownloader.with(context)
    .enableHistory(true);
```

## Queue and concurrency

By default, SimpleDownloader use automatic concurrency. It starts with 1 and go up to 10 slot beased on download speed.

auto concurrency starts when `setMaxConcurrent(0)` or not set.

Stop queued tasks from starting automatically when a slot becomes free:

```java
SimpleDownloader.with(context)
    .setDownloadOnSlotFree(false);
```

Lock task in the queue:

```java
task.setLockedInQueue(true);
```

Priorities :

```java
Priority.NEXT
Priority.HIGH
Priority.NORMAL
Priority.LOW

// NEXT > HIGH > NORMAL > LOW
```

Task statuses:

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

## Retry policy

The default policy with one automatic retry. Configure:

```java
RetryPolicy retryPolicy = RetryPolicy.builder()
    .maxRetryCount(3)
    .initialDelayMs(1000)
    .multiplier(2.0)
    .maxDelayMs(30_000)
    .build();

SimpleDownloader downloader = SimpleDownloader.with(context)
    .setRetryPolicy(retryPolicy);
```

Retry settings stay on the `SimpleDownloader` instance used to create or restore tasks.

## MIME Type
A MIME type (media type) is a used to identify the format of a file:

```java
DownloadTask task = SimpleDownloader.with(context)
    .setOutput(folderUri, "manual.pdf")
    .setMimeType("application/pdf")
    .setFileUrl(fileUrl)
    .startDownload();
```

Or use automatic resolution:
```java
.setMimeType(MimeType.AUTO) // or MimeType.FROM_NAME
```

SimpleDownloader handles this automatically When no MIME type is specified.

## Network, headers, cookies

```java
DownloadTask task = SimpleDownloader.with(context)
    .setOutput(folderUri, FileName.AUTO)
    .setFileUrl(fileUrl)
    .setHeader("Authorization", "Bearer " + token)
    .setHeader("Referer", pageUrl)
    .setCookies("session=" + sessionId)
    .setWifiOnly(true)
    .startDownload();
```

Add headers:

```java
Map<String, String> headers = new HashMap<>();
headers.put("Authorization", "Bearer " + token);
headers.put("Referer", pageUrl);

SimpleDownloader.with(context)
    .setHeaders(headers);
```

Network info:

```java
boolean available = SimpleDownloader.isNetworkAvailable();
int networkType = SimpleDownloader.getNetworkType();
```

Network constants:

```java
NETWORK_TYPE_NONE
NETWORK_TYPE_UNKNOWN
NETWORK_TYPE_WIFI
NETWORK_TYPE_CELLULAR
NETWORK_TYPE_ETHERNET
NETWORK_TYPE_BLUETOOTH
NETWORK_TYPE_VPN
NETWORK_TYPE_USB
NETWORK_TYPE_ROAMING
```

Waiting tasks resume when the network becomes available by default. Change with:

```java
SimpleDownloader.with(context)
    .enableResumeOnNetworkGain(false);
```

## Notifications

Notifications are optional and disabled by default.

```java
DownloadNotification notification = new DownloadNotification()
    .setSmallIcon(R.drawable.ic_download)
    .setCompleteIcon(R.drawable.ic_download_done)
    .setErrorIcon(R.drawable.ic_download_error)
    .setColorAccent(0xFF0087E5)
    .setShowPauseAction(true)
    .setShowCancelAction(true)
    .setShowRetryAction(true);

DownloadTask task = SimpleDownloader.with(context)
    .enableNotifications(true)
    .setNotification(notification)
    .setOutput(folderUri, FileName.AUTO)
    .setFileUrl(fileUrl)
    .startDownload();
```
`DownloadNotification` configuration is optional, only use `enableNotifications(true)` if you don't want to customize. It will use the default config.

Run tasks using a foreground service:

```java
SimpleDownloader.with(context)
    .enableForeground(true)
    .setOutput(folderUri, FileName.AUTO)
    .setFileUrl(fileUrl)
    .startDownload();
```

SimpleDownloader has built in thumbnail system for notifications, it can generate a thumbnail from a video, image, audio (album art), APK, PDF automatically.

You can also set a thumbnail:

```java
notification.setThumbnail(bitmap);
```

Or load it from a URL:

```java
notification.setThumbnailUrl(thumbnailUrl, thumbHeaders);

// pass null for headers, if it not available
```

`DownloadNotification` can configure the channel, importance, lock-screen visibility, sound, vibration, color, update interval, actions, etc.

## Checksums

Verify the completed file with algorithms supported by `MessageDigest`. Like SHA-256, SHA-1, or MD5:

```java
DownloadTask task = SimpleDownloader.with(context)
    .setChecksum("SHA-256", expectedChecksum)
    .setOutput(folderUri, FileName.AUTO)
    .setFileUrl(fileUrl)
    .startDownload();
```

## File names and MIME types modes

```java
FileName.AUTO
FileName.TIME_BASED
```

```java
MimeType.AUTO
MimeType.FROM_NAME
```

`AUTO` uses the URL, response headers, file extension, and content type to resolve the name and MIME type.

## Error handling

Failures exceptions are instance of `DownloadException`:

```java
@Override
public void onError(long id, Uri outputUri, Exception error, DownloadTask task) {
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
NETWORK_LOST
TIMEOUT
DNS_ERROR
SSL_ERROR
HTTP_ERROR
ENOSPC
FILE_ERROR
STORAGE_PERMISSION_DENIED
OUTPUT_INVALID
RANGE_NOT_SUPPORTED
EMPTY_RESPONSE
CHECKSUM_FAILED
CANCELLED
UNKNOWN
```

## Other configuration

```java
SimpleDownloader downloader = SimpleDownloader.with(context)
    .setId(customId)
    .setUserAgent(userAgent)
    .setConnectTimeout(30_000)
    .setReadTimeout(30_000)
    .setProgressInterval(300)
    .setBufferSize(16 * 1024)
    .enableHistory(true)
    .enableSorting(true);
```

Custom IDs are optional. SimpleDownloader generates an ID when `setId()` is not used. An active task cannot be replaced by another task with the same ID.

Passing 0 for a connection or read timeout keeps the OkHttp default.

Use your own HTTP client:

```java
OkHttpClient client = new OkHttpClient.Builder()
    .followRedirects(true)
    .build();

SimpleDownloader.with(context)
    .setHttpClient(client);
```

The HTTP client cannot be replaced while a worker is running or already scheduled.

Use a custom task list order:

```java
SimpleDownloader.with(context)
    .setTaskComparator(myComparator);
```

## Formatting helpers

```java
String size = Formator.formatBytes(bytes);
String speed = Formator.formatSpeed(bytesPerSecond);
String eta = Formator.formatEta(etaMs);
```

`TypeResolver` is used internally, but it is also available for resolving file extensions and MIME types for you 🙂.

## Cleanup

When listeners and observers are owned by an Activity or Fragment, release them using the same owner object given to `with(...)`:

```java
@Override
protected void onDestroy() {
    SimpleDownloader.releaseCallbacks(this);
    SimpleDownloader.releaseObserver(observer);
    super.onDestroy();
}
```

Call `shutdown()` only when you intentionally want to stop the library and release all workers, network callbacks, HTTP resources, thumbnails, database, etc:

```java
SimpleDownloader.shutdown();
```

You do not need to call `shutdown()` normally or when an Activity is destroyed.

## Support

Found a problem or have a suggestion? Open an issue:
https://github.com/jeetarc/SimpleDownloader/issues

Copyright © 2026 Jeet / Jeetarc.
