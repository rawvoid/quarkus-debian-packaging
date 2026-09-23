# Configuration Hot-Reload Architecture

This document describes the architectural design and runtime mechanics of the dynamic configuration hot-reload subsystem in `quarkus-debian-packaging`.

## 1. Overview

The extension enables live, zero-downtime configuration updates for Quarkus applications packaged as Debian services (`systemd`). Configuration changes applied to `/etc/<app>/application.properties` can be reloaded on-demand via a UNIX domain socket command (`systemctl reload <app>` triggering `echo reload | nc -U /run/<app>/control.sock`).

To maintain high throughput and low latency in production, configuration reloading is designed around three non-negotiable principles:
1. **Zero Runtime Reflection & Zero Hot-Path Allocation**: Hot paths execute via direct interface dispatch without reflection, `VarHandle`, heap allocations, or map lookups.
2. **Atomic Snapshot Pointer Swapping**: The active configuration state transitions via an `AtomicReference` pointer swap only after the entire configuration payload is validated.
3. **Transparent CDI Injection**: Application injection points (`@Inject MyConfig config`) seamlessly receive reloadable instances without altering business code or introducing custom annotations.

---

## 2. Architectural Components

```mermaid
flowchart TD
    subgraph Build Time [Deployment Module]
        A["ConfigClassBuildItem (isMapping)"] --> B["ConfigProxyGenerator (Gizmo)"]
        B --> C["&lt;Config&gt;$$ReloadProxy bytecode"]
        C --> D["SyntheticBeanBuildItem (@Alternative, @Priority 1000)"]
    end

    subgraph Runtime Initialization [Runtime Module]
        D --> E["ReloadableConfigCreator"]
        E --> F["Bootstrap Snapshot from SmallRyeConfig"]
        F --> G["ReloadableConfigRegistry (ConfigMappingKey -> AtomicReference)"]
        E --> H["Instantiate &lt;Config&gt;$$ReloadProxy (binds holder field)"]
    end

    subgraph Application Hot Path [Zero Reflection & Zero Allocation]
        I["Application Bean (@Inject MyConfig)"] --> H
        H -->|"1. this.holder.get() (single volatile read)"| J["Active Config Snapshot ($$CMImpl)"]
        H -->|"2. Invoke getter on snapshot"| J
    end

    subgraph Reload Transaction [ConfigReloadService]
        K["UNIX Socket 'reload'"] --> L["Phase 1: Read /etc/&lt;app&gt;/application.properties"]
        L --> M["Phase 2: Build Candidate SmallRyeConfig & Validate"]
        M -->|"Validation Failed"| N["Fail-Fast: Reject & Keep Active State"]
        M -->|"Validation Passed"| O["Phase 3: Atomic Snapshot Swap"]
        O -->|"Swap pointer in Registry"| G
        O --> P["Phase 4: Fire CDI ConfigReloadedEvent"]
    end
```

### 2.1 Build-Time AOT Proxy Generation (`deployment`)

During Quarkus extension build time:
- **`ConfigProxyGenerator`**: Inspects user `@ConfigMapping` interfaces and compiles a lightweight proxy `<Interface>$$ReloadProxy implements <Interface>` using Gizmo bytecode generation.
- **Direct Holder Binding & Method Delegation**:
  Each generated proxy class declares a private final field:
  ```java
  private final AtomicReference<Object> holder;
  ```
  The proxy constructor binds this holder once upon creation via `ReloadableConfigRegistry.getHolder(Interface.class, prefix)`.
  Every interface getter method delegates directly to:
  ```java
  ((Interface) this.holder.get()).<method>();
  ```
  Standard `Object` methods (`toString`, `hashCode`, `equals`) similarly forward through `this.holder.get()`.
  This guarantees **zero heap allocations** and **zero `Map` lookups** on the configuration hot path.
- **Arc Synthetic Alternative Bean**:
  `ConfigReloadProcessor` registers each proxy as a synthetic CDI bean targeting the config mapping interface:
  - Scope: `@Singleton`
  - Identifier: `<InterfaceName>_reloadable_proxy`
  - Alternative: `@Alternative` with `@Priority(1000)`
  - Creator: `ReloadableConfigCreator`
  Arc CDI container automatically chooses this alternative over SmallRye's default non-alternative synthetic mapping bean, ensuring any `@Inject MyConfig` gets the reloadable proxy.

### 2.2 Thread-Safe Registry & Domain Model (`runtime`)

- **`ConfigMappingKey`**: Immutable domain record `(Class<?> mappingClass, String prefix)` providing consistent mapping identification across the registry and reload service.
- **`ReloadableConfigRegistry`**: Maintains a thread-safe registry keyed by `ConfigMappingKey` mapped to an `AtomicReference<Object>`.
- **Atomic Pointer Swap**: Swapping configuration is a single atomic volatile pointer update (`AtomicReference.set(newSnapshot)`). Reading threads always observe either the entire old valid snapshot or the entire new valid snapshot, with zero lock contention.

### 2.3 Transactional Configuration Reload (`ConfigReloadService`)

When a reload request arrives via the control socket (`ControlSocketServer`), `ConfigReloadService` executes a strict four-phase transaction:

1. **Phase 1: Raw File Ingestion**:
   Reads `/etc/<app>/application.properties` from disk into memory. Syntax or I/O errors abort the reload immediately.
2. **Phase 2: Snapshot Validation & Candidate Construction**:
   Constructs an isolated, candidate `SmallRyeConfig` instance backed by the new properties layered over default sources. SmallRye Config and Hibernate Validator validate all constraints. If any constraint fails, the reload is aborted and descriptive errors are returned; existing application state is completely untouched.
3. **Phase 3: Atomic Pointer Swap & Source Commit**:
   Upon successful validation, updates `ExternalConfigSource` and atomically replaces snapshots in `ReloadableConfigRegistry`.
4. **Phase 4: CDI Event Publication**:
   Dispatches a synchronous CDI `ConfigReloadedEvent` containing the updated keys for services that require secondary reconciliation (e.g., reconnecting clients, flushing caches).

---

## 3. Native Image & Performance Invariants

- **100% GraalVM Native Image Safe**: Proxies are generated at build time; constructors are registered via `ReflectiveClassBuildItem`. No dynamic class loading, bytecode manipulation, or deep reflection occurs at runtime.
- **Hot-Path Efficiency**: The invocation overhead is strictly equivalent to one field dereference (`getfield`) + one volatile reference read (`holder.get()`) followed by a standard monomorphic interface call.
- **Build Step Isolation**: Proxy registration is scheduled in `registerReloadableProxies` consuming `ConfigClassBuildItem` prior to Arc injection validation, preventing circular dependency cycles in the Quarkus build step execution graph.
