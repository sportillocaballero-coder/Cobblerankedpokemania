package cn.kurt6.cobblemon_ranked

enum class RankedFormat(
    val displayName: String,
    val formatKey: String,
    val baseElo: Int
) {
    SINGLE_BATTLE(displayName = "§6Single Battle", formatKey = "single_battle", baseElo = 1000),
    DOUBLES_BATTLE(displayName = "§5Doubles Battle", formatKey = "doubles_battle", baseElo = 1000),
    RANDOM_BATTLE(displayName = "§bRandom Battle", formatKey = "random_battle", baseElo = 1000),
    DOUBLE_RANDOM_BATTLE(displayName = "§3Double Random Battle", formatKey = "double_random_battle", baseElo = 1000);

    companion object {
        fun fromKey(key: String): RankedFormat? = entries.firstOrNull { it.formatKey == key }
        fun fromDisplayName(name: String): RankedFormat? = entries.firstOrNull { it.displayName == name }
    }
}