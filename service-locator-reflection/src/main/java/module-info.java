/**
 * Adds reflective, constructor-based service registration on top of
 * {@code dev.sorokin.servicelocator.core}: see
 * {@link dev.sorokin.servicelocator.reflection.ReflectiveServiceLocator}.
 */
module dev.sorokin.servicelocator.reflection {
    requires transitive dev.sorokin.servicelocator.core;

    exports dev.sorokin.servicelocator.reflection;
}