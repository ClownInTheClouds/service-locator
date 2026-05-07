package dev.sorokin.di;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SimpleServiceLocator implements ServiceLocator {

    protected final Map<Class<?>, Supplier<?>> factories = new HashMap<>();
    protected final Map<Class<?>, Object> singletons = new HashMap<>();

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
        singletons.put(type, instance);
    }

    @Override
    public <T> void addFactory(Class<T> type, Supplier<T> factory) {
        factories.put(type, factory);
    }

    @Override
    public <T> T getService(Class<T> type) {
        if (singletons.containsKey(type)) {
            return type.cast(singletons.get(type));
        }

        var factory = factories.get(type);
        if (factory == null) {
            throw new RuntimeException("No factory for " + type);
        }

        var singleton = factory.get();
        singletons.put(type, singleton);

        return type.cast(singleton);
    }
}
