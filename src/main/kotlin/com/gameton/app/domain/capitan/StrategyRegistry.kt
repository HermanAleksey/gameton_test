package com.gameton.app.domain.capitan

enum class StrategyId(
    val title: String,
    val description: String
) {
    SimpleExpansion(
        title = "Simple",
        description = "Minimal safe expansion"
    ),
    StableExpansion(
        title = "Stable",
        description = "HQ-first stable growth"
    ),
    ThreatAwareStable(
        title = "ThreatAware",
        description = "Stable growth with retreat/aggression"
    ),
    HighScoreExpansion(
        title = "HighScore",
        description = "Score-first expansion with HQ safety"
    )
}

data class StrategyDescriptor(
    val id: StrategyId,
    val maker: () -> DecisionMaker
)

object StrategyRegistry {
    val all: List<StrategyDescriptor> = listOf(
        StrategyDescriptor(StrategyId.SimpleExpansion) { SimpleExpansionDecisionMaker() },
        StrategyDescriptor(StrategyId.StableExpansion) { StableExpansionDecisionMaker() },
        StrategyDescriptor(StrategyId.ThreatAwareStable) { ThreatAwareStableExpansionDecisionMaker() },
        StrategyDescriptor(StrategyId.HighScoreExpansion) { HighScoreExpansionDecisionMaker() }
    )

    fun default(): StrategyDescriptor = all.first { it.id == StrategyId.HighScoreExpansion }

    fun byId(id: StrategyId): StrategyDescriptor = all.first { it.id == id }
}
