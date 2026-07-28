# SimpleDownloader

[![JitPack](https://jitpack.io/v/jeetarc/SimpleDownloader.svg)](https://jitpack.io/#jeetarc/SimpleDownloader)

SimpleDownloader is an Android download library project. It handles the parts that usually make downloading difficult: queues, concurrent downloads, pause and resume, unstable networks, scoped storage, task persistence, foreground, notifications, progress updates, etc.

The simple API:

```java
DownloadTask task = SimpleDownloader.with(context)
    .setOutput(folderPath, FileName.AUTO) // or .setOutput(folderUri, FileName.AUTO, MimeType.AUTO)
    .setFileUrl(fileUrl)
    .startDownload();
```

## Features

- Multiple downloads with queue and priority support
- automatic download concurrency
- Pause, resume, cancel, retry, remove, requeue, and force download
- Resume using HTTP range requests
- Network loss handling and Wi-Fi-only downloads
- Task persistence and restoration with filter.
- Android scoped-storage support using folder and document URIs
- File-system path output
- Automatic file name and MIME type resolution
- Progress, speed, ETA, status, and lifecycle callbacks
- Progress, completion, and error notifications with action, thumbnail, etc.
- Optional foreground execution
- Custom OkHttpClient support
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
    implementation "com.github.jeetarc:SimpleDownloader:1.0.0-beta.1"
}
```

SimpleDownloader is built with Java 8 and compileSdk 35.

## Android setup

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

Foreground mode can automatically enable notifications, also use can use `enableNotifications(true)`

## Quick start

### Download into a selected folder

Using a folder URI from folder picker:

```java
DownloadTask task = SimpleDownloader.with(context)
    .setOutput(folderUri, FileName.AUTO, MimeType.AUTO)
    .setFileUrl("https://example.com/files/document.pdf")
    .startDownload();
```

SimpleDownloader creates a new file inside the folder. If a file with the same name already exists, it creates a unique name.

Keep the folder permission for download to survive app restart:

```java
int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

getContentResolver().takePersistableUriPermission(folderUri, flags);
```

### Download into a file-system folder

```java

DownloadTask task = SimpleDownloader.with(context)
    .setOutput(folderPath FileName.AUTO)
    .setFileUrl(fileUrl)
    .startDownload();
```

### Use a fixed name and MIME when known.

```java
DownloadTask task = SimpleDownloader.with(context)
    .setOutput(folderUri, "manual.pdf", "application/pdf")
    .setFileUrl(fileUrl)
    .startDownload();
```

### Overwrite a file

Use a document URI:

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

## Listener for download updates

All `DownloadListener` callbacks run on the main thread. Every method is optional.

```java
DownloadListener listener = new DownloadListener() {
    @Override
    public void onProgress(long id, int progress,long speed, long etaMs,DownloadTask task
    ) {
        progressBar.setProgress(progress);
        speedText.setText(Formator.formatSpeed(speed));
        etaText.setText(Formator.formatEta(etaMs));
    }

    @Override
    public void onComplete(long id, Uri outPut(), ownloadTask task) {
        // The download finished successfully.
    }

    @Override
    public void onError(long id, Uri outputUri, Exception error, DownloadTask task
    ) {
        // The download failed.
    }
};

DownloadTask task = SimpleDownloader.with(this)
    .setOutput(folderUri, FileName.AUTO, MimeType.AUTO)
    .setFileUrl(fileUrl)
    .addListener(listener)
    .startDownload();
```

Other callbacks include:

```java
onStart(...)
onQueued(...)
onPaused(...)
onResumed(...)
onCancelled(...)
onRemoved(...)
onRetry(...)
onWaitingForNetwork(...)
onStatusChanged(...)
onActiveChanged(...)
onLifecycleChanged(...)
```

`onStart()` can run again after resume, retry, etc. use `onLifecycleChanged()`. to know start or end for the full lifestyle.

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
        adapter.submitList(tasks);
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

Update a task by ID:

```java
SimpleDownloader.setPriority(id, Priority.NEXT);
SimpleDownloader.setWifiOnly(id, true);
SimpleDownloader.setLockedInQueue(id, true);
SimpleDownloader.setDeleteOnRemoval(id, true);
```
Those are static.

## Restore tasks after app restart

Active Tasks are stored automatically. Restore them when your app is ready to show or continue downloads:

```java
SimpleDownloader downloader = SimpleDownloader.with(this)
    .addListener(listener)
    .addObserver(observer);

List<DownloadTask> restored = downloader.restoreTasks();
```java
Active tasks are restored as paused. Resume the tasks you want to continue:

```java
SimpleDownloader.resumeAll();

//or
for (DownloadTask task : restored) {
     task.resume();
}
```

### Restore matching tasks

```java
List<DownloadTask> paused =
    downloader.restoreTasks(TaskField.STATUS, Status.PAUSED);

List<DownloadTask> videos =
    downloader.restoreTasks(TaskField.MIME_TYPE, "video/mp4");

List<DownloadTask> matchingUrl =
    downloader.restoreTasks(TaskField.FILE_URL, fileUrl);
```

`restoreTasks()` returns an empty list when no match.

```java
DownloadTask task =
    downloader.restoreTask(TaskField.FILE_URL, fileUrl);

if (task != null) task.resume();
```

`restoreTask()` returns the newest matching a single task, or `null` when no match.

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

## Network, headers, cookies

```java
DownloadTask task = SimpleDownloader.with(context)
    .setOutput(folderUri, FileName.AUTO, MimeType.AUTO)
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
    .enableRetryOnNetworkGain(false);
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
    .setOutput(folderUri, FileName.AUTO, MimeType.AUTO)
    .setFileUrl(fileUrl)
    .startDownload();
```
DownloadNotification configuration is optional, only use `enableNotifications(true)` if you don't want to customize, it will use default config.

Run tasks using a foreground service:

```java
SimpleDownloader.with(context)
    .enableForeground(true)
    .setOutput(folderUri, FileName.AUTO, MimeType.AUTO)
    .setFileUrl(fileUrl)
    .startDownload();
```

You can also set a thumbnail:

```java
notification.setThumbnail(bitmap);
```

Or load it from a URL:

```java
notification.setThumbnailUrl(thumbnailUrl, thumbnailHeaders);
```

`DownloadNotification` can configure the channel, importance, lock-screen visibility, sound, vibration, color, update interval, actions, etc.

## Checksums

Verify the completed file with algorithms supported by `MessageDigest`,like SHA-256, SHA-1, or MD5:

```java
DownloadTask task = SimpleDownloader.with(context)
    .setChecksum("SHA-256", expectedChecksum)
    .setOutput(folderUri, FileName.AUTO, MimeType.AUTO)
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

`AUTO` use the URL, response headers, file extension, content type to resolve name and MIME.

## Task info

```java
task.getId();
task.getFileUrl();
task.getFileName();
task.getMimeType();

task.getOutputUri();
task.getOutputFile();
task.getOutputDocumentFile();
task.getOutputPath();
task.getOutputFolderPath();

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

get task from registry:

```java
DownloadTask task = SimpleDownloader.getTask(id);
List<DownloadTask> all = SimpleDownloader.getTasks();
List<DownloadTask> completed =
    SimpleDownloader.getTasks(Status.COMPLETED);
List<DownloadTask> highPriority =
    SimpleDownloader.getTasks(Priority.HIGH);
List<DownloadTask> videos =
    SimpleDownloader.getTasks("video/mp4");

int total = SimpleDownloader.getTotalCount();
int active = SimpleDownloader.getActiveCount();
int queued = SimpleDownloader.getQueuedCount();
int occupied = SimpleDownloader.getOccupiedCount();
int concurrency = SimpleDownloader.getEffectiveMaxConcurrent();
```

## Error handling

Failures exceptions are instance `DownloadException`:

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

Passing `0` for a connection or read timeout keeps the OkHttp default.

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

[github.com/jeetarc/SimpleDownloader/issues](https://github.com/jeetarc/SimpleDownloader/issues)

Copyright © 2026 Jeet / Jeetarc.
