package dev.sorokin.servicelocator.reflection;

import dev.sorokin.servicelocator.Module;
import dev.sorokin.servicelocator.ServiceRegistry;

public interface ReflectiveModule extends Module {

    void configure(ReflectiveServiceRegistry serviceRegistry);

    @Override
    default void configure(ServiceRegistry serviceRegistry) {
        if (serviceRegistry instanceof ReflectiveServiceRegistry reflectiveServiceRegistry) {
            configure(reflectiveServiceRegistry);
        }
    }
}
