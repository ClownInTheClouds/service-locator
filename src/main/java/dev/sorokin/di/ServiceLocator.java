package dev.sorokin.di;

import java.util.function.Supplier;

public interface ServiceLocator {

    void install(Module module, Module... additional);

    <T> void addInstance(Class<T> type, T instance);

    <T> void addFactory(Class<T> type, Supplier<T> factory);

    <T> T getService(Class<T> type);
}
