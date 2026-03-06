package cn.kurt6.cobblemon_ranked

import com.cobblemon.mod.common.CobblemonItems
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.component.type.ProfileComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.Optional

object RankedMenu {

    // ─────────────────────────────────────────────────────────────────────────
    // Utilidades
    // ─────────────────────────────────────────────────────────────────────────

    private fun filler(item: net.minecraft.item.Item = Items.BLACK_STAINED_GLASS_PANE): ItemStack =
        ItemStack(item).also { it.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" ")) }

    private fun gold() = filler(Items.YELLOW_STAINED_GLASS_PANE)
    private fun gray() = filler(Items.GRAY_STAINED_GLASS_PANE)
    private fun dark() = filler(Items.BLACK_STAINED_GLASS_PANE)
    private fun red()  = filler(Items.RED_STAINED_GLASS_PANE)
    private fun blue() = filler(Items.BLUE_STAINED_GLASS_PANE)
    private fun purp() = filler(Items.PURPLE_STAINED_GLASS_PANE)

    private fun fillEmpty(inv: SimpleInventory, size: Int, exclude: Set<Int>) {
        for (i in 0 until size) {
            if (i !in exclude && inv.getStack(i).isEmpty) inv.setStack(i, dark())
        }
    }

    private fun backButton(): ItemStack = ItemStack(Items.ARROW).also {
        it.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§7« Volver"))
    }

    private fun makeItem(item: net.minecraft.item.Item, name: String, lore: List<String> = emptyList()): ItemStack =
        ItemStack(item).also { stack ->
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name))
            if (lore.isNotEmpty()) stack.set(DataComponentTypes.LORE, LoreComponent(lore.map { Text.literal(it) }))
        }

    fun getRankColor(rankTitle: String): String = when {
        rankTitle.startsWith("Legendario") -> "§6"
        rankTitle.startsWith("Maestro")    -> "§5"
        rankTitle.startsWith("Élite")      -> "§b"
        rankTitle.startsWith("Experto")    -> "§a"
        rankTitle.startsWith("Rival")      -> "§e"
        else                               -> "§f"
    }

    fun getRankBall(rankTitle: String): net.minecraft.item.Item = when {
        rankTitle.startsWith("Legendario") -> CobblemonItems.BEAST_BALL
        rankTitle.startsWith("Maestro")    -> CobblemonItems.MASTER_BALL
        rankTitle.startsWith("Élite")      -> CobblemonItems.LUXURY_BALL
        rankTitle.startsWith("Experto")    -> CobblemonItems.MOON_BALL
        else                               -> CobblemonItems.ANCIENT_IVORY_BALL
    }

    fun formatDisplayName(format: String): String = when (format) {
        "single_battle"        -> "Single Battle"
        "doubles_battle"       -> "Doubles Battle"
        "random_battle"        -> "Random Battle"
        "double_random_battle" -> "Double Random"
        else                   -> format
    }

    fun formatColor(format: String): String = when (format) {
        "single_battle"        -> "§c"
        "doubles_battle"       -> "§9"
        "random_battle"        -> "§e"
        "double_random_battle" -> "§a"
        else                   -> "§f"
    }

    fun formatBall(format: String): net.minecraft.item.Item = when (format) {
        "single_battle"        -> CobblemonItems.POKE_BALL
        "doubles_battle"       -> CobblemonItems.GREAT_BALL
        "random_battle"        -> CobblemonItems.ULTRA_BALL
        "double_random_battle" -> CobblemonItems.SAFARI_BALL
        else                   -> CobblemonItems.POKE_BALL
    }

    private fun sep() = "§8§m────────────────────"

    // ─────────────────────────────────────────────────────────────────────────
    // MENÚ PRINCIPAL — 6 filas
    //
    //  0  1  2  3  4  5  6  7  8   ← franja dorada (4=título)
    //  9 10 11 12 13 14 15 16 17   ← cabeza(12), rango(14), temporada(16)
    // 18 19 20 21 22 23 24 25 26   ← separador gris
    // 27 28 29 30 31 32 33 34 35   ← separador oscuro
    // 36 37 38 39 40 41 42 43 44   ← [Modos:37] [Ranking:40] [Premios:43]
    // 45 46 47 48 49 50 51 52 53   ← franja dorada (49=Bans)
    // ─────────────────────────────────────────────────────────────────────────

    fun open(player: ServerPlayerEntity) {
        val inv = SimpleInventory(54)
        val dao = CobblemonRanked.rankDao
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val queue = CobblemonRanked.matchmakingQueue
        val config = CobblemonRanked.config
        val remaining = CobblemonRanked.seasonManager.getRemainingTime()
        val defaultFormat = config.allowedFormats.firstOrNull() ?: "single_battle"
        val data = dao.getPlayerData(player.uuid, seasonId, defaultFormat)
        val elo = data?.elo ?: config.initialElo
        val rankTitle = data?.getRankTitle() ?: "Aprendiz ★"
        val rankColor = getRankColor(rankTitle)
        val wins = data?.wins ?: 0
        val losses = data?.losses ?: 0
        val winStreak = data?.winStreak ?: 0

        // Fila 1 — franja dorada
        for (i in 0..8) inv.setStack(i, gold())

        // Slot 5 (índice 4) — mega_stone_crystal con info de temporada
        val crystalItem = net.minecraft.registry.Registries.ITEM.get(
            net.minecraft.util.Identifier.of("mega_showdown", "mega_stone_crystal")
        )
        val crystalStack = ItemStack(crystalItem)
        crystalStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§l⚔ PokeMania Ranked ⚔"))
        crystalStack.set(DataComponentTypes.LORE, LoreComponent(listOf(
            Text.literal(sep()),
            Text.literal("§7Temporada §f$seasonId §8| §a${remaining.days}d ${remaining.hours}h ${remaining.minutes}m restantes"),
            Text.literal(sep())
        )))
        inv.setStack(4, crystalStack)

        // Fila 2 — cabeza en slot 4 (índice 12)
        val head = ItemStack(Items.PLAYER_HEAD)
        head.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b§l${player.name.string}"))
        head.set(DataComponentTypes.LORE, LoreComponent(listOf(
            Text.literal(sep()),
            Text.literal("§8▪ §7Rango: $rankColor§l$rankTitle"),
            Text.literal("§8▪ §7ELO: §b$elo  §8| §7W/L: §a$wins§7/§c$losses"),
            Text.literal("§8▪ §7Racha actual: §e$winStreak"),
            Text.literal(sep()),
            Text.literal("§7Click para ver perfil completo.")
        )))
        try {
            head.set(DataComponentTypes.PROFILE, ProfileComponent(
                Optional.of(player.gameProfile.name),
                Optional.of(player.gameProfile.id),
                player.gameProfile.properties
            ))
        } catch (e: Exception) {}
        inv.setStack(13, head) // fila 2, slot 5

        // Fila 3 — modos slot 3 (índice 20), stellar slot 7 (índice 24)
        for (i in 18..26) inv.setStack(i, gray())

        inv.setStack(20, makeItem(CobblemonItems.POKE_BALL, "§c§l⚔ Modos de Juego", listOf(
            sep(),
            "§7Elige formato y entra en cola.",
            "§8▪ §7Jugadores en cola: §e${queue.queue.size}",
            sep(),
            "§aClick para abrir."
        )))

        inv.setStack(24, makeItem(Items.BARRIER, "§b§l📜 Reglas del Ranked", listOf(
            sep(),
            "§7Consulta los Pokémon, movimientos,",
            "§7objetos prohibidos y cláusulas activas",
            "§7de cada formato.",
            sep(),
            "§aClick para abrir."
        )))

        // Fila 4 — misiones slot 3 (índice 29), trofeo slot 7 (índice 33)
        for (i in 27..35) inv.setStack(i, dark())

        val stackableBook = net.minecraft.registry.Registries.ITEM.get(
            net.minecraft.util.Identifier.of("handcrafted", "stackable_book")
        )
        inv.setStack(29, ItemStack(stackableBook).also { stack ->
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§l📖 Misiones"))
            stack.set(DataComponentTypes.LORE, LoreComponent(listOf(
                Text.literal(sep()),
                Text.literal("§8🔒 Próximamente"),
                Text.literal("§8Las misiones llegarán en"),
                Text.literal("§8una futura actualización."),
                Text.literal(sep())
            )))
        })

        val trophy = net.minecraft.registry.Registries.ITEM.get(
            net.minecraft.util.Identifier.of("pokeblocks", "pokemon_trophy")
        )
        inv.setStack(33, ItemStack(trophy).also { stack ->
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§l🏆 Clasificación"))
            stack.set(DataComponentTypes.LORE, LoreComponent(listOf(
                Text.literal(sep()),
                Text.literal("§7Top jugadores por formato."),
                Text.literal("§7Temporada: §f$seasonId"),
                Text.literal(sep()),
                Text.literal("§aClick para abrir.")
            )))
        })

        // Fila 5 — recompensas slot 4 (índice 39)
        val teraPouch = net.minecraft.registry.Registries.ITEM.get(
            net.minecraft.util.Identifier.of("mega_showdown", "tera_pouch_black")
        )
        inv.setStack(40, ItemStack(teraPouch).also { stack ->
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§d§l🎁 Recompensas"))
            stack.set(DataComponentTypes.LORE, LoreComponent(listOf(
                Text.literal(sep()),
                Text.literal("§7Premios de temporada"),
                Text.literal("§7según tu rango alcanzado."),
                Text.literal("§8▪ §7Rango actual: $rankColor$rankTitle"),
                Text.literal(sep()),
                Text.literal("§aClick para abrir.")
            )))
        })

        // Fila 6 — franja dorada
        for (i in 45..53) inv.setStack(i, gold())

        fillEmpty(inv, 54, setOf(4, 13, 20, 24, 29, 33, 40))

        player.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        when (slotIndex) {
                            13     -> { sp.closeHandledScreen(); openProfileMenu(sp) }
                            20     -> { sp.closeHandledScreen(); openModesMenu(sp) }
                            24     -> { sp.closeHandledScreen(); openRulesMenu(sp) }
                            33     -> { sp.closeHandledScreen(); openTopMenu(sp) }
                            40     -> { sp.closeHandledScreen(); openRewardsMenu(sp) }
                        }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§6⚔ PokeMania Ranked ⚔")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MENÚ DE MODOS — 5 filas
    //
    //  0  1  2  3  4  5  6  7  8   ← franja roja (4=título)
    //  9 10 11 12 13 14 15 16 17   ← modos activos: 10,12,14,16
    // 18 19 20 21 22 23 24 25 26   ← separador
    // 27 28 29 30 31 32 33 34 35   ← próximamente: 28,30,32,34
    // 36 37 38 39 40 41 42 43 44   ← back(40)
    // ─────────────────────────────────────────────────────────────────────────

    fun openModesMenu(player: ServerPlayerEntity) {
        val inv = SimpleInventory(45)
        val queue = CobblemonRanked.matchmakingQueue
        val dao = CobblemonRanked.rankDao
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val config = CobblemonRanked.config

        // Fila 1 — franja roja
        for (i in 0..8) inv.setStack(i, red())
        inv.setStack(4, makeItem(Items.IRON_SWORD, "§c§l⚔ Modos de Juego", listOf(
            sep(),
            "§7Elige formato y entra en cola.",
            "§7En cola: §e${queue.queue.size} jugadores",
            sep()
        )))

        // Fila 2 — 4 modos activos
        val activeFormats = listOf(
            "single_battle"        to 10,
            "doubles_battle"       to 12,
            "random_battle"        to 14,
            "double_random_battle" to 16
        )

        activeFormats.forEach { (formatKey, slot) ->
            val inQueue = queue.getPlayer(player.uuid, formatKey) != null
            val inQueueCount = queue.queue.values.count { it.formatName == formatKey }
            val color = formatColor(formatKey)
            val data = dao.getPlayerData(player.uuid, seasonId, formatKey)
            val elo = data?.elo ?: config.initialElo
            val rankTitle = data?.getRankTitle() ?: "Sin rango"
            val rankColor = getRankColor(rankTitle)
            val wins = data?.wins ?: 0
            val losses = data?.losses ?: 0

            inv.setStack(slot, ItemStack(formatBall(formatKey)).also { stack ->
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("$color§l${formatDisplayName(formatKey)}"))
                stack.set(DataComponentTypes.LORE, LoreComponent(listOf(
                    Text.literal(sep()),
                    Text.literal("§8▪ §7Tu ELO: §b$elo"),
                    Text.literal("§8▪ §7Tu rango: $rankColor$rankTitle"),
                    Text.literal("§8▪ §7W/L: §a$wins §7/ §c$losses"),
                    Text.literal(sep()),
                    Text.literal("§8▪ §7En cola: §f$inQueueCount jugadores"),
                    Text.literal(""),
                    if (inQueue) Text.literal("§c● En cola — Click para salir")
                    else Text.literal("§a● Disponible — Click para entrar")
                )))
            })
        }

        // Fila 3 — separador
        for (i in 18..26) inv.setStack(i, gray())

        // Fila 4 — próximamente
        val comingSoon = listOf("§8⚔ Ranked Draft", "§8⚔ Monotype", "§8⚔ Ubers", "§8⚔ Little Cup")
        listOf(28, 30, 32, 34).forEachIndexed { i, slot ->
            inv.setStack(slot, makeItem(Items.GRAY_DYE, comingSoon[i], listOf(
                sep(),
                "§8🔒 Próximamente",
                sep()
            )))
        }

        // Fila 5 — volver
        inv.setStack(40, backButton())
        fillEmpty(inv, 45, activeFormats.map { it.second }.toSet() + setOf(4, 28, 30, 32, 34, 40))

        player.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X5, syncId, playerInv, inv, 5) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        when {
                            slotIndex == 40 -> { sp.closeHandledScreen(); open(sp) }
                            slotIndex in activeFormats.map { it.second } -> {
                                val formatKey = activeFormats.find { it.second == slotIndex }?.first ?: return
                                sp.closeHandledScreen()
                                if (queue.queue.containsKey(sp.uuid)) {
                                    queue.removePlayer(sp.uuid)
                                    sp.sendMessage(Text.literal("§cHas salido de la cola."), false)
                                } else {
                                    queue.addPlayer(sp, formatKey)
                                }
                            }
                        }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§c⚔ Modos de Juego")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MENÚ PERFIL — 6 filas
    //
    //  0  1  2  3  4  5  6  7  8   ← franja azul (4=título)
    //  9 10 11 12 13 14 15 16 17   ← cabeza centrada (13)
    // 18 19 20 21 22 23 24 25 26   ← cards 4 formatos (19,21,23,25)
    // 27 28 29 30 31 32 33 34 35   ← stats globales (29,31,33)
    // 36 37 38 39 40 41 42 43 44   ← separador gris
    // 45 46 47 48 49 50 51 52 53   ← franja dorada + back(49)
    // ─────────────────────────────────────────────────────────────────────────

    fun openProfileMenu(player: ServerPlayerEntity) {
        val inv = SimpleInventory(54)
        val dao = CobblemonRanked.rankDao
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val config = CobblemonRanked.config

        // Fila 1 — franja azul completa
        for (i in 0..8) inv.setStack(i, blue())

        // Stats globales
        val totalWins   = config.allowedFormats.sumOf { dao.getPlayerData(player.uuid, seasonId, it)?.wins ?: 0 }
        val totalLosses = config.allowedFormats.sumOf { dao.getPlayerData(player.uuid, seasonId, it)?.losses ?: 0 }
        val bestStreak  = config.allowedFormats.maxOfOrNull { dao.getPlayerData(player.uuid, seasonId, it)?.bestWinStreak ?: 0 } ?: 0

        // Cabeza fila 2
        val head = ItemStack(Items.PLAYER_HEAD)
        head.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b§l${player.name.string}"))
        head.set(DataComponentTypes.LORE, LoreComponent(listOf(
            Text.literal(sep()),
            Text.literal("§8▪ §7Total victorias: §a$totalWins"),
            Text.literal("§8▪ §7Total derrotas: §c$totalLosses"),
            Text.literal("§8▪ §7Mejor racha: §e$bestStreak"),
            Text.literal(sep())
        )))
        try {
            head.set(DataComponentTypes.PROFILE, ProfileComponent(
                Optional.of(player.gameProfile.name),
                Optional.of(player.gameProfile.id),
                player.gameProfile.properties
            ))
        } catch (e: Exception) {}
        inv.setStack(13, head)

        // Fila 3 — cards por formato
        val formatSlots = listOf(
            "single_battle"        to 19,
            "doubles_battle"       to 21,
            "random_battle"        to 23,
            "double_random_battle" to 25
        )

        formatSlots.forEach { (formatKey, slot) ->
            val d = dao.getPlayerData(player.uuid, seasonId, formatKey)
            val elo = d?.elo ?: config.initialElo
            val rankTitle = d?.getRankTitle() ?: "Sin rango"
            val rc = getRankColor(rankTitle)
            val wins = d?.wins ?: 0
            val losses = d?.losses ?: 0
            val streak = d?.winStreak ?: 0
            val best = d?.bestWinStreak ?: 0
            val color = formatColor(formatKey)
            val position = try {
                val lb = dao.getLeaderboard(seasonId, formatKey, 0, Int.MAX_VALUE)
                val idx = lb.indexOfFirst { it.playerId == player.uuid }
                if (idx >= 0) "#${idx + 1}" else "—"
            } catch (e: Exception) { "—" }

            inv.setStack(slot, ItemStack(formatBall(formatKey)).also { stack ->
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("$color§l${formatDisplayName(formatKey)}"))
                stack.set(DataComponentTypes.LORE, LoreComponent(listOf(
                    Text.literal(sep()),
                    Text.literal("§8▪ §7ELO: §b$elo  §8| §7Pos: §e$position"),
                    Text.literal("§8▪ §7Rango: $rc$rankTitle"),
                    Text.literal(sep()),
                    Text.literal("§8▪ §7Victorias: §a$wins  §cDerrotas: $losses"),
                    Text.literal("§8▪ §7Racha actual: §e$streak  §6Mejor: $best"),
                    Text.literal(sep())
                )))
            })
        }

        // Fila 4 — stats globales
        val decidiumZ = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of("mega_showdown", "decidium_z"))
        val inciniumZ = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of("mega_showdown", "incinium_z"))
        val pikashuniumZ = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of("mega_showdown", "pikashunium_z"))

        inv.setStack(29, ItemStack(decidiumZ).also { stack ->
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§lVictorias totales"))
            stack.set(DataComponentTypes.LORE, LoreComponent(listOf(
                Text.literal(sep()), Text.literal("§f$totalWins §7victorias en total"), Text.literal(sep())
            )))
        })
        inv.setStack(31, ItemStack(inciniumZ).also { stack ->
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c§lDerrotas totales"))
            stack.set(DataComponentTypes.LORE, LoreComponent(listOf(
                Text.literal(sep()), Text.literal("§f$totalLosses §7derrotas en total"), Text.literal(sep())
            )))
        })
        inv.setStack(33, ItemStack(pikashuniumZ).also { stack ->
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§lMejor racha"))
            stack.set(DataComponentTypes.LORE, LoreComponent(listOf(
                Text.literal(sep()), Text.literal("§f$bestStreak §7victorias seguidas"), Text.literal(sep())
            )))
        })

        // Fila 5 — separador
        for (i in 36..44) inv.setStack(i, gray())

        // Fila 6 — franja dorada + back
        for (i in 45..53) inv.setStack(i, gold())
        inv.setStack(49, backButton())

        fillEmpty(inv, 54, formatSlots.map { it.second }.toSet() + setOf(4, 13, 29, 31, 33, 49))

        player.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        if (slotIndex == 49) { sp.closeHandledScreen(); open(sp) }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§b👤 Mi Perfil — T$seasonId")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MENÚ CLASIFICACIÓN — 6 filas
    // ─────────────────────────────────────────────────────────────────────────

    fun openTopMenu(player: ServerPlayerEntity) {
        val inv = SimpleInventory(54)
        val dao = CobblemonRanked.rankDao
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val remaining = CobblemonRanked.seasonManager.getRemainingTime()

        for (i in 0..8) inv.setStack(i, gold())
        inv.setStack(4, makeItem(Items.NETHER_STAR, "§e§l🏆 Clasificación — T$seasonId", listOf(
            sep(),
            "§7Tiempo restante: §a${remaining.days}d ${remaining.hours}h",
            sep()
        )))

        val formatSlots = listOf(
            "single_battle"        to 10,
            "doubles_battle"       to 14,
            "random_battle"        to 28,
            "double_random_battle" to 32
        )

        formatSlots.forEach { (formatKey, slot) ->
            val top = dao.getLeaderboard(seasonId, formatKey, 0, 5)
            val color = formatColor(formatKey)
            val lore = mutableListOf(sep())
            top.forEachIndexed { idx, d ->
                val medal = when (idx) { 0 -> "§6★ 1º"; 1 -> "§7✦ 2º"; 2 -> "§c✦ 3º"; else -> "§8  ${idx+1}º" }
                val rc = getRankColor(d.getRankTitle())
                lore.add("$medal §f${d.playerName}  §b${d.elo}  $rc${d.getRankTitle()}")
            }
            if (top.isEmpty()) lore.add("§8Sin datos aún.")
            lore.add(sep())

            inv.setStack(slot, ItemStack(formatBall(formatKey)).also { stack ->
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("$color§l${formatDisplayName(formatKey)}"))
                stack.set(DataComponentTypes.LORE, LoreComponent(lore.map { Text.literal(it) }))
            })
        }

        for (i in 18..26) inv.setStack(i, gray())
        for (i in 45..53) inv.setStack(i, gold())
        inv.setStack(49, backButton())
        fillEmpty(inv, 54, formatSlots.map { it.second }.toSet() + setOf(4, 49))

        player.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        if (slotIndex == 49) { sp.closeHandledScreen(); open(sp) }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§e🏆 Clasificación — T$seasonId")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MENÚ POKÉMON PROHIBIDOS — selector de modo
    // ─────────────────────────────────────────────────────────────────────────

    fun openBlacklistMenu(player: ServerPlayerEntity) {
        val inv = SimpleInventory(36)
        val config = CobblemonRanked.config

        for (i in 0..8) inv.setStack(i, red())
        inv.setStack(4, makeItem(Items.BARRIER, "§c§l🚫 Pokémon Prohibidos", listOf(
            sep(),
            "§7Selecciona un modo para ver",
            "§7sus Pokémon baneados.",
            "§8▪ §7Total baneados: §c${config.bannedPokemon.size}",
            sep()
        )))

        listOf(
            "single_battle"        to 11,
            "doubles_battle"       to 13,
            "random_battle"        to 15,
            "double_random_battle" to 17
        ).forEach { (formatKey, slot) ->
            val color = formatColor(formatKey)
            inv.setStack(slot, makeItem(formatBall(formatKey), "$color§l${formatDisplayName(formatKey)}", listOf(
                sep(),
                "§7Pokémon baneados: §c${config.bannedPokemon.size}",
                sep(),
                "§aClick para ver la lista."
            )))
        }

        inv.setStack(31, backButton())
        fillEmpty(inv, 36, setOf(4, 11, 13, 15, 17, 31))

        player.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X4, syncId, playerInv, inv, 4) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        when (slotIndex) {
                            31 -> { sp.closeHandledScreen(); open(sp) }
                            11 -> { sp.closeHandledScreen(); openBlacklistForMode(sp, "single_battle") }
                            13 -> { sp.closeHandledScreen(); openBlacklistForMode(sp, "doubles_battle") }
                            15 -> { sp.closeHandledScreen(); openBlacklistForMode(sp, "random_battle") }
                            17 -> { sp.closeHandledScreen(); openBlacklistForMode(sp, "double_random_battle") }
                        }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§c🚫 Pokémon Prohibidos")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MENÚ POKÉMON PROHIBIDOS — lista por modo
    // ─────────────────────────────────────────────────────────────────────────

    fun openBlacklistForMode(player: ServerPlayerEntity, formatKey: String) {
        val banned = CobblemonRanked.config.bannedPokemon
        val rows = maxOf(3, ((banned.size + 8) / 9) + 1).coerceAtMost(6)
        val totalSlots = rows * 9
        val inv = SimpleInventory(totalSlots)

        for (i in 0..8) inv.setStack(i, red())
        inv.setStack(4, makeItem(Items.BARRIER, "§c🚫 Prohibidos — §f${formatDisplayName(formatKey)}", listOf(
            sep(), "§7Total: §c${banned.size}", sep()
        )))

        banned.forEachIndexed { idx, pokemonName ->
            val slot = 9 + idx
            if (slot >= totalSlots - 9) return@forEachIndexed
            val modelItem = net.minecraft.registry.Registries.ITEM.get(
                net.minecraft.util.Identifier.of("cobblemon", "pokemon_model")
            )
            inv.setStack(slot, ItemStack(modelItem).also { stack ->
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c$pokemonName"))
                stack.set(DataComponentTypes.LORE, LoreComponent(listOf(
                    Text.literal("§8Prohibido en §c${formatDisplayName(formatKey)}")
                )))
                try {
                    val nbt = net.minecraft.nbt.NbtCompound()
                    nbt.putString("pokemon", pokemonName.lowercase())
                    stack.set(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(nbt))
                } catch (e: Exception) {}
            })
        }

        if (banned.isEmpty()) {
            inv.setStack(13, makeItem(Items.LIME_STAINED_GLASS_PANE, "§aSin Pokémon prohibidos", listOf(
                "§7No hay Pokémon baneados en este modo."
            )))
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

        player.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(screenType, syncId, playerInv, inv, rows) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        if (slotIndex == backSlot) { sp.closeHandledScreen(); openBlacklistMenu(sp) }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§c🚫 Prohibidos — ${formatDisplayName(formatKey)}")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MENÚ RECOMPENSAS
    // ─────────────────────────────────────────────────────────────────────────

    fun openRewardsMenu(player: ServerPlayerEntity) {
        val inv = SimpleInventory(36)
        val dao = CobblemonRanked.rankDao
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val config = CobblemonRanked.config
        val defaultFormat = config.allowedFormats.firstOrNull() ?: "single_battle"
        val data = dao.getPlayerData(player.uuid, seasonId, defaultFormat)
        val rankTitle = data?.getRankTitle() ?: "Sin rango"
        val rankColor = getRankColor(rankTitle)

        for (i in 0..8) inv.setStack(i, purp())
        inv.setStack(4, makeItem(CobblemonItems.RARE_CANDY, "§d§l🎁 Recompensas — T$seasonId", listOf(
            sep(), "§7Rango actual: $rankColor$rankTitle", sep()
        )))

        inv.setStack(13, makeItem(CobblemonItems.RARE_CANDY, "§dRecompensas por rango", listOf(
            sep(),
            "§7Las recompensas se otorgan",
            "§7al final de la temporada",
            "§7según tu rango más alto.",
            sep(),
            "§7Rango actual: $rankColor§l$rankTitle",
            sep(),
            "§8Próximamente: reclamación manual."
        )))

        inv.setStack(31, backButton())
        fillEmpty(inv, 36, setOf(4, 13, 31))

        player.openHandledScreen(SimpleNamedScreenHandlerFactory(
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
            Text.literal("§d🎁 Recompensas — T$seasonId")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MENÚ REGLAS — selector de formato
    // ─────────────────────────────────────────────────────────────────────────

    fun openRulesMenu(player: ServerPlayerEntity) {
        val inv = SimpleInventory(36)

        for (i in 0..8) inv.setStack(i, ItemStack(Items.CYAN_STAINED_GLASS_PANE).also {
            it.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "))
        })
        inv.setStack(4, makeItem(Items.BARRIER, "§b§l📜 Reglas del Ranked", listOf(
            sep(),
            "§7Selecciona un formato para ver",
            "§7sus reglas activas.",
            sep()
        )))

        val formatSlots = listOf(
            "single_battle"        to 11,
            "doubles_battle"       to 13,
            "random_battle"        to 15,
            "double_random_battle" to 17
        )
        formatSlots.forEach { (formatKey, slot) ->
            val color = formatColor(formatKey)
            val rules = CobblemonRanked.config.formatRules[formatKey]
            inv.setStack(slot, makeItem(formatBall(formatKey), "$color§l${formatDisplayName(formatKey)}", listOf(
                sep(),
                "§8▪ §7Pokémon baneados: §c${rules?.bannedPokemon?.size ?: 0}",
                "§8▪ §7Movimientos baneados: §c${rules?.bannedMoves?.size ?: 0}",
                "§8▪ §7Items baneados: §c${rules?.bannedItems?.size ?: 0}",
                "§8▪ §7Cláusulas activas: §a${rules?.clauses?.size ?: 0}",
                sep(),
                "§aClick para ver las reglas."
            )))
        }

        inv.setStack(31, backButton())
        fillEmpty(inv, 36, formatSlots.map { it.second }.toSet() + setOf(4, 31))

        player.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X4, syncId, playerInv, inv, 4) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        when (slotIndex) {
                            31 -> { sp.closeHandledScreen(); open(sp) }
                            11 -> { sp.closeHandledScreen(); openRulesForFormat(sp, "single_battle") }
                            13 -> { sp.closeHandledScreen(); openRulesForFormat(sp, "doubles_battle") }
                            15 -> { sp.closeHandledScreen(); openRulesForFormat(sp, "random_battle") }
                            17 -> { sp.closeHandledScreen(); openRulesForFormat(sp, "double_random_battle") }
                        }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§b📜 Reglas del Ranked")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MENÚ REGLAS — 4 categorías por formato
    // ─────────────────────────────────────────────────────────────────────────

    fun openRulesForFormat(player: ServerPlayerEntity, formatKey: String) {
        val inv = SimpleInventory(36)
        val color = formatColor(formatKey)
        val rules = CobblemonRanked.config.formatRules[formatKey]

        for (i in 0..8) inv.setStack(i, ItemStack(Items.CYAN_STAINED_GLASS_PANE).also {
            it.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "))
        })
        inv.setStack(4, makeItem(formatBall(formatKey), "$color§l${formatDisplayName(formatKey)}", listOf(
            sep(), "§7Selecciona una categoría.", sep()
        )))

        inv.setStack(11, makeItem(Items.FEATHER, "§e§l⚡ Movimientos Prohibidos", listOf(
            sep(), "§8▪ §7Total: §c${rules?.bannedMoves?.size ?: 0}", sep(), "§aClick para ver."
        )))
        inv.setStack(13, makeItem(CobblemonItems.POKE_BALL, "§c§l🚫 Pokémon Prohibidos", listOf(
            sep(), "§8▪ §7Total: §c${rules?.bannedPokemon?.size ?: 0}", sep(), "§aClick para ver."
        )))
        inv.setStack(15, makeItem(Items.CHEST, "§6§l🎒 Items Prohibidos", listOf(
            sep(), "§8▪ §7Total: §c${rules?.bannedItems?.size ?: 0}", sep(), "§aClick para ver."
        )))
        inv.setStack(20, makeItem(Items.DRAGON_BREATH, "§d§l✦ Habilidades Prohibidas", listOf(
            sep(), "§8▪ §7Total: §c${rules?.bannedAbilities?.size ?: 0}", sep(), "§aClick para ver."
        )))
        inv.setStack(22, makeItem(Items.BOOK, "§a§l📋 Cláusulas Activas", listOf(
            sep(), "§8▪ §7Total: §a${rules?.clauses?.size ?: 0}", sep(), "§aClick para ver."
        )))

        inv.setStack(31, backButton())
        fillEmpty(inv, 36, setOf(4, 11, 13, 15, 20, 22, 31))

        player.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X4, syncId, playerInv, inv, 4) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        when (slotIndex) {
                            31 -> { sp.closeHandledScreen(); openRulesMenu(sp) }
                            11 -> { sp.closeHandledScreen(); openRuleCategory(sp, formatKey, "moves") }
                            13 -> { sp.closeHandledScreen(); openRuleCategory(sp, formatKey, "pokemon") }
                            15 -> { sp.closeHandledScreen(); openRuleCategory(sp, formatKey, "items") }
                            20 -> { sp.closeHandledScreen(); openRuleCategory(sp, formatKey, "abilities") }
                            22 -> { sp.closeHandledScreen(); openRuleCategory(sp, formatKey, "clauses") }
                        }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§b📜 ${formatDisplayName(formatKey)}")
        ))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MENÚ REGLAS — lista de categoría
    // ─────────────────────────────────────────────────────────────────────────

    fun openRuleCategory(player: ServerPlayerEntity, formatKey: String, category: String, page: Int = 0) {
        val rules = CobblemonRanked.config.formatRules[formatKey]
        val color = formatColor(formatKey)

        val entries: List<String>
        val categoryName: String
        val categoryColor: String
        val entryItem: net.minecraft.item.Item
        when (category) {
            "moves"     -> { entries = rules?.bannedMoves ?: emptyList();     categoryName = "Movimientos Prohibidos";  categoryColor = "§e"; entryItem = Items.FEATHER }
            "pokemon"   -> { entries = rules?.bannedPokemon ?: emptyList();   categoryName = "Pokémon Prohibidos";      categoryColor = "§c"; entryItem = Items.BARRIER }
            "items"     -> { entries = rules?.bannedItems ?: emptyList();     categoryName = "Items Prohibidos";        categoryColor = "§6"; entryItem = Items.CHEST }
            "abilities" -> { entries = rules?.bannedAbilities ?: emptyList(); categoryName = "Habilidades Prohibidas";  categoryColor = "§d"; entryItem = Items.DRAGON_BREATH }
            else        -> { entries = rules?.clauses ?: emptyList();         categoryName = "Cláusulas Activas";       categoryColor = "§a"; entryItem = Items.BOOK }
        }

        // Pokemon siempre 6 filas con paginación; moves/clauses/items tamaño dinámico con paginación
        val isPokemon = category == "pokemon"
        val contentRows = if (isPokemon) 4 else maxOf(1, (entries.size + 8) / 9).coerceAtMost(4)
        val contentSlots = contentRows * 9
        val entriesPerPage = contentSlots
        val totalPages = maxOf(1, (entries.size + entriesPerPage - 1) / entriesPerPage)
        val currentPage = page.coerceIn(0, totalPages - 1)
        val pageEntries = entries.drop(currentPage * entriesPerPage).take(entriesPerPage)

        val totalRows = contentRows + 2 // cabecera + contenido + navegación
        val totalSlots = totalRows * 9
        val inv = SimpleInventory(totalSlots)
        val contentStart = 9
        val navStart = contentStart + contentSlots

        // Fila 1 — cabecera cyan
        for (i in 0..8) inv.setStack(i, ItemStack(Items.CYAN_STAINED_GLASS_PANE).also {
            it.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "))
        })
        inv.setStack(4, makeItem(entryItem, "$categoryColor§l$categoryName", listOf(
            sep(),
            "§7Formato: $color${formatDisplayName(formatKey)}",
            "§8▪ §7Total: §f${entries.size}  §8|  §7Página: §f${currentPage + 1}§7/§f$totalPages",
            sep()
        )))

        // Contenido
        if (entries.isEmpty()) {
            inv.setStack(contentStart + contentSlots / 2 - 1, makeItem(Items.LIME_STAINED_GLASS_PANE, "§aSin restricciones", listOf(
                sep(), "§7No hay nada en esta categoría.", sep()
            )))
        } else {
            pageEntries.forEachIndexed { idx, entry ->
                val slot = contentStart + idx
                val icon = when (category) {
                    "pokemon" -> {
                        try {
                            // Intentar múltiples variantes del nombre para Cobblemon
                            val baseName = entry.substringBefore("-").lowercase()
                            val fullName = entry.lowercase()
                            val noHyphen = entry.lowercase().replace("-", "")
                            val withUnderscore = entry.lowercase().replace("-", "_")
                            val species =
                                com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(fullName)
                                    ?: com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(noHyphen)
                                    ?: com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(withUnderscore)
                                    ?: com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(baseName)
                            if (species != null) {
                                val poke = com.cobblemon.mod.common.pokemon.Pokemon()
                                poke.species = species
                                poke.level = 50
                                // Intentar asignar forma si el nombre tiene sufijo (ej. -Mega, -Origin)
                                if (entry.contains("-")) {
                                    val formName = entry.substringAfter("-").lowercase()
                                    try {
                                        val form = species.forms.find {
                                            it.name.lowercase().contains(formName) ||
                                                    it.formOnlyShowdownId().lowercase().contains(formName)
                                        }
                                        if (form != null) poke.form = form
                                    } catch (e: Exception) {}
                                }
                                val tint = org.joml.Vector4f(1.0f, 1.0f, 1.0f, 1.0f)
                                val stack = com.cobblemon.mod.common.item.PokemonItem.from(poke, 1, tint)
                                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c§l$entry"))
                                stack.set(DataComponentTypes.LORE, LoreComponent(listOf(
                                    Text.literal("§8Prohibido en §f${formatDisplayName(formatKey)}")
                                )))
                                stack
                            } else makeItem(Items.BARRIER, "§c$entry", listOf("§8Prohibido en §f${formatDisplayName(formatKey)}"))
                        } catch (e: Exception) { makeItem(Items.BARRIER, "§c$entry", listOf("§8Prohibido en §f${formatDisplayName(formatKey)}")) }
                    }
                    "moves" -> {
                        // Intentar tmcraft:tm_movename, si no existe usar libro como fallback
                        val tmKey = "tm_${entry.lowercase().replace(" ", "_").replace("-", "_")}"
                        val tmItem = net.minecraft.registry.Registries.ITEM.getOrEmpty(
                            net.minecraft.util.Identifier.of("tmcraft", tmKey)
                        ).orElse(null)
                            ?: net.minecraft.registry.Registries.ITEM.getOrEmpty(
                                net.minecraft.util.Identifier.of("cobblemon", tmKey)
                            ).orElse(Items.WRITABLE_BOOK)
                        val stack = ItemStack(tmItem)
                        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§l$entry"))
                        stack.set(DataComponentTypes.LORE, LoreComponent(listOf(
                            Text.literal(sep()),
                            Text.literal("§8Prohibido en §f${formatDisplayName(formatKey)}")
                        )))
                        stack
                    }
                    "clauses" -> {
                        val parts = entry.split("|", limit = 2)
                        val clauseName = parts[0].trim()
                        val clauseDesc = parts.getOrNull(1)?.trim() ?: ""
                        makeItem(Items.PAPER, "§a§l✔ $clauseName", listOf(sep(), "§7$clauseDesc", sep()))
                    }
                    "abilities" -> makeItem(Items.DRAGON_BREATH, "§d$entry", listOf(
                        "§8Prohibida en §f${formatDisplayName(formatKey)}"
                    ))
                    else -> makeItem(Items.CHEST, "§6$entry", listOf("§8Prohibido en §f${formatDisplayName(formatKey)}"))
                }
                inv.setStack(slot, icon)
            }
        }

        // Fila navegación
        for (i in navStart until navStart + 9) inv.setStack(i, ItemStack(Items.CYAN_STAINED_GLASS_PANE).also {
            it.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "))
        })
        if (currentPage > 0) {
            inv.setStack(navStart, ItemStack(Items.ARROW).also {
                it.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e« Página anterior"))
                it.set(DataComponentTypes.LORE, LoreComponent(listOf(Text.literal("§8Página §f${currentPage}§8/§f$totalPages"))))
            })
        }
        inv.setStack(navStart + 4, makeItem(Items.PAPER, "§fPágina §e${currentPage + 1} §fde §e$totalPages", listOf(
            sep(), "§7Total: §f${entries.size} entradas", sep()
        )))
        if (currentPage < totalPages - 1) {
            inv.setStack(navStart + 8, ItemStack(Items.ARROW).also {
                it.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§ePágina siguiente »"))
                it.set(DataComponentTypes.LORE, LoreComponent(listOf(Text.literal("§8Página §f${currentPage + 2}§8/§f$totalPages"))))
            })
        }
        inv.setStack(navStart + 2, backButton())

        fillEmpty(inv, totalSlots, (contentStart until contentStart + pageEntries.size).toSet() +
                setOf(4, navStart, navStart + 2, navStart + 4, navStart + 8))

        val screenType = when (totalRows) {
            3 -> ScreenHandlerType.GENERIC_9X3
            4 -> ScreenHandlerType.GENERIC_9X4
            5 -> ScreenHandlerType.GENERIC_9X5
            else -> ScreenHandlerType.GENERIC_9X6
        }

        player.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, playerInv, _ ->
                object : GenericContainerScreenHandler(screenType, syncId, playerInv, inv, totalRows) {
                    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, p: PlayerEntity) {
                        if (actionType != SlotActionType.PICKUP) return
                        val sp = p as? ServerPlayerEntity ?: return
                        when (slotIndex) {
                            navStart + 2 -> { sp.closeHandledScreen(); openRulesForFormat(sp, formatKey) }
                            navStart     -> if (currentPage > 0) { sp.closeHandledScreen(); openRuleCategory(sp, formatKey, category, currentPage - 1) }
                            navStart + 8 -> if (currentPage < totalPages - 1) { sp.closeHandledScreen(); openRuleCategory(sp, formatKey, category, currentPage + 1) }
                        }
                    }
                    override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                }
            },
            Text.literal("§b📜 $categoryName §8(${currentPage + 1}/$totalPages)")
        ))
    }
}
