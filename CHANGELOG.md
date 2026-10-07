## v1.0.2

### Added

- Added `.setHoldSlotOnPause(boolean enable)` to control whether a paused download holds or releases its concurrency slot.
Can be configured via `SimpleDownloader.builder()` or a `downloader` instance.
- Added `.hasListeners()` and `.hasListener(Listener)` methods to both `DownloadTask` and `SimpleDownloader`.
- Added `.hasObservers()` and `.hasObserver(Observer)` methods to `SimpleDownloader`.
- Expanded `Status` with helper methods - `isQueued()`, `isPaused()`, `isComplete()`, `isFailed()`, `isCancelled()`, and more.
- Added `Formatter.formatTime(long timeMillis, String format)` for timestamp formatting.

### Improvements

- Improved restore behavior when automatic restore is disabled.
- Improved pause, concurrency-slot, and notification handling with `holdSlotOnPause`.
Paused downloads now release their concurrency slot by default. `holdSlotOnPause` is disabled by default.
- Improved notification grouping, ongoing notification state, and foreground service handling.
- Improved `Content-Range` and response handling in the HTTP engine.
- Improved timeout client caching in the HTTP engine.
- Fixed waiting for network state execution ordering that was blocking queued submissions.
- Finalized the Listener API, removed deprecated callback signatures from both `DownloadTask.Listener` and `SimpleDownloader.Listener`.
- Renamed the library package from `com.jeet.simpledownloader` to `com.jeetarc.simpledownloader`.
- General cleanup and improvements.

### Migration

**1. Package Rename**

The library package has been renamed to:

```text
com.jeetarc.simpledownloader
```

Update any existing imports from `com.jeet.simpledownloader` to `com.jeetarc.simpledownloader`

**2. Update Dependency**

```gradle
implementation "com.github.jeetarc:SimpleDownloader:1.0.2"
```

**3. Listener API**

If you are using deprecated Listener callback methods, update them to the new Listener API signatures.

## v1.0.1

### Improvements:

- `DownloadTask.Listener` callback methods now contain a `DownloadTask` parameter.
- Removed the `long id` parameter from `SimpleDownloader.Listener` callback methods for cleaner APIs.
- Moved output validation checks off the download thread.
- Added randomized output validation intervals.
- The ETA text will no longer be shown in notifications when it’s unavailable or being calculated.

### Fixes:

- Fixed global concurrency counters not being released correctly when active tasks were cleared during shutdown.
- Renamed `Formator` to `Formatter`.

### Migration:

Update the dependency:
```gradle
implementation "com.github.jeetarc:SimpleDownloader:1.0.1"
```

Add the `DownloadTask` parameter as the last parameter for all `DownloadTask.Listener` callbacks. So replace:
```java
@Override
public void onProgress(int progress, long speed, long etaMs) {}
```
with (v1.0.1):
```java
@Override
public void onProgress(int progress, long speed, long etaMs, DownloadTask task) {}
```

Remove the `long id` (1st) parameter from all `SimpleDownloader.Listener` callbacks. So replace:
```java
@Override
public void onProgress(long id, int progress, long speed, long etaMs, DownloadTask task) {}
```
with (v1.0.1):
```java
@Override
public void onProgress(int progress, long speed, long etaMs, DownloadTask task) {
    // Get the ID directly from the task:
    // task.getId();
}
```
Same for all other methods.

If you are using the SimpleDownloader `Formator` utility, rename it to `Formatter`.

## v1.0.0

Stable release of SimpleDownloader with a cleaner API, improved task management, and many more.

### Added

- Instance-based downloader API with `SimpleDownloader.getInstance(context)` and `SimpleDownloader.builder(context)`.
- `DownloadRequest` API for defining individual downloads separately from downloader configuration.
- Downloader ownership through `ownerId`, if not set, it use library default: `"default_owner"`.
- Added support for batch downloads and clearer separation between global downloader settings and per download options.

### Fixes & Improvements

