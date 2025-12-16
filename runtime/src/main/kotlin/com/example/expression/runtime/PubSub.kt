package com.example.expression.runtime

interface Publisher<T> {
    fun subscribe(subscriber: Subscriber<T>): Subscription
}

interface Subscriber<T> {
    fun onNext(value: T)
    fun onError(error: Throwable)
    fun onComplete()
}

interface Subscription {
    fun cancel()
    val cancelled: Boolean
}

data class SimpleSubscription(private val cancelAction: () -> Unit) : Subscription {
    @Volatile
    private var isCancelled: Boolean = false

    override fun cancel() {
        if (!isCancelled) {
            isCancelled = true
            cancelAction()
        }
    }

    override val cancelled: Boolean
        get() = isCancelled
}
