package dev.sorokin.servicelocator;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class SimpleServiceLocator implements ServiceLocator {

    private static final long DEFAULT_WAIT_TIMEOUT_SECONDS = 10;
    private static final ScopedValue<Set<Class<?>>> CREATING = ScopedValue.newInstance();

    private final ConcurrentMap<Class<?>, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, ServiceDescriptor<?>> registrations = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, Object> instances = new ConcurrentHashMap<>();

    private final long waitTimeoutSeconds;

    public SimpleServiceLocator() {
        this(DEFAULT_WAIT_TIMEOUT_SECONDS);
    }

    public SimpleServiceLocator(long waitTimeoutSeconds) {
        this.waitTimeoutSeconds = waitTimeoutSeconds;
    }

    @Override
    public void install(Module module, Module... additional) {
        ServiceLocator.configureAll(this, module, additional);
    }

    @Override
    public <T> void addInstance(Class<T> type, T instance) {
        instances.put(type, instance);
    }

    @Override
    public <T> void addFactory(Class<T> type, Supplier<T> factory, Scope scope) {
        registrations.put(type, switch (scope) {
            case SINGLETON -> new ServiceDescriptor.Singleton<>(factory);
            case PROTOTYPE -> new ServiceDescriptor.Prototype<>(factory);
        });
    }

    @Override
    public <T> T getService(Class<T> serviceType) {
        var instance = instances.get(serviceType);
        if (instance != null) {
            return serviceType.cast(instance);
        }

        var descriptor = registrations.get(serviceType);
        if (descriptor == null) {
            throw new IllegalStateException("No factory or instance registered for " + serviceType.getName());
        }
        var created = (descriptor instanceof ServiceDescriptor.Prototype<?>)
                ? resolvePrototype(serviceType, descriptor.factory())
                : resolveSingleton(serviceType, descriptor.factory());
        return serviceType.cast(created);
    }

    private Object resolvePrototype(Class<?> serviceType, Supplier<?> factory) {
        checkNotCircular(serviceType);
        return createInstance(serviceType, factory);
    }

    private Object resolveSingleton(Class<?> serviceType, Supplier<?> factory) {
        checkNotCircular(serviceType);
        var creatingFuture = new CompletableFuture<>();
        var existing = pending.putIfAbsent(serviceType, creatingFuture);
        if (existing != null) {
            return awaitCreation(serviceType, existing);
        }

        try {
            var created = createInstance(serviceType, factory);
            instances.put(serviceType, created);
            creatingFuture.complete(created);
            return serviceType.cast(created);
        } catch (Throwable throwable) {
            creatingFuture.completeExceptionally(throwable);
            throw throwable;
        } finally {
            pending.remove(serviceType, creatingFuture);
        }
    }

    private <T> void checkNotCircular(Class<T> serviceType) {
        if (CREATING.orElse(Set.of()).contains(serviceType)) {
            throw new IllegalStateException("Circular dependency detected for: " + serviceType.getName());
        }
    }

    private <T> T awaitCreation(Class<T> serviceType, CompletableFuture<?> future) {
        try {
            return serviceType.cast(future.get(waitTimeoutSeconds, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    "Timed out waiting for " + serviceType.getName() + " after " + waitTimeoutSeconds + "s — "
                            + "вероятен дедлок между потоками, резолвящими взаимно зависимые сервисы.", e);
        } catch (ExecutionException e) {
            var cause = e.getCause();
            switch (cause) {
                case RuntimeException re -> throw re;
                case Error error -> throw error;
                default -> throw new RuntimeException(cause);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + serviceType.getName(), e);
        }
    }

    private Object createInstance(Class<?> type, Supplier<?> factory) {
        if (factory == null) {
            throw new IllegalStateException("No factory registered for " + type.getName());
        }
        var next = new HashSet<>(CREATING.orElse(Set.of()));
        next.add(type);
        try {
            return ScopedValue.where(CREATING, Set.copyOf(next)).call(factory::get);
        } catch (Exception e) {
            throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
        }
    }
}