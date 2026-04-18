package com.gameton.app.domain.capitan

import com.gameton.app.domain.model.ArenaState
import com.gameton.app.domain.model.Coordinate
import com.gameton.app.domain.model.Plantation
import com.gameton.app.ui.model.CommandActionUi
import com.gameton.app.ui.model.CommandRequestUi
import com.gameton.app.ui.model.UiCoordinate
import kotlin.math.abs

private const val T_BASE_SETTLEMENT_LIMIT = 30
private const val T_BASE_SIGNAL_RANGE = 3
private const val T_BASE_CONSTRUCTION_SPEED = 5
private const val T_BASE_REPAIR_SPEED = 5
private const val T_BASE_SABOTAGE_POWER = 5
private const val T_BASE_TERRAFORM_SPEED = 5
private const val T_BUILD_COMPLETION_HP = 50

class ThreatAwareStableExpansionDecisionMaker : DecisionMaker {
    private companion object {
        const val HIGH_TERRAFORMATION_PROGRESS = 80
        const val MID_TERRAFORMATION_PROGRESS = 60
        const val HQ_RELOCATE_TURN_THRESHOLD = 2
        const val HQ_CRITICAL_HP = 20
        const val THREATENED_HP = 18
        const val NORMAL_CELL_POINT_VALUE = 10
        const val BOOSTED_CELL_POINT_VALUE = 15
    }

    private var hqSupportTarget: UiCoordinate? = null

    override fun makeTurn(state: ArenaState): CommandRequestUi {
        val context = ThreatContext(state)
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

        val threatAssessment = analyzeThreats(context)
        val tacticalCommands = when (threatAssessment.mode) {
            StrategicMode.RETREAT -> planRetreat(context, threatAssessment, usedAuthors, outputUsage)
            StrategicMode.AGGRESSION -> planAggression(context, threatAssessment, usedAuthors, outputUsage)
            StrategicMode.EXPANSION -> emptyList()
        }
        tacticalCommands.forEach {
            usedAuthors += it.author
            outputUsage[it.output] = (outputUsage[it.output] ?: 0) + 1
        }

        val expansionCommands = when (threatAssessment.mode) {
            StrategicMode.RETREAT -> planExpansion(
                context = context,
                usedAuthors = usedAuthors,
                outputUsage = outputUsage,
                plannedNewTargets = hqPlan.plannedNewTargets.toMutableSet(),
                maxCommands = 1,
                safeOnly = true
            )

            StrategicMode.AGGRESSION -> planExpansion(
                context = context,
                usedAuthors = usedAuthors,
                outputUsage = outputUsage,
                plannedNewTargets = hqPlan.plannedNewTargets.toMutableSet(),
                maxCommands = 1,
                safeOnly = false
            )

            StrategicMode.EXPANSION -> planExpansion(
                context = context,
                usedAuthors = usedAuthors,
                outputUsage = outputUsage,
                plannedNewTargets = hqPlan.plannedNewTargets.toMutableSet(),
                maxCommands = Int.MAX_VALUE,
                safeOnly = false
            )
        }

        val allCommands = hqPlan.commands + tacticalCommands + expansionCommands
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

    private fun chooseUpgrade(context: ThreatContext): String? {
        val upgrades = context.state.plantationUpgrades ?: return null
        if (upgrades.points <= 0) return null

        val priority = buildList {
            if (context.isEarthquakeImminent()) add("earthquake_mitigation")
            if ((context.mainPlantation()?.hp ?: 0) <= HQ_CRITICAL_HP) add("max_hp")
            addAll(
                listOf(
                    "signal_range",
                    "max_hp",
                    "settlement_limit",
                    "repair_power",
                    "beaver_damage_mitigation",
                    "earthquake_mitigation",
                    "vision_range",
                    "decay_mitigation"
                )
            )
        }

        val tierByName = upgrades.tiers.associateBy { it.name }
        return priority.firstOrNull { name ->
            val tier = tierByName[name] ?: return@firstOrNull false
            tier.current < tier.max
        }
    }

    private fun planHqContinuity(
        context: ThreatContext,
        usedAuthors: MutableSet<UiCoordinate>,
        outputUsage: MutableMap<UiCoordinate, Int>
    ): ThreatHqPlan {
        val main = context.mainPlantation() ?: return ThreatHqPlan()
        val mainPoint = main.position.toUi()
        val turnsToHqDisappear = turnsUntilDisappears(context.cellProgressAt(mainPoint), T_BASE_TERRAFORM_SPEED)
        val supportCandidates = neighborCandidates(mainPoint)
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
                return ThreatHqPlan(relocate = relocation)
            }
        }

