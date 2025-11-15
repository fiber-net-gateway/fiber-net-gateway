# Injector Reference

[中文版本](injector_cn.md)

## Overview
The dependency injection utilities live in `fiber-gateway-common` under `io.fiber.net.common.ioc`. An `Injector` is a lightweight runtime container that knows how to resolve classes described by `Module` bindings. The entry point is `Injector.getRoot()`, which represents an empty container. Real applications compose one or more modules and call `createChild` on the root to obtain an `InjectorImpl` instance backed by a `BeanDefinationRegistry`.

## Bean Resolution
Modules receive a `Binder` and register bindings via `bind`, `bindFactory`, `bindPrototype`, or `bindMultiBean`. When a consumer calls `injector.getInstance(SomeType.class)`, the injector looks up the bean definition in the registry, falling back to the parent if the current level has no mapping. Factories cache a singleton per injector and support lifecycle callbacks: an object that implements `Initializable` has its `init()` run automatically, and `Destroyable` instances are tracked so `Injector.destroy()` can tear them down in reverse order. `getInstances` collects multi-bind results (class, instance, or creator entries) sorted by the `order` supplied to `bindMultiBean*`.

## Child Injectors and Forking
`Injector.createChild(modules)` adds new bindings on top of the current injector. The resulting child inherits every parent binding and can introduce request-specific state (for example, a `RequestContext` prototype). `Injector.fork()` creates a sibling injector with the same parent and module registry but a fresh singleton cache. Forking is useful for warmups or multi-tenant copies where you need the same bindings without sharing instantiated singletons. `deepFork(Predicate)` keeps walking up the parent chain until the predicate fails, enabling selective copy-on-write of an injector tree.

## Lifecycle Semantics
Only objects produced by `bindFactory` are tracked for destruction. When an injector is destroyed, each tracked `Destroyable` has its `destroy()` invoked in LIFO order. Prototype beans cannot implement `Destroyable` (the registry rejects such bindings) to avoid dangling references. Always destroy short-lived child injectors after a request completes so temporary `Destroyable`s (connection pools, metrics buffers, etc.) are properly shut down.

## Example Usage
`fiber-gateway-example/src/main/java/io/fiber/net/example/ioc/InjectorUsageExample.java` demonstrates the complete API surface:
- Root → engine → project → request injectors built via `createChild`.
- Warmup performed through `Injector.fork()` to clone binding graphs without touching the long-lived injector.
- `getInstance` retrieves singletons such as `GreetingWorkflow`, and `getInstances` aggregates multiple `GreetingHandler` implementations registered with `bindMultiBean`.
- `LifecycleAwareResource` implements `Destroyable`, is created through a factory, and is automatically cleaned up once the corresponding injector is destroyed.

To experiment locally, run `mvn -pl fiber-gateway-example -am test` and read the accompanying JUnit cases in `InjectorUsageExampleTest`. They assert the parent/child relationships, request scoped children, and destroyable lifecycles.
