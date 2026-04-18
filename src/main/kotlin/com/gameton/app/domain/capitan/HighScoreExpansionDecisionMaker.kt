package com.gameton.app.domain.capitan

import com.gameton.app.domain.model.ArenaState
import com.gameton.app.domain.model.Coordinate
import com.gameton.app.domain.model.EnemyPlantation
import com.gameton.app.domain.model.Plantation
import com.gameton.app.ui.model.CommandActionUi
import com.gameton.app.ui.model.CommandRequestUi
import com.gameton.app.ui.model.UiCoordinate
import kotlin.math.abs

private const val HS_BASE_SETTLEMENT_LIMIT = 30
private const val HS_BASE_SIGNAL_RANGE = 3
private const val HS_BASE_CONSTRUCTION_SPEED = 5
private const val HS_BASE_REPAIR_SPEED = 5
private const val HS_BASE_SABOTAGE_POWER = 5
private const val HS_BASE_TERRAFORM_SPEED = 5
private const val HS_BUILD_COMPLETION_HP = 50
private const val HS_HQ_CRITICAL_HP = 18
private const val HS_HQ_DANGER_HP = 28
private const val HS_THREATENED_HP = 16
private const val HS_BASE_HQ_TERRAFORM_BUFFER_TURNS = 3

/**
 * Score-first strategy:
 * - most turns are spent on stable expansion and boosted-cell capture,
 * - HQ continuity is protected aggressively to avoid catastrophic total loss,
 * - sabotage is used only when it is cheap and likely to pay off immediately.
 */
class HighScoreExpansionDecisionMaker : DecisionMaker {
    private companion object {
        const val HIGH_TERRAFORMATION_PROGRESS = 80
        const val MID_TERRAFORMATION_PROGRESS = 60
        const val HQ_RELOCATE_TURN_THRESHOLD = 2
        const val HQ_EMERGENCY_SUPPORT_THRESHOLD = 3
        const val NORMAL_CELL_POINT_VALUE = 10
        const val BOOSTED_CELL_POINT_VALUE = 15
    }

    private var hqSupportTarget: UiCoordinate? = null

    override fun makeTurn(state: ArenaState): CommandRequestUi {
        val context = HsContext(state)
        val upgrade = chooseUpgrade(context)
        val usedAuthors = mutableSetOf<UiCoordinate>()
        val outputUsage = mutableMapOf<UiCoordinate, Int>()

        val hqPlan = planHqContinuity(context, usedAuthors, outputUsage)
        if (hqPlan.relocate != null) {
            return CommandRequestUi(
                plantationUpgrade = upgrade,
                relocateMain = listOf(hqPlan.relocate.from, hqPlan.relocate.to)
            )
        }

        val posture = assessPosture(context)
        val defensiveCommands = when (posture.mode) {
            HsMode.SURVIVAL -> planDefensiveRepairs(context, posture, usedAuthors, outputUsage)
            else -> emptyList()
        }
        defensiveCommands.forEach { command ->
            usedAuthors += command.author
            outputUsage[command.output] = (outputUsage[command.output] ?: 0) + 1
        }

        val pressureCommands = when (posture.mode) {
            HsMode.PRESSURE -> planPressure(context, posture, usedAuthors, outputUsage)
            else -> emptyList()
        }
        pressureCommands.forEach { command ->
            usedAuthors += command.author
            outputUsage[command.output] = (outputUsage[command.output] ?: 0) + 1
        }

        val expansionCommands = when (posture.mode) {
            HsMode.SURVIVAL -> planExpansion(
                context = context,
                usedAuthors = usedAuthors,
                outputUsage = outputUsage,
                plannedNewTargets = hqPlan.plannedNewTargets.toMutableSet(),
                maxCommands = 1,
                safeOnly = true
            )

            HsMode.PRESSURE -> planExpansion(
                context = context,
                usedAuthors = usedAuthors,
                outputUsage = outputUsage,
                plannedNewTargets = hqPlan.plannedNewTargets.toMutableSet(),
                maxCommands = Int.MAX_VALUE,
                safeOnly = false
            )

            HsMode.SCORE -> planExpansion(
                context = context,
                usedAuthors = usedAuthors,
                outputUsage = outputUsage,
                plannedNewTargets = hqPlan.plannedNewTargets.toMutableSet(),
                maxCommands = Int.MAX_VALUE,
                safeOnly = false
            )
        }

        val allCommands = hqPlan.commands + defensiveCommands + pressureCommands + expansionCommands
        return when {
            allCommands.isNotEmpty() -> CommandRequestUi(
                command = allCommands.map { action ->
                    CommandActionUi(path = listOf(action.author, action.output, action.target))
                },
                plantationUpgrade = upgrade
            )

            upgrade != null -> CommandRequestUi(plantationUpgrade = upgrade)
            else -> CommandRequestUi()
        }
    }

