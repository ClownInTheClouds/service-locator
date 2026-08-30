package dev.sorokin.servicelocator;

/**
 * The lifecycle of a service registered via
 * {@link ServiceRegistry#addFactory(Class, java.util.function.Supplier, Scope)}.
 *
 * @author Sorokin Anton
 */
public enum Scope {

    /**
     * Exactly one instance is created, on first resolution, and the same instance is
     * returned by every subsequent {@link ServiceRegistry#getService(Class)} call.
     */
    SINGLETON,

    /**
     * A new instance is created by invoking the factory on every
     * {@link ServiceRegistry#getService(Class)} call; no instance is cached.
     */
    PROTOTYPE
}