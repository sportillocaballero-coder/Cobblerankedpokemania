package cn.kurt6.cobblemon_ranked.battle

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object TeamPreviewMenu {

    // ─────────────────────────────────────────────────────────────────────────
    // Estado de las sesiones de preview activas
    // ─────────────────────────────────────────────────────────────────────────

    data class PreviewSession(
        val player1: ServerPlayerEntity,
        val player2: ServerPlayerEntity,
        val team1Uuids: List<UUID>,
        val team2Uuids: List<UUID>,
        val formatName: String,
        var p1LeadIndex: Int = 0,       // índice en team1Uuids del líder elegido por p1
        var p2LeadIndex: Int = 0,
        var p1Confirmed: Boolean = false,
        var p2Confirmed: Boolean = false,
        var timeoutTask: ScheduledFuture<*>? = null
    )

    private val sessions = ConcurrentHashMap<UUID, PreviewSession>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val TIMEOUT_SECONDS = 60L

    // ─────────────────────────────────────────────────────────────────────────
    // Abrir el menú para ambos jugadores
    // ─────────────────────────────────────────────────────────────────────────

    fun openForBoth(
        player1: ServerPlayerEntity,
        team1Uuids: List<UUID>,
        player2: ServerPlayerEntity,
        team2Uuids: List<UUID>,
        formatName: String,
        onBothConfirmed: (leader1Uuid: UUID, leader2Uuid: UUID) -> Unit
    ) {
        val session = PreviewSession(player1, player2, team1Uuids, team2Uuids, formatName)
        sessions[player1.uuid] = session
        sessions[player2.uuid] = session

        // Abrir menú para cada jugador
        player1.server.execute {
            openForPlayer(player1, session) { onBothConfirmed(team1Uuids[session.p1LeadIndex], team2Uuids[session.p2LeadIndex]) }
            openForPlayer(player2, session) { onBothConfirmed(team1Uuids[session.p1LeadIndex], team2Uuids[session.p2LeadIndex]) }
        }

        // Timer de timeout
        session.timeoutTask = scheduler.schedule({
            val s = sessions[player1.uuid] ?: return@schedule
            player1.server.execute {
                sessions.remove(player1.uuid)
                sessions.remove(player2.uuid)
                try { player1.closeHandledScreen() } catch (e: Exception) {}
                try { player2.closeHandledScreen() } catch (e: Exception) {}
                onBothConfirmed(team1Uuids[s.p1LeadIndex], team2Uuids[s.p2LeadIndex])
            }
        }, TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Construye y abre el inventario para un jugador
    // ─────────────────────────────────────────────────────────────────────────

    private fun openForPlayer(
        player: ServerPlayerEntity,
        session: PreviewSession,
        onBothConfirmed: () -> Unit
    ) {
        val isP1 = player.uuid == session.player1.uuid
        val myUuids   = if (isP1) session.team1Uuids else session.team2Uuids
        val oppUuids  = if (isP1) session.team2Uuids else session.team1Uuids
        val myParty   = Cobblemon.storage.getParty(player)
        val oppParty  = Cobblemon.storage.getParty(if (isP1) session.player2 else session.player1)
        val opponent  = if (isP1) session.player2 else session.player1
        val myLead    = if (isP1) session.p1LeadIndex else session.p2LeadIndex

        // 6 filas:
        // Fila 1 (0-8):   separador superior
        // Fila 2 (9-17):  TU equipo (slots 10-15 para 6 pokes)
        // Fila 3 (18-26): separador rival
        // Fila 4 (27-35): equipo RIVAL (slots 28-33 para 6 pokes)
        // Fila 5 (36-44): separador
        // Fila 6 (45-53): botón confirmar (slot 49)

        val inv = SimpleInventory(54)

        // — Separadores de color —
        val myColor  = ItemStack(Items.LIME_STAINED_GLASS_PANE).also {
            it.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a Tu equipo"))
        }
        val oppColor = ItemStack(Items.RED_STAINED_GLASS_PANE).also {
            it.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c Equipo rival — ${opponent.name.string}"))
        }
        val filler = ItemStack(Items.BLACK_STAINED_GLASS_PANE).also {
            it.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "))
        }

        for (i in 0..8)  inv.setStack(i, myColor.copy())
        for (i in 18..26) inv.setStack(i, oppColor.copy())
        for (i in 36..44) inv.setStack(i, filler.copy())
        for (i in 45..53) inv.setStack(i, filler.copy())

        // — Tu equipo (fila 2, slots 10-15) —
        val mySlots = listOf(10, 11, 12, 13, 14, 15)
        myUuids.forEachIndexed { idx, uuid ->
            val slot = mySlots.getOrNull(idx) ?: return@forEachIndexed
            val pokemon = myParty.find { it?.uuid == uuid } ?: return@forEachIndexed
            val isLead = idx == myLead
            inv.setStack(slot, makePokemonItem(pokemon, isLead, isOwn = true))
        }

        // — Equipo rival (fila 4, slots 28-33) —
        val oppSlots = listOf(28, 29, 30, 31, 32, 33)
        oppUuids.forEachIndexed { idx, uuid ->
            val slot = oppSlots.getOrNull(idx) ?: return@forEachIndexed
            val pokemon = oppParty.find { it?.uuid == uuid } ?: return@forEachIndexed
            inv.setStack(slot, makePokemonItem(pokemon, isLead = false, isOwn = false))
        }

        // — Botón confirmar (slot 49) —
        val confirmed = if (isP1) session.p1Confirmed else session.p2Confirmed
        inv.setStack(49, makeConfirmButton(confirmed))

        // Filler restante
        for (i in 0 until 54) {
            if (inv.getStack(i).isEmpty) inv.setStack(i, filler.copy())
        }

        val title = if (isP1) "§6⚔ Preview — §aTú vs §c${opponent.name.string}"
        else       "§6⚔ Preview — §aTú vs §c${opponent.name.string}"

        val myLeadSlots  = mySlots
        val confirmSlot  = 49

        player.openHandledScreen(
            SimpleNamedScreenHandlerFactory(
                { syncId, playerInv, _ ->
                    object : GenericContainerScreenHandler(
                        ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6
                    ) {
                        override fun onSlotClick(
                            slotIndex: Int, button: Int,
                            actionType: SlotActionType,
                            p: PlayerEntity
                        ) {
                            if (actionType != SlotActionType.PICKUP) return
                            val sp = p as? ServerPlayerEntity ?: return
                            val sess = sessions[sp.uuid] ?: return
                            val isPlayer1 = sp.uuid == sess.player1.uuid
                            val alreadyConfirmed = if (isPlayer1) sess.p1Confirmed else sess.p2Confirmed
                            if (alreadyConfirmed) return

                            when {
                                // Click en tu propio Pokémon → seleccionar como líder
                                slotIndex in myLeadSlots -> {
                                    val idx = myLeadSlots.indexOf(slotIndex)
                                    val uuids = if (isPlayer1) sess.team1Uuids else sess.team2Uuids
                                    if (idx >= uuids.size) return

                                    if (isPlayer1) sess.p1LeadIndex = idx
                                    else sess.p2LeadIndex = idx

                                    // Refrescar menú
                                    sp.closeHandledScreen()
                                    sp.server.execute { openForPlayer(sp, sess, onBothConfirmed) }
                                }

                                // Click en confirmar
                                slotIndex == confirmSlot -> {
                                    if (isPlayer1) sess.p1Confirmed = true
                                    else sess.p2Confirmed = true

                                    sp.closeHandledScreen()
                                    sp.sendMessage(Text.literal("§a✔ Equipo confirmado. Esperando al rival..."), false)

                                    // Si ambos confirmaron → iniciar
                                    if (sess.p1Confirmed && sess.p2Confirmed) {
                                        sess.timeoutTask?.cancel(false)
                                        sessions.remove(sess.player1.uuid)
                                        sessions.remove(sess.player2.uuid)
                                        sp.server.execute { onBothConfirmed() }
                                    }
                                }
                            }
                        }
                        override fun quickMove(p: PlayerEntity, index: Int) = ItemStack.EMPTY
                        override fun canInsertIntoSlot(stack: ItemStack, slot: net.minecraft.screen.slot.Slot) = false
                    }
                },
                Text.literal(title)
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Items
    // ─────────────────────────────────────────────────────────────────────────

    private fun makePokemonItem(pokemon: Pokemon, isLead: Boolean, isOwn: Boolean): ItemStack {
        val tint = org.joml.Vector4f(1.0f, 1.0f, 1.0f, 1.0f)
        val stack = try {
            com.cobblemon.mod.common.item.PokemonItem.from(pokemon, 1, tint)
        } catch (e: Exception) {
            ItemStack(Registries.ITEM.get(Identifier.of("cobblemon", "poke_ball")))
        }

        val leadPrefix = if (isLead) "§e★ " else ""
        val nameColor  = if (isOwn) "§a" else "§c"
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("$leadPrefix$nameColor${pokemon.species.name}"))

        val lore = if (isOwn) {
            mutableListOf(
                "§8▪ §7Nivel: §f${pokemon.level}",
                "§8▪ §7Habilidad: §b${pokemon.ability.name}",
                "§8▪ §7Tipo: §f${pokemon.species.primaryType.name}",
                "",
                "§7Movimientos:",
                *pokemon.moveSet.getMoves().map { "§8- §f${it.name}" }.toTypedArray(),
                "",
                if (isLead) "§e★ Líder seleccionado" else "§7Click para elegir como líder"
            )
        } else {
            listOf(
                "§8▪ §7Nivel: §f${pokemon.level}",
                "§8▪ §7Tipo: §f${pokemon.species.primaryType.name}",
                "",
                "§8§o(Información del rival)"
            )
        }

        stack.set(DataComponentTypes.LORE, LoreComponent(lore.map { Text.literal(it) }))
        return stack
    }

    private fun makeConfirmButton(alreadyConfirmed: Boolean): ItemStack {
        return if (alreadyConfirmed) {
            ItemStack(Items.LIME_STAINED_GLASS_PANE).also {
                it.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a✔ Confirmado — esperando rival..."))
            }
        } else {
            ItemStack(Items.EMERALD_BLOCK).also {
                it.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a✔ Confirmar equipo"))
                it.set(DataComponentTypes.LORE, LoreComponent(listOf(
                    Text.literal("§7Tiempo restante: §e${TIMEOUT_SECONDS}s"),
                    Text.literal("§7Confirma tu equipo de Random Battle."),
                    Text.literal("§7La batalla empezará cuando ambos"),
                    Text.literal("§7confirmen o se acabe el tiempo.")
                )))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Limpieza si alguien se desconecta
    // ─────────────────────────────────────────────────────────────────────────

    fun handleDisconnect(player: ServerPlayerEntity) {
        val session = sessions.remove(player.uuid) ?: return
        session.timeoutTask?.cancel(false)
        sessions.remove(session.player1.uuid)
        sessions.remove(session.player2.uuid)
        try { session.player1.closeHandledScreen() } catch (e: Exception) {}
        try { session.player2.closeHandledScreen() } catch (e: Exception) {}
    }

    fun isInPreview(uuid: UUID) = sessions.containsKey(uuid)

    fun shutdown() {
        sessions.clear()
        try { scheduler.shutdownNow() } catch (e: Exception) {}
    }
}
