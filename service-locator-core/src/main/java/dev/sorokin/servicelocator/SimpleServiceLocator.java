package dev.sorokin.servicelocator;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class SimpleServiceLocator implements ServiceLocator {

    private static final long WAIT_TIMEOUT_SECONDS = 10;
    private static final ScopedValue<Set<Class<?>>> CREATING = ScopedValue.newInstance();

    private final ConcurrentMap<Class<?>, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();

    protected final ConcurrentMap<Class<?>, Supplier<?>> factories = new ConcurrentHashMap<>();
    protected final ConcurrentMap<Class<?>, Object> instances = new ConcurrentHashMap<>();

    @Override
    public void install(Module module, Module... additional) {
        module.configure(this);
        if (additional == null) return;
        for (var additionalModule : additional) {
            additionalModule.configure(this);
        }
    }

    @Override
    public <T> void addInstance(Class<T> type, T instance) {
        instances.put(type, instance);
    }

    @Override
    public <T> void addFactory(Class<T> type, Supplier<T> factory) {
        factories.put(type, factory);
    }

    @Override
    public <T> T getService(Class<T> serviceType) {
        var instance = instances.get(serviceType);
        if (instance != null) {
            return serviceType.cast(instance);
        }

        if (CREATING.orElse(Set.of()).contains(serviceType)) {
            throw new IllegalStateException("Circular dependency detected for: " + serviceType.getName());
        }

        var creatingFuture = new CompletableFuture<>();
        var existing = pending.putIfAbsent(serviceType, creatingFuture);
        if (existing != null) {
            return awaitCreation(serviceType, existing);
        }

        try {
            var created = createInstance(serviceType);
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

    private <T> T awaitCreation(Class<T> serviceType, CompletableFuture<?> future) {
        try {
            return serviceType.cast(future.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    "Timed out waiting for " + serviceType.getName() + " after " + WAIT_TIMEOUT_SECONDS + "s — "
                            + "вероятен дедлок между потоками, резолвящими взаимно зависимые сервисы.", e);
        } catch (ExecutionException e) {
            throw (e.getCause() instanceof RuntimeException re) ? re : new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + serviceType.getName(), e);
        }
    }

    private Object createInstance(Class<?> type) {
        var factory = factories.get(type);
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