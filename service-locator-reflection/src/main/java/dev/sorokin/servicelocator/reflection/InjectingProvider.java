package dev.sorokin.servicelocator.reflection;

import lombok.RequiredArgsConstructor;

import java.util.function.Supplier;

@RequiredArgsConstructor
public class InjectingProvider<T> implements Supplier<T> {

    private final Class<T> type;
    private final ReflectiveServiceLocator reflectiveServiceLocator;

    @Override
    public T get() {
        try {
            var constructor = type.getConstructors()[0];

            var paramTypes = constructor.getParameterTypes();
            var params = new Object[paramTypes.length];

            for (int i = 0; i < paramTypes.length; i++) {
                params[i] = reflectiveServiceLocator.getService(paramTypes[i]);
            }

            return type.cast(constructor.newInstance(params));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
