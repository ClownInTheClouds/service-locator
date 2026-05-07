package dev.sorokin.di.reflection;

import dev.sorokin.di.SimpleServiceLocator;

public class ReflectionServiceLocator extends SimpleServiceLocator {

    public <T> void addFactory(Class<T> type) {
        factories.put(type, new ReflectionProvider<>(type, this));
    }

    public <T> void addFactory(Class<T> type, Class<? extends T> implementation) {
        factories.put(type, new ReflectionProvider<>(implementation, this));
    }
}
