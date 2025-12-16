# Expression DSL

Много-модульный Kotlin/JVM проект с Compose Desktop UI, демонстрирующий типобезопасный DSL для построения дерева вычислений и его исполнения синхронно и асинхронно через собственный ThreadLoop и Publisher/Subscriber.

## Стек
- Kotlin 1.9.22, JVM toolchain 17
- Compose Desktop UI
- DI: Koin 3.5
- Пользовательский ThreadLoop + WorkerPool без coroutines
- Publisher/Subscriber с событиями `EvalEvent`
- ktlint для стиля кода

## Структура
```
/build.gradle.kts
/settings.gradle.kts
/core/src/main/kotlin/...
/core/src/test/kotlin/...
/runtime/src/main/kotlin/...
/runtime/src/test/kotlin/...
/app/src/main/kotlin/...
```

## Запуск
```bash
./gradlew ktlintCheck
./gradlew test
./gradlew :app:run
```

## Пример DSL (обязательный из задания)
```kotlin
val computation = Expression
  .value(2, 3)
  .every(
    { (a, b) -> ((a + b) / 2.0).let { it * it } },
    { (a, b) -> ((a - b) / 2.0).let { it * it } },
    { (a, b) -> maxOf(a, b) },
    { (a, b) -> minOf(a, b) },
  )
  .then { (halfSum2, halfDiff2, mx, mn) -> (halfSum2 + halfDiff2) * mx / mn }

val syncResult = computation.eval()
val asyncPublisher = computation.evalAsync()
```

## Архитектура
- **core** — модели `Expression`, DSL-операторы (`then`, `every`, `map`, `fold`, `zip` и т.д.).
- **runtime** — интерпретатор `Evaluator`, события `EvalEvent`, Publisher/Subscriber, ThreadLoop + WorkerPool, Koin-модуль runtime.
- **app** — Compose Desktop UI с кнопками для запуска примеров и логом событий.

## ThreadLoop и pub/sub (коротко)
ThreadLoop — одиночный фоновой поток с очередью задач. WorkerPool раздаёт задания по кольцу между несколькими ThreadLoop. EvalAsync планирует корневую задачу в WorkerPool, а каждое вычисление узла публикует события через `EventPublisher`. Подписчик получает `Started`, `NodeStarted`, `NodeCompleted`, `Completed/Failed/Cancelled`. Отмена вызывает `Subscription.cancel()`, что прекращает публикацию новых событий и прерывает цепочку вычислений.

## Добавление нового оператора
1. В `core` создайте новую `Expression`-реализацию + DSL-функцию.
2. В `runtime/Eval.kt` добавьте ветку в `evaluateNode`.
3. При необходимости расширьте события/параллельность.
4. Покройте тестами в `runtime/src/test`.
