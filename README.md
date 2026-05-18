# PostgreSQL Database Monitor

A Compose Multiplatform desktop application to monitor PostgreSQL databases across Localhost, Docker, and Cloud (Supabase) environments.

## Features

*   **Multi-Environment Support:**
*   **Real-time Metrics:**
    *   Active Connections
    *   Database Size
    *   Cache Hit Ratio
    *   Index Usage
    *   Host CPU & Memory (using OSHI, gracefully degraded for cloud instances)
*   **Slow Query Monitoring:** Track queries executing longer than a customizable threshold.
*   **Historical Trends:** View simple line charts for active connections and database growth.
*   **AI Insights Helper:** Layer that analyzes the past 10 snapshots and generates an actionable health report using an external API, falling back to local generated metrics if unavailable.
*   **Data Persistence:** Saves metric snapshots locally as JSON and allows storing connection information. On Windows, it's stored in: "C:\Users\{username}\AppData\Roaming\DatabaseMonitor\metrics_history.json"

## Architecture

This project follows a Kotlin Multiplatform structure.
*   **`shared` module:** Contains the core connection logic, coroutine-based background polling, metric collection (using JDBC and OSHI), state management (ViewModel), and Compose UI components (`MonitorScreen`, `DashboardView`). Code relies heavily on `jvmMain` for the JDBC and OSHI interactions.
*   **`desktopApp` module:** The main entry point that launches the Compose Desktop `Window`.

## Setup & Running

### Prerequisites

*   JDK 17+
*   A locally running PostgreSQL instance
*   Native Docker installation (on Linux, or Docker Desktop for Windows) 

### Running the App

To run the application from the command line:

```bash
./gradlew :desktopApp:run
```

## Running the Mock Docker Database

This project comes with a fully configured `docker-compose.yml` to spin up a mock PostgreSQL database pre-populated with data, as well as a separate load generation container mimicking slow queries, random reads, and active connections.

### Starting the Environment

To start the database and the load generator in the background:

*Note: A hyphen might be necessary (docker-compose) depending on the installation environment.*

```bash
docker compose up -d
```

In the application, select **DOCKER** and use:
* **User**: `monitor_user`
* **Password**: `monitor_password`
* **Database**: `monitor_db`

### Stopping and Cleaning Up

To stop the containers:
```bash
docker compose stop
```

To completely delete the containers, networks, volumes, and images created by docker compose:
```bash
docker compose down -v --rmi all
```
*Using `-v` ensures that anonymous and named volumes are destroyed, while `--rmi all` removes the built/pulled images for these services.*

## How It Works

1.  **Connection (`DatabaseConnectionManager`):** Connects to the database using `org.postgresql:postgresql`. Supabase requires specific `sslmode=require` which is handled based on the `ProfileType`.
2.  **Collection (`MetricCollector`):** A `Flow` running on `Dispatchers.IO` queries `pg_stat_activity`, `pg_statio_user_tables`, etc. at a configurable interval.
3.  **UI (`MonitorViewModel` & `MonitorScreen`):** Updates a `StateFlow` consumed by Compose Multiplatform components to ensure non-blocking renders.
4.  **Storage (`SnapshotStorage`):** Periodically writes `DbMetricsSnapshot` objects to the user's local application data directory.


# Functionality Documentation

## Backend Monitoring Engine
Added `DatabaseConnectionManager` to handle connections to Local, Docker, and Supabase. Supports SSL properly.
Added `MetricCollector` which uses JDBC to execute queries against `pg_stat_activity`, `pg_database_size`, `pg_statio_user_tables` and `pg_stat_user_tables` to collect metrics. Includes OSHI for host metrics like CPU and Memory. Uses Coroutines to return a Flow.

## Snapshot Storage
Added `SnapshotStorage` to store snapshots into a JSON file locally using `kotlinx.serialization`. Retains the last 100 snapshots.

## AI Insights
Added `AiInsights` to generate a markdown string evaluating health, anomalies, and tips based on previous data.

## View Model
Added `MonitorViewModel` to wrap the backend metrics in `StateFlow` and handle background Coroutines for polling metrics and generating AI insights.

## Compose UI
Added `MonitorScreen` to provide a multiplatform Compose interface. Includes a Dashboard showing active metrics, a simple Canvas-based historical line chart for database size and connection amount, and slow query info. Includes a dropdown to change the slow query threshold.

# AI Usage

## ChatGPT
Used ChatGPT for task analysis, in order to decide the architecture of the app, whether to make it web or desktop,
compared technologies in order to get the easiest to install and run environment, while also having the code written in a familiar language
   
## Gemini
Used the info gathered from the previous analysis step to build a suitable prompt for a coding AI agent
Decided for Gemini Code Assist to generate the backend and the overall UI, the implementation of the test DB using Docker Compose

## DeepSeek
Made human-readable comments for each class/method
