package dev.sorokin.servicelocator;

/**
 * A {@link ServiceRegistry} that can additionally be configured in bulk via {@link Module}s.
 *
 * <p>Implementations are expected to be usable interchangeably: code that only depends on
 * {@code ServiceLocator} should behave the same whether it is backed by
 * {@link SimpleServiceLocator}, a reflective implementation, or any future implementation, as
 * long as they all honor this interface's contract.
 *
 * @author Sorokin Anton
 * @see SimpleServiceLocator
 */
public interface ServiceLocator extends ServiceRegistry {

    /**
     * Configures this locator using {@code module} and, if given, every module in
     * {@code additional}.
     *
     * @param module     the first module to configure this locator with
     * @param additional further modules to configure this locator with, in order; may be empty
     */
    void install(Module module, Module... additional);

    /**
     * Configures {@code module} (and {@code additional}, if any) against
     * {@code registry}. Implementations of {@link #install} — including
     * decorators wrapping another {@link ServiceLocator} — should call this
     * with {@code this} rather than delegating {@code install} directly to
     * an inner delegate, so that modules see the outer instance and any
     * {@code instanceof}-based capability checks behave correctly.
     *
     * @param registry   the registry passed to each module's {@link Module#configure(ServiceRegistry)}
     * @param module     the first module to configure
     * @param additional further modules to configure, in order; {@code null} is treated as empty
     */
    static void configureAll(ServiceRegistry registry, Module module, Module... additional) {
        module.configure(registry);
        if (additional == null) return;
        for (var additionalModule : additional) {
            additionalModule.configure(registry);
        }
    }
}