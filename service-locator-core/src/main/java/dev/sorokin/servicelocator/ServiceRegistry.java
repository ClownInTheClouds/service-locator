package dev.sorokin.servicelocator;

import java.util.function.Supplier;

public interface ServiceRegistry {

    <T> void addInstance(Class<T> type, T instance);

    default <T> void addFactory(Class<T> type, Supplier<T> factory) {
        addFactory(type, factory, Scope.SINGLETON);
    }

    <T> void addFactory(Class<T> type, Supplier<T> factory, Scope scope);

    <T> T getService(Class<T> type);
}
