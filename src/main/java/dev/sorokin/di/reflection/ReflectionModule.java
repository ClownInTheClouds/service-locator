package dev.sorokin.di.reflection;

import dev.sorokin.di.Module;
import dev.sorokin.di.ServiceLocator;

public interface ReflectionModule extends Module {

    void configure(ReflectionServiceLocator serviceLocator);

    @Override
    default void configure(ServiceLocator serviceLocator) {
        if (serviceLocator instanceof ReflectionServiceLocator reflectionServiceLocator) {
            configure(reflectionServiceLocator);
        }
    }
}
