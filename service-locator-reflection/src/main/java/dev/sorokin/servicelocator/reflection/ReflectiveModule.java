package dev.sorokin.servicelocator.reflection;

import dev.sorokin.servicelocator.Module;
import dev.sorokin.servicelocator.ServiceLocator;

public interface ReflectiveModule extends Module {

    void configure(ReflectiveServiceLocator serviceLocator);

    @Override
    default void configure(ServiceLocator serviceLocator) {
        if (serviceLocator instanceof ReflectiveServiceLocator reflectiveServiceLocator) {
            configure(reflectiveServiceLocator);
        }
    }
}
