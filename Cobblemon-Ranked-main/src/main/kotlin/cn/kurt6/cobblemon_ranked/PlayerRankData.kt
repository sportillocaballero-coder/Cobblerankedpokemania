package cn.kurt6.cobblemon_ranked

data class PlayerRankData(
    var elo: Int,
    var wins: Int,
    var losses: Int,
    var currentStreak: Int,
    var bestStreak: Int
) {

    companion object {
        fun createDefault(baseElo: Int): PlayerRankData {
            return PlayerRankData(
                elo = baseElo,
                wins = 0,
                losses = 0,
                currentStreak = 0,
                bestStreak = 0
            )
        }
    }

    fun recordWin(kFactor: Int = 32, opponentElo: Int) {
        wins++
        currentStreak++
        if (currentStreak > bestStreak) {
            bestStreak = currentStreak
        }

        elo += calculateEloChange(elo, opponentElo, true, kFactor)
    }

    fun recordLoss(kFactor: Int = 32, opponentElo: Int) {
        losses++
        currentStreak = 0

        elo += calculateEloChange(elo, opponentElo, false, kFactor)
    }

    private fun calculateEloChange(
        playerElo: Int,
        opponentElo: Int,
        win: Boolean,
        kFactor: Int
    ): Int {
        val expectedScore = 1.0 / (1.0 + Math.pow(10.0, ((opponentElo - playerElo) / 400.0)))
        val actualScore = if (win) 1.0 else 0.0
        return (kFactor * (actualScore - expectedScore)).toInt()
    }

    fun totalGames(): Int {
        return wins + losses
    }

    fun winRate(): Double {
        val total = totalGames()
        return if (total == 0) 0.0 else (wins.toDouble() / total.toDouble()) * 100.0
    }
}