    private fun chooseUpgrade(context: HsContext): String? {
        val upgrades = context.state.plantationUpgrades ?: return null
        if (upgrades.points <= 0) return null

        val tieBreakPriority = listOf(
            "settlement_limit",
            "repair_power",
            "max_hp",
            "signal_range",
            "beaver_damage_mitigation",
            "earthquake_mitigation",
            "vision_range",
            "decay_mitigation"
        )

        val available = upgrades.tiers
            .filter { it.current < it.max }
            .map { it.name }

        return available.maxWithOrNull(
            compareBy<String>(
                { scoreUpgrade(it, context) },
                { -tieBreakPriority.indexOf(it).let { index -> if (index == -1) Int.MAX_VALUE else index } }
            )
        )?.takeIf { scoreUpgrade(it, context) > Int.MIN_VALUE / 2 }
    }

    private fun scoreUpgrade(name: String, context: HsContext): Int {
        val mainHp = context.mainPlantation()?.hp ?: 0
        val hqUnderPressure = mainHp <= HS_HQ_DANGER_HP || context.mainInSevereDanger
        val severeThreat = mainHp <= HS_HQ_CRITICAL_HP || (context.isEarthquakeImminent() && mainHp <= HS_HQ_DANGER_HP)

        return when (name) {
            "settlement_limit" -> {
                var score = 140
                if (context.nearSettlementCap) score += 320
                if (context.boostedFrontierTargetCount > 0) score += 90
                score += minOf(context.safeFrontierTargetCount, 6) * 18
                if (context.activeSettlementCount >= context.effectiveSettlementLimit - 1) score += 140
                score
            }

            "repair_power" -> {
                var score = 120
                if (hqUnderPressure) score += 220
                score += minOf(context.threatenedOperationalCount, 5) * 35
                score += minOf(context.beaverThreatenedCount, 4) * 28
                if (context.state.construction.isNotEmpty()) score += 35
                if (severeThreat) score += 90
                score
            }

            "max_hp" -> {
                var score = 110
                if (hqUnderPressure) score += 180
                if (context.isEarthquakeImminent()) score += 120
                if (context.mainInSevereDanger) score += 80
                score += minOf(context.lowHpOperationalCount, 5) * 24
                score
            }

            "signal_range" -> {
                var score = 90
                score += minOf(context.operationalPlantations.size, 12) * 10
                score += minOf(context.frontierTargetCount, 6) * 8
                if (context.boostedFrontierTargetCount > 0) score += 40
                if (hqUnderPressure) score -= 70
                if (context.nearSettlementCap) score -= 35
                score
            }

            "beaver_damage_mitigation" -> {
                var score = 70
                score += minOf(context.beaverThreatenedCount, 6) * 40
                if (context.mainBeaverThreatened) score += 90
                if (context.beavers.isEmpty()) score -= 45
                score
            }

            "earthquake_mitigation" -> {
                var score = 60
                if (context.isEarthquakeImminent()) score += 260
                score += minOf(context.lowHpOperationalCount, 5) * 18
                score += minOf(context.activeSettlementCount, 20) * 2
                score
            }

            "vision_range" -> {
                var score = 45
                if (context.boostedFrontierTargetCount == 0 && context.safeFrontierTargetCount <= 2) score += 25
                if (hqUnderPressure) score -= 35
                score
            }

            "decay_mitigation" -> {
                var score = 40
                score += minOf(context.degradingCellCount, 6) * 14
                if (context.state.construction.size >= 2) score += 30
                score
            }

            else -> Int.MIN_VALUE
        }
    }

    private fun planHqContinuity(
        context: HsContext,
        usedAuthors: MutableSet<UiCoordinate>,
        outputUsage: MutableMap<UiCoordinate, Int>
    ): HsHqPlan {
        val main = context.mainPlantation() ?: return HsHqPlan()
        val mainPoint = main.position.toUi()
        val turnsToHqDisappear = context.turnsToMainDisappear
        val supportCandidates = hsNeighborCandidates(mainPoint)
            .mapNotNull { point ->
                val plantation = context.ownPlantationByPosition[point] ?: return@mapNotNull null
                if (plantation.isMain || plantation.isIsolated) return@mapNotNull null
                point to plantation
            }
        val hasAdjacentOperationalSupport = supportCandidates.isNotEmpty()

        if (hasAdjacentOperationalSupport && turnsToHqDisappear <= HQ_RELOCATE_TURN_THRESHOLD) {
            val relocation = chooseMainRelocation(context, supportCandidates)
            if (relocation != null) {
                hqSupportTarget = null
                return HsHqPlan(relocate = relocation)
            }
        }

        if (hasAdjacentOperationalSupport) {
            hqSupportTarget = null
            return HsHqPlan()
        }

        val target = chooseHqSupportTarget(context, mainPoint) ?: return HsHqPlan()
        hqSupportTarget = target
        val remainingProgress = (HS_BUILD_COMPLETION_HP - (context.constructionByPosition[target]?.progress ?: 0)).coerceAtLeast(0)
        if (remainingProgress == 0) return HsHqPlan()

        val authors = context.operationalAuthors.filter { it !in usedAuthors }
        if (authors.isEmpty()) return HsHqPlan()

        val emergency = context.requiresEmergencyHqSupport()

        val selected = planAssignmentsForTarget(
            target = target,
            authors = authors,
            outputUsage = outputUsage,
            context = context,
            basePower = HS_BASE_CONSTRUCTION_SPEED,
            maximize = emergency
        )
        if (selected.commands.isEmpty()) return HsHqPlan()

        selected.commands.forEach { command ->
            usedAuthors += command.author
            outputUsage[command.output] = (outputUsage[command.output] ?: 0) + 1
        }
        val plannedNewTargets = if (target !in context.constructionPositions) setOf(target) else emptySet()
        return HsHqPlan(commands = selected.commands, plannedNewTargets = plannedNewTargets)
    }

