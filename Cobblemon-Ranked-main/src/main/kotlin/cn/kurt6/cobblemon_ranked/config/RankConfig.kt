package cn.kurt6.cobblemon_ranked.config

import blue.endless.jankson.Comment

data class ArenaCoordinate(
    val x: Double,
    val y: Double,
    val z: Double
)

data class BattleArena(
    val world: String = "minecraft:overworld",
    val playerPositions: List<ArenaCoordinate> = listOf(
        ArenaCoordinate(0.0, 70.0, 0.0),
        ArenaCoordinate(10.0, 70.0, 0.0),
    )
)

data class FormatRules(
    @Comment("Banned moves for this format")
    var bannedMoves: List<String> = listOf(),

    @Comment("Banned Pokémon for this format")
    var bannedPokemon: List<String> = listOf(),

    @Comment("Banned held items for this format")
    var bannedItems: List<String> = listOf(),

    @Comment("Active clauses for this format (e.g. Sleep Clause, Species Clause, Evasion Clause)")
    var clauses: List<String> = listOf("Species Clause", "Sleep Clause", "Evasion Clause", "OHKO Clause")
)

data class RankConfig(
    @Comment("Default language: zh or en")
    var defaultLang: String = "en",

    @Comment("Default battle format: 'singles' / 默认的战斗模式'singles'（单打）")
    var defaultFormat: String = "single_battle",

    @Comment("Minimum number of Pokémon allowed in a team / 宝可梦限制最少数量")
    var minTeamSize: Int = 1,

    @Comment("Maximum number of Pokémon allowed in a team / 宝可梦限制最多数量")
    var maxTeamSize: Int = 6,

    @Comment("Allow duplicate held items in a team / 是否允许队伍中携带重复道具 (道具条款)")
    var allowDuplicateItems: Boolean = true,

    @Comment("Enable Team Preview and Selection (Bring 6 Pick 3/4) / 是否启用队伍预览和选出阶段 (6选3/4)")
    var enableTeamPreview: Boolean = false,

    @Comment("Time allowed for team selection in seconds / 队伍选择阶段的时间限制(秒)")
    var teamSelectionTime: Int = 90,

    @Comment("Number of Pokemon to select for Singles (usually 3) / 单打模式选出的宝可梦数量 (通常为3)")
    var singlesPickCount: Int = 3,

    @Comment("Number of Pokemon to select for Doubles (usually 4) / 双打模式选出的宝可梦数量 (通常为4)")
    var doublesPickCount: Int = 4,

    @Comment("Maximum allowed Elo difference in matchmaking / 队伍Elo差限制")
    var maxEloDiff: Int = 200,

    @Comment("Max wait time for matchmaking (seconds), Elo range linearly expands to max multiplier / 最大匹配等待时间（秒），Elo 差距将线性放宽，从1倍放宽至最大倍率（如3倍）")
    var maxQueueTime: Int = 300,

    @Comment("Max Elo multiplier for matchmaking range (linear expansion) / 最大 Elo 匹配放宽倍率（线性放宽）")
    var maxEloMultiplier: Double = 3.0,

    @Comment("Season duration in days / 每个赛季的持续时间（天）")
    val seasonDuration: Int = 30,

    @Comment("Initial Elo at the start of the season / 赛季初始Elo")
    val initialElo: Int = 1000,

    @Comment("K-factor in Elo calculation / Elo计算中的K因子")
    val eloKFactor: Int = 32,

    @Comment("Minimum possible Elo score / 最低Elo分数限制")
    val minElo: Int = 0,

    @Comment("Loser protection rate (0.0 to 1.0). Loser will lose at most (winner_gain * loserProtectionRate). 1.0 = no protection / 败者保护率，败者最多扣除胜者获得的Elo * loserProtectionRate。1.0 = 不启用保护")
    val loserProtectionRate: Double = 1.0,

    @Comment("Rules per format (banned moves, pokemon, items, clauses)")
    var formatRules: Map<String, FormatRules> = mapOf(
        "single_battle" to FormatRules(
            bannedMoves   = listOf("Baton Pass", "Shed Tail", "Last Respects", "Assist", "Electrify"),
            bannedPokemon = listOf(
                "Arceus", "Blaziken", "Darkrai", "Deoxys", "Deoxys-Attack", "Deoxys-Speed",
                "Dialga", "Eternatus", "Genesect", "Giratina", "Giratina-Origin",
                "Groudon", "Ho-Oh", "Kangaskhan-Mega", "Kyogre", "Kyurem-Black", "Kyurem-White",
                "Landorus", "Lucario-Mega", "Lugia", "Lunala", "Marshadow",
                "Mewtwo", "Naganadel", "Necrozma-Dawn-Wings", "Necrozma-Dusk-Mane",
                "Palkia", "Pheromosa", "Rayquaza", "Reshiram",
                "Salamence-Mega", "Shaymin-Sky", "Solgaleo",
                "Xerneas", "Yveltal", "Zacian", "Zacian-Crowned",
                "Zamazenta", "Zamazenta-Crowned", "Zekrom", "Zygarde-Complete",
                "Flutter-Mane", "Roaring-Moon", "Palafin", "Houndstone",
                "Koraidon", "Miraidon", "Calyrex-Ice", "Calyrex-Shadow",
                "Gouging-Fire", "Blastoise-Mega", "Gengar-Mega"
            ),
            bannedItems   = listOf(),
            clauses       = listOf(
                "Species Clause|Solo puedes usar 1 ejemplar de cada especie de Pokémon en tu equipo.",
                "Sleep Clause|No puedes dormir a más de 1 Pokémon rival al mismo tiempo.",
                "Evasion Clause|Están prohibidos los movimientos que aumenten la evasión directamente (p.ej. Double Team).",
                "OHKO Clause|Están prohibidos los movimientos de KO instantáneo (p.ej. Guillotine, Fissure).",
                "Endless Battle Clause|Está prohibido intentar generar una batalla infinita deliberadamente.",
                "Dynamax Clause|Dynamax y Gigantamax están prohibidos."
            )
        ),
        "doubles_battle" to FormatRules(
            bannedMoves   = listOf("Baton Pass", "Shed Tail", "Last Respects", "Assist", "Electrify"),
            bannedPokemon = listOf(
                "Arceus", "Blaziken", "Darkrai", "Deoxys", "Deoxys-Attack", "Deoxys-Speed",
                "Dialga", "Eternatus", "Genesect", "Giratina", "Giratina-Origin",
                "Groudon", "Ho-Oh", "Kangaskhan-Mega", "Kyogre", "Kyurem-Black", "Kyurem-White",
                "Landorus", "Lucario-Mega", "Lugia", "Lunala", "Marshadow",
                "Mewtwo", "Naganadel", "Necrozma-Dawn-Wings", "Necrozma-Dusk-Mane",
                "Palkia", "Pheromosa", "Rayquaza", "Reshiram",
                "Salamence-Mega", "Shaymin-Sky", "Solgaleo",
                "Xerneas", "Yveltal", "Zacian", "Zacian-Crowned",
                "Zamazenta", "Zamazenta-Crowned", "Zekrom", "Zygarde-Complete",
                "Flutter-Mane", "Roaring-Moon", "Palafin", "Houndstone",
                "Koraidon", "Miraidon", "Calyrex-Ice", "Calyrex-Shadow",
                "Gouging-Fire", "Blastoise-Mega", "Gengar-Mega"
            ),
            bannedItems   = listOf(),
            clauses       = listOf(
                "Species Clause|Solo puedes usar 1 ejemplar de cada especie de Pokémon en tu equipo.",
                "Sleep Clause|No puedes dormir a más de 1 Pokémon rival al mismo tiempo.",
                "OHKO Clause|Están prohibidos los movimientos de KO instantáneo (p.ej. Guillotine, Fissure).",
                "Endless Battle Clause|Está prohibido intentar generar una batalla infinita deliberadamente.",
                "Dynamax Clause|Dynamax y Gigantamax están prohibidos."
            )
        ),
        "random_battle" to FormatRules(
            bannedMoves   = listOf(),
            bannedPokemon = listOf(
                "Arceus", "Blaziken", "Darkrai", "Deoxys", "Deoxys-Attack", "Deoxys-Speed",
                "Dialga", "Eternatus", "Genesect", "Giratina", "Giratina-Origin",
                "Groudon", "Ho-Oh", "Kangaskhan-Mega", "Kyogre", "Kyurem-Black", "Kyurem-White",
                "Landorus", "Lucario-Mega", "Lugia", "Lunala", "Marshadow",
                "Mewtwo", "Naganadel", "Necrozma-Dawn-Wings", "Necrozma-Dusk-Mane",
                "Palkia", "Pheromosa", "Rayquaza", "Reshiram",
                "Salamence-Mega", "Shaymin-Sky", "Solgaleo",
                "Xerneas", "Yveltal", "Zacian", "Zacian-Crowned",
                "Zamazenta", "Zamazenta-Crowned", "Zekrom", "Zygarde-Complete",
                "Flutter-Mane", "Roaring-Moon", "Palafin", "Houndstone",
                "Koraidon", "Miraidon", "Calyrex-Ice", "Calyrex-Shadow",
                "Gouging-Fire", "Blastoise-Mega", "Gengar-Mega"
            ),
            bannedItems   = listOf(),
            clauses       = listOf(
                "Species Clause|Solo puedes usar 1 ejemplar de cada especie de Pokémon en tu equipo.",
                "Sleep Clause|No puedes dormir a más de 1 Pokémon rival al mismo tiempo.",
                "Endless Battle Clause|Está prohibido intentar generar una batalla infinita deliberadamente."
            )
        ),
        "double_random_battle" to FormatRules(
            bannedMoves   = listOf(),
            bannedPokemon = listOf(
                "Arceus", "Blaziken", "Darkrai", "Deoxys", "Deoxys-Attack", "Deoxys-Speed",
                "Dialga", "Eternatus", "Genesect", "Giratina", "Giratina-Origin",
                "Groudon", "Ho-Oh", "Kangaskhan-Mega", "Kyogre", "Kyurem-Black", "Kyurem-White",
                "Landorus", "Lucario-Mega", "Lugia", "Lunala", "Marshadow",
                "Mewtwo", "Naganadel", "Necrozma-Dawn-Wings", "Necrozma-Dusk-Mane",
                "Palkia", "Pheromosa", "Rayquaza", "Reshiram",
                "Salamence-Mega", "Shaymin-Sky", "Solgaleo",
                "Xerneas", "Yveltal", "Zacian", "Zacian-Crowned",
                "Zamazenta", "Zamazenta-Crowned", "Zekrom", "Zygarde-Complete",
                "Flutter-Mane", "Roaring-Moon", "Palafin", "Houndstone",
                "Koraidon", "Miraidon", "Calyrex-Ice", "Calyrex-Shadow",
                "Gouging-Fire", "Blastoise-Mega", "Gengar-Mega"
            ),
            bannedItems   = listOf(),
            clauses       = listOf(
                "Species Clause|Solo puedes usar 1 ejemplar de cada especie de Pokémon en tu equipo.",
                "Sleep Clause|No puedes dormir a más de 1 Pokémon rival al mismo tiempo.",
                "Endless Battle Clause|Está prohibido intentar generar una batalla infinita deliberadamente."
            )
        )
    ),

    @Comment("Banned Pokémon / 禁止使用的宝可梦")
    var bannedPokemon: List<String> = listOf("Mewtwo", "Arceus"),

    @Comment("Restricted Pokémon list / 受限宝可梦列表")
    var restrictedPokemon: List<String> = listOf("Mew", "Celebi"),

    @Comment("Maximum number of restricted Pokémon allowed in a team / 受限宝可梦最大允许携带数量")
    var maxRestrictedCount: Int = 2,

    @Comment("Banned held items for Pokémon / 禁止宝可梦携带的道具")
    var bannedHeldItems: List<String> = listOf("cobblemon:leftovers"),

    @Comment("Banned items in player's inventory / 禁止玩家背包携带的物品")
    var bannedCarriedItems: List<String> = listOf("cobblemon:leftovers"),

    @Comment("Banned moves for Pokémon / 禁止宝可梦使用的技能")
    var bannedMoves: List<String> = listOf("leechseed"),

    @Comment("Banned personalities for Pokémon /  禁止宝可梦使用的性格")
    var bannedNatures: List<String> = listOf(),

    @Comment("Banned abilities for Pokémon / 禁止宝可梦使用的特性")
    var bannedAbilities: List<String> = listOf(),

    @Comment("Banned gender for Pokémon / 禁止宝可梦使用的性别")
    var bannedGenders: List<String> = listOf(),

    @Comment("Banned shiny Pokémon from participating in battles / 是否禁止闪光宝可梦参战")
    var bannedShiny: Boolean = false,

    @Comment("Ban Pokémon with usage rate below this threshold (0 to disable, 0.1 = 10%) / 禁止使用率低于此阈值的宝可梦（0为关闭，0.1表示10%）")
    var banUsageBelow: Double = 0.0,

    @Comment("Ban Pokémon with usage rate above this threshold (0 to disable, 0.1 = 10%) / 禁止使用率高于此阈值的宝可梦（0为关闭，0.1表示10%）")
    var banUsageAbove: Double = 0.0,

    @Comment("Ban top N most used Pokémon (0 to disable) / 禁止使用排行前N的宝可梦（0为关闭）")
    var banTopUsed: Int = 0,

    @Comment("Only allow base form Pokémon that can evolve / 是否只允许使用能够进化的最初形态")
    var onlyBaseFormWithEvolution: Boolean = false,

    @Comment("Allowed battle formats: 'singles', 'doubles', '2v2singles' / 允许的战斗模式：'singles'（单打）, 'doubles'（双打）, '2v2singles'（2v2单打）")
    var allowedFormats: List<String> = listOf("single_battle", "doubles_battle", "random_battle", "double_random_battle"),

    @Comment("Max Pokémon level allowed (0 = no limit) / 允许的宝可梦等级，0 = 无限制")
    var maxLevel: Int = 0,

    @Comment("Allowed to have the same species of Pokémon in a single team / 允许同一个队伍中出现相同的宝可梦")
    var allowDuplicateSpecies: Boolean = false,

    @Comment("Enable forced modification of Pokémon levels / 是否启用强制修改宝可梦等级")
    var enableCustomLevel: Boolean = false,

    @Comment("Forcefully modify the level of Pokémon / 设置强制修改宝可梦的等级")
    var customBattleLevel: Int = 50,

    @Comment("Prevent players from breaking blocks during ranked battles / 对战时是否禁止玩家破坏方块")
    var preventBlockBreaking: Boolean = true,

    @Comment("Restore Pokémon HP after battle / 对战结束后是否恢复宝可梦血量")
    var restorePokemonHpAfterBattle: Boolean = true,

    @Comment("Available battle arenas after matchmaking, each with 2 teleport coordinates / 匹配成功后可用的战斗场地列表，支持多个场地随机挑选，每个场地需要定义 2 个传送坐标")
    var battleArenas: List<BattleArena> = listOf(
        BattleArena(
            world = "minecraft:overworld",
            playerPositions = listOf(
                ArenaCoordinate(0.0, 70.0, 0.0),
                ArenaCoordinate(10.0, 70.0, 0.0),
            )
        ),
        BattleArena(
            world = "minecraft:overworld",
            playerPositions = listOf(
                ArenaCoordinate(100.0, 65.0, 100.0),
                ArenaCoordinate(110.0, 65.0, 100.0),
            )
        )
    ),

    @Comment("Victory rewards configuration (executed after each win) / 每场比赛获胜的奖励配置")
    var victoryRewards: List<String> = listOf(
        "give {player} minecraft:experience_bottle 5",
        "give {player} minecraft:emerald 1"
    ),

    @Comment("Rank rewards configuration per format / 段位奖励配置，每种模式可单独配置 ")
    var rankRewards: Map<String, Map<String, List<String>>> = mapOf(
        "single_battle" to mapOf(
            "Aprendiz ★★★"   to listOf("give {player} minecraft:apple 5"),
            "Experto ★★★"    to listOf("give {player} minecraft:golden_apple 3"),
            "Élite ★★★"      to listOf("give {player} minecraft:diamond 2"),
            "Maestro ★★★"    to listOf("give {player} minecraft:diamond_block 1"),
            "Legendario ★★★" to listOf("give {player} minecraft:netherite_ingot 1")
        ),
        "doubles_battle" to mapOf(
            "Aprendiz ★★★"   to listOf("give {player} minecraft:apple 5"),
            "Experto ★★★"    to listOf("give {player} minecraft:golden_apple 3"),
            "Élite ★★★"      to listOf("give {player} minecraft:diamond 2"),
            "Maestro ★★★"    to listOf("give {player} minecraft:diamond_block 1"),
            "Legendario ★★★" to listOf("give {player} minecraft:netherite_ingot 1")
        ),
        "random_battle" to mapOf(
            "Aprendiz ★★★"   to listOf("give {player} minecraft:apple 5"),
            "Experto ★★★"    to listOf("give {player} minecraft:golden_apple 3"),
            "Élite ★★★"      to listOf("give {player} minecraft:diamond 2"),
            "Maestro ★★★"    to listOf("give {player} minecraft:diamond_block 1"),
            "Legendario ★★★" to listOf("give {player} minecraft:netherite_ingot 1")
        ),
        "double_random_battle" to mapOf(
            "Aprendiz ★★★"   to listOf("give {player} minecraft:apple 5"),
            "Experto ★★★"    to listOf("give {player} minecraft:golden_apple 3"),
            "Élite ★★★"      to listOf("give {player} minecraft:diamond 2"),
            "Maestro ★★★"    to listOf("give {player} minecraft:diamond_block 1"),
            "Legendario ★★★" to listOf("give {player} minecraft:netherite_ingot 1")
        )
    ),

    @Comment("Elo thresholds for rank titles (customizable) / 段位名称配置（可增减）")
    var rankTitles: Map<Int, String> = mapOf(
        0    to "Aprendiz ★",
        200  to "Aprendiz ★★",
        400  to "Aprendiz ★★★",
        600  to "Experto ★",
        800  to "Experto ★★",
        1000 to "Experto ★★★",
        1200 to "Élite ★",
        1400 to "Élite ★★",
        1600 to "Élite ★★★",
        1800 to "Maestro ★",
        2000 to "Maestro ★★",
        2200 to "Maestro ★★★",
        2400 to "Legendario ★",
        2600 to "Legendario ★★",
        2800 to "Legendario ★★★"
    ),

    @Comment("Minimum winning rate requirement for each rank reward / 每个段位奖励领取的最小胜率要求（0.0 ~ 1.0）")
    var rankRequirements: Map<String, Double> = mapOf(
        "Aprendiz ★★★"   to 0.0,
        "Experto ★★★"    to 0.3,
        "Élite ★★★"      to 0.3,
        "Maestro ★★★"    to 0.3,
        "Legendario ★★★" to 0.3
    ),

    @Comment("Enable cross-server matchmaking / 是否启用跨服匹配")
    var enableCrossServer: Boolean = false,

    @Comment("Cloud server ID for this server(Cannot be repeated with others) / 本服的云端标识(不可与他人重复)")
    var cloudServerId: String = "",

    @Comment("Cloud server auth token(Leave blank for the public cloud server) / 云端验证用密钥(公开云服留空即可)")
    var cloudToken: String = "",

    @Comment("Cloud API address(Either IP or domain name is acceptable) / 云端 API 地址(ip或者域名都可以)")
    var cloudApiUrl: String = "",

    @Comment("Cloud WebSocket Address(Either IP or domain name is acceptable) / 云端 WebSocket 地址(ip或者域名都可以)")
    var cloudWebSocketUrl: String = ""
)