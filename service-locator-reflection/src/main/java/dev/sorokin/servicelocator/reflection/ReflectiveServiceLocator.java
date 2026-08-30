package dev.sorokin.servicelocator.reflection;

import dev.sorokin.servicelocator.Module;
import dev.sorokin.servicelocator.Scope;
import dev.sorokin.servicelocator.ServiceLocator;
import dev.sorokin.servicelocator.SimpleServiceLocator;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A {@link ServiceLocator} decorator that adds reflective, constructor-based registration
 * (see {@link ReflectiveServiceRegistry}) on top of an arbitrary {@link ServiceLocator} delegate.
 *
 * <p>This class wraps rather than extends its delegate, so it works with any current or future
 * {@link ServiceLocator} implementation — including one that is itself a decorator — without
 * needing access to that implementation's internals.
 *
 * <p>{@link #install} is implemented against {@code this}, not the delegate: passing the
 * delegate to {@link Module#configure(dev.sorokin.servicelocator.ServiceRegistry)} instead of
 * this wrapper would prevent {@link ReflectiveModule}'s {@code instanceof}-based dispatch from
 * ever seeing a {@link ReflectiveServiceRegistry}, silently disabling reflective registration
 * for installed modules.
 *
 * @author Sorokin Anton
 * @see SimpleServiceLocator
 * @see ReflectiveServiceRegistry
 */
public class ReflectiveServiceLocator implements ServiceLocator, ReflectiveServiceRegistry {

    private final ServiceLocator delegate;

    /**
     * Creates a locator wrapping a new {@link SimpleServiceLocator} with default settings.
     */
    public ReflectiveServiceLocator() {
        this(new SimpleServiceLocator());
    }

    /**
     * Creates a locator wrapping the given delegate.
     *
     * @param delegate the {@link ServiceLocator} to delegate plain (non-reflective)
     *                 registration and resolution to
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public ReflectiveServiceLocator(ServiceLocator delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
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
        delegate.addInstance(type, instance);
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
        delegate.addFactory(type, factory, scope);
    }

    /**
     * {@inheritDoc}
     *
     * @param type           the service type to register {@code implementation} under
     * @param implementation the concrete type to instantiate reflectively; must have exactly
     *                       one public constructor, must not be an interface or abstract
     *                       class, and must not be a non-static inner class
     * @param scope          how instances of {@code implementation} are cached across
     *                       {@link #getService(Class)} calls
     * @param <T>            the service type
     */
    @Override
    public <T> void addFactory(Class<T> type, Class<? extends T> implementation, Scope scope) {
        addFactory(type, new InjectingProvider<>(implementation, delegate), scope);
    }

    /**
     * {@inheritDoc}
     *
     * @param type the requested service type
     * @param <T>  the service type
     * @return the resolved instance; never {@code null}
     * @throws IllegalStateException if no instance or factory is registered for {@code type},
     *                               if resolving {@code type} would form a circular dependency, if a concurrent
     *                               creation on another thread does not complete before the delegate's wait timeout
     *                               elapses, or if reflective construction fails (missing or ambiguous public
     *                               constructor, an abstract type, a non-static inner class, or an inaccessible
     *                               constructor)
     */
    @Override
    public <T> T getService(Class<T> type) {
        return delegate.getService(type);
    }
}