package cn.kurt6.cobblemon_ranked.random

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CompletableFuture

// ─────────────────────────────────────────────────────────────────────────────
// Estructuras de datos del JSON de Showdown Gen9 Random Battle
// ─────────────────────────────────────────────────────────────────────────────

data class ShowdownRole(
    val movepool: List<String> = emptyList(),
    val abilities: List<String> = emptyList(),
    val items: List<String> = emptyList(),
    val teraTypes: List<String> = emptyList()
)

data class ShowdownEntry(
    val level: Int = 80,
    val roles: Map<String, ShowdownRole> = emptyMap()
) {
    // Combina todos los movedools de todos los roles en uno solo
    val allMoves: List<String> get() = roles.values.flatMap { it.movepool }.distinct()
    val allAbilities: List<String> get() = roles.values.flatMap { it.abilities }.distinct()
    val allItems: List<String> get() = roles.values.flatMap { it.items }.distinct()

    // Elige un rol al azar y devuelve su set
    fun randomRole(): ShowdownRole = roles.values.randomOrNull() ?: ShowdownRole()
}

// ─────────────────────────────────────────────────────────────────────────────
// Loader
// ─────────────────────────────────────────────────────────────────────────────

object ShowdownDataLoader {

    private val logger = CobblemonRanked.logger
    private val gson = Gson()

    // URL del JSON oficial de Showdown Gen9 Random Battle
    private const val SETS_URL =
        "https://raw.githubusercontent.com/smogon/pokemon-showdown/master/data/random-battles/gen9/sets.json"

    // Ruta local donde se guarda el archivo
    private val localFile: File get() =
        CobblemonRanked.dataPath.resolve("randbats.json").toFile()

    // Mapa cargado en memoria: NombrePokemon -> ShowdownEntry
    private var data: Map<String, ShowdownEntry> = emptyMap()

    val isLoaded: Boolean get() = data.isNotEmpty()

    // ─────────────────────────────────────────────────────────────────────────
    // Inicialización al arrancar el servidor
    // ─────────────────────────────────────────────────────────────────────────

    fun init() {
        CompletableFuture.runAsync {
            try {
                if (localFile.exists() && localFile.length() > 1000) {
                    logger.info("[RandomBattle] randbats.json encontrado localmente, cargando...")
                    loadFromFile()
                } else {
                    logger.info("[RandomBattle] Descargando sets de Showdown Gen9...")
                    downloadAndSave()
                    loadFromFile()
                }
                logger.info("[RandomBattle] Sets cargados: ${data.size} Pokémon disponibles.")
            } catch (e: Exception) {
                logger.error("[RandomBattle] Error cargando sets de Showdown: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Descarga el JSON y lo guarda localmente
    // ─────────────────────────────────────────────────────────────────────────

    private fun downloadAndSave() {
        val conn = URL(SETS_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "CobblemonRanked/1.0")
        conn.connect()

        if (conn.responseCode != 200) {
            throw RuntimeException("HTTP ${conn.responseCode} al descargar sets.json")
        }

        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        localFile.parentFile.mkdirs()
        localFile.writeText(json, Charsets.UTF_8)
        logger.info("[RandomBattle] randbats.json guardado en ${localFile.absolutePath}")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsea el JSON guardado localmente
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadFromFile() {
        val json = localFile.readText(Charsets.UTF_8)
        val root = gson.fromJson(json, JsonObject::class.java)

        val parsed = mutableMapOf<String, ShowdownEntry>()

        root.entrySet().forEach { (name, element) ->
            try {
                val obj = element.asJsonObject

                val level = obj.get("level")?.asInt ?: 80

                val roles = mutableMapOf<String, ShowdownRole>()

                // Gen9 tiene estructura "roles": { "Sweeper": { movepool, abilities, items, teraTypes } }
                val rolesObj = obj.getAsJsonObject("roles")
                rolesObj?.entrySet()?.forEach { (roleName, roleEl) ->
                    val roleObj = roleEl.asJsonObject
                    val movepool = roleObj.getAsJsonArray("movepool")
                        ?.map { it.asString } ?: emptyList()
                    val abilities = roleObj.getAsJsonArray("abilities")
                        ?.map { it.asString } ?: emptyList()
                    val items = roleObj.getAsJsonArray("items")
                        ?.map { it.asString } ?: emptyList()
                    val teraTypes = roleObj.getAsJsonArray("teraTypes")
                        ?.map { it.asString } ?: emptyList()
                    roles[roleName] = ShowdownRole(movepool, abilities, items, teraTypes)
                }

                // Fallback: algunos Pokémon en Gen9 sets.json tienen estructura plana sin roles
                if (roles.isEmpty()) {
                    val movepool = obj.getAsJsonArray("moves")
                        ?.map { it.asString } ?: emptyList()
                    val abilities = obj.getAsJsonArray("abilities")
                        ?.map { it.asString } ?: emptyList()
                    val items = obj.getAsJsonArray("items")
                        ?.map { it.asString } ?: emptyList()
                    if (movepool.isNotEmpty()) {
                        roles["Default"] = ShowdownRole(movepool, abilities, items)
                    }
                }

                parsed[name] = ShowdownEntry(level, roles)
            } catch (e: Exception) {
                // Ignorar entradas malformadas
            }
        }

        data = parsed
    }

    // ─────────────────────────────────────────────────────────────────────────
    // API pública
    // ─────────────────────────────────────────────────────────────────────────

    /** Devuelve el entry de un Pokémon por nombre exacto (como en Showdown: "Pikachu", "Garchomp", etc.) */
    fun getEntry(name: String): ShowdownEntry? = data[name]

    /** Devuelve todos los nombres de Pokémon disponibles */
    fun getAllNames(): Set<String> = data.keys

    /** Elige N Pokémon al azar del pool */
    fun randomPokemon(count: Int): List<Pair<String, ShowdownEntry>> {
        return data.entries
            .shuffled()
            .take(count)
            .map { it.key to it.value }
    }

    /** Fuerza re-descarga del JSON (útil para comando admin) */
    fun forceReload() {
        CompletableFuture.runAsync {
            try {
                logger.info("[RandomBattle] Forzando re-descarga de randbats.json...")
                localFile.delete()
                downloadAndSave()
                loadFromFile()
                logger.info("[RandomBattle] Re-descarga completada. ${data.size} Pokémon cargados.")
            } catch (e: Exception) {
                logger.error("[RandomBattle] Error en re-descarga: ${e.message}")
            }
        }
    }
}
