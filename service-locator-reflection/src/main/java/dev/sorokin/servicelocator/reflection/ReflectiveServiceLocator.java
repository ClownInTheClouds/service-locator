package dev.sorokin.servicelocator.reflection;

import dev.sorokin.servicelocator.SimpleServiceLocator;

public class ReflectiveServiceLocator extends SimpleServiceLocator {

    public <T> void addFactory(Class<T> type) {
        factories.put(type, new InjectingProvider<>(type, this));
    }

    public <T> void addFactory(Class<T> type, Class<? extends T> implementation) {
        factories.put(type, new InjectingProvider<>(implementation, this));
    }
}