    private fun chooseMainRelocation(
        context: HsContext,
        supportCandidates: List<Pair<UiCoordinate, Plantation>>
    ): HsRelocateChoice? {
        val main = context.mainPlantation() ?: return null
        val mainPoint = main.position.toUi()
        val target = supportCandidates
            .sortedWith(
                compareBy<Pair<UiCoordinate, Plantation>> { context.cellProgressAt(it.first) }
                    .thenBy { boolToOrder(context.isSandstormRisk(it.first)) }
                    .thenBy { boolToOrder(context.isBeaverDanger(it.first)) }
                    .thenBy { boolToOrder(context.isEnemyThreat(it.first)) }
                    .thenByDescending { it.second.hp }
                    .thenBy { it.first.x }
                    .thenBy { it.first.y }
            )
            .firstOrNull()
            ?.first
            ?: return null
        return HsRelocateChoice(from = mainPoint, to = target)
    }

    private fun chooseHqSupportTarget(context: HsContext, mainPoint: UiCoordinate): UiCoordinate? {
        val adjacentConstructions = hsNeighborCandidates(mainPoint)
            .filter { it in context.constructionPositions }
            .sortedWith(
                compareByDescending<UiCoordinate> { context.constructionByPosition[it]?.progress ?: 0 }
                    .thenBy { boolToOrder(context.isDangerousExpansion(it)) }
                    .thenBy { it.x }
                    .thenBy { it.y }
            )
        if (adjacentConstructions.isNotEmpty()) {
            return hqSupportTarget
                ?.takeIf { it in adjacentConstructions }
                ?: adjacentConstructions.first()
        }

        if (!canStartNewConstruction(context, emptySet())) return null

        return hsNeighborCandidates(mainPoint)
            .filter { it.isBuildable(context) && !context.isSevereDanger(it) }
            .ifEmpty { hsNeighborCandidates(mainPoint).filter { it.isBuildable(context) && !context.isDangerousExpansion(it) } }
            .ifEmpty { hsNeighborCandidates(mainPoint).filter { it.isBuildable(context) } }
            .sortedWith(
                compareByDescending<UiCoordinate> { scoreHqSupportTarget(it, context) }
                    .thenBy { it.x }
                    .thenBy { it.y }
            )
            .firstOrNull()
    }

    private fun assessPosture(context: HsContext): HsAssessment {
        val main = context.mainPlantation()
        val threatenedOwn = context.operationalPlantations
            .filter { plantation ->
                val point = plantation.position.toUi()
                plantation.hp <= HS_THREATENED_HP ||
                    (plantation.isMain && plantation.hp <= HS_HQ_DANGER_HP) ||
                    context.isSevereDanger(point) ||
                    (context.isEarthquakeImminent() && plantation.hp <= HS_HQ_DANGER_HP)
            }
            .sortedWith(compareBy<Plantation>({ !it.isMain }, { it.hp }, { it.position.x }, { it.position.y }))

        val pressureCandidates = context.enemyPlantations
            .mapNotNull { enemy ->
                val pressure = scorePressureTarget(enemy, context)
                if (pressure <= 0) null else HsPressureTarget(enemy.position.toUi(), enemy.hp, pressure)
            }
            .sortedByDescending { it.score }

        val turnsToHqDisappear = main?.let {
            context.turnsToMainDisappear
        } ?: 0

        val hqCritical = when {
            main == null -> true
            main.hp <= HS_HQ_CRITICAL_HP -> true
            turnsToHqDisappear <= HQ_EMERGENCY_SUPPORT_THRESHOLD &&
                hsNeighborCandidates(main.position.toUi()).none { it in context.nonMainOperationalOwnPositions } -> true
            context.isSevereDanger(main.position.toUi()) && main.hp <= HS_HQ_DANGER_HP -> true
            context.isEarthquakeImminent() && main.hp <= HS_HQ_DANGER_HP -> true
            else -> false
        }

        val mode = when {
            hqCritical -> HsMode.SURVIVAL
            context.requiresEmergencyHqSupport() -> HsMode.SURVIVAL
            threatenedOwn.any { it.isMain && it.hp <= HS_HQ_DANGER_HP } -> HsMode.SURVIVAL
            pressureCandidates.isNotEmpty() -> HsMode.PRESSURE
            else -> HsMode.SCORE
        }

        return HsAssessment(
            mode = mode,
            threatenedOwn = threatenedOwn.map { it.position.toUi() },
            pressureTargets = pressureCandidates
        )
    }