        if (hasAdjacentOperationalSupport) {
            hqSupportTarget = null
            return ThreatHqPlan()
        }

        val target = chooseHqSupportTarget(context, mainPoint) ?: return ThreatHqPlan()
        hqSupportTarget = target
        val remainingProgress = (T_BUILD_COMPLETION_HP - (context.constructionByPosition[target]?.progress ?: 0)).coerceAtLeast(0)
        if (remainingProgress == 0) return ThreatHqPlan()

        val authors = context.operationalAuthors.filter { it !in usedAuthors }
        if (authors.isEmpty()) return ThreatHqPlan()

        val requiredSafetyMargin = 1 + if (context.isEarthquakeImminent()) 1 else 0
        val dangerWindow = turnsToHqDisappear - requiredSafetyMargin
        val emergency = dangerWindow <= 1

        val selected = planAssignmentsForTarget(
            target = target,
            authors = authors,
            outputUsage = outputUsage,
            context = context,
            basePower = T_BASE_CONSTRUCTION_SPEED,
            maximize = emergency
        )
        if (selected.commands.isEmpty()) return ThreatHqPlan()

        selected.commands.forEach { command ->
            usedAuthors += command.author
            outputUsage[command.output] = (outputUsage[command.output] ?: 0) + 1
        }
        val plannedNewTargets = if (target !in context.constructionPositions) setOf(target) else emptySet()
        return ThreatHqPlan(commands = selected.commands, plannedNewTargets = plannedNewTargets)
    }

    private fun chooseMainRelocation(
        context: ThreatContext,
        supportCandidates: List<Pair<UiCoordinate, Plantation>>
    ): ThreatRelocateChoice? {
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
        return ThreatRelocateChoice(from = mainPoint, to = target)
    }

    private fun chooseHqSupportTarget(context: ThreatContext, mainPoint: UiCoordinate): UiCoordinate? {
        val adjacentConstructions = neighborCandidates(mainPoint)
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

        return neighborCandidates(mainPoint)
            .filter { it.isBuildable(context) && !context.isDangerousExpansion(it) }
            .ifEmpty { neighborCandidates(mainPoint).filter { it.isBuildable(context) } }
            .sortedWith(
                compareByDescending<UiCoordinate> { scoreHqSupportTarget(it, context) }
                    .thenBy { it.x }
                    .thenBy { it.y }
            )
            .firstOrNull()
    }

    private fun analyzeThreats(context: ThreatContext): ThreatAssessment {
        val main = context.mainPlantation()
        val threatenedOwn = context.operationalPlantations
            .filter { plantation ->
                val point = plantation.position.toUi()
                plantation.hp <= THREATENED_HP ||
                    context.isBeaverDanger(point) ||
                    context.isEnemyThreat(point) ||
                    context.isSandstormRisk(point)
            }
            .sortedWith(compareBy<Plantation>({ !it.isMain }, { it.hp }, { it.position.x }, { it.position.y }))

        val vulnerableEnemies = context.enemyPlantations
            .filter { enemy ->
                enemy.hp <= 20 || neighborCandidates(enemy.position.toUi()).any { it in context.ownPositions }
            }
            .sortedWith(compareBy({ it.hp }, { it.position.x }, { it.position.y }, { it.id }))

        val hqCritical = when {
            main == null -> true
            main.hp <= HQ_CRITICAL_HP -> true
            turnsUntilDisappears(context.cellProgressAt(main.position.toUi()), T_BASE_TERRAFORM_SPEED) <= HQ_RELOCATE_TURN_THRESHOLD -> true
            threatenedOwn.any { it.isMain } -> true
            else -> false
        }

        val mode = when {
            hqCritical || threatenedOwn.size >= 2 || context.isEarthquakeImminent() -> StrategicMode.RETREAT
            vulnerableEnemies.isNotEmpty() -> StrategicMode.AGGRESSION
            else -> StrategicMode.EXPANSION
        }

        return ThreatAssessment(
            mode = mode,
            threatenedOwn = threatenedOwn.map { it.position.toUi() },
            vulnerableEnemies = vulnerableEnemies.map { it.position.toUi() }
        )
    }

    private fun planRetreat(
        context: ThreatContext,
        threatAssessment: ThreatAssessment,
        usedAuthors: Set<UiCoordinate>,
        outputUsage: Map<UiCoordinate, Int>
    ): List<ThreatPlannedActionCommand> {
        val repairTarget = selectRetreatRepairTarget(context, threatAssessment) ?: return emptyList()
        val authors = context.operationalAuthors.filter { it !in usedAuthors && it != repairTarget }
        return planAssignmentsForTarget(
            target = repairTarget,
            authors = authors,
            outputUsage = outputUsage,
            context = context,
            basePower = T_BASE_REPAIR_SPEED,
            maximize = true
        ).commands
    }

    private fun selectRetreatRepairTarget(context: ThreatContext, threatAssessment: ThreatAssessment): UiCoordinate? {
        val main = context.mainPlantation()?.position?.toUi()
        if (main != null && main in threatAssessment.threatenedOwn) return main
        return threatAssessment.threatenedOwn
            .sortedWith(
                compareBy<UiCoordinate> { boolToOrder(it != main) }
                    .thenBy { context.ownPlantationByPosition[it]?.hp ?: Int.MAX_VALUE }
                    .thenBy { it.x }
                    .thenBy { it.y }
            )
            .firstOrNull()
    }

    private fun planAggression(
        context: ThreatContext,
        threatAssessment: ThreatAssessment,
        usedAuthors: Set<UiCoordinate>,
        outputUsage: Map<UiCoordinate, Int>
    ): List<ThreatPlannedActionCommand> {
        val target = threatAssessment.vulnerableEnemies.firstOrNull() ?: return emptyList()
        val authors = context.operationalAuthors.filter { it !in usedAuthors }
        val assignments = planAssignmentsForTarget(
            target = target,
            authors = authors,
            outputUsage = outputUsage,
            context = context,
            basePower = T_BASE_SABOTAGE_POWER,
            maximize = false
        )
        return assignments.commands.take(2)
    }

    private fun planExpansion(
        context: ThreatContext,
        usedAuthors: Set<UiCoordinate>,
        outputUsage: Map<UiCoordinate, Int>,
        plannedNewTargets: MutableSet<UiCoordinate>,
        maxCommands: Int,
        safeOnly: Boolean
    ): List<ThreatPlannedActionCommand> {
        val commands = mutableListOf<ThreatPlannedActionCommand>()
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
        context: ThreatContext,
        usedAuthors: Set<UiCoordinate>,
        outputUsage: Map<UiCoordinate, Int>,
        plannedNewTargets: Set<UiCoordinate>,
        safeOnly: Boolean
    ): ThreatPlannedActionCommand? {
        var best: ThreatScoredActionCandidate? = null

        for (author in context.operationalAuthors) {
            if (author in usedAuthors) continue

            val outputs = context.operationalOwnPositionsSorted.filter { output ->
                chebyshevDistance(author, output) <= context.signalRange
            }
            for (output in outputs) {
                val effectivePower = effectivePower(T_BASE_CONSTRUCTION_SPEED, output, outputUsage)
                if (effectivePower <= 0) continue

                for (target in neighborCandidates(output)) {
                    if (chebyshevDistance(output, target) > context.state.actionRange) continue
                    val targetType = when {
                        target in context.constructionPositions -> ThreatTargetType.EXISTING_CONSTRUCTION
                        target in plannedNewTargets -> ThreatTargetType.PLANNED_NEW_CONSTRUCTION
                        target.isBuildable(context) && canStartNewConstruction(context, plannedNewTargets) -> ThreatTargetType.NEW_CONSTRUCTION
                        else -> null
                    } ?: continue

                    if (safeOnly && context.isDangerousExpansion(target)) continue
                    val score = scoreExpansionTarget(target, targetType, effectivePower, context)
                    val candidate = ThreatScoredActionCandidate(
                        command = ThreatPlannedActionCommand(author, output, target, effectivePower),
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
        context: ThreatContext,
        basePower: Int,
        maximize: Boolean
    ): ThreatAssignmentPlan {
        val commands = mutableListOf<ThreatPlannedActionCommand>()
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

        return ThreatAssignmentPlan(commands, totalPower)
    }

    private fun selectBestAssignmentForTarget(
        target: UiCoordinate,
        authors: List<UiCoordinate>,
        usedAuthors: Set<UiCoordinate>,
        outputUsage: Map<UiCoordinate, Int>,
        context: ThreatContext,
        basePower: Int
    ): ThreatPlannedActionCommand? {
        var best: ThreatPlannedActionCommand? = null

        for (author in authors) {
            if (author in usedAuthors) continue
            if (author == target) continue

            val outputs = context.operationalOwnPositionsSorted.filter { output ->
                chebyshevDistance(author, output) <= context.signalRange &&
                    chebyshevDistance(output, target) <= context.state.actionRange
            }

            for (output in outputs) {
                val effectivePower = effectivePower(basePower, output, outputUsage)
                if (effectivePower <= 0) continue

                val candidate = ThreatPlannedActionCommand(author, output, target, effectivePower)
                if (best == null || candidate.isBetterThan(best!!, outputUsage)) {
                    best = candidate
                }
            }
        }

        return best
    }

    private fun scoreHqSupportTarget(target: UiCoordinate, context: ThreatContext): Int {
        var score = remainingCellScore(target, context) * 25
        score += connectedNeighborCount(target, context) * 2_200
        score += futureBranchingPotential(target, context) * 1_000
        if (isBoosted(target)) score += 4_000
        if (context.cellProgressAt(target) >= HIGH_TERRAFORMATION_PROGRESS) score -= 9_000
        if (context.isDangerousExpansion(target)) score -= 3_000
        return score
    }

    private fun scoreExpansionTarget(
        target: UiCoordinate,
        targetType: ThreatTargetType,
        effectivePower: Int,
        context: ThreatContext
    ): Int {
        val mapCenter = UiCoordinate(context.state.size.width / 2, context.state.size.height / 2)
        val centerDist = abs(target.x - mapCenter.x) + abs(target.y - mapCenter.y)
        val terraformProgress = context.cellProgressAt(target)
        val constructionProgress = context.constructionByPosition[target]?.progress ?: 0
        var score = remainingCellScore(target, context) * 20
        score += connectedNeighborCount(target, context) * 1_400
        score += futureBranchingPotential(target, context) * 700
        score -= centerDist * 6

        when (targetType) {
            ThreatTargetType.EXISTING_CONSTRUCTION -> {
                score += 12_000
                score += constructionProgress * 250
            }
            ThreatTargetType.PLANNED_NEW_CONSTRUCTION -> score += 8_000
            ThreatTargetType.NEW_CONSTRUCTION -> {
                score -= terraformProgress * 100
                if (terraformProgress >= HIGH_TERRAFORMATION_PROGRESS) score -= 10_000
                else if (terraformProgress >= MID_TERRAFORMATION_PROGRESS) score -= 3_000
            }
        }

        if (isBoosted(target)) score += 9_000
        if (context.isDangerousExpansion(target)) score -= 3_500
        score += effectivePower * 600
        return score
    }

    private fun canStartNewConstruction(context: ThreatContext, plannedNewTargets: Set<UiCoordinate>): Boolean {
        return context.activeSettlementCount + plannedNewTargets.size < context.effectiveSettlementLimit
    }

    private fun effectivePower(basePower: Int, output: UiCoordinate, outputUsage: Map<UiCoordinate, Int>): Int {
        return (basePower - (outputUsage[output] ?: 0)).coerceAtLeast(0)
    }

    private fun remainingCellScore(target: UiCoordinate, context: ThreatContext): Int {
        return ((100 - context.cellProgressAt(target)).coerceAtLeast(0)) * cellPointValue(target)
    }

    private fun connectedNeighborCount(target: UiCoordinate, context: ThreatContext): Int {
        return neighborCandidates(target).count { it in context.ownPositions || it in context.constructionPositions }
    }

    private fun futureBranchingPotential(target: UiCoordinate, context: ThreatContext): Int {
        return neighborCandidates(target).count { candidate ->
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
        return if (remaining == 0) 0 else ceilDiv(remaining, terraformingSpeed.coerceAtLeast(1))
    }

    private fun boolToOrder(value: Boolean): Int = if (value) 1 else 0
}

private data class ThreatContext(
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
    val signalRange: Int = T_BASE_SIGNAL_RANGE + (
        state.plantationUpgrades?.tiers?.firstOrNull { it.name == "signal_range" }?.current ?: 0
        )
    val effectiveSettlementLimit: Int = T_BASE_SETTLEMENT_LIMIT + (
        state.plantationUpgrades?.tiers?.firstOrNull { it.name == "settlement_limit" }?.current ?: 0
        )
    val activeSettlementCount: Int = state.plantations.size + state.construction.size

    fun cellProgressAt(point: UiCoordinate): Int = cellByPosition[point]?.terraformationProgress ?: 0

    fun mainPlantation(): Plantation? = state.plantations.firstOrNull { it.isMain && !it.isIsolated }

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
}

private enum class StrategicMode {
    RETREAT,
    AGGRESSION,
    EXPANSION
}

private data class ThreatAssessment(
    val mode: StrategicMode,
    val threatenedOwn: List<UiCoordinate>,
    val vulnerableEnemies: List<UiCoordinate>
)

private data class ThreatPlannedActionCommand(
    val author: UiCoordinate,
    val output: UiCoordinate,
    val target: UiCoordinate,
    val effectivePower: Int
)

private data class ThreatRelocateChoice(
    val from: UiCoordinate,
    val to: UiCoordinate
)

private data class ThreatHqPlan(
    val commands: List<ThreatPlannedActionCommand> = emptyList(),
    val relocate: ThreatRelocateChoice? = null,
    val plannedNewTargets: Set<UiCoordinate> = emptySet()
)

private data class ThreatAssignmentPlan(
    val commands: List<ThreatPlannedActionCommand>,
    val totalPower: Int
)

private data class ThreatScoredActionCandidate(
    val command: ThreatPlannedActionCommand,
    val score: Int
) : Comparable<ThreatScoredActionCandidate> {
    override fun compareTo(other: ThreatScoredActionCandidate): Int {
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

private enum class ThreatTargetType {
    NEW_CONSTRUCTION,
    PLANNED_NEW_CONSTRUCTION,
    EXISTING_CONSTRUCTION
}

private fun ThreatPlannedActionCommand.isBetterThan(
    other: ThreatPlannedActionCommand,
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

private fun UiCoordinate.isBuildable(context: ThreatContext): Boolean {
    return isInside(context.state) && this !in context.mountains && this !in context.occupied
}

private fun neighborCandidates(point: UiCoordinate): List<UiCoordinate> = listOf(
    UiCoordinate(point.x, point.y - 1),
    UiCoordinate(point.x + 1, point.y),
    UiCoordinate(point.x, point.y + 1),
    UiCoordinate(point.x - 1, point.y)
)

private fun UiCoordinate.isInside(state: ArenaState): Boolean {
    return x >= 0 && y >= 0 && x < state.size.width && y < state.size.height
}

private fun Coordinate.toUi(): UiCoordinate = UiCoordinate(x, y)

private fun chebyshevDistance(from: UiCoordinate, to: UiCoordinate): Int {
    return maxOf(abs(from.x - to.x), abs(from.y - to.y))
}

private fun ceilDiv(value: Int, divisor: Int): Int {
    if (divisor <= 0) return Int.MAX_VALUE
    return (value + divisor - 1) / divisor
}
