package dev.sorokin.servicelocator.reflection;

import dev.sorokin.servicelocator.Scope;
import dev.sorokin.servicelocator.ServiceRegistry;

public interface ReflectiveServiceRegistry extends ServiceRegistry {

    default <T> void addFactory(Class<T> type) {
        addFactory(type, type, Scope.SINGLETON);
    }

    default <T> void addFactory(Class<T> type, Scope scope) {
        addFactory(type, type, scope);
    }

    default <T> void addFactory(Class<T> type, Class<? extends T> implementation) {
        addFactory(type, implementation, Scope.SINGLETON);
    }

    <T> void addFactory(Class<T> type, Class<? extends T> implementation, Scope scope);
}
