package top.foxball.nekomainsite.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ServerStatusServiceTest @Autowired constructor(
    private val service: ServerStatusService,
) {
    @Test
    fun `status response uses the nested players object`() {
        val result = service.parsePingResult(
            """{"version":{"name":"1.21.1","protocol":767},"players":{"max":40,"online":12}}""",
        )

        assertEquals(12, result.online)
        assertEquals(40, result.capacity)
    }

    @Test
    fun `status response rejects missing player counts`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.parsePingResult("""{"description":{"text":"missing players"}}""")
        }
    }

    @Test
    fun `handshake encodes the port as an unsigned big endian short`() {
        val payload = service.buildHandshake("mc.example.com", 25565)

        assertArrayEquals(
            byteArrayOf(0x63, 0xdd.toByte(), 0x01),
            payload.takeLast(3).toByteArray(),
        )
    }
}
