# Quarkus Debian Packaging

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-red.svg)](https://quarkus.io/)

A Quarkus extension that builds production-ready Debian binary packages (`.deb`) directly in pure Java with Linux FHS layout, systemd service integration, and external configuration loading.

---

## 🌟 Key Features

- **Pure Java Debian Packaging**: Builds binary `.deb` archives (`ar` archive containing `debian-binary`, `control.tar.gz`, and `data.tar.gz`) directly from Java using Apache Commons Compress. **Zero external platform dependencies**—no `dpkg-deb`, `fakeroot`, `ar`, `tar`, or Linux host required. Builds deterministically on macOS, Linux, and Windows CI.
- **Dual Runtime Packaging**:
  - **JVM Mode (Fast-Jar / Uber-Jar / Legacy-Jar)**: Bundles the Quarkus application layout, wrapper launcher and startup scripts, JVM options file, and systemd service.
  - **Native Executable Mode**: Directly packages GraalVM native Linux ELF executables (auto-detecting Debian target architecture from ELF headers) with streamlined startup scripts and minimal footprint.
- **Linux FHS & Systemd Integration**: Conforms to the Linux Filesystem Hierarchy Standard (`/usr/share/<app>`, `/etc/<app>`, `/var/log/<app>`, `/var/lib/<app>`), installs systemd unit files (`/usr/lib/systemd/system/<app>.service`), provisions dedicated non-root service accounts, and configures ambient network capabilities (`AmbientCapabilities=CAP_NET_BIND_SERVICE`, `CapabilityBoundingSet=CAP_NET_BIND_SERVICE`) with `NoNewPrivileges=true` to allow unprivileged binding to privileged ports (< 1024).
- **External Runtime Configuration**: Automatically loads `/etc/<app>/application.properties` (and profile companion files `/etc/<app>/application-<profile>.properties`) when launched via the Debian package entrypoint, while staying completely inert during `mvn package`, `quarkus:dev`, and `@QuarkusTest`.

---

## 📦 Getting Started

### 1. Add Dependency

Add `quarkus-debian-packaging` to your Quarkus project `pom.xml`:

```xml
<dependency>
    <groupId>io.github.rawvoid</groupId>
    <artifactId>quarkus-debian-packaging</artifactId>
    <version>${quarkus-debian-packaging.version}</version>
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

The resulting package is generated under `target/`:

```text
target/
├── <app>-runner.jar
└── <app>_<version>_<arch>.deb      # e.g., _all.deb (JVM) or _amd64.deb / _arm64.deb (Native)
```

### 3. Install and Run on Debian / Ubuntu

```bash
# Install the package
sudo dpkg -i target/<app>_<version>_<arch>.deb

# Verify systemd service status
sudo systemctl status my-app

# Start the service
sudo systemctl start my-app
```

---

## 📂 Linux Filesystem Layout (FHS)

The generated Debian package distributes files according to standard Linux conventions:

| Path | Ownership & Mode | Description |
|:---|:---|:---|
| `/usr/share/<app>/` | `root:<app> 0750` | Application directory containing `quarkus-app/` (or native binary) and the `startup` script |
| `/etc/<app>/` | `root:<app> 0750` | Production configuration directory |
| `/etc/<app>/application.properties` | `root:<app> 0640` | External base configuration file (SmallRye ordinal 275, Debian conffile) |
| `/etc/<app>/jvm.options` | `root:<app> 0640` | JVM startup options (heap size, GC flags, etc., JVM mode only, Debian conffile) |
| `/etc/default/<app>` | `root:root 0644` | Environment variable overrides sourced by the launcher before startup (Debian conffile) |
| `/usr/bin/<app>` | `root:root 0755` | Executable launcher wrapper |
| `/usr/lib/systemd/system/<app>.service` | `root:root 0644` | Systemd service unit definition |
| `/var/log/<app>/` | `<app>:<app> 0750` | Application log directory |
| `/var/lib/<app>/` | `<app>:<app> 0750` | Application persistent state and service user home directory |

---

## ⚙️ Configuration Reference

### Build-Time Packaging Configuration (`quarkus.debian.*`)

Configured in `src/main/resources/application.properties` or Maven `-D` properties:

| Property | Default Value | Description |
|:---|:---|:---|
| `quarkus.debian.enabled` | `true` | Enables or disables Debian package generation and external config registration. |
| `quarkus.debian.name` | `${quarkus.application.name}` | Debian package name. Must match `[a-z0-9][a-z0-9+.-]+`. |
| `quarkus.debian.version` | `${quarkus.application.version}` | Debian package version string. |
| `quarkus.debian.description` | `${quarkus.application.name} service` | Package description used in `control` and systemd unit. |
| `quarkus.debian.maintainer` | `Unknown <unknown@unknown>` | Maintainer contact field for the `control` file. |
| `quarkus.debian.section` | `web` | Debian package section (e.g. `web`, `admin`, `net`). |
| `quarkus.debian.priority` | `optional` | Debian package priority (`optional`, `standard`, `extra`). |
| `quarkus.debian.architecture` | `all` (JVM) / Linux ELF header (native) | Target CPU architecture (`all`, `amd64`, `arm64`, `i386`, `ppc64el`, `ppc64`, `s390x`, `riscv64`). For native packages, the architecture is determined directly from the Linux ELF binary header, failing fast if the binary is missing, non-ELF, or unsupported. |
| `quarkus.debian.depends` | (Computed dynamically) | Additional package dependencies to include in the Debian control `Depends` field. Base dependencies (`systemd`, and JRE for JVM) are computed automatically. |
| `quarkus.debian.install-dir` | `/usr/share/${name}` | Installation directory for the application payload. |
| `quarkus.debian.config-dir` | `/etc/${name}` | External configuration directory. |
| `quarkus.debian.config-file` | `/etc/${name}/application.properties` | External configuration file path. |
| `quarkus.debian.data-dir` | `/var/lib/${name}` | Application state and service user home directory. |
| `quarkus.debian.log-dir` | `/var/log/${name}` | Application log directory. |
| `quarkus.debian.executable-file` | `/usr/bin/${name}` | Path to the installed launcher script. |
| `quarkus.debian.defaults-file` | `/etc/default/${name}` | Path to the environment defaults file. |
| `quarkus.debian.systemd-unit-file` | `/usr/lib/systemd/system/${name}.service` | Path to the systemd service file. |
| `quarkus.debian.service-user` | `${name}` | Dedicated system user for running the service. |
| `quarkus.debian.service-group` | `${name}` | Dedicated system group for running the service. |
| `quarkus.debian.output-name` | `${name}_${version}_${arch}.deb` | Filename of the generated package in `target/`. |

---

## 🔧 External Configuration & CLI

### External Configuration Hierarchy

When the installed service is started via `/usr/bin/<app>`, the launcher sources `/etc/default/<app>` and exports:

```sh
export QUARKUS_DEBIAN_EXTERNAL_CONFIG=true
export QUARKUS_DEBIAN_CONFIG_FILE="${QUARKUS_DEBIAN_CONFIG_FILE:-/etc/<app>/application.properties}"
```

SmallRye Config loads external properties at runtime with the following ordinal precedence:

| Layer | Ordinal | Source |
|:---|:---|:---|
| Packaged Defaults | `250` | Bundled `application.properties` inside the JAR / executable |
| External Base File | `275` | `/etc/<app>/application.properties` (or `QUARKUS_DEBIAN_CONFIG_FILE`) |
| External Profile Files | `275 + N - i` (< `300`) | `/etc/<app>/application-<profile>.properties`, ordered by active profile priority |
| Environment Variables | `300` | OS environment variables & `/etc/default/<app>` |
| System Properties | `400` | JVM system properties (`-Dkey=value`) |

Because `QUARKUS_DEBIAN_EXTERNAL_CONFIG=true` is only exported by the installed production launcher, build-time packaging (`mvn package`), `quarkus:dev`, and `@QuarkusTest` never accidentally read host `/etc` files. Apply external configuration edits by restarting the service (`sudo systemctl restart <app>`).

### Launcher CLI Commands

The installed executable (`/usr/bin/<app>`) provides convenience CLI operations:

```bash
# Query systemd service status (forwards extra arguments to systemctl)
my-app status
my-app status -n 50 --no-pager

# Print package version and architecture
my-app --version

# Show usage guide and configuration paths
my-app --help
```

---

## 🛡️ Maintainer Scripts & Safety Guarantees

The generated `.deb` package incorporates robust Debian maintainer scripts (`postinst`, `prerm`, `postrm`):

- **Zero Data Loss**: Existing configuration files in `/etc/<app>/` and `/etc/default/<app>` are registered in `conffiles` and preserved during package upgrades or re-installations.
- **Service Account Isolation**: Automatically creates a dedicated, unprivileged system account (`${serviceUser}`) with home directory set to `/var/lib/<app>` and shell `/usr/sbin/nologin`.
- **Systemd Lifecycle Coordination**:
  - `postinst`: Reloads systemd daemon units and safely restarts the service if it was running prior to an upgrade.
  - `prerm`: Stops the service cleanly prior to package removal or upgrade.
  - `postrm`: Deregisters systemd units on package removal; cleans directories and service account on `apt purge`.

---

## 📋 Requirements

- **Build Host**: JDK 17+, Maven 3.8+ (Cross-platform: macOS, Linux, Windows)
- **Runtime Target**: Debian 11+ (Bullseye), Ubuntu 20.04+ (Focal), or any systemd-based Linux distribution

---

## 📄 License

This project is licensed under the [Apache License, Version 2.0](LICENSE).
