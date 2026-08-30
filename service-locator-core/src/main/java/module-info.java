/**
 * Provides the {@code service-locator} core API: {@link dev.sorokin.servicelocator.ServiceRegistry}
 * for service registration and lookup, {@link dev.sorokin.servicelocator.ServiceLocator} adding
 * bulk configuration via {@link dev.sorokin.servicelocator.Module}, and the default
 * {@link dev.sorokin.servicelocator.SimpleServiceLocator} implementation.
 */
module dev.sorokin.servicelocator.core {
    exports dev.sorokin.servicelocator;
}