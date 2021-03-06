package io.ktor.springer

import io.ktor.client.engine.*
import io.ktor.client.engine.apache.*
import io.ktor.features.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.experimental.*
import org.junit.Test
import java.util.concurrent.*
import kotlin.test.*

class RetroFitTest {
    private inline fun <reified T : Routes> testClient(noinline gen: () -> RoutesBackend, noinline test: suspend T.() -> Unit) {
        tempServer({ gen() }) { endPoint ->
            val client = createClient<T>(Apache.config { followRedirects = true }, endPoint)
            test(client)
        }
    }

    @Test
    fun name() = testClient<MyService>({ MyServiceBackend() }) {
        assertEquals("Hello world", hello("world"))
        assertEquals("127.0.0.1", getIp())
        assertEquals("127.0.0.1", getIp())
    }

    // Code in Common, use createClient to create a client implementing this interface by calling to an HTTP endpoint
    interface MyService : Routes {
        @Get("/ip")
        suspend fun getIp(): String

        @Get("/hello/{}")
        suspend fun hello(name: String): String

        @Get("/redirect")
        suspend fun redirect(): String
    }

    // Code in the Backend
    class MyServiceBackend : MyService, RoutesBackend {
        override suspend fun getIp(): String {
            return call().request.origin.remoteHost
        }

        override suspend fun hello(name: String): String {
            return "Hello $name"
        }

        override suspend fun redirect(): String {
            redirect("/ip", permanent = true)
        }
    }
}

private fun tempServer(backend: () -> RoutesBackend, callback: suspend (endpoint: String) -> Unit) {
    runBlocking {
        // @TODO: Random Available PORT 0 doesn't seems to work,
        // @TODO:    later it returns 0 too (Ktor should get the bound port instead)!
        //val engine = embeddedServer(Netty, port = 0, host = "127.0.0.1") {
        val engine = embeddedServer(Netty, port = 19912, host = "127.0.0.1") {
            routing {
                registerRoutesInstance(backend())
            }
        }
        engine.start(wait = false)
        val port = engine.environment.connectors.map { it.port }.first()
        try {
            callback("http://127.0.0.1:$port/")
        } finally {
            engine.stop(0L, 0L, TimeUnit.SECONDS)
        }
    }
}
