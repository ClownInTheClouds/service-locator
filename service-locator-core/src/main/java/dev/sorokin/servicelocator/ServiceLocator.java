package dev.sorokin.servicelocator;

import java.util.function.Supplier;

public interface ServiceLocator {

    void install(Module module, Module... additional);

    <T> void addInstance(Class<T> type, T instance);

    <T> void addFactory(Class<T> type, Supplier<T> factory, Scope scope);

    <T> T getService(Class<T> type);
}
