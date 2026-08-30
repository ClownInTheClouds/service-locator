package dev.sorokin.servicelocator;

/**
 * A unit of {@link ServiceRegistry} configuration.
 *
 * <p>Implementations group related {@link ServiceRegistry#addInstance(Class, Object)} and
 * {@link ServiceRegistry#addFactory(Class, java.util.function.Supplier)} registrations
 * together so they can be applied as a single, reusable unit via
 * {@link ServiceLocator#install(Module, Module...)}.
 *
 * <p>A module only receives a {@link ServiceRegistry}, not the full {@link ServiceLocator} —
 * it can register and look up services, but it cannot call {@link ServiceLocator#install}
 * itself. This is intentional: installing further modules is not something a module's own
 * configuration step should be able to trigger.
 *
 * @author Sorokin Anton
 */
public interface Module {

    /**
     * Registers this module's services against {@code serviceRegistry}.
     *
     * @param serviceRegistry the registry to configure
     */
    void configure(ServiceRegistry serviceRegistry);
}