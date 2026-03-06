package cn.kurt6.cobblemon_ranked

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RankDataManager {

    // UUID -> (Formato -> Datos)
    private val playerData: MutableMap<UUID, MutableMap<RankedFormat, PlayerRankData>> =
        ConcurrentHashMap()

    fun getData(uuid: UUID, format: RankedFormat): PlayerRankData {
        val formats = playerData.computeIfAbsent(uuid) { ConcurrentHashMap() }

        return formats.computeIfAbsent(format) {
            PlayerRankData.createDefault(format.baseElo)
        }
    }

    fun recordWin(
        winnerUUID: UUID,
        loserUUID: UUID,
        format: RankedFormat
    ) {
        val winnerData = getData(winnerUUID, format)
        val loserData = getData(loserUUID, format)

        val winnerOldElo = winnerData.elo
        val loserOldElo = loserData.elo

        winnerData.recordWin(opponentElo = loserOldElo)
        loserData.recordLoss(opponentElo = winnerOldElo)
    }

    fun getTopPlayers(format: RankedFormat, limit: Int = 10): List<Pair<UUID, PlayerRankData>> {
        return playerData
            .mapNotNull { (uuid, formats) ->
                formats[format]?.let { uuid to it }
            }
            .sortedByDescending { it.second.elo }
            .take(limit)
    }

    fun getAllFormats(uuid: UUID): Map<RankedFormat, PlayerRankData> {
        return playerData[uuid] ?: emptyMap()
    }
}