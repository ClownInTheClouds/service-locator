package dev.sorokin.servicelocator;

import java.util.function.Supplier;

sealed interface ServiceDescriptor<T> permits ServiceDescriptor.Singleton, ServiceDescriptor.Prototype {
    Supplier<T> factory();

    record Singleton<T>(Supplier<T> factory) implements ServiceDescriptor<T> {}

    record Prototype<T>(Supplier<T> factory) implements ServiceDescriptor<T> {}
}
