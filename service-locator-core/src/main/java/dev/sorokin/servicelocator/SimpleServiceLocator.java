package dev.sorokin.servicelocator;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class SimpleServiceLocator implements ServiceLocator {

    protected final ConcurrentMap<Class<?>, Supplier<?>> factories = new ConcurrentHashMap<>();
    protected final ConcurrentMap<Class<?>, Object> instances = new ConcurrentHashMap<>();

    private final ConcurrentMap<Class<?>, Object> locks = new ConcurrentHashMap<>();
    private final ThreadLocal<Set<Class<?>>> creating = ThreadLocal.withInitial(HashSet::new);

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
        var lock = locks.computeIfAbsent(serviceType, _ -> new Object());
        synchronized (lock) {
            instance = instances.get(serviceType);
            if (instance == null) {
                instance = createInstance(serviceType);
                instances.put(serviceType, instance);
            }
            return serviceType.cast(instance);
        }
    }

    private Object createInstance(Class<?> type) {
        var current = creating.get();
        if (!current.add(type)) {
            throw new IllegalStateException("Circular dependency detected for: " + type.getName());
        }
        try {
            var factory = factories.get(type);
            if (factory == null) {
                throw new IllegalStateException("No factory registered for " + type.getName());
            }
            return factory.get();
        } finally {
            current.remove(type);
        }
    }
}