    private fun scorePressureTarget(enemy: EnemyPlantation, context: HsContext): Int {
        val point = enemy.position.toUi()
        val reachableAssignments = planAssignmentsForTarget(
            target = point,
            authors = context.operationalAuthors,
            outputUsage = emptyMap(),
            context = context,
            basePower = HS_BASE_SABOTAGE_POWER,
            maximize = true
        )
        if (reachableAssignments.commands.isEmpty()) return 0

        var score = 0
        if (enemy.hp <= reachableAssignments.totalPower) score += 8_000
        if (enemy.hp <= HS_BASE_SABOTAGE_POWER * 2) score += 2_500
        if (isBoosted(point)) score += 4_500
        if (hsNeighborCandidates(point).any { it in context.ownPositions }) score += 1_800
        if (hsNeighborCandidates(point).any { it == context.mainPlantation()?.position?.toUi() }) score += 3_200
        score -= enemy.hp * 150
        return score
    }

    private fun planDefensiveRepairs(
        context: HsContext,
        posture: HsAssessment,
        usedAuthors: Set<UiCoordinate>,
        outputUsage: Map<UiCoordinate, Int>
    ): List<HsPlannedActionCommand> {
        val repairTarget = selectRepairTarget(context, posture) ?: return emptyList()
        val authors = context.operationalAuthors.filter { it !in usedAuthors && it != repairTarget }
        return planAssignmentsForTarget(
            target = repairTarget,
            authors = authors,
            outputUsage = outputUsage,
            context = context,
            basePower = HS_BASE_REPAIR_SPEED,
            maximize = true
        ).commands.take(2)
    }

    private fun selectRepairTarget(context: HsContext, posture: HsAssessment): UiCoordinate? {
        val main = context.mainPlantation()?.position?.toUi()
        if (main != null && main in posture.threatenedOwn) return main
        return posture.threatenedOwn
            .sortedWith(
                compareBy<UiCoordinate> { boolToOrder(it != main) }
                    .thenBy { context.ownPlantationByPosition[it]?.hp ?: Int.MAX_VALUE }
                    .thenBy { it.x }
                    .thenBy { it.y }
            )
            .firstOrNull()
    }

    private fun planPressure(
        context: HsContext,
        posture: HsAssessment,
        usedAuthors: Set<UiCoordinate>,
        outputUsage: Map<UiCoordinate, Int>
    ): List<HsPlannedActionCommand> {
        val target = posture.pressureTargets.firstOrNull() ?: return emptyList()
        val authors = context.operationalAuthors.filter { it !in usedAuthors }
        val assignments = planAssignmentsForTarget(
            target = target.position,
            authors = authors,
            outputUsage = outputUsage,
            context = context,
            basePower = HS_BASE_SABOTAGE_POWER,
            maximize = true
        )
        if (assignments.totalPower < minOf(target.hp, HS_BASE_SABOTAGE_POWER * 2)) {
            return emptyList()
        }
        val trimmed = trimCommandsToRequiredPower(assignments.commands, target.hp)
        return if (trimmed.size <= 2) trimmed else emptyList()
    }

    private fun trimCommandsToRequiredPower(
        commands: List<HsPlannedActionCommand>,
        requiredPower: Int
    ): List<HsPlannedActionCommand> {
        val trimmed = mutableListOf<HsPlannedActionCommand>()
        var total = 0
        for (command in commands) {
            trimmed += command
            total += command.effectivePower
            if (total >= requiredPower) break
        }
        return trimmed
    }

    private fun planExpansion(
        context: HsContext,
        usedAuthors: Set<UiCoordinate>,
        outputUsage: Map<UiCoordinate, Int>,
        plannedNewTargets: MutableSet<UiCoordinate>,
        maxCommands: Int,
        safeOnly: Boolean
    ): List<HsPlannedActionCommand> {
        val commands = mutableListOf<HsPlannedActionCommand>()
        val localUsedAuthors = usedAuthors.toMutableSet()
        val localOutputUsage = outputUsage.toMutableMap()

        while (commands.size < maxCommands) {
            val candidate = selectBestExpansionAssignment(
                context = context,
                usedAuthors = localUsedAuthors,
                outputUsage = localOutputUsage,
                plannedNewTargets = plannedNewTargets,
                safeOnly = safeOnly
            ) ?: break

            commands += candidate
            localUsedAuthors += candidate.author
            localOutputUsage[candidate.output] = (localOutputUsage[candidate.output] ?: 0) + 1
            if (candidate.target !in context.constructionPositions) {
                plannedNewTargets += candidate.target
            }
        }
        return commands
    }

