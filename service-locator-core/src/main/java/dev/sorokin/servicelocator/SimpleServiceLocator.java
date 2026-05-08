package dev.sorokin.servicelocator;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SimpleServiceLocator implements ServiceLocator {

    protected final Map<Class<?>, Supplier<?>> factories = new HashMap<>();
    protected final Map<Class<?>, Object> instances = new HashMap<>();

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
        var serviceInstance = instances.computeIfAbsent(serviceType, type -> {
            var factory = factories.get(type);
            if (factory == null) {
                throw new RuntimeException("No factory registered for " + type);
            }
            return factory.get();
        });
        return serviceType.cast(serviceInstance);
    }
}
