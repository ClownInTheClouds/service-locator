# service-locator

A small, thread-safe, JPMS-friendly service locator for Java, built for desktop applications
that ship as `jlink` custom runtime images.

The library ships as two modules: a dependency-free `core` with the public API and a default
implementation, and an optional `reflection` module adding constructor-based auto-wiring on top
of it.

## Features

- **Minimal, composable API** — `ServiceRegistry` (register + look up) and `ServiceLocator`
  (`ServiceRegistry` plus bulk configuration via `Module`s) are separate interfaces, so a
  `Module` only ever sees the narrow registration surface, never `install(...)` itself.
- **Singleton and prototype scopes** — pick per registration via `Scope`.
- **Thread-safe by design** — singleton creation is de-duplicated across concurrent callers
  without a global lock; a same-thread circular dependency is detected and rejected immediately;
  a cross-thread deadlock (two threads resolving mutually dependent services in opposite order)
  fails fast with a diagnosable `IllegalStateException` after a configurable timeout instead of
  hanging forever.
- **Reflective auto-wiring, as a decorator, not a subclass** — `ReflectiveServiceLocator` wraps
  *any* `ServiceLocator` implementation rather than extending one, so it composes with the
  default implementation or any future one without needing access to its internals.
- **Fully modular** — both modules have a `module-info.java`, `core` has zero external
  dependencies, and `reflection` correctly declares `requires transitive`, so consumers only
  need to `requires` the module they actually use.

## Requirements

- Gradle 8.x+ (built and tested with Gradle 9.x)
- Java 25 (toolchain)

## Modules

| Module | Artifact | Depends on | Adds |
|---|---|---|---|
| `service-locator-core` | `service-locator-core` | — | `ServiceRegistry`, `ServiceLocator`, `Module`, `Scope`, `SimpleServiceLocator` |
| `service-locator-reflection` | `service-locator-reflection` | `service-locator-core` (transitively) | `ReflectiveServiceLocator` — constructor-based auto-wiring |

## Usage

### Quick start

```groovy
dependencies {
    implementation 'dev.sorokin.servicelocator:service-locator-core:2.0.0'
}
```

```java
var locator = new SimpleServiceLocator();
locator.addInstance(Clock.class, Clock.systemUTC());
locator.addFactory(UserService.class, () -> new UserService(locator.getService(Clock.class)));

var userService = locator.getService(UserService.class);
```

### Singleton vs. prototype scope

```java
locator.addFactory(ConnectionPool.class, ConnectionPool::new);                    // singleton (default)
locator.addFactory(RequestContext.class, RequestContext::new, Scope.PROTOTYPE);   // new instance every call
```

### Modules

Group related registrations and apply them together:

```java
Module infrastructureModule = registry -> {
    registry.addInstance(Clock.class, Clock.systemUTC());
    registry.addFactory(ConnectionPool.class, ConnectionPool::new);
};

locator.install(infrastructureModule);
```

A `Module` receives only a `ServiceRegistry`, not the full `ServiceLocator` — it can register and
look up services, but it cannot itself trigger the installation of further modules.

### Reflective auto-wiring (`service-locator-reflection`)

```groovy
dependencies {
    implementation 'dev.sorokin.servicelocator:service-locator-reflection:2.0.0'
}
```

```java
var locator = new ReflectiveServiceLocator();          // wraps a SimpleServiceLocator by default
locator.addFactory(Clock.class, Clock::systemUTC);      // plain factories still work
locator.addFactory(UserService.class);                  // constructor auto-wired reflectively
```

`UserService` must have exactly one public constructor; each of its parameters is resolved
recursively through the locator. Interfaces, abstract classes, and non-static inner classes are
rejected up front with a clear error instead of failing deep inside construction.

`ReflectiveServiceLocator` wraps rather than extends its delegate, so it can sit on top of a
locator configured however you like:

```java
var locator = new ReflectiveServiceLocator(new SimpleServiceLocator(30)); // custom wait timeout
```

### Handling concurrent and circular dependencies

```java
var locator = new SimpleServiceLocator(30); // wait timeout in seconds, defaults to 10
```

- Resolving the same singleton from multiple threads at once is safe: the factory runs at most
  once, and every other caller receives the same instance without racing to create their own.
- A circular dependency formed within a single thread's call stack (`A` needs `B`, `B` needs `A`)
  is detected immediately and throws `IllegalStateException`.
- A circular dependency formed *across* threads cannot be detected in general — instead of
  hanging forever, resolution fails with a clear, diagnosable `IllegalStateException` once the
  configured wait timeout elapses.

## Building with `jlink`

Both modules are fully modular: `core` has no external dependencies at all, and `reflection`
declares `requires transitive dev.sorokin.servicelocator.core`, so a consumer only needs to
require the module it actually uses:

```java
module your.app {
    requires dev.sorokin.servicelocator.reflection;
}
```

If `ReflectiveServiceLocator` reflectively constructs one of *your* classes, that class's
package must be **exported** (not necessarily **opened**) from your module — reflective
construction only ever invokes a public constructor, under the same visibility rules that would
apply to calling `new` from outside your module.

## Publishing

Published via [`publish-plugin`](https://github.com/ClownInTheClouds/publish-plugin), which
configures the `mavenJava` publication for each module automatically — see its README for
details.

```groovy
publishing {
    repositories {
        maven { url = uri('https://your.repo/maven2') }
    }
}
```

## Testing

Both modules are tested with JUnit 5 (Jupiter) and Mockito:

```
./gradlew test
```
