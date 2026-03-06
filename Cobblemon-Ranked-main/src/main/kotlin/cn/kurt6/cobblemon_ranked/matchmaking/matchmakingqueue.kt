package cn.kurt6.cobblemon_ranked.matchmaking

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion.config
import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion.matchmakingQueue
import cn.kurt6.cobblemon_ranked.RankedFormat
import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.battle.TeamSelectionManager
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.config.RankConfig
import cn.kurt6.cobblemon_ranked.network.TeamSelectionStartPayload
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleFormat
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("RankedBattle")

/** Modos en los que el equipo se genera aleatoriamente (no viene del party del jugador) */
val RANDOM_FORMATS = setOf("random_battle", "double_random_battle")

class MatchmakingQueue {

    val queue = ConcurrentHashMap<UUID, QueueEntry>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val formatMap = mutableMapOf<String, BattleFormat>()
    private val processingMatches = ConcurrentHashMap.newKeySet<UUID>()

    companion object {
        val activeRankedBattles = ConcurrentHashMap<String, RankedFormat>()
    }

    init {
        scheduler.scheduleAtFixedRate(
            { cleanupStaleEntries(); processQueue() },
            5, 5, TimeUnit.SECONDS
        )
        initializeFormatMap()
    }

    private fun initializeFormatMap() {
        // 4 modos soportados, cada uno con Elo independiente
        formatMap["single_battle"]        = BattleFormat.GEN_9_SINGLES
        formatMap["doubles_battle"]       = BattleFormat.GEN_9_DOUBLES
        formatMap["random_battle"]        = BattleFormat.GEN_9_SINGLES
        formatMap["double_random_battle"] = BattleFormat.GEN_9_DOUBLES
    }

    // -------------------------------------------------------------------------
    // Cola pública
    // -------------------------------------------------------------------------

