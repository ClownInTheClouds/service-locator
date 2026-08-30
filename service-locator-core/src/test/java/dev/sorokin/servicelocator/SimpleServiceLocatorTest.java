package dev.sorokin.servicelocator;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SimpleServiceLocator}, grouped by scenario.
 *
 * @author Sorokin Anton
 */
@ExtendWith(MockitoExtension.class)
class SimpleServiceLocatorTest {

    /**
     * Pre-built instances registered via {@link SimpleServiceLocator#addInstance}.
     */
    @Nested
    class InstanceRegistration {

        @Test
        void getServiceReturnsThePreRegisteredInstance() {
            var locator = new SimpleServiceLocator();
            var widget = new Widget();

            locator.addInstance(Widget.class, widget);

            assertSame(widget, locator.getService(Widget.class));
        }

        @Test
        void preRegisteredInstanceTakesPriorityAndFactoryIsNeverCalled() {
            var locator = new SimpleServiceLocator();
            var widget = new Widget();
            locator.addInstance(Widget.class, widget);
            Supplier<Widget> factory = mock();
            locator.addFactory(Widget.class, factory);

            assertSame(widget, locator.getService(Widget.class));
            verify(factory, never()).get();
        }
    }

    /**
     * {@link Scope#SINGLETON} resolution, including concurrent access.
     */
    @Nested
    class SingletonScope {

        @Test
        void secondCallReturnsTheSameCachedInstanceAndFactoryIsInvokedOnlyOnce() {
            var locator = new SimpleServiceLocator();
            Supplier<Widget> factory = mock();
            when(factory.get()).thenReturn(new Widget());
            locator.addFactory(Widget.class, factory);

            var first = locator.getService(Widget.class);
            var second = locator.getService(Widget.class);

            assertSame(first, second);
            verify(factory, times(1)).get();
        }

        @Test
        void twoArgAddFactoryOverloadDefaultsToSingleton() {
            var locator = new SimpleServiceLocator();
            Supplier<Widget> factory = mock();
            when(factory.get()).thenReturn(new Widget());

            locator.addFactory(Widget.class, factory);

            locator.getService(Widget.class);
            locator.getService(Widget.class);
            verify(factory, times(1)).get();
        }

        @Test
        void concurrentResolutionOfSameSingletonCallsFactoryExactlyOnce() throws Exception {
            var locator = new SimpleServiceLocator();
            var factoryStarted = new CountDownLatch(1);
            var releaseFactory = new CountDownLatch(1);
            Supplier<Widget> factory = mock();
            when(factory.get()).thenAnswer(_ -> {
                factoryStarted.countDown();
                awaitUninterruptibly(releaseFactory);
                return new Widget();
            });
            locator.addFactory(Widget.class, factory);

            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> locator.getService(Widget.class));
                assertTrue(factoryStarted.await(2, TimeUnit.SECONDS));

                var second = executor.submit(() -> locator.getService(Widget.class));
                releaseFactory.countDown();

                assertSame(first.get(2, TimeUnit.SECONDS), second.get(2, TimeUnit.SECONDS));
                verify(factory, times(1)).get();
            }
        }
    }

    /**
     * {@link Scope#PROTOTYPE} resolution.
     */
    @Nested
    class PrototypeScope {

        @Test
        void everyCallProducesANewInstanceAndFactoryIsInvokedEveryTime() {
            var locator = new SimpleServiceLocator();
            Supplier<Widget> factory = mock();
            when(factory.get()).thenReturn(new Widget(), new Widget(), new Widget());
            locator.addFactory(Widget.class, factory, Scope.PROTOTYPE);

            var first = locator.getService(Widget.class);
            var second = locator.getService(Widget.class);
            var third = locator.getService(Widget.class);

            assertNotSame(first, second);
            assertNotSame(second, third);
            verify(factory, times(3)).get();
        }
    }

    /**
     * Failure modes: unregistered types and circular dependencies.
     */
    @Nested
    class ErrorHandling {

        @Test
        void resolvingAnUnregisteredTypeFailsFastInsteadOfReturningNull() {
            var locator = new SimpleServiceLocator();

            assertThrows(IllegalStateException.class, () -> locator.getService(Widget.class));
        }

        @Test
        void selfReferencingSingletonFactoryIsDetectedAsCircular() {
            var locator = new SimpleServiceLocator();
            locator.addFactory(Widget.class, () -> locator.getService(Widget.class));

            var thrown = assertThrows(IllegalStateException.class, () -> locator.getService(Widget.class));
            assertTrue(thrown.getMessage().contains("Circular dependency"));
        }

        @Test
        void twoStepCircularDependencyWithinOneThreadIsDetected() {
            var locator = new SimpleServiceLocator();
            locator.addFactory(A.class, () -> new A(locator.getService(B.class)));
            locator.addFactory(B.class, () -> new B(locator.getService(A.class)));

            var thrown = assertThrows(IllegalStateException.class, () -> locator.getService(A.class));
            assertTrue(thrown.getMessage().contains("Circular dependency"));
        }
    }

    /**
     * Cross-thread mutual dependencies that cannot be detected and must instead time out.
     */
    @Nested
    class CrossThreadDeadlock {

        @Test
        void mutuallyDependentSingletonsResolvedFromDifferentThreadsTimeOutInsteadOfHangingForever() {
            var locator = new SimpleServiceLocator(1);
            var barrier = new CyclicBarrier(2);

            locator.addFactory(A.class, () -> {
                awaitBarrier(barrier);
                return new A(locator.getService(B.class));
            });
            locator.addFactory(B.class, () -> {
                awaitBarrier(barrier);
                return new B(locator.getService(A.class));
            });

            try (var executor = Executors.newFixedThreadPool(2)) {
                var futureA = executor.submit(() -> locator.getService(A.class));
                var futureB = executor.submit(() -> locator.getService(B.class));

                var thrown = assertThrows(ExecutionException.class,
                        () -> futureA.get(5, TimeUnit.SECONDS));
                assertInstanceOf(IllegalStateException.class, thrown.getCause());
                assertTrue(thrown.getCause().getMessage().contains("Timed out"));

                futureB.cancel(true);
            }
        }
    }

    /**
     * {@link SimpleServiceLocator#install} and {@link ServiceLocator#configureAll}.
     */
    @Nested
    class ModuleInstallation {

        @Test
        void installConfiguresAllProvidedModules() {
            var locator = new SimpleServiceLocator();
            var first = mock(Module.class);
            var second = mock(Module.class);

            locator.install(first, second);

            verify(first, times(1)).configure(locator);
            verify(second, times(1)).configure(locator);
        }

        @Test
        void moduleReceivesTheLocatorItselfNotADifferentDelegate() {
            var locator = new SimpleServiceLocator();
            var module = mock(Module.class);

            locator.install(module);

            verify(module).configure(same(locator));
        }

        @Test
        void configureAllToleratesNullAdditionalModulesArrayAndStillConfiguresTheFirstModule() {
            var locator = new SimpleServiceLocator();
            var module = mock(Module.class);

            assertDoesNotThrow(() -> ServiceLocator.configureAll(locator, module, (Module[]) null));

            verify(module, times(1)).configure(locator);
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for latch to count down");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class Widget {
    }

    private record A(B b) {
    }

    private record B(A a) {
    }
}