package dev.sorokin.servicelocator.reflection;

import dev.sorokin.servicelocator.Module;
import dev.sorokin.servicelocator.Scope;
import dev.sorokin.servicelocator.ServiceLocator;
import dev.sorokin.servicelocator.SimpleServiceLocator;

import java.util.Objects;
import java.util.function.Supplier;

public class ReflectiveServiceLocator implements ServiceLocator, ReflectiveServiceRegistry {

    private final ServiceLocator delegate;

    public ReflectiveServiceLocator() {
        this(new SimpleServiceLocator());
    }

    public ReflectiveServiceLocator(ServiceLocator delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public void install(Module module, Module... additional) {
        ServiceLocator.configureAll(this, module, additional);
    }

    @Override
    public <T> void addInstance(Class<T> type, T instance) {
        delegate.addInstance(type, instance);
    }

    @Override
    public <T> void addFactory(Class<T> type, Supplier<T> factory, Scope scope) {
        delegate.addFactory(type, factory, scope);
    }

    @Override
    public <T> void addFactory(Class<T> type, Class<? extends T> implementation, Scope scope) {
        addFactory(type, new InjectingProvider<>(implementation, delegate), scope);
    }

    @Override
    public <T> T getService(Class<T> type) {
        return delegate.getService(type);
    }
}