    fun addPlayer(player: ServerPlayerEntity, formatName: String) {
        val lang = config.defaultLang

        if (Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) != null) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.already_in_battle", lang))
            return
        }
        if (TeamSelectionManager.isPlayerInSelection(player.uuid)) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.already_in_battle", lang))
            return
        }
        if (BattleHandler.isPlayerWaitingForArena(player.uuid)) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.cannot_join", lang))
            return
        }
        if (config.enableTeamPreview && !ServerPlayNetworking.canSend(player, TeamSelectionStartPayload.ID)) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.mod_required", lang))
            return
        }
        if (config.enableTeamPreview &&
            !cn.kurt6.cobblemon_ranked.util.ClientVersionTracker.isPlayerCompatible(player.uuid)) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.version_outdated", lang))
            return
        }
        if (!canPlayerJoinQueue(player)) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.cannot_join", lang))
            return
        }

        val format = formatMap[formatName] ?: run {
            RankUtils.sendMessage(player, MessageConfig.get("queue.invalid_format", lang))
            return
        }

        try {
            val isRandom = formatName in RANDOM_FORMATS

            // Bloquear entrada a random si tiene Pokémon propios en el party
            if (isRandom && BattleHandler.hasOwnPokemon(player)) {
                RankUtils.sendMessage(player, "§cNo puedes entrar al Random Battle con Pokémon propios en el equipo. Vacía tu party primero.")
                return
            }

            val team: List<UUID> = if (isRandom) {
                // En random NO se genera el equipo aquí — se genera al teleportar a la arena
                emptyList()
            } else {
                val t = getPlayerTeam(player)
                if (!BattleHandler.validateTeam(player, t, format)) return
                t
            }

            val seasonId = CobblemonRanked.seasonManager.currentSeasonId
            val currentElo = java.util.concurrent.CompletableFuture.supplyAsync {
                CobblemonRanked.rankDao.getPlayerData(player.uuid, seasonId, formatName)?.elo
                    ?: config.initialElo
            }.get()

            queue[player.uuid] = QueueEntry(
                player     = player,
                format     = format,
                team       = team,
                joinTime   = System.currentTimeMillis(),
                elo        = currentElo,
                formatName = formatName
            )

            RankUtils.sendMessage(player, MessageConfig.get("queue.join_success.$formatName", lang))
        } catch (e: Exception) {
            if (formatName in RANDOM_FORMATS) BattleHandler.restorePartyIfRandom(player)
            RankUtils.sendMessage(player, MessageConfig.get("queue.error", lang, "error" to e.message.toString()))
        }
    }

    fun removePlayer(uuid: UUID) {
        val entry = queue.remove(uuid)
        processingMatches.remove(uuid)

        // En modo random el equipo no se asigna hasta el teleport,
        // así que no hay party que restaurar al salir de la cola
        // Solo limpiar el marcador por si acaso
        if (entry != null && entry.formatName in RANDOM_FORMATS) {
            entry.player.server?.execute {
                BattleHandler.setPlayerInRandomBattle(entry.player.uuid, false)
            }
        }
    }

    fun getPlayer(playerId: UUID, format: String? = null): ServerPlayerEntity? {
        val entry = queue[playerId] ?: return null
        if (format == null) return entry.player
        return if (entry.formatName == format) entry.player else null
    }

    fun clear() {
        queue.clear()
        processingMatches.clear()
    }

    // -------------------------------------------------------------------------
    // Matchmaking interno
    // -------------------------------------------------------------------------

    private fun processQueue() {
        val entries = synchronized(queue) {
            if (queue.size < 2) return
            queue.values.toList()
        }

        val matchedPairs = mutableListOf<Pair<QueueEntry, QueueEntry>>()
        val processedInThisRound = mutableSetOf<UUID>()

        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val p1 = entries[i]
                val p2 = entries[j]

                if (p1.player.uuid in processedInThisRound || p2.player.uuid in processedInThisRound) continue
                if (p1.player.uuid in processingMatches || p2.player.uuid in processingMatches) continue

                // Solo hacer match dentro del mismo formato exacto (Elo separado por modo)
                if (p1.formatName != p2.formatName) continue
                if (!isEloCompatible(p1, p2)) continue

                if (Cobblemon.battleRegistry.getBattleByParticipatingPlayer(p1.player) != null ||
                    Cobblemon.battleRegistry.getBattleByParticipatingPlayer(p2.player) != null) {
                    synchronized(queue) {
                        queue.remove(p1.player.uuid)
                        queue.remove(p2.player.uuid)
                    }
                    continue
                }

                val removed = synchronized(queue) {
                    val r1 = queue.remove(p1.player.uuid)
                    val r2 = queue.remove(p2.player.uuid)
                    if (r1 == null || r2 == null) {
                        r1?.let { queue[p1.player.uuid] = it }
                        r2?.let { queue[p2.player.uuid] = it }
                        null
                    } else {
                        p1 to p2
                    }
                }

                if (removed != null) {
                    processingMatches.add(p1.player.uuid)
                    processingMatches.add(p2.player.uuid)
                    processedInThisRound.add(p1.player.uuid)
                    processedInThisRound.add(p2.player.uuid)
                    matchedPairs.add(p1 to p2)
                    break
                }
            }
        }

        matchedPairs.forEach { (p1, p2) ->
            try {
                startRankedBattle(p1, p2)
            } catch (e: Exception) {
                logger.error("Error starting battle between ${p1.player.name.string} and ${p2.player.name.string}", e)
                processingMatches.remove(p1.player.uuid)
                processingMatches.remove(p2.player.uuid)
                if (p1.formatName in RANDOM_FORMATS) BattleHandler.setPlayerInRandomBattle(p1.player.uuid, false)
                if (p2.formatName in RANDOM_FORMATS) BattleHandler.setPlayerInRandomBattle(p2.player.uuid, false)
                synchronized(queue) {
                    queue[p1.player.uuid] = p1
                    queue[p2.player.uuid] = p2
                }
            }
        }
    }

    private fun isEloCompatible(p1: QueueEntry, p2: QueueEntry): Boolean {
        val waitTime    = System.currentTimeMillis() - minOf(p1.joinTime, p2.joinTime)
        val maxWaitTime = config.maxQueueTime * 1000L
        val ratio       = (waitTime.toDouble() / maxWaitTime).coerceIn(0.0, 1.0)
        val multiplier  = 1.0 + (ratio * (config.maxEloMultiplier - 1.0))
        return kotlin.math.abs(p1.elo - p2.elo) <= (config.maxEloDiff * multiplier).toInt()
    }

    private fun startRankedBattle(player1: QueueEntry, player2: QueueEntry) {
        val lang   = config.defaultLang
        val server = player1.player.server

        BattleHandler.requestArena(
            listOf(player1.player, player2.player),
            2,
            onArenaFound = { arena, positions ->
                val worldId  = Identifier.tryParse(arena.world)
                val worldKey = if (worldId != null) RegistryKey.of(RegistryKeys.WORLD, worldId) else null
                val world    = if (worldKey != null) server.getWorld(worldKey) else null

                if (world == null) {
                    RankUtils.sendMessage(player1.player, MessageConfig.get("queue.world_load_fail", lang, "world" to arena.world))
                    RankUtils.sendMessage(player2.player, MessageConfig.get("queue.world_load_fail", lang, "world" to arena.world))
                    processingMatches.remove(player1.player.uuid)
                    processingMatches.remove(player2.player.uuid)
                    return@requestArena
                }

                RankUtils.sendMessage(player1.player, MessageConfig.get("queue.match_success", lang))
                RankUtils.sendMessage(player2.player, MessageConfig.get("queue.match_success", lang))

                server.execute {
                    try {
                        if (player1.player.isDisconnected || player2.player.isDisconnected) {
                            processingMatches.remove(player1.player.uuid)
                            processingMatches.remove(player2.player.uuid)
                            BattleHandler.releaseArena(arena)
                            return@execute
                        }

                        BattleHandler.setReturnLocation(player1.player.uuid, player1.player.serverWorld,
                            Triple(player1.player.x, player1.player.y, player1.player.z))
                        BattleHandler.setReturnLocation(player2.player.uuid, player2.player.serverWorld,
                            Triple(player2.player.x, player2.player.y, player2.player.z))

                        // Generar equipos Showdown JUSTO ANTES del teleport (regla: no reciben pokes hasta llegar a la arena)
                        val team1Final = if (player1.formatName in RANDOM_FORMATS) {
                            BattleHandler.prepareRandomTeam(player1.player, player1.formatName)
                        } else player1.team

                        val team2Final = if (player2.formatName in RANDOM_FORMATS) {
                            BattleHandler.prepareRandomTeam(player2.player, player2.formatName)
                        } else player2.team

                        player1.player.teleport(world, positions[0].x, positions[0].y, positions[0].z, 0f, 0f)
                        player2.player.teleport(world, positions[1].x, positions[1].y, positions[1].z, 0f, 0f)

                        // Los modos random saltan la selección de equipo (ya viene determinado)
                        TeamSelectionManager.startSelection(
                            player1         = player1.player,
                            team1Uuids      = team1Final,
                            p1SkipSelection = player1.formatName in RANDOM_FORMATS,
                            player2         = player2.player,
                            team2Uuids      = team2Final,
                            p2SkipSelection = player2.formatName in RANDOM_FORMATS,
                            format          = player1.format,
                            formatName      = player1.formatName,
                            p1Pos           = positions[0],
                            p2Pos           = positions[1]
                        )
                        processingMatches.remove(player1.player.uuid)
                        processingMatches.remove(player2.player.uuid)
                    } catch (e: Exception) {
                        logger.error("Error in battle startup", e)
                        // Si falla, restaurar parties random si corresponde
                        if (player1.formatName in RANDOM_FORMATS) BattleHandler.restorePartyIfRandom(player1.player)
                        if (player2.formatName in RANDOM_FORMATS) BattleHandler.restorePartyIfRandom(player2.player)
                        BattleHandler.releaseArena(arena)
                        processingMatches.remove(player1.player.uuid)
                        processingMatches.remove(player2.player.uuid)
                    }
                }
            },
            onAbort = { survivor ->
                val entry = if (survivor.uuid == player1.player.uuid) player1 else player2
                synchronized(queue) {
                    if (!queue.containsKey(entry.player.uuid)) {
                        queue[entry.player.uuid] = entry
                    }
                }
                processingMatches.remove(entry.player.uuid)
                RankUtils.sendMessage(survivor, MessageConfig.get("queue.opponent_disconnected", lang))
            }
        )
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private fun getPlayerTeam(player: ServerPlayerEntity): List<UUID> {
        val party = Cobblemon.storage.getParty(player)
        if (party.count() == 0) throw IllegalStateException("El equipo está vacío")
        return party.mapNotNull { it?.uuid }
    }

    fun reloadConfig(newConfig: RankConfig) { initializeFormatMap() }

    fun shutdown() {
        scheduler.shutdownNow()
        queue.clear()
        processingMatches.clear()
    }

    fun cleanupStaleEntries() {
        synchronized(queue) {
            val toRemove = queue.values.filter {
                Cobblemon.battleRegistry.getBattleByParticipatingPlayer(it.player) != null
                        || it.player.isDisconnected
            }.map { it.player.uuid }
            toRemove.forEach { queue.remove(it); processingMatches.remove(it) }
        }
    }

    fun canPlayerJoinQueue(player: ServerPlayerEntity): Boolean {
        if (queue.containsKey(player.uuid) || processingMatches.contains(player.uuid)) return false
        if (Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) != null || player.isDisconnected) return false
        if (TeamSelectionManager.isPlayerInSelection(player.uuid)) return false
        return true
    }

    // -------------------------------------------------------------------------
    // Modelo de datos
    // -------------------------------------------------------------------------

    /**
     * @param formatName  Clave del modo ("single_battle", "doubles_battle", "random_battle",
     *                    "double_random_battle"). Se usa como clave de Elo en la BD.
     */
    data class QueueEntry(
        val player:     ServerPlayerEntity,
        val format:     BattleFormat,
        val team:       List<UUID>,
        val joinTime:   Long,
        val elo:        Int    = 1000,
        val formatName: String = "single_battle"
    )
}

