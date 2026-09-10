# Pod Status Notifier

A lightweight Windows system-tray application that monitors Kubernetes pod health on a configurable interval and shows native desktop notifications when pods enter a bad state.

---

## Features

- Reads all kube contexts from `~/.kube/config` — pick one from a dropdown
- Fetches namespaces live from the selected cluster
- Configurable polling interval (1–60 minutes) with wake-from-sleep detection
- Selective alerting — choose which pod states trigger a notification: `CrashLoopBackOff`, `Error`, `Pending`, `OOMKilled`, `Terminating`
- Bottom-right toast popup with Dismiss / Show Status actions, auto-dismisses after 10 seconds
- Terminal-style status window showing raw `kubectl get po` output
- Daily rolling log files written to `~/PodStatusNotifier/`
- Remembers last used log directory across restarts
- Reconfigure without restarting — change context, namespace, or interval from the tray menu

---

## Requirements

| Requirement | Version |
|---|---|
| Java JDK | 17+ |
| Maven | 3.6+ |
| kubectl | Any — must be on `PATH` |
| OS | Windows (uses `cmd.exe` and system tray) |

---

## Build

```bash
mvn clean package
```

Produces a fat JAR at:

```
target/pod-status-notifier-1.0.0.jar
```

---

## Run

```bash
java -jar target/pod-status-notifier-1.0.0.jar
```

Or double-click the JAR if your OS has a `.jar` file association with Java.

---

## First Launch

A configuration dialog opens on startup:

| Field | Description |
|---|---|
| **Kube context** | Dropdown populated from `~/.kube/config` — current context is pre-selected |
| **Namespace** | Dropdown populated live from the selected cluster |
| **Log Directory** | Where daily log files are written (default: `~/PodStatusNotifier`) |
| **Check Interval** | How often to poll — slider from 1 to 60 minutes |
| **Alert on Pod States** | Checkboxes for each bad state to watch |

Click **Start Monitoring** — the window closes and the app runs in the system tray.

---

## Tray Menu

Right-click the tray icon for:

| Item | Action |
|---|---|
| Check Pods Now | Run an immediate check outside the schedule |
| Show Status | Open the terminal-style kubectl output window |
| Reconfigure… | Re-open the config dialog without restarting |
| Exit | Quit the application |

Double-click the tray icon to show the status window directly.

---

## Logs

Daily rolling logs are written to `~/PodStatusNotifier/`:

```
~/PodStatusNotifier/pod-status-2026-09-10.log
~/PodStatusNotifier/pod-status-2026-09-11.log
...
```

Each line is timestamped:

```
[2026-09-10 08:32:01] Application started
[2026-09-10 08:32:01] Config — cluster=gke_sr-dwh-prod-01 namespace=debezium-server interval=5min
[2026-09-10 08:32:03] Running: kubectl get po -n debezium-server
[2026-09-10 08:32:05] Total: 8 | Running: 8 | Bad: 0
[2026-09-10 08:32:05] OK — All pods running fine.
```

---

## Project Structure

```
PodStatusNotifier/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/example/podmonitor/
    │   ├── PodStatusNotifier.java    Entry point — tray icon, status window
    │   ├── ConfigDialog.java         Startup config UI (modal dialog)
    │   ├── MonitorConfig.java        Config POJO
    │   ├── KubeConfig.java           ~/.kube/config parser (SnakeYAML)
    │   ├── PodChecker.java           kubectl runner + alert logic
    │   ├── PopupNotification.java    Bottom-right toast popup
    │   ├── TimerManager.java         Polling timer + wake-from-sleep detection
    │   ├── Logger.java               Daily rolling file logger
    │   └── ShellRunner.java          cmd.exe wrapper
    └── resources/
        └── icon.svg                  Tray icon
```

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| SnakeYAML | 2.2 | Parse `~/.kube/config` |
| Batik Transcoder | 1.17 | Render SVG icon for system tray |
| Batik Codec | 1.17 | Batik image codec support |

---

## How It Works

1. On launch, the config dialog reads `~/.kube/config` via SnakeYAML and populates the context dropdown
2. When a context is selected, `kubectl get namespaces --context=<ctx>` is run on a background thread to populate the namespace dropdown
3. On **Start Monitoring**, `kubectl config use-context <ctx>` switches the active context, then `kubectl get po -n <namespace>` is polled on the configured interval
4. Each poll result is parsed for bad pod states — if any match the selected alert filters, a beep sounds and a toast popup appears
5. A wake-from-sleep detector thread checks every 30 seconds for gaps larger than 2 minutes, and runs an immediate check + restarts the timer when detected
6. All events are written to a daily log file in `~/PodStatusNotifier/`
