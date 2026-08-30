package dev.sorokin.servicelocator.reflection;

import dev.sorokin.servicelocator.Scope;
import dev.sorokin.servicelocator.ServiceRegistry;

/**
 * A {@link ServiceRegistry} extended with reflective, constructor-based registration:
 * instead of supplying a {@link java.util.function.Supplier} explicitly, a type is registered
 * directly, and its single public constructor is invoked reflectively on resolution, with
 * each parameter resolved recursively via {@link #getService(Class)}.
 *
 * <p>This is the narrow view exposed to {@link ReflectiveModule#configure(ReflectiveServiceRegistry)}.
 *
 * @author Sorokin Anton
 * @see ReflectiveServiceLocator
 */
public interface ReflectiveServiceRegistry extends ServiceRegistry {

    /**
     * Registers {@code type} to be created reflectively via its own constructor, with
     * {@link Scope#SINGLETON} scope.
     *
     * <p>Equivalent to {@code addFactory(type, type, Scope.SINGLETON)}.
     *
     * @param type the concrete type to instantiate reflectively
     * @param <T>  the service type
     */
    default <T> void addFactory(Class<T> type) {
        addFactory(type, type, Scope.SINGLETON);
    }

    /**
     * Registers {@code type} to be created reflectively via its own constructor, with the
     * given scope.
     *
     * <p>Equivalent to {@code addFactory(type, type, scope)}.
     *
     * @param type  the concrete type to instantiate reflectively
     * @param scope how instances of {@code type} are cached (or not) across
     *              {@link #getService(Class)} calls
     * @param <T>   the service type
     */
    default <T> void addFactory(Class<T> type, Scope scope) {
        addFactory(type, type, scope);
    }

    /**
     * Registers {@code implementation} as the reflectively-created source of {@code type},
     * with {@link Scope#SINGLETON} scope.
     *
     * <p>Equivalent to {@code addFactory(type, implementation, Scope.SINGLETON)}.
     *
     * @param type           the service type to register {@code implementation} under
     * @param implementation the concrete type to instantiate reflectively
     * @param <T>            the service type
     */
    default <T> void addFactory(Class<T> type, Class<? extends T> implementation) {
        addFactory(type, implementation, Scope.SINGLETON);
    }

    /**
     * Registers {@code implementation} as the reflectively-created source of {@code type},
     * with the given scope.
     *
     * <p>{@code implementation} must have exactly one public constructor; each of its
     * parameters is resolved via {@link #getService(Class)} when an instance is created. See
     * {@link ReflectiveServiceLocator} for the full set of instantiability requirements.
     *
     * @param type           the service type to register {@code implementation} under
     * @param implementation the concrete type to instantiate reflectively
     * @param scope          how instances of {@code implementation} are cached (or not) across
     *                       {@link #getService(Class)} calls
     * @param <T>            the service type
     */
    <T> void addFactory(Class<T> type, Class<? extends T> implementation, Scope scope);
}