package cn.kurt6.cobblemon_ranked

import cn.kurt6.cobblemon_ranked.config.ArenaCoordinate
import cn.kurt6.cobblemon_ranked.config.BattleArena
import cn.kurt6.cobblemon_ranked.config.ConfigManager
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.data.PlayerRankData
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object AdminMenu {

    // Estado temporal para edición de ELO (espera input de chat)
    private val pendingEloEdit = ConcurrentHashMap<UUID, Triple<UUID, String, Int>>() // admin → (target, format, currentElo)
    // Estado temporal para coordenadas de arena
    private val pendingCoordCapture = ConcurrentHashMap<UUID, Pair<Int, Int>>() // admin → (arenaIndex, playerSlot)

    // ─────────────────────────────────────────────────────────────────────────
    // Utilidades
    // ─────────────────────────────────────────────────────────────────────────

    private fun filler(color: net.minecraft.item.Item = Items.BLACK_STAINED_GLASS_PANE): ItemStack =
        ItemStack(color).also { it.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" ")) }

    private fun makeItem(item: net.minecraft.item.Item, name: String, lore: List<String> = emptyList()): ItemStack =
        ItemStack(item).also { stack ->
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name))
            if (lore.isNotEmpty()) stack.set(DataComponentTypes.LORE, LoreComponent(lore.map { Text.literal(it) }))
        }

    private fun fillEmpty(inv: SimpleInventory, size: Int, exclude: Set<Int>) {
        for (i in 0 until size) if (i !in exclude && inv.getStack(i).isEmpty) inv.setStack(i, filler())
    }

    private fun backButton(): ItemStack = makeItem(Items.ARROW, "§7« Volver al panel")

    // ─────────────────────────────────────────────────────────────────────────
    // MENÚ PRINCIPAL
    // ─────────────────────────────────────────────────────────────────────────

    fun open(admin: ServerPlayerEntity) {
        val inv = SimpleInventory(27)
        val queue = CobblemonRanked.matchmakingQueue

        // Fila 2 — 6 opciones principales
        inv.setStack(10, makeItem(Items.PLAYER_HEAD, "§b👤 Gestión de Jugadores", listOf(
            "§7Buscar jugador, ver y editar",
            "§7ELO por formato, resetear stats.",
            "",
            "§7Click para abrir."
        )))
        inv.setStack(11, makeItem(Items.COMPASS, "§6🏟 Gestión de Arenas", listOf(
            "§7Ver arenas configuradas.",
            "§7Asignar coordenadas de spawn",
            "§7para cada jugador.",
            "",
            "§7Click para abrir."
        )))
        inv.setStack(12, makeItem(Items.WRITABLE_BOOK, "§e📊 Estadísticas", listOf(
            "§7Jugadores en cola: §f${queue.queue.size}",
            "§7Batallas activas: §f${countActiveBattles()}",
            "",
            "§7Click para ver más."
        )))
        inv.setStack(13, makeItem(Items.BARRIER, "§c🚫 Pokémon Prohibidos", listOf(
            "§7Añadir o quitar bans",
            "§7en tiempo real.",
            "",
            "§8▪ §7Baneados: §c${CobblemonRanked.config.bannedPokemon.size}",
            "",
            "§7Click para gestionar."
        )))
        inv.setStack(14, makeItem(Items.NETHER_STAR, "§d🎭 Temporada", listOf(
            "§8▪ §7Temporada actual: §f${CobblemonRanked.seasonManager.currentSeasonId}",
            "§8▪ §7Tiempo restante: §a${formatRemaining()}",
            "",
            "§7Click para gestionar."
        )))
        inv.setStack(15, makeItem(Items.LIGHTNING_ROD, "§a⚡ Acciones Rápidas", listOf(
            "§7Limpiar colas, teleportar",
            "§7jugadores atascados,",
            "§7recargar configuración.",
            "",
            "§7Click para abrir."
        )))

        fillEmpty(inv, 27, setOf(10, 11, 12, 13, 14, 15))

        admin.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInv, inv, 3) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        when (slotIndex) {
                            10 -> { sp.closeHandledScreen(); openPlayerManagement(sp) }
                            11 -> { sp.closeHandledScreen(); openArenaManagement(sp) }
                            12 -> { sp.closeHandledScreen(); openStats(sp) }
                            13 -> { sp.closeHandledScreen(); openBanList(sp) }
                            14 -> { sp.closeHandledScreen(); openSeason(sp) }
                            15 -> { sp.closeHandledScreen(); openQuickActions(sp) }
                        }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§4⚙ Panel de Administración")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GESTIÓN DE JUGADORES
    // ─────────────────────────────────────────────────────────────────────────

    fun openPlayerManagement(admin: ServerPlayerEntity) {
        val inv = SimpleInventory(54)
        val server = admin.server
        val onlinePlayers = server.playerManager.playerList
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId

        inv.setStack(4, makeItem(Items.PLAYER_HEAD, "§b👤 Gestión de Jugadores", listOf(
            "§7Jugadores en línea: §f${onlinePlayers.size}",
            "§7Click en un jugador para editar."
        )))

        // Mostrar jugadores online (slots 9 en adelante)
        onlinePlayers.forEachIndexed { idx, player ->
            val slot = 9 + idx
            if (slot >= 53) return@forEachIndexed
            val defaultFormat = CobblemonRanked.config.allowedFormats.firstOrNull() ?: "single_battle"
            val data = CobblemonRanked.rankDao.getPlayerData(player.uuid, seasonId, defaultFormat)
            val elo = data?.elo ?: CobblemonRanked.config.initialElo
            val rank = data?.getRankTitle() ?: "Sin rango"
            inv.setStack(slot, makeItem(Items.PLAYER_HEAD, "§f${player.name.string}", listOf(
                "§8▪ §7ELO (${defaultFormat}): §b$elo",
                "§8▪ §7Rango: §6$rank",
                "§8▪ §7En combate: §f${if (BattleHandler.isPlayerInRankedBattle(player)) "§cSí" else "§aNo"}",
                "",
                "§7Click para editar."
            )))
        }

        inv.setStack(49, backButton())
        fillEmpty(inv, 54, (9 until 9 + onlinePlayers.size).toSet() + setOf(4, 49))

        admin.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        when (slotIndex) {
                            49 -> { sp.closeHandledScreen(); open(sp) }
                            in 9..52 -> {
                                val idx = slotIndex - 9
                                val target = onlinePlayers.getOrNull(idx) ?: return
                                sp.closeHandledScreen()
                                openPlayerDetail(sp, target)
                            }
                        }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§b👤 Gestión de Jugadores")
        ))
    }

    private fun openPlayerDetail(admin: ServerPlayerEntity, target: ServerPlayerEntity) {
        val inv = SimpleInventory(54)
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val formats = CobblemonRanked.config.allowedFormats

        inv.setStack(4, makeItem(Items.PLAYER_HEAD, "§f${target.name.string}", listOf(
            "§8UUID: §7${target.uuid}",
            "§8▪ §7En combate: §f${if (BattleHandler.isPlayerInRankedBattle(target)) "§cSí" else "§aNo"}",
            "§8▪ §7En random: §f${if (BattleHandler.isPlayerInRandomBattle(target.uuid)) "§cSí" else "§aNo"}"
        )))

        // ELO por formato (fila 2)
        val eloSlots = listOf(10, 12, 14, 16)
        formats.forEachIndexed { idx, format ->
            val slot = eloSlots.getOrNull(idx) ?: return@forEachIndexed
            val data = CobblemonRanked.rankDao.getPlayerData(target.uuid, seasonId, format)
            val elo = data?.elo ?: CobblemonRanked.config.initialElo
            val wins = data?.wins ?: 0
            val losses = data?.losses ?: 0
            inv.setStack(slot, makeItem(Items.EXPERIENCE_BOTTLE, "§b${formatDisplayName(format)}", listOf(
                "§8▪ §7ELO: §b$elo",
                "§8▪ §7Rango: §6${data?.getRankTitle() ?: "Sin rango"}",
                "§8▪ §7Victorias: §a$wins",
                "§8▪ §7Derrotas: §c$losses",
                "§8▪ §7Racha: §e${data?.winStreak ?: 0}",
                "",
                "§eClick izquierdo: §7+100 ELO",
                "§eClick derecho: §7-100 ELO",
                "§eShift+Click: §7Editar exacto (chat)"
            )))
        }

        // Acciones (fila 4)
        inv.setStack(28, makeItem(Items.EMERALD, "§aForzar teleport de vuelta", listOf(
            "§7Teleporta al jugador a su",
            "§7posición original si está",
            "§7atascado en una arena."
        )))
        inv.setStack(30, makeItem(Items.REDSTONE, "§cLimpiar datos de combate", listOf(
            "§7Limpia el estado de combate",
            "§7del jugador (party, arena, etc.)"
        )))
        inv.setStack(32, makeItem(Items.TNT, "§4Resetear stats §c(${formats.firstOrNull()})", listOf(
            "§cReset ELO al valor inicial",
            "§cy stats en el formato activo.",
            "",
            "§c⚠ Esta acción no se puede deshacer."
        )))
        inv.setStack(34, makeItem(Items.BOOK, "§eVer historial de combates", listOf(
            "§7Próximamente..."
        )))

        inv.setStack(49, backButton())
        fillEmpty(inv, 54, setOf(4, 10, 12, 14, 16, 28, 30, 32, 34, 49))

        val formatsCopy = formats.toList()
        admin.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        val seasonId2 = CobblemonRanked.seasonManager.currentSeasonId
                        when (slotIndex) {
                            49 -> { sp.closeHandledScreen(); openPlayerManagement(sp) }
                            in eloSlots -> {
                                val idx = eloSlots.indexOf(slotIndex)
                                val format = formatsCopy.getOrNull(idx) ?: return
                                CompletableFuture.runAsync {
                                    val dao = CobblemonRanked.rankDao
                                    val data = dao.getPlayerData(target.uuid, seasonId2, format)
                                        ?: PlayerRankData(target.uuid, seasonId2, format).also { it.elo = CobblemonRanked.config.initialElo }
                                    data.elo = (data.elo + 100).coerceAtLeast(0)
                                    dao.savePlayerData(data)
                                }
                                sp.sendMessage(Text.literal("§a+100 ELO a §f${target.name.string} §aen §f$format"), false)
                                sp.closeHandledScreen()
                                sp.server.execute { openPlayerDetail(sp, target) }
                            }
                            28 -> {
                                sp.closeHandledScreen()
                                BattleHandler.teleportBackIfPossible(target)
                                sp.sendMessage(Text.literal("§aTeleportado §f${target.name.string} §aa su posición original."), false)
                            }
                            30 -> {
                                sp.closeHandledScreen()
                                BattleHandler.forceCleanupPlayerBattleData(target)
                                sp.sendMessage(Text.literal("§aDatos de combate limpiados para §f${target.name.string}"), false)
                            }
                            32 -> {
                                sp.closeHandledScreen()
                                val format = formatsCopy.firstOrNull() ?: return
                                CompletableFuture.runAsync {
                                    val dao = CobblemonRanked.rankDao
                                    val data = dao.getPlayerData(target.uuid, seasonId2, format)
                                    if (data != null) {
                                        data.elo = CobblemonRanked.config.initialElo
                                        data.wins = 0; data.losses = 0; data.winStreak = 0; data.bestWinStreak = 0
                                        dao.savePlayerData(data)
                                    }
                                }
                                sp.sendMessage(Text.literal("§cStats reseteadas para §f${target.name.string}"), false)
                            }
                        }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§b👤 ${target.name.string}")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GESTIÓN DE ARENAS
    // ─────────────────────────────────────────────────────────────────────────

    fun openArenaManagement(admin: ServerPlayerEntity) {
        val inv = SimpleInventory(54)
        val arenas = CobblemonRanked.config.battleArenas

        inv.setStack(4, makeItem(Items.COMPASS, "§6🏟 Gestión de Arenas", listOf(
            "§8▪ §7Arenas configuradas: §f${arenas.size}",
            "§7Click en una arena para gestionar",
            "§7sus coordenadas de spawn.",
            "",
            "§7Las arenas se guardan en",
            "§7cobblemon_ranked.json"
        )))

        // Botón añadir arena (slot 6)
        inv.setStack(6, makeItem(Items.EMERALD, "§a+ Nueva arena", listOf(
            "§7Añade una arena nueva usando",
            "§7tu posición actual como",
            "§7coordenada del jugador 1.",
            "",
            "§7Click para crear."
        )))

        // Listar arenas existentes (fila 2 en adelante)
        arenas.forEachIndexed { idx, arena ->
            val slot = 9 + idx
            if (slot >= 53) return@forEachIndexed
            val p1 = arena.playerPositions.getOrNull(0)
            val p2 = arena.playerPositions.getOrNull(1)
            inv.setStack(slot, makeItem(Items.BEACON, "§6Arena ${idx + 1}", listOf(
                "§8▪ §7Mundo: §f${arena.world}",
                "§8▪ §7Jugador 1: §f${p1?.let { "(${it.x.toInt()}, ${it.y.toInt()}, ${it.z.toInt()})" } ?: "§cNo definido"}",
                "§8▪ §7Jugador 2: §f${p2?.let { "(${it.x.toInt()}, ${it.y.toInt()}, ${it.z.toInt()})" } ?: "§cNo definido"}",
                "§8▪ §7Estado: ${if (cn.kurt6.cobblemon_ranked.battle.BattleHandler.playerToArena.values.contains(arena)) "§cOcupada" else "§aLibre"}",
                "",
                "§7Click para editar coordenadas."
            )))
        }

        inv.setStack(49, backButton())
        val arenaSlots = (9 until 9 + arenas.size).toSet()
        fillEmpty(inv, 54, arenaSlots + setOf(4, 6, 49))

        admin.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        when {
                            slotIndex == 49 -> { sp.closeHandledScreen(); open(sp) }
                            slotIndex == 6 -> {
                                sp.closeHandledScreen()
                                addNewArena(sp)
                            }
                            slotIndex in arenaSlots -> {
                                val idx = slotIndex - 9
                                sp.closeHandledScreen()
                                openArenaDetail(sp, idx)
                            }
                        }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§6🏟 Gestión de Arenas")
        ))
    }

    private fun openArenaDetail(admin: ServerPlayerEntity, arenaIndex: Int) {
        val inv = SimpleInventory(36)
        val arenas = CobblemonRanked.config.battleArenas
        val arena = arenas.getOrNull(arenaIndex) ?: run {
            admin.sendMessage(Text.literal("§cArena no encontrada."), false)
            openArenaManagement(admin)
            return
        }

        val p1 = arena.playerPositions.getOrNull(0)
        val p2 = arena.playerPositions.getOrNull(1)

        inv.setStack(4, makeItem(Items.BEACON, "§6Arena ${arenaIndex + 1}", listOf(
            "§8▪ §7Mundo: §f${arena.world}"
        )))

        // Slot jugador 1
        inv.setStack(10, makeItem(Items.LIME_WOOL, "§aSpawn Jugador 1", listOf(
            "§7Posición actual: §f${p1?.let { "(${it.x.toInt()}, ${it.y.toInt()}, ${it.z.toInt()})" } ?: "§cNo definido"}",
            "",
            "§7Click para usar TU posición actual",
            "§7como spawn del jugador 1."
        )))

        // Slot jugador 2
        inv.setStack(12, makeItem(Items.RED_WOOL, "§cSpawn Jugador 2", listOf(
            "§7Posición actual: §f${p2?.let { "(${it.x.toInt()}, ${it.y.toInt()}, ${it.z.toInt()})" } ?: "§cNo definido"}",
            "",
            "§7Click para usar TU posición actual",
            "§7como spawn del jugador 2."
        )))

        // Cambiar mundo
        inv.setStack(14, makeItem(Items.ENDER_PEARL, "§bMundo: §f${arena.world}", listOf(
            "§7Click para usar el mundo",
            "§7en el que estás ahora.",
            "",
            "§8▪ §7overworld = minecraft:overworld",
            "§8▪ §7nether = minecraft:the_nether",
            "§8▪ §7end = minecraft:the_end"
        )))

        // Eliminar arena
        inv.setStack(16, makeItem(Items.TNT, "§cEliminar esta arena", listOf(
            "§c⚠ Esta acción no se puede deshacer.",
            "§7Elimina la arena ${arenaIndex + 1}",
            "§7de la configuración."
        )))

        inv.setStack(31, backButton())
        fillEmpty(inv, 36, setOf(4, 10, 12, 14, 16, 31))

        admin.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X4, syncId, playerInv, inv, 4) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        when (slotIndex) {
                            31 -> { sp.closeHandledScreen(); openArenaManagement(sp) }
                            10 -> { // Capturar posición jugador 1
                                updateArenaPosition(sp, arenaIndex, 0)
                                sp.closeHandledScreen()
                                sp.server.execute { openArenaDetail(sp, arenaIndex) }
                            }
                            12 -> { // Capturar posición jugador 2
                                updateArenaPosition(sp, arenaIndex, 1)
                                sp.closeHandledScreen()
                                sp.server.execute { openArenaDetail(sp, arenaIndex) }
                            }
                            14 -> { // Cambiar mundo al actual
                                updateArenaWorld(sp, arenaIndex)
                                sp.closeHandledScreen()
                                sp.server.execute { openArenaDetail(sp, arenaIndex) }
                            }
                            16 -> { // Eliminar arena
                                deleteArena(sp, arenaIndex)
                                sp.closeHandledScreen()
                                sp.server.execute { openArenaManagement(sp) }
                            }
                        }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§6Arena ${arenaIndex + 1} — Editar")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ESTADÍSTICAS
    // ─────────────────────────────────────────────────────────────────────────

    fun openStats(admin: ServerPlayerEntity) {
        val inv = SimpleInventory(36)
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val queue = CobblemonRanked.matchmakingQueue
        val config = CobblemonRanked.config

        // Cola por formato
        val formatStats = config.allowedFormats.map { format ->
            val count = queue.queue.values.count { it.formatName == format }
            "§8▪ §7${formatDisplayName(format)}: §f$count en cola"
        }

        inv.setStack(10, makeItem(Items.CLOCK, "§e⏱ Cola en tiempo real", listOf(
            "§8▪ §7Total en cola: §f${queue.queue.size}",
            "§8▪ §7Batallas activas: §f${countActiveBattles()}",
            *formatStats.toTypedArray()
        )))

        // Top Pokémon más usados
        val topPokemon = CompletableFuture.supplyAsync {
            CobblemonRanked.rankDao.getPokemonUsage(seasonId, 5, 0)
        }.get()

        inv.setStack(12, makeItem(Items.DIAMOND, "§b🏆 Top Pokémon más usados", listOf(
            "§7Temporada §f$seasonId:",
            *topPokemon.mapIndexed { i, (name, count) ->
                "§8${i+1}. §f$name §7(§a$count§7 veces)"
            }.toTypedArray(),
            if (topPokemon.isEmpty()) "§7Sin datos aún." else ""
        )))

        // Participación por formato
        val participationLines = config.allowedFormats.map { format ->
            val count = CompletableFuture.supplyAsync {
                CobblemonRanked.rankDao.getParticipationCount(seasonId, format)
            }.get()
            "§8▪ §7${formatDisplayName(format)}: §f$count jugadores"
        }

        inv.setStack(14, makeItem(Items.EMERALD, "§a👥 Participación", listOf(
            "§7Temporada §f$seasonId:",
            *participationLines.toTypedArray()
        )))

        // Info servidor
        inv.setStack(16, makeItem(Items.IRON_INGOT, "§7🖥 Servidor", listOf(
            "§8▪ §7Jugadores online: §f${admin.server.currentPlayerCount}",
            "§8▪ §7TPS: §f${String.format("%.1f", admin.server.averageTickTime)}ms",
            "§8▪ §7Temporada: §f$seasonId",
            "§8▪ §7Tiempo restante: §a${formatRemaining()}"
        )))

        inv.setStack(31, backButton())
        fillEmpty(inv, 36, setOf(10, 12, 14, 16, 31))

        admin.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X4, syncId, playerInv, inv, 4) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        if (slotIndex == 31) { sp.closeHandledScreen(); open(sp) }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§e📊 Estadísticas")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LISTA DE BANS
    // ─────────────────────────────────────────────────────────────────────────

    fun openBanList(admin: ServerPlayerEntity) {
        val config = CobblemonRanked.config
        val banned = config.bannedPokemon.toMutableList()
        val rows = maxOf(3, ((banned.size + 2) / 9) + 2).coerceAtMost(6)
        val totalSlots = rows * 9
        val inv = SimpleInventory(totalSlots)

        inv.setStack(4, makeItem(Items.BARRIER, "§c🚫 Pokémon Prohibidos", listOf(
            "§8▪ §7Baneados: §c${banned.size}",
            "§7Click en un Pokémon para §cdesbanear.",
            "§7Escribe §e/rankadmin ban <nombre>",
            "§7para añadir un ban."
        )))

        // Listar baneados
        banned.forEachIndexed { idx, name ->
            val slot = 9 + idx
            if (slot >= totalSlots - 9) return@forEachIndexed
            val modelItem = net.minecraft.registry.Registries.ITEM.get(
                net.minecraft.util.Identifier.of("cobblemon", "pokemon_model")
            )
            inv.setStack(slot, ItemStack(modelItem).also { stack ->
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c$name"))
                stack.set(DataComponentTypes.LORE, LoreComponent(listOf(
                    Text.literal("§7Click para §cdesbanear §7este Pokémon.")
                )))
                try {
                    val nbt = net.minecraft.nbt.NbtCompound()
                    nbt.putString("pokemon", name.lowercase())
                    stack.set(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(nbt))
                } catch (e: Exception) {}
            })
        }

        val backSlot = totalSlots - 5
        inv.setStack(backSlot, backButton())
        fillEmpty(inv, totalSlots, (9 until 9 + banned.size).toSet() + setOf(4, backSlot))

        val screenType = when (rows) {
            3 -> ScreenHandlerType.GENERIC_9X3
            4 -> ScreenHandlerType.GENERIC_9X4
            5 -> ScreenHandlerType.GENERIC_9X5
            else -> ScreenHandlerType.GENERIC_9X6
        }

        admin.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(screenType, syncId, playerInv, inv, rows) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        when {
                            slotIndex == backSlot -> { sp.closeHandledScreen(); open(sp) }
                            slotIndex in 9 until 9 + banned.size -> {
                                val idx = slotIndex - 9
                                val name = banned.getOrNull(idx) ?: return
                                val newBanned = config.bannedPokemon.toMutableList().also { it.remove(name) }
                                val newConfig = config.copy(bannedPokemon = newBanned)
                                CobblemonRanked.config = newConfig
                                ConfigManager.save(newConfig)
                                sp.sendMessage(Text.literal("§a✔ ${name} §adesbaneado y guardado."), false)
                                sp.closeHandledScreen()
                                sp.server.execute { openBanList(sp) }
                            }
                        }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§c🚫 Pokémon Prohibidos")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEMPORADA
    // ─────────────────────────────────────────────────────────────────────────

    fun openSeason(admin: ServerPlayerEntity) {
        val inv = SimpleInventory(36)
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId

        inv.setStack(10, makeItem(Items.NETHER_STAR, "§d📋 Temporada actual", listOf(
            "§8▪ §7ID: §f$seasonId",
            "§8▪ §7Tiempo restante: §a${formatRemaining()}"
        )))

        inv.setStack(12, makeItem(Items.CLOCK, "§cForzar fin de temporada", listOf(
            "§c⚠ Termina la temporada ahora.",
            "§7Se repartirán recompensas y",
            "§7comenzará la siguiente temporada.",
            "",
            "§c⚠ Esta acción no se puede deshacer."
        )))

        inv.setStack(14, makeItem(Items.PAPER, "§aRecargar configuración", listOf(
            "§7Recarga cobblemon_ranked.json",
            "§7sin reiniciar el servidor."
        )))

        inv.setStack(31, backButton())
        fillEmpty(inv, 36, setOf(10, 12, 14, 31))

        admin.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X4, syncId, playerInv, inv, 4) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        when (slotIndex) {
                            31 -> { sp.closeHandledScreen(); open(sp) }
                            12 -> {
                                sp.closeHandledScreen()
                                sp.server.execute {
                                    CobblemonRanked.seasonManager.checkSeasonEnd(sp.server)
                                    sp.sendMessage(Text.literal("§d✔ Temporada finalizada manualmente."), false)
                                }
                            }
                            14 -> {
                                sp.closeHandledScreen()
                                val newConfig = ConfigManager.reload()
                                CobblemonRanked.config = newConfig
                                sp.sendMessage(Text.literal("§a✔ Configuración recargada."), false)
                            }
                        }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§d🎭 Temporada $seasonId")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ACCIONES RÁPIDAS
    // ─────────────────────────────────────────────────────────────────────────

    fun openQuickActions(admin: ServerPlayerEntity) {
        val inv = SimpleInventory(36)
        val queue = CobblemonRanked.matchmakingQueue

        inv.setStack(10, makeItem(Items.BUCKET, "§eLimpiar todas las colas", listOf(
            "§7Elimina a todos los jugadores",
            "§7de todas las colas activas.",
            "",
            "§8▪ §7En cola ahora: §f${queue.queue.size}"
        )))

        inv.setStack(12, makeItem(Items.ENDER_EYE, "§bTeleportar atascados", listOf(
            "§7Teleporta a todos los jugadores",
            "§7con returnLocation guardada",
            "§7de vuelta a su origen."
        )))

        inv.setStack(14, makeItem(Items.REPEATER, "§aRecargar config", listOf(
            "§7Recarga cobblemon_ranked.json",
            "§7sin reiniciar el servidor."
        )))

        inv.setStack(16, makeItem(Items.RECOVERY_COMPASS, "§cLimpiar batallas huérfanas", listOf(
            "§7Limpia marcadores de combate",
            "§7de jugadores que ya no están",
            "§7en batalla activa."
        )))

        inv.setStack(31, backButton())
        fillEmpty(inv, 36, setOf(10, 12, 14, 16, 31))

        admin.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X4, syncId, playerInv, inv, 4) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        when (slotIndex) {
                            31 -> { sp.closeHandledScreen(); open(sp) }
                            10 -> {
                                sp.closeHandledScreen()
                                val count = queue.queue.size
                                queue.clear()
                                sp.sendMessage(Text.literal("§a✔ $count jugadores eliminados de la cola."), false)
                            }
                            12 -> {
                                sp.closeHandledScreen()
                                var count = 0
                                sp.server.playerManager.playerList.forEach { player ->
                                    if (CobblemonRanked.rankDao.getReturnLocation(player.uuid) != null) {
                                        BattleHandler.teleportBackIfPossible(player)
                                        count++
                                    }
                                }
                                sp.sendMessage(Text.literal("§a✔ $count jugadores teleportados de vuelta."), false)
                            }
                            14 -> {
                                sp.closeHandledScreen()
                                val newConfig = ConfigManager.reload()
                                CobblemonRanked.config = newConfig
                                sp.sendMessage(Text.literal("§a✔ Configuración recargada."), false)
                            }
                            16 -> {
                                sp.closeHandledScreen()
                                BattleHandler.cleanupStaleRankedBattleMarkers(sp.server)
                                BattleHandler.cleanupStaleUsedPokemonMarkers(sp.server)
                                sp.sendMessage(Text.literal("§a✔ Marcadores huérfanos limpiados."), false)
                            }
                        }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§a⚡ Acciones Rápidas")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers de arenas
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateArenaPosition(admin: ServerPlayerEntity, arenaIndex: Int, playerSlot: Int) {
        val config = CobblemonRanked.config
        val arenas = config.battleArenas.toMutableList()
        val arena = arenas.getOrNull(arenaIndex) ?: return
        val positions = arena.playerPositions.toMutableList()

        val newCoord = ArenaCoordinate(admin.x, admin.y, admin.z)
        while (positions.size <= playerSlot) positions.add(ArenaCoordinate(0.0, 64.0, 0.0))
        positions[playerSlot] = newCoord

        arenas[arenaIndex] = arena.copy(playerPositions = positions)
        val newConfig = config.copy(battleArenas = arenas)
        CobblemonRanked.config = newConfig
        ConfigManager.save(newConfig)

        admin.sendMessage(Text.literal("§a✔ Posición del jugador ${playerSlot + 1} actualizada: " +
                "(${admin.x.toInt()}, ${admin.y.toInt()}, ${admin.z.toInt()})"), false)
    }

    private fun updateArenaWorld(admin: ServerPlayerEntity, arenaIndex: Int) {
        val config = CobblemonRanked.config
        val arenas = config.battleArenas.toMutableList()
        val arena = arenas.getOrNull(arenaIndex) ?: return
        val worldId = admin.serverWorld.registryKey.value.toString()

        arenas[arenaIndex] = arena.copy(world = worldId)
        val newConfig = config.copy(battleArenas = arenas)
        CobblemonRanked.config = newConfig
        ConfigManager.save(newConfig)

        admin.sendMessage(Text.literal("§a✔ Mundo de la arena actualizado: §f$worldId"), false)
    }

    private fun addNewArena(admin: ServerPlayerEntity) {
        val config = CobblemonRanked.config
        val worldId = admin.serverWorld.registryKey.value.toString()
        val newArena = BattleArena(
            world = worldId,
            playerPositions = listOf(
                ArenaCoordinate(admin.x, admin.y, admin.z),
                ArenaCoordinate(admin.x + 10, admin.y, admin.z)
            )
        )
        val newConfig = config.copy(battleArenas = config.battleArenas + newArena)
        CobblemonRanked.config = newConfig
        ConfigManager.save(newConfig)
        admin.sendMessage(Text.literal("§a✔ Arena ${newConfig.battleArenas.size} creada. Edita las coordenadas del jugador 2 en el menú."), false)
        admin.server.execute { openArenaManagement(admin) }
    }

    private fun deleteArena(admin: ServerPlayerEntity, arenaIndex: Int) {
        val config = CobblemonRanked.config
        val arenas = config.battleArenas.toMutableList()
        if (arenaIndex < 0 || arenaIndex >= arenas.size) return
        arenas.removeAt(arenaIndex)
        val newConfig = config.copy(battleArenas = arenas)
        CobblemonRanked.config = newConfig
        ConfigManager.save(newConfig)
        admin.sendMessage(Text.literal("§c✔ Arena ${arenaIndex + 1} eliminada."), false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers generales
    // ─────────────────────────────────────────────────────────────────────────

    private fun countActiveBattles(): Int {
        return 0 // API de battleRegistry no expone conteo público en esta versión
    }

    private fun formatRemaining(): String {
        val r = CobblemonRanked.seasonManager.getRemainingTime()
        return "${r.days}d ${r.hours}h ${r.minutes}m"
    }

    private fun formatDisplayName(format: String) = when (format) {
        "single_battle"        -> "Single Battle"
        "doubles_battle"       -> "Doubles Battle"
        "random_battle"        -> "Random Battle"
        "double_random_battle" -> "Double Random"
        else                   -> format
    }
}
