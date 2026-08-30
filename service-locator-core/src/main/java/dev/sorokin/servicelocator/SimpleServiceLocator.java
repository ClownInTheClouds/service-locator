package dev.sorokin.servicelocator;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * The default {@link ServiceLocator} implementation.
 *
 * <p>Thread safety and correctness under concurrency are central design goals:
 * <ul>
 *   <li>Singleton resolution uses {@code putIfAbsent} on a map of in-flight
 *       {@link CompletableFuture}s rather than an explicit lock: the thread that wins the
 *       race actually creates the instance, and every other thread requesting the same type
 *       waits on that thread's future instead of racing to create its own.</li>
 *   <li>If the thread performing creation fails, every thread waiting on it is woken
 *       immediately with the real cause, instead of waiting out its own timeout.</li>
 *   <li>If no thread completes creation within the configured timeout, resolution fails with
 *       a clear {@link IllegalStateException} instead of hanging forever — this can happen if
 *       two threads concurrently resolve two services that depend on each other, which this
 *       class cannot detect and prevent in general.</li>
 *   <li>Circular dependencies formed entirely within a single thread's call stack (for
 *       example, a factory for {@code A} that resolves {@code B}, whose factory resolves
 *       {@code A} again) are detected via a {@link ScopedValue}, bound to the set of types
 *       currently under construction on that thread.</li>
 * </ul>
 *
 * @author Sorokin Anton
 * @see ServiceLocator
 */
public class SimpleServiceLocator implements ServiceLocator {

    private static final long DEFAULT_WAIT_TIMEOUT_SECONDS = 10;
    private static final ScopedValue<Set<Class<?>>> CREATING = ScopedValue.newInstance();

    private final ConcurrentMap<Class<?>, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, ServiceDescriptor<?>> registrations = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, Object> instances = new ConcurrentHashMap<>();

    private final long waitTimeoutSeconds;

    /**
     * Creates a locator with the default {@value #DEFAULT_WAIT_TIMEOUT_SECONDS}-second wait
     * timeout for singleton resolution performed on another thread.
     */
    public SimpleServiceLocator() {
        this(DEFAULT_WAIT_TIMEOUT_SECONDS);
    }

    /**
     * Creates a locator with a configurable wait timeout.
     *
     * @param waitTimeoutSeconds how long, in seconds, a thread waits for another thread to
     *                           finish creating a singleton before resolution fails with an
     *                           {@link IllegalStateException}
     */
    public SimpleServiceLocator(long waitTimeoutSeconds) {
        this.waitTimeoutSeconds = waitTimeoutSeconds;
    }

    /**
     * {@inheritDoc}
     *
     * @param module     the first module to configure this locator with
     * @param additional further modules to configure this locator with, in order
     */
    @Override
    public void install(Module module, Module... additional) {
        ServiceLocator.configureAll(this, module, additional);
    }

    /**
     * {@inheritDoc}
     *
     * @param type     the service type to register {@code instance} under
     * @param instance the instance to return for every future {@link #getService(Class)} call
     * @param <T>      the service type
     */
    @Override
    public <T> void addInstance(Class<T> type, T instance) {
        instances.put(type, instance);
    }

    /**
     * {@inheritDoc}
     *
     * @param type    the service type to register {@code factory} under
     * @param factory the factory that produces instances of {@code type}
     * @param scope   how instances produced by {@code factory} are cached across
     *                {@link #getService(Class)} calls
     * @param <T>     the service type
     */
    @Override
    public <T> void addFactory(Class<T> type, Supplier<T> factory, Scope scope) {
        registrations.put(type, switch (scope) {
            case SINGLETON -> new ServiceDescriptor.Singleton<>(factory);
            case PROTOTYPE -> new ServiceDescriptor.Prototype<>(factory);
        });
    }

    /**
     * {@inheritDoc}
     *
     * @param serviceType the requested service type
     * @param <T>         the service type
     * @return the resolved instance; never {@code null}
     * @throws IllegalStateException if no instance or factory is registered for
     *                               {@code serviceType}, if resolving it would form a circular dependency, or — for
     *                               a {@link Scope#SINGLETON} registration being created on another thread — if
     *                               that thread does not finish within the configured wait timeout
     */
    @Override
    public <T> T getService(Class<T> serviceType) {
        var instance = instances.get(serviceType);
        if (instance != null) {
            return serviceType.cast(instance);
        }

        var descriptor = registrations.get(serviceType);
        if (descriptor == null) {
            throw new IllegalStateException("No factory or instance registered for " + serviceType.getName());
        }
        var created = (descriptor instanceof ServiceDescriptor.Prototype<?>)
                ? resolvePrototype(serviceType, descriptor.factory())
                : resolveSingleton(serviceType, descriptor.factory());
        return serviceType.cast(created);
    }

