package dev.sorokin.servicelocator.reflection;

import dev.sorokin.servicelocator.ServiceLocator;
import dev.sorokin.servicelocator.ServiceRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ReflectiveServiceLocator}, grouped by scenario.
 *
 * @author Sorokin Anton
 */
@ExtendWith(MockitoExtension.class)
class ReflectiveServiceLocatorTest {

    /**
     * Plain (non-reflective) delegation to the wrapped {@link ServiceLocator}.
     */
    @Nested
    class Delegation {

        @Test
        void nullDelegateIsRejectedAtConstruction() {
            assertThrows(NullPointerException.class, () -> new ReflectiveServiceLocator(null));
        }

        @Test
        void addInstanceDelegatesToTheWrappedLocator() {
            var delegate = mock(ServiceLocator.class);
            var locator = new ReflectiveServiceLocator(delegate);
            var widget = new Gadget();

            locator.addInstance(Gadget.class, widget);

            verify(delegate).addInstance(Gadget.class, widget);
        }

        @Test
        void getServiceDelegatesToTheWrappedLocator() {
            var delegate = mock(ServiceLocator.class);
            var locator = new ReflectiveServiceLocator(delegate);
            var widget = new Gadget();
            when(delegate.getService(Gadget.class)).thenReturn(widget);

            assertSame(widget, locator.getService(Gadget.class));
        }

        @Test
        void installPassesTheWrapperItselfToModulesNotTheDelegate() {
            var locator = new ReflectiveServiceLocator();
            var seenRegistry = new AtomicReference<ReflectiveServiceRegistry>();
            ReflectiveModule module = seenRegistry::set;

            locator.install(module);

            assertSame(locator, seenRegistry.get());
        }
    }

    /**
     * Reflective, constructor-based registration and resolution.
     */
    @Nested
    class AutoWiring {

        @Test
        void noArgConstructorIsAutoInvoked() {
            var locator = new ReflectiveServiceLocator();
            locator.addFactory(Gadget.class);

            assertNotNull(locator.getService(Gadget.class));
        }

        @Test
        void constructorDependencyIsResolvedRecursively() {
            var locator = new ReflectiveServiceLocator();
            locator.addFactory(Gadget.class);
            locator.addFactory(WidgetWithDependency.class);

            var widget = locator.getService(WidgetWithDependency.class);

            assertNotNull(widget.gadget());
        }

        @Test
        void typeIsRegisteredAgainstADifferentImplementation() {
            var locator = new ReflectiveServiceLocator();
            locator.addFactory(SomeInterface.class, InterfaceImpl.class);

            assertInstanceOf(InterfaceImpl.class, locator.getService(SomeInterface.class));
        }
    }

    /**
     * Enforcement of the "exactly one public constructor" requirement.
     */
    @Nested
    class ConstructorSelection {

        @Test
        void typeWithNoPublicConstructorIsRejected() {
            var locator = new ReflectiveServiceLocator();
            locator.addFactory(NoPublicConstructor.class);

            var thrown = assertThrows(IllegalStateException.class,
                    () -> locator.getService(NoPublicConstructor.class));
            assertTrue(thrown.getMessage().contains("exactly one public constructor"));
        }

        @Test
        void typeWithMultiplePublicConstructorsIsRejected() {
            var locator = new ReflectiveServiceLocator();
            locator.addFactory(TwoPublicConstructors.class);

            var thrown = assertThrows(IllegalStateException.class,
                    () -> locator.getService(TwoPublicConstructors.class));
            assertTrue(thrown.getMessage().contains("exactly one public constructor"));
        }
    }

    /**
     * Rejection of types that cannot be reflectively instantiated at all.
     */
    @Nested
    class InstantiabilityValidation {

        @Test
        void interfaceIsRejected() {
            var locator = new ReflectiveServiceLocator();
            locator.addFactory(SomeInterface.class, SomeInterface.class);

            var thrown = assertThrows(IllegalStateException.class,
                    () -> locator.getService(SomeInterface.class));
            assertTrue(thrown.getMessage().contains("abstract or an interface"));
        }