    private fun selectBestExpansionAssignment(
        context: HsContext,
        usedAuthors: Set<UiCoordinate>,
        outputUsage: Map<UiCoordinate, Int>,
        plannedNewTargets: Set<UiCoordinate>,
        safeOnly: Boolean
    ): HsPlannedActionCommand? {
        if (context.shouldFreezeExpansionForHq(plannedNewTargets)) return null
        var best: HsScoredActionCandidate? = null

        for (author in context.operationalAuthors) {
            if (author in usedAuthors) continue

            val outputs = context.operationalOwnPositionsSorted.filter { output ->
                hsChebyshevDistance(author, output) <= context.signalRange
            }
            for (output in outputs) {
                val effectivePower = effectivePower(HS_BASE_CONSTRUCTION_SPEED, output, outputUsage)
                if (effectivePower <= 0) continue

                for (target in hsNeighborCandidates(output)) {
                    if (hsChebyshevDistance(output, target) > context.state.actionRange) continue
                    val targetType = when {
                        target in context.constructionPositions -> HsTargetType.EXISTING_CONSTRUCTION
                        target in plannedNewTargets -> HsTargetType.PLANNED_NEW_CONSTRUCTION
                        target.isBuildable(context) && canStartNewConstruction(context, plannedNewTargets) -> HsTargetType.NEW_CONSTRUCTION
                        else -> null
                    } ?: continue

                    if (safeOnly && context.isDangerousExpansion(target)) continue
                    if (context.isForbiddenExpansion(target)) continue

                    val score = scoreExpansionTarget(target, targetType, effectivePower, context)
                    val candidate = HsScoredActionCandidate(
                        command = HsPlannedActionCommand(author, output, target, effectivePower),
                        score = score
                    )
                    if (best == null || candidate > best!!) {
                        best = candidate
                    }
                }
            }
        }

        return best?.command
    }

    private fun planAssignmentsForTarget(
        target: UiCoordinate,
        authors: List<UiCoordinate>,
        outputUsage: Map<UiCoordinate, Int>,
        context: HsContext,
        basePower: Int,
        maximize: Boolean
    ): HsAssignmentPlan {
        val commands = mutableListOf<HsPlannedActionCommand>()
        val localUsedAuthors = mutableSetOf<UiCoordinate>()
        val localOutputUsage = outputUsage.toMutableMap()
        var totalPower = 0

        while (true) {
            val command = selectBestAssignmentForTarget(
                target = target,
                authors = authors,
                usedAuthors = localUsedAuthors,
                outputUsage = localOutputUsage,
                context = context,
                basePower = basePower
            ) ?: break

            commands += command
            localUsedAuthors += command.author
            localOutputUsage[command.output] = (localOutputUsage[command.output] ?: 0) + 1
            totalPower += command.effectivePower

            if (!maximize) break
        }

        return HsAssignmentPlan(commands, totalPower)
    }

    private fun selectBestAssignmentForTarget(
        target: UiCoordinate,
        authors: List<UiCoordinate>,
        usedAuthors: Set<UiCoordinate>,
        outputUsage: Map<UiCoordinate, Int>,
        context: HsContext,
        basePower: Int
    ): HsPlannedActionCommand? {
        var best: HsPlannedActionCommand? = null

        for (author in authors) {
            if (author in usedAuthors) continue
            if (author == target) continue

            val outputs = context.operationalOwnPositionsSorted.filter { output ->
                hsChebyshevDistance(author, output) <= context.signalRange &&
                    hsChebyshevDistance(output, target) <= context.state.actionRange
            }

            for (output in outputs) {
                val effectivePower = effectivePower(basePower, output, outputUsage)
                if (effectivePower <= 0) continue

                val candidate = HsPlannedActionCommand(author, output, target, effectivePower)
                if (best == null || candidate.isBetterThan(best!!, outputUsage)) {
                    best = candidate
                }
            }
        }

        return best
    }

    private fun scoreHqSupportTarget(target: UiCoordinate, context: HsContext): Int {
        var score = remainingCellScore(target, context) * 24
        score += connectedNeighborCount(target, context) * 2_400
        score += futureBranchingPotential(target, context) * 1_100
        if (isBoosted(target)) score += 3_500
        if (context.cellProgressAt(target) >= HIGH_TERRAFORMATION_PROGRESS) score -= 9_000
        if (context.isDangerousExpansion(target)) score -= 3_000
        return score
    }

