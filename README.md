# Quarkus Debian Packaging

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-red.svg)](https://quarkus.io/)

A Quarkus extension that builds production-ready, Debian binary packages (`.deb`) and provides zero-downtime, in-place configuration hot reload via UNIX domain sockets and low-level memory patching.

---

## 🌟 Key Features

- **Pure Java Debian Packaging**: Builds binary `.deb` archives (AR archive containing `debian-binary`, `control.tar.gz`, and `data.tar.gz`) directly from Java using Apache Commons Compress. **Zero external platform dependencies**—no `dpkg-deb`, `fakeroot`, `ar`, `tar`, or Linux host required. Builds deterministically on macOS, Linux, and Windows CI.
- **Dual Runtime Packaging**:
  - **JVM Fast-Jar Mode**: Bundles the Quarkus fast-jar application layout (`quarkus-app/`), wrapper startup scripts, JVM option flags, and systemd service.
  - **Native Executable Mode**: Directly packages GraalVM native binary executables with streamlined startup scripts and minimal footprint.
- **Linux FHS & Systemd Integration**: Conforms to the Linux Filesystem Hierarchy Standard (`/usr/share/<app>`, `/etc/<app>`, `/var/log/<app>`, `/var/lib/<app>`, `/run/<app>`), installs systemd unit files (`/usr/lib/systemd/system/<app>.service`), sets up dedicated non-root service accounts, and manages security sandboxing (`NoNewPrivileges`, `AmbientCapabilities=CAP_NET_BIND_SERVICE`).
- **Zero-Downtime Configuration Hot Reload**:
  - Hot reload external configuration files (`/etc/<app>/application.properties`) without restarting the JVM or dropping incoming connections.
  - **Zero Hot-Path Overhead**: In-place memory patching of SmallRye `@ConfigMapping` singletons via reflection and `VarHandle` with a release fence. Code reads configuration properties via standard single-cycle `GETFIELD` without proxies, wrappers, or volatile locks.
  - **Atomic Snapshot Validation**: Validates the entire updated configuration against registered `@ConfigMapping` interfaces before applying changes. If validation fails, live state remains completely untouched.
  - **Native Systemd & CLI Integration**: Trigger reload natively via `systemctl reload <app>`, the CLI wrapper `<app> --reload`, or direct UNIX domain socket IPC (`/run/<app>/control.sock`).
  - **CDI Event Notification**: Broadcasts `ConfigReloadedEvent` upon successful reload so beans can trigger custom logic (e.g., resizing connection pools, evicting caches).

---

## 📦 Getting Started

### 1. Add Dependency

Add `quarkus-debian-packaging` to your Quarkus project `pom.xml`:

```xml
<dependency>
    <groupId>io.github.rawvoid</groupId>
    <artifactId>quarkus-debian-packaging</artifactId>
    <version>2.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Build the Debian Package

The Debian package is automatically generated during Quarkus production packaging:

```bash
# JVM Mode (Fast-Jar)
mvn clean package

# GraalVM Native Image Mode
mvn clean package -Dnative
```

The resulting package will be generated under `target/`:

```text
target/
├── my-app-1.0.0-SNAPSHOT-runner.jar
└── my-app_1.0.0-SNAPSHOT_all.deb      # (or _amd64.deb / _arm64.deb for native)
```

### 3. Install and Run on Debian / Ubuntu

```bash
# Install the package
sudo dpkg -i target/my-app_1.0.0-SNAPSHOT_all.deb

# Verify systemd service status
sudo systemctl status my-app

# Start the service (if not auto-started)
sudo systemctl start my-app
```

---

## 📂 Linux Filesystem Layout (FHS)

The generated Debian package distributes files according to standard Linux conventions:

| Path | Ownership & Mode | Description |
|:---|:---|:---|
| `/usr/share/<app>/` | `root:<app> 0750` | Application directory containing `quarkus-app/` (or native binary), launcher scripts, and the `reload` helper |
| `/etc/<app>/` | `root:<app> 0750` | Production configuration directory |
| `/etc/<app>/application.properties` | `root:<app> 0640` | External configuration file (highest ordinal override, hot-reloadable) |
| `/etc/<app>/jvm.options` | `root:<app> 0640` | JVM startup options (heap size, GC parameters, etc., JVM mode only) |
| `/etc/default/<app>` | `root:root 0644` | Environment variable overrides sourced by launcher before startup |
| `/usr/bin/<app>` | `root:root 0755` | Executable launcher wrapper |
| `/usr/lib/systemd/system/<app>.service` | `root:root 0644` | Systemd service unit definition |
| `/var/log/<app>/` | `<app>:<app> 0750` | Application log directory |
| `/var/lib/<app>/` | `<app>:<app> 0750` | Application persistent state and home directory |
| `/run/<app>/control.sock` | `<app>:<app> 0750` | UNIX domain socket for control and hot reload |

---

## ⚙️ Configuration Reference

### Build-Time Packaging Configuration (`quarkus.debian.*`)

Configured in `src/main/resources/application.properties`:

| Property | Default Value | Description |
|:---|:---|:---|
| `quarkus.debian.enabled` | `true` | Enables or disables Debian package generation during packaging. |
| `quarkus.debian.name` | `${quarkus.application.name}` | Debian package name. Must match `[a-z0-9][a-z0-9+.-]+`. |
| `quarkus.debian.version` | `${quarkus.application.version}` | Debian package version string. |
| `quarkus.debian.description` | `${quarkus.application.name}` | Package description used in `control` and systemd unit. |
| `quarkus.debian.maintainer` | `Unknown <unknown@unknown>` | Maintainer contact field for the `control` file. |
| `quarkus.debian.section` | `web` | Debian package section (e.g. `web`, `admin`, `net`). |
| `quarkus.debian.priority` | `optional` | Debian package priority (`optional`, `standard`, `extra`). |
| `quarkus.debian.architecture` | `all` (JVM) / host arch (native) | Target CPU architecture (`all`, `amd64`, `arm64`). |
| `quarkus.debian.depends` | `systemd, python3` | Debian package dependencies (`Depends:` field in `control`). |
| `quarkus.debian.install-dir` | `/usr/share/${name}` | Installation directory for the application payload. |
| `quarkus.debian.config-dir` | `/etc/${name}` | External configuration directory. |
| `quarkus.debian.config-file` | `/etc/${name}/application.properties` | External configuration file path. |
| `quarkus.debian.data-dir` | `/var/lib/${name}` | Application state and service user home directory. |
| `quarkus.debian.log-dir` | `/var/log/${name}` | Application log directory. |
| `quarkus.debian.executable-file` | `/usr/bin/${name}` | Path to the installed launcher script. |
| `quarkus.debian.defaults-file` | `/etc/default/${name}` | Path to the environment defaults file. |
| `quarkus.debian.systemd-unit-file`| `/usr/lib/systemd/system/${name}.service` | Path to the systemd service file. |
| `quarkus.debian.service-user` | `${name}` | Dedicated system user for running the service. |
| `quarkus.debian.service-group` | `${name}` | Dedicated system group for running the service. |
| `quarkus.debian.output-name` | `${name}_${version}_${arch}.deb` | Filename of the generated package in `target/`. |

---

## 🔄 Configuration Hot Reload

### Architectural Overview

In production environments, modifying application properties typically requires restarting the JVM process, which causes service downtime, terminates active connections, and invalidates JVM JIT compiler warmups.

`quarkus-debian-packaging` provides a non-disruptive, zero-overhead configuration hot-reload mechanism:

```text
+---------------------------------------+
|  /etc/<app>/application.properties   | (Edited by admin/Ansible)
+---------------------------------------+
                   |
     [systemctl reload <app>]
     [<app> --reload]
                   |
                   v
+---------------------------------------+
|  /run/<app>/control.sock (UNIX Socket)|
+---------------------------------------+
                   |
                   v
+---------------------------------------+
|        ControlSocketServer            |
|       (Receives "RELOAD")             |
+---------------------------------------+
                   |
                   v
+---------------------------------------+
|        ConfigReloadService            |
| 1. Read /etc/<app>/application.props  |
| 2. Build candidate SmallRyeConfig     |
| 3. Validate constraints (Fail-Fast)   |
+---------------------------------------+
           /                \
   [Valid]                    [Invalid]
      |                           |
      v                           v
+------------------------+  +---------------------------+
| ConfigMappingPatcher   |  | Reject & log errors       |
| In-place VarHandle     |  | Live state UNTOUCHED      |
| update + releaseFence  |  +---------------------------+
+------------------------+
      |
      v
+------------------------+
| Fire CDI Event         |
| (ConfigReloadedEvent)  |
+------------------------+
```

### Hot-Path Zero-Overhead Guarantee

Unlike typical reload solutions that wrap configuration properties in dynamic proxies, `ThreadLocal` lookups, or `volatile`/`AtomicReference` containers, `quarkus-debian-packaging` uses **in-place field mutation via `VarHandle` followed by a memory release fence (`VarHandle.releaseFence()`)**:

- Injected `@ConfigMapping` bean instances retain their original object identity.
- Reading configuration properties executes standard single-cycle `GETFIELD` machine instructions.
- Business critical hot paths incur **zero runtime performance penalty**.

### Triggering Hot Reload

Once `/etc/<app>/application.properties` has been modified, trigger hot reload using any of the following methods:

#### Method 1: Systemd Reload (Recommended)
```bash
sudo systemctl reload my-app
```

#### Method 2: Launcher CLI Helper
```bash
# Invokes the built-in python reload helper
my-app --reload
```

#### Method 3: Direct UNIX Domain Socket Command
```bash
echo "RELOAD" | socat - UNIX-CONNECT:/run/my-app/control.sock
```

To check socket server status:
```bash
echo "STATUS" | socat - UNIX-CONNECT:/run/my-app/control.sock
# Response: OK Control Socket Server is running on /run/my-app/control.sock
```

### Hot Reload Configuration (`quarkus.debian.reload.*`)

Configured at runtime in `src/main/resources/application.properties` or `/etc/<app>/application.properties`:

| Property | Default Value | Description |
|:---|:---|:---|
| `quarkus.debian.reload.enabled` | `true` | Enables or disables the UNIX domain socket reload server. |
| `quarkus.debian.reload.socket-path`| `/run/${packageName}/control.sock` | Path to the UNIX domain control socket. |

---

## 💻 Application Code Example

### 1. Define a Config Mapping

Declare your business configuration using standard SmallRye `@ConfigMapping`:

```java
package com.example;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@ConfigMapping(prefix = "app.feature")
public interface FeatureConfig {

    @WithDefault("false")
    boolean rateLimiterEnabled();

    @Min(1)
    @Max(10000)
    @WithDefault("100")
    int maxRequestsPerSecond();

    @WithDefault("Standard message")
    String bannerMessage();
}
```

Inject and use it anywhere:

```java
@ApplicationScoped
public class OrderService {

    @Inject
    FeatureConfig featureConfig;

    public void processOrder() {
        if (featureConfig.rateLimiterEnabled()) {
            int limit = featureConfig.maxRequestsPerSecond();
            // ...
        }
    }
}
```

When `/etc/<app>/application.properties` changes:
```properties
app.feature.rate-limiter-enabled=true
app.feature.max-requests-per-second=500
```
Running `systemctl reload my-app` updates `featureConfig.rateLimiterEnabled()` and `featureConfig.maxRequestsPerSecond()` **immediately in-memory**.

### 2. Observe Reload Events (Optional)

If your application needs to trigger follow-up actions (e.g., resizing connection pools, evicting caches, or logging audit trails), observe `ConfigReloadedEvent`:

```java
package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;
import io.github.rawvoid.quarkus.debian.packaging.runtime.config.ConfigReloadedEvent;

@ApplicationScoped
public class ConfigChangeListener {

    private static final Logger LOG = Logger.getLogger(ConfigChangeListener.class);

    void onConfigReload(@Observes ConfigReloadedEvent event) {
        LOG.infof("Configuration reloaded at %s from %s", 
                event.timestamp(), event.configFile());
        
        LOG.infof("Changed configuration keys: %s", event.changedKeys());

        if (event.changedKeys().contains("app.feature.max-requests-per-second")) {
            LOG.info("Updating internal rate limiter bucket capacity...");
            // adjust internal state
        }
    }
}
```

---

## 🛡️ Maintainer Scripts & Safety Guarantees

The generated `.deb` package incorporates robust Debian maintainer scripts (`postinst`, `prerm`, `postrm`):

- **Zero Data Loss**: Existing configuration files in `/etc/<app>/` are preserved during package upgrades or re-installations.
- **Service Account Isolation**: Automatically creates a dedicated, unprivileged system account (`${serviceUser}`) with home directory set to `/var/lib/<app>` and shell `/usr/sbin/nologin`.
- **Systemd Lifecycle Coordination**:
  - `postinst`: Reloads systemd daemon units and safely restarts the service if it was running prior to an upgrade.
  - `prerm`: Stops the service cleanly prior to package removal or upgrade.
  - `postrm`: Deregisters systemd units on package removal; cleans directories on `apt purge`.

---

## 📋 Requirements

- **Build Host**: JDK 17+, Maven 3.8+ (Cross-platform: macOS, Linux, Windows)
- **Runtime Target**: Debian 11+ (Bullseye), Ubuntu 20.04+ (Focal), or any systemd-based Linux distribution with `python3` (for reload helper)

---

## 📄 License

This project is licensed under the [Apache License, Version 2.0](LICENSE).
