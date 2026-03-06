package cn.kurt6.cobblemon_ranked.util

import cn.kurt6.cobblemon_ranked.network.ClientVersionPayload
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ClientVersionTracker {
    private val playerVersions = ConcurrentHashMap<UUID, String>()

    fun setPlayerVersion(playerUuid: UUID, version: String) {
        playerVersions[playerUuid] = version
    }

    fun getPlayerVersion(playerUuid: UUID): String? {
        return playerVersions[playerUuid]
    }

    fun isPlayerCompatible(playerUuid: UUID): Boolean {
        val version = playerVersions[playerUuid] ?: return false
        return ClientVersionPayload.isVersionCompatible(version)
    }

    fun removePlayer(playerUuid: UUID) {
        playerVersions.remove(playerUuid)
    }

    fun clear() {
        playerVersions.clear()
    }
}