    private fun scoreExpansionTarget(
        target: UiCoordinate,
        targetType: HsTargetType,
        effectivePower: Int,
        context: HsContext
    ): Int {
        val terraformProgress = context.cellProgressAt(target)
        val constructionProgress = context.constructionByPosition[target]?.progress ?: 0
        var score = remainingCellScore(target, context) * 28
        score += connectedNeighborCount(target, context) * 1_700
        score += futureBranchingPotential(target, context) * 900
        score += ringAffinity(target) * 320

        if (context.requiresEmergencyHqSupport()) {
            if (context.isAdjacentToMain(target)) {
                score += 15_000
            } else if (targetType == HsTargetType.NEW_CONSTRUCTION) {
                score -= 18_000
            }
        }

        when (targetType) {
            HsTargetType.EXISTING_CONSTRUCTION -> {
                score += 18_000
                score += constructionProgress * 280
            }

            HsTargetType.PLANNED_NEW_CONSTRUCTION -> score += 9_500

            HsTargetType.NEW_CONSTRUCTION -> {
                score -= terraformProgress * 120
                if (terraformProgress >= HIGH_TERRAFORMATION_PROGRESS) score -= 12_000
                else if (terraformProgress >= MID_TERRAFORMATION_PROGRESS) score -= 4_000
            }
        }

        if (isBoosted(target)) score += 13_000
        if (context.isBeaverDanger(target)) score -= 4_500
        if (context.isSandstormRisk(target)) score -= 2_600
        if (context.isEnemyThreat(target)) score -= 3_400
        if (context.isSevereDanger(target)) score -= 3_000
        score += effectivePower * 650
        return score
    }

    private fun ringAffinity(target: UiCoordinate): Int {
        val dx = minOf(target.x % 7, 7 - (target.x % 7))
        val dy = minOf(target.y % 7, 7 - (target.y % 7))
        return 6 - minOf(dx + dy, 6)
    }

    private fun canStartNewConstruction(context: HsContext, plannedNewTargets: Set<UiCoordinate>): Boolean {
        return context.activeSettlementCount + plannedNewTargets.size < context.effectiveSettlementLimit
    }

    private fun effectivePower(basePower: Int, output: UiCoordinate, outputUsage: Map<UiCoordinate, Int>): Int {
        return (basePower - (outputUsage[output] ?: 0)).coerceAtLeast(0)
    }

    private fun remainingCellScore(target: UiCoordinate, context: HsContext): Int {
        return ((100 - context.cellProgressAt(target)).coerceAtLeast(0)) * cellPointValue(target)
    }

    private fun connectedNeighborCount(target: UiCoordinate, context: HsContext): Int {
        return hsNeighborCandidates(target).count { it in context.ownPositions || it in context.constructionPositions }
    }

    private fun futureBranchingPotential(target: UiCoordinate, context: HsContext): Int {
        return hsNeighborCandidates(target).count { candidate ->
            candidate.isInside(context.state) &&
                candidate !in context.mountains &&
                candidate !in context.occupied
        }
    }

    private fun cellPointValue(target: UiCoordinate): Int {
        return if (isBoosted(target)) BOOSTED_CELL_POINT_VALUE else NORMAL_CELL_POINT_VALUE
    }

    private fun isBoosted(target: UiCoordinate): Boolean {
        return target.x % 7 == 0 && target.y % 7 == 0
    }

    private fun turnsUntilDisappears(progress: Int, terraformingSpeed: Int): Int {
        val remaining = (100 - progress).coerceAtLeast(0)
        return if (remaining == 0) 0 else hsCeilDiv(remaining, terraformingSpeed.coerceAtLeast(1))
    }

    private fun boolToOrder(value: Boolean): Int = if (value) 1 else 0
}

