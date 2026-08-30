package dev.sorokin.servicelocator;

import java.util.function.Supplier;

/**
 * The service-registration and lookup surface of a service locator.
 *
 * <p>This is the narrow view exposed to {@link Module#configure(ServiceRegistry)} — it
 * intentionally excludes {@link ServiceLocator#install}, so a module's configuration step
 * cannot itself trigger the installation of further modules.
 *
 * @author Sorokin Anton
 * @see ServiceLocator
 */
public interface ServiceRegistry {

    /**
     * Registers a pre-built instance as the service for {@code type}.
     *
     * <p>A pre-registered instance always takes priority over any factory registered for the
     * same {@code type}: {@link #getService(Class)} returns it directly without ever
     * invoking a factory.
     *
     * @param type     the service type to register {@code instance} under
     * @param instance the instance to return for every future {@link #getService(Class)} call
     *                 for {@code type}
     * @param <T>      the service type
     */
    <T> void addInstance(Class<T> type, T instance);

    /**
     * Registers {@code factory} as the {@link Scope#SINGLETON}-scoped source of {@code type}.
     *
     * <p>Equivalent to {@code addFactory(type, factory, Scope.SINGLETON)}.
     *
     * @param type    the service type to register {@code factory} under
     * @param factory the factory that produces the single cached instance
     * @param <T>     the service type
     */
    default <T> void addFactory(Class<T> type, Supplier<T> factory) {
        addFactory(type, factory, Scope.SINGLETON);
    }

    /**
     * Registers {@code factory} as the source of {@code type}, resolved according to
     * {@code scope}.
     *
     * @param type    the service type to register {@code factory} under
     * @param factory the factory that produces instances of {@code type}
     * @param scope   how instances produced by {@code factory} are cached (or not) across
     *                {@link #getService(Class)} calls
     * @param <T>     the service type
     */
    <T> void addFactory(Class<T> type, Supplier<T> factory, Scope scope);

    /**
     * Resolves the service registered for {@code type}.
     *
     * @param type the requested service type
     * @param <T>  the service type
     * @return the resolved instance; never {@code null}
     * @throws IllegalStateException if no instance or factory is registered for {@code type},
     *                               if resolving {@code type} would form a circular dependency, if resolving
     *                               {@code type} on another thread does not complete before the implementation's
     *                               wait timeout elapses, or if the registered factory itself throws
     */
    <T> T getService(Class<T> type);
}