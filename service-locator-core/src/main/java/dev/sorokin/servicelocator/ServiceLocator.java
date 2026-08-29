package dev.sorokin.servicelocator;

public interface ServiceLocator extends ServiceRegistry {

    void install(Module module, Module... additional);

    /**
     * Configures {@code module} (and {@code additional}, if any) against
     * {@code registry}. Implementations of {@link #install} — including
     * decorators wrapping another {@link ServiceLocator} — should call this
     * with {@code this} rather than delegating {@code install} directly to
     * an inner delegate, so that modules see the outer instance and any
     * {@code instanceof}-based capability checks behave correctly.
     */
    static void configureAll(ServiceRegistry registry, Module module, Module... additional) {
        module.configure(registry);
        if (additional == null) return;
        for (var additionalModule : additional) {
            additionalModule.configure(registry);
        }
    }
}