- Safer task management when multiple downloader instances are used.
- Improved separation of task state, queues, and concurrency between downloader instances.
- Improved database handling and task ownership.
- General stability and lifecycle improvements for the stable release.


### Migration for beta users

#### 1. Update the dependency:

```gradle
implementation "com.github.jeetarc:SimpleDownloader:1.0.0"
```

#### 2. Replace the old download API:

Beta:

```java
DownloadTask task = SimpleDownloader.with(context)
    .setOutput(folderUri, FileName.AUTO)
    .setFileUrl(url)
    .startDownload();
```

Stable:

```java
SimpleDownloader downloader = SimpleDownloader.getInstance(context);
DownloadRequest request = DownloadRequest.from(folderUri, FileName.AUTO, fileUrl);
DownloadTask task = downloader.startDownload(request);
```
Use `SimpleDownloader.builder(context)` and `DownloadRequest.builder()` for more controls.

#### 3. Update listeners:

Move from the beta listener API to the stable APIs:

Beta: 
- `DownloadListener`

Stable:
- `DownloadTask.Listener`
- `SimpleDownloader.Listener`

#### 4. Read README for more:
https://github.com/jeetarc/SimpleDownloader/blob/main/README.md

## v1.0.0-beta.3
Adds MediaStore and subfolder support and improves output recovery and reliability.

### Added
- MediaStore output support on Android 10+.
- `setSubFolder(...)` support for filesystem paths, SAF/document-tree output, and MediaStore output.
- MediaStore item URI support with `overwrite(Uri)`.
- `TaskField.SUB_FOLDER_PATH`.
- `task.getSubFolderPath()`.
- `downloader.setAutoRestore(...)` for automatic restore and resume of tasks after app restart.

### Improved
- MediaStore unique filename handling, including pending/concurrent outputs.
- Resume handling for MediaStore outputs.
- Output validation and recovery after externally deleted/invalid outputs.
- Automatic recreation of deleted subfolders during retry/resume.
- Persistence and restoration of MediaStore/subfolder output information.

### Fixed
- Retry could reuse a deleted output path/URI and write into another task's file.
- SAF/filesystem retry could reuse a filename that had already been claimed by another task.
- Deleted subfolders could cause retry to fail instead of being recreated.
- MediaStore stale rows could cause unnecessary `(1)`, `(2)` filenames.
- MediaStore overwrite/invalid-output edge cases.

## v1.0.0-beta.2
This update contains important internal and API changes.

- Download speed and optimization improvements.
- Independent progress and notification interval handling.
- Lighter thumbnail checks.
- Missing FileProvider no longer stops path downloads.
- `forceDownload()` bypasses concurrency and survives pause/resume.
- `TaskField` filters for `getTask()` and `getTasks()`.
- Simplified `onQueued()` callback (now contains 3 parameters).
- Some other fixes.

Thank You!

## 1.0.0-beta.1
SimpleDownloader v1.0.0-beta.1 release.

Added new features, API, and improved stability.

### What's new
- Built-in foreground service.
- Built-in notifications with actions for pause, resume, cancel, and retry.
- Notification thumbnails.
- `DownloadListener` for individual task callbacks.
- `TaskListObserver` for observing the complete task list.
- Filtered task restoration using `TaskField`.
- Custom `OkHttpClient`, headers, cookies, and user-agent support.
- Checksum verification.
- Improved pause, resume, cancel, and retry behaviour.
- Better task restoration after reopening the app.
- Better `FileName` and `MimeType` mode handling.
- Improved database structure.
- And many more.

This is a close release of the upcoming v1.0.0 (stable). Bug reports and feedbacks are welcome.

## v1.0.0-beta
This is the first beta release of SimpleDownloader.

Please use/test and share your feedback about:
- Download start/pause/resume/cancel behavior.
- Network awareness.
- Status changes, lifecycle.
- Queue and priority behavior.
- Retry behaviour.
- Scoped storage / DocumentFile output.
- Any crashes or unexpected behavior.
