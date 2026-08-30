package dev.sorokin.servicelocator;

import java.util.function.Supplier;

/**
 * Internal registration record pairing a service {@link Supplier factory} with the
 * {@link Scope} that determines how {@link SimpleServiceLocator} resolves it.
 *
 * <p>This is an implementation detail of the {@code core} module and is intentionally not
 * exported — the public {@link Scope} enum is what {@link ServiceRegistry} consumers interact
 * with; this sealed hierarchy exists purely so that resolution can be dispatched on scope via
 * an exhaustive pattern-matching {@code switch}.
 *
 * @param <T> the type of service produced by {@link #factory()}
 */
sealed interface ServiceDescriptor<T> permits ServiceDescriptor.Singleton, ServiceDescriptor.Prototype {

    /**
     * @return the factory that produces instances of the registered service
     */
    Supplier<T> factory();

    /**
     * A {@link Scope#SINGLETON}-scoped registration.
     *
     * @param <T>     the type of service produced by {@link #factory()}
     * @param factory the factory that produces the single cached instance
     */
    record Singleton<T>(Supplier<T> factory) implements ServiceDescriptor<T> {
    }

    /**
     * A {@link Scope#PROTOTYPE}-scoped registration.
     *
     * @param <T>     the type of service produced by {@link #factory()}
     * @param factory the factory invoked to produce a new instance on every resolution
     */
    record Prototype<T>(Supplier<T> factory) implements ServiceDescriptor<T> {
    }
}