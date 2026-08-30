package dev.sorokin.servicelocator.reflection;

import dev.sorokin.servicelocator.ServiceRegistry;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.function.Supplier;

/**
 * A {@link Supplier} that creates instances of {@link #implementation} by reflectively
 * invoking its single public constructor, resolving each constructor parameter via
 * {@link ServiceRegistry#getService(Class)}.
 *
 * <p>Construction is done through a {@link MethodHandle} rather than
 * {@link Constructor#newInstance}, so that an exception thrown by the constructor itself
 * propagates as-is instead of being wrapped in
 * {@link java.lang.reflect.InvocationTargetException}.
 *
 * @param <T> the service type this provider produces; {@link #implementation} is a subtype of
 *            (or equal to) {@code T}
 */
final class InjectingProvider<T> implements Supplier<T> {

    private final Class<? extends T> implementation;
    private final ServiceRegistry serviceRegistry;

    /**
     * @param implementation  the concrete type to instantiate reflectively
     * @param serviceRegistry the registry used to resolve constructor parameters
     */
    InjectingProvider(Class<? extends T> implementation, ServiceRegistry serviceRegistry) {
        this.implementation = implementation;
        this.serviceRegistry = serviceRegistry;
    }

    /**
     * Creates a new instance of {@link #implementation} by resolving its constructor
     * parameters and invoking the constructor.
     *
     * @return the newly created instance
     * @throws IllegalStateException if {@link #implementation} is an interface or abstract
     *                               class, is a non-static inner class, does not have exactly one public
     *                               constructor, if that constructor's declaring package is not accessible to this
     *                               module, or if the constructor throws a checked exception
     * @throws RuntimeException      if the constructor throws a {@link RuntimeException}
     * @throws Error                 if the constructor throws an {@link Error}
     */
    @Override
    public T get() {
        validateInstantiable(implementation);
        var constructors = implementation.getConstructors();
        if (constructors.length != 1) {
            throw new IllegalStateException(
                    "Type " + implementation.getName() + " must have exactly one public constructor "
                            + "for reflective injection, found " + constructors.length
            );
        }
        var constructor = constructors[0];
        var paramTypes = constructor.getParameterTypes();
        var params = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            params[i] = serviceRegistry.getService(paramTypes[i]);
        }

        var handle = getMethodHandle(constructor);

        try {
            return implementation.cast(handle.invokeWithArguments(params));
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Constructor of " + implementation.getName() + " threw a checked exception", e);
        }
    }

    /**
     * Looks up a {@link MethodHandle} for {@code constructor}.
     *
     * @param constructor the constructor to look up
     * @return a method handle that invokes {@code constructor}
     * @throws IllegalStateException if {@code constructor} is not accessible — typically
     *                               because its declaring package is not exported by its module
     */
    private MethodHandle getMethodHandle(Constructor<?> constructor) {
        try {
            return MethodHandles.publicLookup().unreflectConstructor(constructor);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "Cannot access constructor of " + implementation.getName()
                            + " — is its package exported from the declaring module?", e
            );
        }
    }

    /**
     * Validates that {@code implementation} is a concrete, top-level or static nested class
     * that can be instantiated reflectively.
     *
     * @param implementation the type to validate
     * @throws IllegalStateException if {@code implementation} is an interface, an abstract
     *                               class, or a non-static inner class
     */
    private static void validateInstantiable(Class<?> implementation) {
        if (implementation.isInterface() || Modifier.isAbstract(implementation.getModifiers())) {
            throw new IllegalStateException(
                    "Type " + implementation.getName() + " is abstract or an interface and cannot be reflectively instantiated"
            );
        }
        if (implementation.isMemberClass() && !Modifier.isStatic(implementation.getModifiers())) {
            throw new IllegalStateException(
                    "Type " + implementation.getName() + " is a non-static inner class; "
                            + "make it static or top-level for reflective injection"
            );
        }
    }
}