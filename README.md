# Curl Visualizer

Desktop curl response visualizer built with Kotlin and Compose Desktop.

## Features

- Execute a single `curl` / `curl.exe` command.
- Inspect status, headers, body, stderr, exit code, and elapsed time.
- Copy response body.
- Search response body with previous/next navigation, Enter-to-next, and match count.
- Format JSON responses.
- Save request history and response history under `~/.curl-visualizer/history.json`.
- Deduplicate repeated commands and repeated response bodies by updating timestamps.
- Restore the last window size from `~/.curl-visualizer/settings.json`.

## Run

```bash
./gradlew run
```

## Build Packages

```bash
./gradlew packageDmg
./gradlew packageMsi
```

`packageMsi` needs to run on Windows. The app executes only commands that start with `curl` or `curl.exe` and blocks shell operators plus file-output flags.

History may contain tokens, cookies, or response data. Use the in-app delete and clear actions when needed.
