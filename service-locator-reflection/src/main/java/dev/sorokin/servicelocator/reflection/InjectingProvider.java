package dev.sorokin.servicelocator.reflection;

import dev.sorokin.servicelocator.ServiceRegistry;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.function.Supplier;

final class InjectingProvider<T> implements Supplier<T> {

    private final Class<? extends T> implementation;
    private final ServiceRegistry serviceRegistry;

    InjectingProvider(Class<? extends T> implementation, ServiceRegistry serviceRegistry) {
        this.implementation = implementation;
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public T get() {
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

        MethodHandle handle;
        try {
            handle = MethodHandles.publicLookup().unreflectConstructor(constructor);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "Cannot access constructor of " + implementation.getName()
                            + " — is its package exported from the declaring module?", e
            );
        }

        try {
            return implementation.cast(handle.invokeWithArguments(params));
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Constructor of " + implementation.getName() + " threw a checked exception", e);
        }
    }
}