private data class HsContext(
    val state: ArenaState
) {
    val ownPlantationByPosition: Map<UiCoordinate, Plantation> = state.plantations.associateBy { it.position.toUi() }
    val ownPositions: Set<UiCoordinate> = ownPlantationByPosition.keys
    val operationalPlantations: List<Plantation> = state.plantations
        .asSequence()
        .filter { !it.isIsolated }
        .sortedWith(compareBy({ it.position.x }, { it.position.y }, { it.id }))
        .toList()
    val operationalAuthors: List<UiCoordinate> = operationalPlantations.map { it.position.toUi() }
    val operationalOwnPositionsSorted: List<UiCoordinate> = operationalAuthors.sortedWith(compareBy({ it.x }, { it.y }))
    val nonMainOperationalOwnPositions: Set<UiCoordinate> = operationalPlantations
        .filterNot { it.isMain }
        .mapTo(hashSetOf()) { it.position.toUi() }
    val enemyPlantations = state.enemy
    val enemyPositions: Set<UiCoordinate> = state.enemy.mapTo(hashSetOf()) { it.position.toUi() }
    val beavers: Set<UiCoordinate> = state.beavers.mapTo(hashSetOf()) { it.position.toUi() }
    val mountains: Set<UiCoordinate> = state.mountains.mapTo(hashSetOf()) { it.toUi() }
    val constructionByPosition = state.construction.associateBy { it.position.toUi() }
    val constructionPositions: Set<UiCoordinate> = constructionByPosition.keys
    val occupied: Set<UiCoordinate> = buildSet {
        addAll(ownPositions)
        addAll(enemyPositions)
        addAll(constructionPositions)
        addAll(beavers)
    }
    val cellByPosition = state.cells.associateBy { it.position.toUi() }
    val signalRange: Int = HS_BASE_SIGNAL_RANGE + (
        state.plantationUpgrades?.tiers?.firstOrNull { it.name == "signal_range" }?.current ?: 0
        )
    val effectiveSettlementLimit: Int = HS_BASE_SETTLEMENT_LIMIT + (
        state.plantationUpgrades?.tiers?.firstOrNull { it.name == "settlement_limit" }?.current ?: 0
        )
    val activeSettlementCount: Int = state.plantations.size + state.construction.size
    val frontierTargets: Set<UiCoordinate> = operationalOwnPositionsSorted
        .asSequence()
        .flatMap { output -> hsNeighborCandidates(output).asSequence() }
        .filter { it.isBuildable(this) }
        .toSet()
    val safeFrontierTargetCount: Int = frontierTargets.count { !isDangerousExpansion(it) }
    val boostedFrontierTargetCount: Int = frontierTargets.count { it.x % 7 == 0 && it.y % 7 == 0 }
    val frontierTargetCount: Int = frontierTargets.size
    val lowHpOperationalCount: Int = operationalPlantations.count { it.hp <= HS_HQ_DANGER_HP }
    val threatenedOperationalCount: Int = operationalPlantations.count { plantation ->
        val point = plantation.position.toUi()
        plantation.hp <= HS_THREATENED_HP || isDangerousExpansion(point)
    }
    val beaverThreatenedCount: Int = operationalPlantations.count { isBeaverDanger(it.position.toUi()) }
    val mainBeaverThreatened: Boolean = mainPlantation()?.position?.toUi()?.let(::isBeaverDanger) == true
    val mainInSevereDanger: Boolean = mainPlantation()?.position?.toUi()?.let(::isSevereDanger) == true
    val nearSettlementCap: Boolean = activeSettlementCount >= effectiveSettlementLimit - 3
    val degradingCellCount: Int = state.cells.count { it.turnsUntilDegradation <= 8 }
    val turnsToMainDisappear: Int = mainPlantation()?.position?.toUi()?.let { point ->
        hsTurnsUntilDisappears(cellProgressAt(point), mainTerraformSpeed())
    } ?: Int.MAX_VALUE
    val hasAdjacentOperationalSupportForMain: Boolean = mainPlantation()?.position?.toUi()?.let { point ->
        hsNeighborCandidates(point).any { it in nonMainOperationalOwnPositions }
    } == true

    fun cellProgressAt(point: UiCoordinate): Int = cellByPosition[point]?.terraformationProgress ?: 0

    fun mainPlantation(): Plantation? = state.plantations.firstOrNull { it.isMain && !it.isIsolated }

    fun isAdjacentToMain(target: UiCoordinate): Boolean {
        val mainPoint = mainPlantation()?.position?.toUi() ?: return false
        return hsNeighborCandidates(mainPoint).any { it == target }
    }

    fun requiresEmergencyHqSupport(): Boolean {
        if (hasAdjacentOperationalSupportForMain) return false
        return turnsToMainDisappear <= hqSupportDeadline()
    }

    fun shouldFreezeExpansionForHq(plannedNewTargets: Set<UiCoordinate>): Boolean {
        if (!requiresEmergencyHqSupport()) return false
        return plannedNewTargets.none { isAdjacentToMain(it) }
    }

    private fun hqSupportDeadline(): Int {
        return HS_BASE_HQ_TERRAFORM_BUFFER_TURNS + if (isEarthquakeImminent()) 1 else 0
    }

    private fun mainTerraformSpeed(): Int {
        return HS_BASE_TERRAFORM_SPEED + (
            state.plantationUpgrades?.tiers?.firstOrNull { it.name == "terraform_speed" }?.current ?: 0
            )
    }

    fun isEarthquakeImminent(): Boolean {
        return state.meteoForecasts.any { it.kind == "earthquake" && (it.turnsUntil ?: Int.MAX_VALUE) <= 1 }
    }

    fun isBeaverDanger(target: UiCoordinate): Boolean {
        return beavers.any { abs(it.x - target.x) <= 2 && abs(it.y - target.y) <= 2 }
    }

    fun isSandstormRisk(target: UiCoordinate): Boolean {
        return state.meteoForecasts.any { forecast ->
            if (!forecast.kind.contains("sand", ignoreCase = true)) return@any false
            if (forecast.forming == true) return@any false
            val position = forecast.nextPosition ?: forecast.position ?: return@any false
            val radius = forecast.radius ?: return@any false
            abs(target.x - position.x) <= radius && abs(target.y - position.y) <= radius
        }
    }

    fun isEnemyThreat(target: UiCoordinate): Boolean {
        return enemyPositions.any { abs(it.x - target.x) <= 2 && abs(it.y - target.y) <= 2 }
    }

    fun isDangerousExpansion(target: UiCoordinate): Boolean {
        return isBeaverDanger(target) || isSandstormRisk(target) || isEnemyThreat(target)
    }

    fun isSevereDanger(target: UiCoordinate): Boolean {
        val dangerCount = listOf(
            isBeaverDanger(target),
            isSandstormRisk(target),
            isEnemyThreat(target)
        ).count { it }
        return dangerCount >= 2 || (isBeaverDanger(target) && isEnemyThreat(target))
    }

    fun isForbiddenExpansion(target: UiCoordinate): Boolean {
        return isSevereDanger(target) && !isBoostedBridge(target)
    }

    private fun isBoostedBridge(target: UiCoordinate): Boolean {
        if (target.x % 7 == 0 && target.y % 7 == 0) return true
        return hsNeighborCandidates(target).any { it.x % 7 == 0 && it.y % 7 == 0 }
    }
}

