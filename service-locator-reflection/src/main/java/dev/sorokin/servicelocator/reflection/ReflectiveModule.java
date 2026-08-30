package dev.sorokin.servicelocator.reflection;

import dev.sorokin.servicelocator.Module;
import dev.sorokin.servicelocator.ServiceRegistry;

/**
 * A {@link Module} that configures a {@link ReflectiveServiceRegistry}, gaining access to its
 * reflective {@code addFactory(Class)} registration methods in addition to the plain
 * {@link ServiceRegistry} ones.
 *
 * <p>{@link #configure(ServiceRegistry)} bridges the plain {@link Module} contract to
 * {@link #configure(ReflectiveServiceRegistry)}: if the registry passed to
 * {@link dev.sorokin.servicelocator.ServiceLocator#install} is not a
 * {@link ReflectiveServiceRegistry}, this module is silently skipped rather than failing —
 * such a module simply does not apply to that locator.
 *
 * @author Sorokin Anton
 */
public interface ReflectiveModule extends Module {

    /**
     * Registers this module's services against {@code serviceRegistry}, with reflective
     * registration methods available.
     *
     * @param serviceRegistry the reflective registry to configure
     */
    void configure(ReflectiveServiceRegistry serviceRegistry);

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link #configure(ReflectiveServiceRegistry)} if {@code serviceRegistry}
     * is a {@link ReflectiveServiceRegistry}; otherwise does nothing.
     *
     * @param serviceRegistry the registry passed by
     *                        {@link dev.sorokin.servicelocator.ServiceLocator#install}
     */
    @Override
    default void configure(ServiceRegistry serviceRegistry) {
        if (serviceRegistry instanceof ReflectiveServiceRegistry reflectiveServiceRegistry) {
            configure(reflectiveServiceRegistry);
        }
    }
}