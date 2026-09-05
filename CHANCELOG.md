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
Use `SimpleDownloader.builder(context)` and `DownloadRequest.builder()` for more controls. [Read APIs](https://github.com/jeetarc/SimpleDownloader/blob/main/README.md)

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
