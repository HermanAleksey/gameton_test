package com.gameton.app.domain.model

/**
 * One upgrade branch state from plantationUpgrades.tiers.
 *
 * Name examples: repair_power, max_hp, settlement_limit, signal_range,
 * vision_range, decay_mitigation, earthquake_mitigation, beaver_damage_mitigation.
 */
data class UpgradeTier(
    val name: String,
    val current: Int,
    val max: Int
)

