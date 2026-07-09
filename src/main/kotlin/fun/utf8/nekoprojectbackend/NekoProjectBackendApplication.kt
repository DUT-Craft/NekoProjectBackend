package `fun`.utf8.nekoprojectbackend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** 应用程序入口，启动 Spring Boot 上下文。 */
@SpringBootApplication
class NekoProjectBackendApplication

fun main(args: Array<String>) {
    runApplication<NekoProjectBackendApplication>(*args)
}
