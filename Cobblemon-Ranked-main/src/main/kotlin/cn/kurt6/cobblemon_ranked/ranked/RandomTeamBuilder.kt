package cn.kurt6.cobblemon_ranked.random

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.IVs
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.server.network.ServerPlayerEntity
import org.slf4j.LoggerFactory
import java.util.UUID

object RandomTeamBuilder {

    private val logger = LoggerFactory.getLogger(RandomTeamBuilder::class.java)

    // Tamaño del equipo según formato
    private fun teamSize(formatName: String) = if (formatName == "double_random_battle") 6 else 6

    // ─────────────────────────────────────────────────────────────────────────
    // Genera el equipo Showdown y lo pone en el party del jugador
    // Devuelve los UUIDs del equipo generado
    // ─────────────────────────────────────────────────────────────────────────

    fun buildAndAssign(player: ServerPlayerEntity, formatName: String): List<UUID> {
        if (!ShowdownDataLoader.isLoaded) {
            logger.warn("[RandomBattle] ShowdownDataLoader no está listo, usando fallback básico")
            return buildFallback(player, formatName)
        }

        val party = Cobblemon.storage.getParty(player)
        val size = teamSize(formatName)

        // Elegir N Pokémon del pool de Showdown
        val pool = ShowdownDataLoader.randomPokemon(size * 3) // toma más para filtrar los no disponibles en Cobblemon
        val selected = mutableListOf<Pokemon>()

        for ((name, entry) in pool) {
            if (selected.size >= size) break
            val pokemon = buildPokemon(name, entry) ?: continue
            selected.add(pokemon)
        }

        // Si no hay suficientes del pool de Showdown, completar con fallback
        if (selected.size < size) {
            val missing = size - selected.size
            logger.warn("[RandomBattle] Solo ${selected.size}/$size Pokémon del pool Showdown, completando con fallback")
            selected.addAll(buildFallbackPokemon(missing))
        }

        // Reemplazar party
        val toRemove = party.mapNotNull { it }
        toRemove.forEach { party.remove(it) }

        // Añadir al party y LUEGO asignar items (necesita estar en storage)
        selected.forEach { pokemon ->
            party.add(pokemon)
        }

        // Asignar items después de que estén en el party
        for ((name, entry) in pool) {
            val role = entry.randomRole()
            if (role.items.isNotEmpty()) {
                val inParty = party.find { it?.species?.name?.lowercase() == name.lowercase() }
                if (inParty != null && inParty.heldItem().isEmpty) {
                    trySetHeldItem(inParty, role.items.random())
                }
            }
        }

        logger.info("[RandomBattle] Equipo asignado a ${player.name.string}: ${selected.joinToString { it.species.name }}")

        return party.mapNotNull { it?.uuid }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Construye un Pokémon individual desde un ShowdownEntry
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildPokemon(showdownName: String, entry: ShowdownEntry): Pokemon? {
        // Buscar la especie en Cobblemon (nombre normalizado)
        val species = findSpecies(showdownName) ?: return null

        val role = entry.randomRole()
        val pokemon = Pokemon()
        pokemon.species = species
        pokemon.level = entry.level.coerceIn(1, 100)

        // IVs 31 en todo (estándar Showdown randbats)
        pokemon.setIV(Stats.HP, 31)
        pokemon.setIV(Stats.ATTACK, 31)
        pokemon.setIV(Stats.DEFENCE, 31)
        pokemon.setIV(Stats.SPECIAL_ATTACK, 31)
        pokemon.setIV(Stats.SPECIAL_DEFENCE, 31)
        pokemon.setIV(Stats.SPEED, 31)

        // EVs 84 en cada stat (estándar Showdown randbats: 508/6 ≈ 84)
        pokemon.setEV(Stats.HP, 84)
        pokemon.setEV(Stats.ATTACK, 84)
        pokemon.setEV(Stats.DEFENCE, 84)
        pokemon.setEV(Stats.SPECIAL_ATTACK, 84)
        pokemon.setEV(Stats.SPECIAL_DEFENCE, 84)
        pokemon.setEV(Stats.SPEED, 84)

        // Movimientos: elegir 4 al azar del movepool del rol
        val moves = pickMoves(role.movepool, 4)
        if (moves.isNotEmpty()) {
            pokemon.moveSet.clear()
            moves.forEach { moveName ->
                val template = findMove(moveName)
                if (template != null) {
                    pokemon.moveSet.add(template.create())
                }
            }
        }

        // Si el moveset quedó vacío (moves no encontrados), inicializar con default
        if (pokemon.moveSet.getMoves().isEmpty()) {
            pokemon.initialize()
        }

        // Habilidad: elegir del pool de habilidades viables
        if (role.abilities.isNotEmpty()) {
            val abilityName = role.abilities.random()
            trySetAbility(pokemon, abilityName)
        }

        // Item: elegir del pool de items del rol
        if (role.items.isNotEmpty()) {
            val itemName = role.items.random()
            trySetHeldItem(pokemon, itemName)
        }

        return pokemon
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Busca la especie en Cobblemon por nombre de Showdown
    // ─────────────────────────────────────────────────────────────────────────

    private fun findSpecies(showdownName: String): com.cobblemon.mod.common.pokemon.Species? {
        // Showdown usa nombres limpios: "Pikachu", "Garchomp", "Iron Valiant"
        // Cobblemon los tiene por nombre en inglés también
        val normalized = showdownName.lowercase().replace(" ", "").replace("-", "")

        return PokemonSpecies.implemented.firstOrNull { species ->
            val speciesName = species.name.lowercase().replace(" ", "").replace("-", "")
            speciesName == normalized
        } ?: PokemonSpecies.implemented.firstOrNull { species ->
            // Segundo intento: startsWith para formas
            species.name.lowercase().replace(" ", "").replace("-", "").startsWith(normalized)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Elige hasta N movimientos al azar del movepool
    // ─────────────────────────────────────────────────────────────────────────

    private fun pickMoves(movepool: List<String>, count: Int): List<String> {
        return movepool.shuffled().take(count)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Busca el MoveTemplate en Cobblemon por nombre de Showdown
    // ─────────────────────────────────────────────────────────────────────────

    private fun findMove(showdownName: String): MoveTemplate? {
        // Showdown: "Volt Switch", "Thunderbolt", "Close Combat"
        // Cobblemon move IDs: "voltswitch", "thunderbolt", "closecombat"
        val id = showdownName.lowercase().replace(" ", "").replace("-", "")
        return try {
            Moves.getByName(id)
        } catch (e: Exception) {
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Intenta asignar la habilidad
    // ─────────────────────────────────────────────────────────────────────────

    private fun trySetAbility(pokemon: Pokemon, abilityName: String) {
        try {
            val normalized = abilityName.lowercase().replace(" ", "").replace("-", "")
            val abilities = pokemon.species.abilities
            val match = abilities.firstOrNull {
                it.template.name.lowercase().replace(" ", "").replace("-", "") == normalized
            }
            if (match != null) {
                pokemon.updateAbility(match.template.create(false))
            }
        } catch (e: Exception) {
            // Si falla, mantener habilidad por defecto
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Asigna el held item al Pokémon
    // Prueba primero cobblemon: luego mega_showdown: para mega stones
    // ─────────────────────────────────────────────────────────────────────────

    private fun trySetHeldItem(pokemon: Pokemon, showdownItemName: String) {
        try {
            // Showdown usa nombres como "Choice Band", "Life Orb", "Lopunnite"
            // Cobblemon usa snake_case: "choice_band", "life_orb"
            // mega_showdown usa snake_case también: "lopunnite"
            val itemId = showdownItemName.lowercase()
                .replace(" ", "_")
                .replace("-", "_")

            // Namespaces a intentar en orden
            val namespaces = listOf("cobblemon", "mega_showdown")
            var found = false

            for (ns in namespaces) {
                val identifier = net.minecraft.util.Identifier.tryParse("$ns:$itemId") ?: continue
                val item = net.minecraft.registry.Registries.ITEM.getOrEmpty(identifier)
                    .orElse(null) ?: continue

                val stack = net.minecraft.item.ItemStack(item)
                if (!stack.isEmpty) {
                    pokemon.swapHeldItem(stack, false)
                    logger.debug("[RandomBattle] Asignado item $ns:$itemId a ${pokemon.species.name}")
                    found = true
                    break
                }
            }

            if (!found) {
                logger.debug("[RandomBattle] Item no encontrado: $showdownItemName (id: $itemId)")
            }
        } catch (e: Exception) {
            logger.debug("[RandomBattle] Error asignando item $showdownItemName: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fallback: genera Pokémon básicos si Showdown no está disponible
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildFallback(player: ServerPlayerEntity, formatName: String): List<UUID> {
        val party = Cobblemon.storage.getParty(player)
        val size = teamSize(formatName)
        val pokemon = buildFallbackPokemon(size)
        val toRemove = party.mapNotNull { it }
        toRemove.forEach { party.remove(it) }
        pokemon.forEach { party.add(it) }
        return party.mapNotNull { it?.uuid }
    }

    private fun buildFallbackPokemon(count: Int): List<Pokemon> {
        val allSpecies = PokemonSpecies.implemented.filter { it.implemented }.shuffled()
        return allSpecies.take(count).map { species ->
            Pokemon().also { pk ->
                pk.species = species
                pk.level = 50
                pk.initialize()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Extensiones para IVs/EVs
    // ─────────────────────────────────────────────────────────────────────────

    private fun Pokemon.setIV(stat: com.cobblemon.mod.common.api.pokemon.stats.Stat, value: Int) {
        try { this.ivs[stat] = value } catch (e: Exception) { }
    }

    private fun Pokemon.setEV(stat: com.cobblemon.mod.common.api.pokemon.stats.Stat, value: Int) {
        try { this.evs[stat] = value } catch (e: Exception) { }
    }
}