    /**
     * Resolves a {@link Scope#PROTOTYPE}-scoped service: always creates a new instance,
     * never reads from or writes to the singleton instance cache.
     *
     * @param serviceType the requested service type
     * @param factory     the factory to invoke
     * @return the newly created instance
     * @throws IllegalStateException if resolving {@code serviceType} would form a circular
     *                               dependency
     */
    private Object resolvePrototype(Class<?> serviceType, Supplier<?> factory) {
        checkNotCircular(serviceType);
        return createInstance(serviceType, factory);
    }

    /**
     * Resolves a {@link Scope#SINGLETON}-scoped service, creating and caching it on first
     * resolution and returning the cached instance (or waiting for a concurrent creation to
     * finish) on every subsequent call.
     *
     * @param serviceType the requested service type
     * @param factory     the factory to invoke if this call wins the race to create the instance
     * @return the resolved (created or cached) instance
     * @throws IllegalStateException if resolving {@code serviceType} would form a circular
     *                               dependency, or if waiting for a concurrent creation on another thread times out
     */
    private Object resolveSingleton(Class<?> serviceType, Supplier<?> factory) {
        checkNotCircular(serviceType);
        var creatingFuture = new CompletableFuture<>();
        var existing = pending.putIfAbsent(serviceType, creatingFuture);
        if (existing != null) {
            return awaitCreation(serviceType, existing);
        }

        try {
            var created = createInstance(serviceType, factory);
            instances.put(serviceType, created);
            creatingFuture.complete(created);
            return serviceType.cast(created);
        } catch (Throwable throwable) {
            creatingFuture.completeExceptionally(throwable);
            throw throwable;
        } finally {
            pending.remove(serviceType, creatingFuture);
        }
    }

    /**
     * Checks that {@code serviceType} is not already being created higher up the current
     * thread's call stack.
     *
     * @param serviceType the service type about to be created
     * @param <T>         the service type
     * @throws IllegalStateException if {@code serviceType} is already under construction on
     *                               this thread
     */
    private <T> void checkNotCircular(Class<T> serviceType) {
        if (CREATING.orElse(Set.of()).contains(serviceType)) {
            throw new IllegalStateException("Circular dependency detected for: " + serviceType.getName());
        }
    }

    /**
     * Waits for another thread's in-progress singleton creation to complete.
     *
     * @param serviceType the requested service type, used only for diagnostic messages
     * @param future      the in-progress creation to wait on
     * @param <T>         the service type
     * @return the instance produced by the creating thread
     * @throws IllegalStateException if the wait times out, or if this thread is interrupted
     *                               while waiting
     * @throws RuntimeException      if the creating thread's factory threw a
     *                               {@link RuntimeException}
     * @throws Error                 if the creating thread's factory threw an {@link Error}
     */
    private <T> T awaitCreation(Class<T> serviceType, CompletableFuture<?> future) {
        try {
            return serviceType.cast(future.get(waitTimeoutSeconds, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    "Timed out waiting for " + serviceType.getName() + " after " + waitTimeoutSeconds + "s — "
                            + "a deadlock between threads resolving mutually dependent services is likely.", e);
        } catch (ExecutionException e) {
            var cause = e.getCause();
            switch (cause) {
                case RuntimeException re -> throw re;
                case Error error -> throw error;
                default -> throw new RuntimeException(cause);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + serviceType.getName(), e);
        }
    }

    /**
     * Invokes {@code factory} with the current thread's under-construction type set extended
     * to include {@code type}, so that any nested {@link #getService(Class)} call made by the
     * factory can detect a circular dependency back to {@code type}.
     *
     * @param type    the type being created, added to the {@link ScopedValue}-bound set for the
     *                duration of the call
     * @param factory the factory to invoke; may be {@code null} if no factory was registered
     *                for {@code type}
     * @return the instance produced by {@code factory}
     * @throws IllegalStateException if {@code factory} is {@code null}
     * @throws RuntimeException      if {@code factory} throws a {@link RuntimeException}
     */
    private Object createInstance(Class<?> type, Supplier<?> factory) {
        if (factory == null) {
            throw new IllegalStateException("No factory registered for " + type.getName());
        }
        var next = new HashSet<>(CREATING.orElse(Set.of()));
        next.add(type);
        try {
            return ScopedValue.where(CREATING, Set.copyOf(next)).call(factory::get);
        } catch (Exception e) {
            throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
        }
    }
}