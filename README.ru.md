# service-locator

[![EN](https://img.shields.io/badge/lang-en-red.svg)](README.md)
[![RU](https://img.shields.io/badge/lang-ru-blue.svg)](README.ru.md)

Небольшой потокобезопасный service locator для Java, дружелюбный к JPMS, сделанный для
desktop-приложений, которые собираются в кастомный runtime-образ через `jlink`.

Библиотека поставляется в виде двух модулей: `core` без внешних зависимостей с публичным API и
реализацией по умолчанию, и опциональный `reflection`, добавляющий поверх него автоматическую
конструкторную инъекцию зависимостей.

## Возможности

- **Минимальный, композируемый API** — `ServiceRegistry` (регистрация + резолв) и
  `ServiceLocator` (`ServiceRegistry` плюс массовая конфигурация через `Module`) — это разные
  интерфейсы, поэтому `Module` видит только узкую поверхность регистрации и никогда не может
  сам вызвать `install(...)`.
- **Singleton и prototype скоупы** — выбираются для каждой регистрации через `Scope`.
- **Потокобезопасность по дизайну** — создание singleton-сервиса дедуплицируется между
  конкурентными вызовами без глобальной блокировки; циклическая зависимость в рамках одного
  потока обнаруживается и отклоняется мгновенно; кросс-поточный дедлок (два потока резолвят
  взаимно зависимые сервисы в противоположном порядке) завершается понятной,
  диагностируемой `IllegalStateException` по истечении настраиваемого таймаута, а не зависает
  навсегда.
- **Рефлективная автосборка как декоратор, а не как подкласс** — `ReflectiveServiceLocator`
  оборачивает **любую** реализацию `ServiceLocator`, а не наследует конкретную, поэтому
  комбинируется с реализацией по умолчанию или с любой будущей, не требуя доступа к её
  внутренностям.
- **Полностью модульная структура** — у обоих модулей есть `module-info.java`, у `core` нет
  внешних зависимостей вовсе, а `reflection` корректно объявляет `requires transitive`, так что
  потребителю достаточно `requires` только тот модуль, который он реально использует.

## Требования

- Gradle 8.x+ (собирается и тестируется на Gradle 9.x)
- Java 25 (toolchain)

## Модули

| Модуль | Артефакт | Зависит от | Добавляет |
|---|---|---|---|
| `service-locator-core` | `service-locator-core` | — | `ServiceRegistry`, `ServiceLocator`, `Module`, `Scope`, `SimpleServiceLocator` |
| `service-locator-reflection` | `service-locator-reflection` | `service-locator-core` (транзитивно) | `ReflectiveServiceLocator` — конструкторная автосборка |

## Применение

### Быстрый старт

```groovy
dependencies {
    implementation 'dev.sorokin.servicelocator:service-locator-core:2.0.0'
}
```

```java
var locator = new SimpleServiceLocator();
locator.addInstance(Clock.class, Clock.systemUTC());
locator.addFactory(UserService.class, () -> new UserService(locator.getService(Clock.class)));

var userService = locator.getService(UserService.class);
```

### Singleton и prototype скоупы

```java
locator.addFactory(ConnectionPool.class, ConnectionPool::new);                    // singleton (по умолчанию)
locator.addFactory(RequestContext.class, RequestContext::new, Scope.PROTOTYPE);   // новый экземпляр на каждый вызов
```

### Модули

Сгруппируйте связанные регистрации и примените их разом:

```java
Module infrastructureModule = registry -> {
    registry.addInstance(Clock.class, Clock.systemUTC());
    registry.addFactory(ConnectionPool.class, ConnectionPool::new);
};

locator.install(infrastructureModule);
```

`Module` получает только `ServiceRegistry`, а не полный `ServiceLocator` — он может
регистрировать и резолвить сервисы, но не может сам инициировать установку других модулей.

### Рефлективная автосборка (`service-locator-reflection`)

```groovy
dependencies {
    implementation 'dev.sorokin.servicelocator:service-locator-reflection:2.0.0'
}
```

```java
var locator = new ReflectiveServiceLocator();          // по умолчанию оборачивает SimpleServiceLocator
locator.addFactory(Clock.class, Clock::systemUTC);      // обычные фабрики по-прежнему работают
locator.addFactory(UserService.class);                  // конструктор собирается рефлективно
```

У `UserService` должен быть ровно один публичный конструктор; каждый его параметр рекурсивно
резолвится через локатор. Интерфейсы, абстрактные классы и non-static inner-классы отклоняются
сразу, с понятной ошибкой, а не падают где-то в глубине конструирования.

`ReflectiveServiceLocator` оборачивает свой делегат, а не наследует его, поэтому его можно
поставить поверх локатора, настроенного как угодно:

```java
var locator = new ReflectiveServiceLocator(new SimpleServiceLocator(30)); // свой таймаут ожидания
```

### Конкурентные и циклические зависимости

```java
var locator = new SimpleServiceLocator(30); // таймаут ожидания в секундах, по умолчанию 10
```

- Резолв одного и того же singleton из нескольких потоков одновременно безопасен: фабрика
  вызывается максимум один раз, а все остальные вызывающие получают тот же самый экземпляр, не
  создавая свой собственный в гонке.
- Циклическая зависимость, сформированная в рамках стека вызовов одного потока (`A` требует
  `B`, `B` требует `A`), обнаруживается сразу и бросает `IllegalStateException`.
- Циклическая зависимость, сформированная **между** потоками, в общем случае не может быть
  обнаружена — вместо вечного зависания резолв завершится понятной, диагностируемой
  `IllegalStateException` по истечении настроенного таймаута ожидания.

## Сборка через `jlink`

Оба модуля полностью модульные: у `core` вообще нет внешних зависимостей, а `reflection`
объявляет `requires transitive dev.sorokin.servicelocator.core`, так что потребителю достаточно
затребовать только тот модуль, который он реально использует:

```java
module your.app {
    requires dev.sorokin.servicelocator.reflection;
}
```

Если `ReflectiveServiceLocator` рефлективно конструирует один из **ваших** классов, пакет этого
класса должен быть **экспортирован** (не обязательно **открыт**) из вашего модуля — рефлективное
конструирование вызывает только публичный конструктор, по тем же правилам видимости, что
действовали бы при обычном `new` снаружи вашего модуля.

## Публикация

Публикуется через [`publish-plugin`](https://github.com/ClownInTheClouds/publish-plugin),
который автоматически настраивает публикацию `mavenJava` для каждого модуля — подробности в его
README.

```groovy
publishing {
    repositories {
        maven { url = uri('https://your.repo/maven2') }
    }
}
```

## Тестирование

Оба модуля тестируются с помощью JUnit 5 (Jupiter) и Mockito:

```
./gradlew test
```