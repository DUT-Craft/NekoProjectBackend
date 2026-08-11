package top.foxball.nekomainsite.config

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import top.foxball.nekomainsite.service.ServerStatusService

@Component
class ServerStatusScheduler(private val service: ServerStatusService) {
    @Scheduled(fixedDelayString = "\${neko.server-status.interval-ms:60000}", initialDelayString = "\${neko.server-status.interval-ms:60000}")
    fun refresh() = service.refreshAll()
}