        @Test
        void abstractClassIsRejected() {
            var locator = new ReflectiveServiceLocator();
            locator.addFactory(SomeAbstractClass.class);

            var thrown = assertThrows(IllegalStateException.class,
                    () -> locator.getService(SomeAbstractClass.class));
            assertTrue(thrown.getMessage().contains("abstract or an interface"));
        }

        @Test
        void nonStaticInnerClassIsRejected() {
            var locator = new ReflectiveServiceLocator();
            locator.addFactory(NonStaticInner.class);

            var thrown = assertThrows(IllegalStateException.class,
                    () -> locator.getService(NonStaticInner.class));
            assertTrue(thrown.getMessage().contains("non-static inner class"));
        }
    }

    /**
     * How exceptions thrown by the constructor itself propagate.
     */
    @Nested
    class ExceptionPropagation {

        @Test
        void runtimeExceptionFromConstructorPropagatesAsIs() {
            var locator = new ReflectiveServiceLocator();
            locator.addFactory(RuntimeExceptionThrowingWidget.class);

            var thrown = assertThrows(WidgetConstructionException.class,
                    () -> locator.getService(RuntimeExceptionThrowingWidget.class));
            assertEquals("boom", thrown.getMessage());
        }

        @Test
        void checkedExceptionFromConstructorIsWrapped() {
            var locator = new ReflectiveServiceLocator();
            locator.addFactory(CheckedExceptionThrowingWidget.class);

            var thrown = assertThrows(IllegalStateException.class,
                    () -> locator.getService(CheckedExceptionThrowingWidget.class));
            assertInstanceOf(IOException.class, thrown.getCause());
        }
    }

    /**
     * {@link ReflectiveModule#configure(ServiceRegistry)}'s instanceof-based bridging.
     */
    @Nested
    class ReflectiveModuleBridging {

        @Test
        void typedConfigureRunsWhenRegistryIsReflective() {
            var called = new AtomicBoolean();
            ReflectiveModule module = _ -> called.set(true);
            ReflectiveServiceRegistry reflectiveRegistry = new ReflectiveServiceLocator();

            module.configure((ServiceRegistry) reflectiveRegistry);

            assertTrue(called.get());
        }

        @Test
        void defaultConfigureIsANoOpForANonReflectiveRegistry() {
            var called = new AtomicBoolean();
            ReflectiveModule module = _ -> called.set(true);
            var plainRegistry = mock(ServiceRegistry.class);

            assertDoesNotThrow(() -> module.configure(plainRegistry));
            assertFalse(called.get());
        }
    }

    public static final class Gadget {
        public Gadget() {
        }
    }

    public record WidgetWithDependency(Gadget gadget) {
    }

    public interface SomeInterface {
    }

    public static final class InterfaceImpl implements SomeInterface {
        public InterfaceImpl() {
        }
    }

    public static abstract class SomeAbstractClass {
        public SomeAbstractClass() {
        }
    }

    public static final class NoPublicConstructor {
        private NoPublicConstructor() {
        }
    }

    public static final class TwoPublicConstructors {
        @SuppressWarnings("unused")
        public TwoPublicConstructors() {
        }

        @SuppressWarnings("unused")
        public TwoPublicConstructors(Gadget gadget) {
        }
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    public final class NonStaticInner {
        public NonStaticInner() {
        }
    }

    public static final class RuntimeExceptionThrowingWidget {
        public RuntimeExceptionThrowingWidget() {
            throw new WidgetConstructionException("boom");
        }
    }

    public static final class WidgetConstructionException extends RuntimeException {
        public WidgetConstructionException(String message) {
            super(message);
        }
    }

    public static final class CheckedExceptionThrowingWidget {
        public CheckedExceptionThrowingWidget() throws IOException {
            throw new IOException("disk on fire");
        }
    }
}