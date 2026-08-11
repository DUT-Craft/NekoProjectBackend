package top.foxball.nekomainsite.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import top.foxball.nekomainsite.config.ServerStatusProperties
import top.foxball.nekomainsite.entity.jdbc.Server
import top.foxball.nekomainsite.entity.jdbc.ServerStatus
import top.foxball.nekomainsite.handlder.ResourceNotFoundException
import top.foxball.nekomainsite.repository.ServerRepository
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.Instant

@Service
class ServerStatusService(
    private val serverRepository: ServerRepository,
    private val properties: ServerStatusProperties,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun refreshAll() {
        if (!properties.enabled) return
        serverRepository.findAll().filter { it.published }.forEach(::refresh)
    }

    @Transactional
    fun refresh(server: Server) {
        if (server.maintenance) {
            server.onlineCount = 0
            server.status = ServerStatus.MAINTENANCE
            server.statusLabel = "维护中"
            server.lastCheckedAt = Instant.now()
            server.statusError = null
            serverRepository.save(server)
            return
        }
        val result = runCatching { ping(server.address) }
        val now = Instant.now()
        result.onSuccess { response ->
            server.onlineCount = response.online
            server.capacity = response.capacity
            server.status = if (response.online > 0) ServerStatus.ONLINE else ServerStatus.AVAILABLE
            server.statusLabel = if (response.online > 0) "在线" else "可以进入"
            server.lastCheckedAt = now
            server.statusError = null
        }.onFailure { error ->
            server.onlineCount = 0
            server.status = ServerStatus.OFFLINE
            server.statusLabel = "离线"
            server.lastCheckedAt = now
            server.statusError = error.message?.take(255) ?: "status ping failed"
        }
        serverRepository.save(server)
    }

    @Transactional
    fun refreshById(id: Long) {
        val server = serverRepository.findById(id).orElseThrow { ResourceNotFoundException("服务器不存在") }
        refresh(server)
    }

    private fun ping(address: String): PingResult {
        val parts = address.trim().split(":", limit = 2)
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 25565
        Socket().use { socket ->
            socket.soTimeout = properties.connectTimeoutMs
            socket.connect(InetSocketAddress(host, port), properties.connectTimeoutMs)
            val out = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())
            val handshake = buildHandshake(host, port)
            writePacket(out, handshake)
            writePacket(out, byteArrayOf(0x00))
            readVarInt(input)
            readVarInt(input)
            return parsePingResult(readString(input))
        }
    }

    internal fun parsePingResult(json: String): PingResult {
        val players = objectMapper.readTree(json).path("players")
        val online = players.path("online").asInt(-1)
        val capacity = players.path("max").asInt(-1)
        require(online >= 0 && capacity >= 0) { "invalid status response" }
        return PingResult(online, capacity)
    }

    internal fun buildHandshake(host: String, port: Int): ByteArray {
        require(port in 1..65535) { "invalid server port" }
        val encodedPort = byteArrayOf((port ushr 8).toByte(), port.toByte())
        return byteArrayOf(0x00) + varInt(766) + mcString(host) + encodedPort + varInt(1)
    }

    private fun writePacket(out: DataOutputStream, payload: ByteArray) {
        out.write(varInt(payload.size))
        out.write(payload)
        out.flush()
    }

    private fun readString(input: DataInputStream): String {
        val size = readVarInt(input)
        require(size in 0..1_000_000) { "invalid status response" }
        val data = ByteArray(size)
        input.readFully(data)
        return String(data, StandardCharsets.UTF_8)
    }

    private fun readVarInt(input: DataInputStream): Int {
        var result = 0
        var shift = 0
        while (shift < 35) {
            val value = input.readUnsignedByte()
            result = result or ((value and 0x7f) shl shift)
            if ((value and 0x80) == 0) return result
            shift += 7
        }
        error("invalid varint")
    }

    private fun varInt(value: Int): ByteArray {
        var current = value
        val bytes = ArrayList<Byte>()
        do {
            var next = current and 0x7f
            current = current ushr 7
            if (current != 0) next = next or 0x80
            bytes += next.toByte()
        } while (current != 0)
        return bytes.toByteArray()
    }

    private fun mcString(value: String): ByteArray {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        return varInt(bytes.size) + bytes
    }

    internal data class PingResult(val online: Int, val capacity: Int)
}
