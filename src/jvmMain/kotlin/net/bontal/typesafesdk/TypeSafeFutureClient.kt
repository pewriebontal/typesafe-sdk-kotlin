package net.bontal.typesafesdk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import kotlinx.coroutines.isActive
import java.util.concurrent.CompletableFuture

public class TypeSafeFutureClient private constructor(private val client: TypeSafeClientAsync) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    public fun systemOne(request: SystemOneRequest): CompletableFuture<SystemOneResult> = submit { client.systemOne(request) }

    public fun systemOne(request: SystemOneRequest, options: RequestOptions): CompletableFuture<SystemOneResult> = submit { client.systemOne(request, options) }

    public fun models(): CompletableFuture<List<ModelCard>> = submit { client.models() }

    public fun models(options: RequestOptions): CompletableFuture<List<ModelCard>> = submit { client.models(options) }

    private fun <T> submit(block: suspend () -> T): CompletableFuture<T> {
        check(scope.isActive) { "The TypeSafeFutureClient has been closed" }
        return scope.future { block() }
    }

    override fun close() {
        scope.cancel()
    }

    public companion object {
        @JvmStatic
        public fun of(client: TypeSafeClient): TypeSafeFutureClient = TypeSafeFutureClient(client.async())

        @JvmStatic
        public fun of(client: TypeSafeClientAsync): TypeSafeFutureClient = TypeSafeFutureClient(client)
    }
}