private enum class HsMode {
    SURVIVAL,
    PRESSURE,
    SCORE
}

private data class HsAssessment(
    val mode: HsMode,
    val threatenedOwn: List<UiCoordinate>,
    val pressureTargets: List<HsPressureTarget>
)

private data class HsPressureTarget(
    val position: UiCoordinate,
    val hp: Int,
    val score: Int
)

private data class HsPlannedActionCommand(
    val author: UiCoordinate,
    val output: UiCoordinate,
    val target: UiCoordinate,
    val effectivePower: Int
)

private data class HsRelocateChoice(
    val from: UiCoordinate,
    val to: UiCoordinate
)

private data class HsHqPlan(
    val commands: List<HsPlannedActionCommand> = emptyList(),
    val relocate: HsRelocateChoice? = null,
    val plannedNewTargets: Set<UiCoordinate> = emptySet()
)

private data class HsAssignmentPlan(
    val commands: List<HsPlannedActionCommand>,
    val totalPower: Int
)

private data class HsScoredActionCandidate(
    val command: HsPlannedActionCommand,
    val score: Int
) : Comparable<HsScoredActionCandidate> {
    override fun compareTo(other: HsScoredActionCandidate): Int {
        return compareValuesBy(
            this,
            other,
            { it.score },
            { it.command.effectivePower },
            { -it.command.author.x },
            { -it.command.author.y },
            { -it.command.output.x },
            { -it.command.output.y },
            { -it.command.target.x },
            { -it.command.target.y }
        )
    }
}

private enum class HsTargetType {
    NEW_CONSTRUCTION,
    PLANNED_NEW_CONSTRUCTION,
    EXISTING_CONSTRUCTION
}

private fun HsPlannedActionCommand.isBetterThan(
    other: HsPlannedActionCommand,
    outputUsage: Map<UiCoordinate, Int>
): Boolean {
    val thisReuse = outputUsage[output] ?: 0
    val otherReuse = outputUsage[other.output] ?: 0
    if (effectivePower != other.effectivePower) return effectivePower > other.effectivePower
    if (thisReuse != otherReuse) return thisReuse < otherReuse
    if (author.x != other.author.x) return author.x < other.author.x
    if (author.y != other.author.y) return author.y < other.author.y
    if (output.x != other.output.x) return output.x < other.output.x
    if (output.y != other.output.y) return output.y < other.output.y
    return false
}

private fun UiCoordinate.isBuildable(context: HsContext): Boolean {
    return isInside(context.state) && this !in context.mountains && this !in context.occupied
}

private fun hsNeighborCandidates(point: UiCoordinate): List<UiCoordinate> = listOf(
    UiCoordinate(point.x, point.y - 1),
    UiCoordinate(point.x + 1, point.y),
    UiCoordinate(point.x, point.y + 1),
    UiCoordinate(point.x - 1, point.y)
)

private fun UiCoordinate.isInside(state: ArenaState): Boolean {
    return x >= 0 && y >= 0 && x < state.size.width && y < state.size.height
}

private fun Coordinate.toUi(): UiCoordinate = UiCoordinate(x, y)

private fun hsChebyshevDistance(from: UiCoordinate, to: UiCoordinate): Int {
    return maxOf(abs(from.x - to.x), abs(from.y - to.y))
}

private fun hsCeilDiv(value: Int, divisor: Int): Int {
    if (divisor <= 0) return Int.MAX_VALUE
    return (value + divisor - 1) / divisor
}

private fun hsTurnsUntilDisappears(progress: Int, terraformingSpeed: Int): Int {
    val remaining = (100 - progress).coerceAtLeast(0)
    return if (remaining == 0) 0 else hsCeilDiv(remaining, terraformingSpeed.coerceAtLeast(1))
}
