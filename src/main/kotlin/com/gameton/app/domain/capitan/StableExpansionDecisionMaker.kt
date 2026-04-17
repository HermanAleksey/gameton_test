package com.gameton.app.domain.capitan

import com.gameton.app.domain.model.ArenaState
import com.gameton.app.domain.model.Coordinate
import com.gameton.app.domain.model.Plantation
import com.gameton.app.ui.model.CommandActionUi
import com.gameton.app.ui.model.CommandRequestUi
import com.gameton.app.ui.model.UiCoordinate
import kotlin.math.abs

private const val BASE_SETTLEMENT_LIMIT = 30
private const val BASE_SIGNAL_RANGE = 3
private const val BASE_CONSTRUCTION_SPEED = 5
private const val BASE_TERRAFORM_SPEED = 5
private const val BUILD_COMPLETION_HP = 50

class StableExpansionDecisionMaker : DecisionMaker {
    private companion object {
        const val HIGH_TERRAFORMATION_PROGRESS = 80
        const val MID_TERRAFORMATION_PROGRESS = 60
        const val HQ_RELOCATE_TURN_THRESHOLD = 1
        const val STALLED_TURN_THRESHOLD = 2
        const val TARGET_COOLDOWN_TURNS = 6
        const val NORMAL_CELL_POINT_VALUE = 10
        const val BOOSTED_CELL_POINT_VALUE = 15
    }

    private var lastConstructionTarget: UiCoordinate? = null
    private var lastConstructionProgress: Int? = null
    private var stalledTurns: Int = 0
    private var lastObservedTurnNo: Int? = null
    private var hqSupportTarget: UiCoordinate? = null
    private val cooldownByTarget: MutableMap<UiCoordinate, Int> = mutableMapOf()

    override fun makeTurn(state: ArenaState): CommandRequestUi {
        val context = TurnContext(state)
        val isNewTurn = state.turnNo != lastObservedTurnNo

        if (isNewTurn) {
            decayCooldowns()
            updateConstructionStallState(context)
            lastObservedTurnNo = state.turnNo
        }

        val upgrade = chooseUpgrade(state)
        val usedAuthors = mutableSetOf<UiCoordinate>()
        val outputUsage = mutableMapOf<UiCoordinate, Int>()

        val hqPlan = planHqContinuity(context, usedAuthors, outputUsage)
        if (hqPlan.relocate != null) {
            return CommandRequestUi(
                plantationUpgrade = upgrade,
                relocateMain = listOf(hqPlan.relocate.from, hqPlan.relocate.to)
            )
        }

        val expansionCommands = planExpansion(
            context = context,
            usedAuthors = usedAuthors,
            outputUsage = outputUsage,
            plannedNewTargets = hqPlan.plannedNewTargets.toMutableSet()
        )

        val allCommands = hqPlan.commands + expansionCommands
        allCommands.firstOrNull()?.let { rememberIssuedTarget(it.target, context) }

        return when {
            allCommands.isNotEmpty() -> {
                CommandRequestUi(
                    command = allCommands.map { build ->
                        CommandActionUi(path = listOf(build.author, build.output, build.target))
                    },
                    plantationUpgrade = upgrade
                )
            }

            upgrade != null -> CommandRequestUi(plantationUpgrade = upgrade)
            else -> CommandRequestUi()
        }
    }

    private fun chooseUpgrade(state: ArenaState): String? {
        val upgrades = state.plantationUpgrades ?: return null
        if (upgrades.points <= 0) return null

        val earthquakeImminent = state.meteoForecasts.any {
            it.kind == "earthquake" && (it.turnsUntil ?: Int.MAX_VALUE) <= 1
        }
        val priority = buildList {
            if (earthquakeImminent) {
                add("earthquake_mitigation")
            }
            addAll(
                listOf(
                    "signal_range",
                    "max_hp",
                    "vision_range",
                    "settlement_limit",
                    "earthquake_mitigation",
                    "beaver_damage_mitigation",
                    "repair_power",
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
        context: TurnContext,
        usedAuthors: MutableSet<UiCoordinate>,
        outputUsage: MutableMap<UiCoordinate, Int>
    ): HqPlan {
        val main = context.mainPlantation() ?: return HqPlan()
        val mainPoint = main.position.toUi()
        val mainProgress = context.cellProgressAt(mainPoint)
        val turnsToHqDisappear = turnsUntilDisappears(mainProgress, BASE_TERRAFORM_SPEED)
        val hasAdjacentOperationalSupport = neighborCandidates(mainPoint).any {
            it in context.operationalOwnPositions && it != mainPoint
        }

        if (hasAdjacentOperationalSupport) {
            hqSupportTarget = null
            if (turnsToHqDisappear <= HQ_RELOCATE_TURN_THRESHOLD) {
                val relocation = chooseMainRelocation(context, mainPoint)
                if (relocation != null) {
                    return HqPlan(relocate = relocation)
                }
            }
            return HqPlan()
        }

        val target = chooseHqSupportTarget(context, mainPoint) ?: return HqPlan()
        hqSupportTarget = target

        val remainingProgress = (BUILD_COMPLETION_HP - (context.constructionByPosition[target]?.progress ?: 0)).coerceAtLeast(0)
        if (remainingProgress == 0) {
            return HqPlan()
        }

        val authors = context.operationalAuthors.filter { it !in usedAuthors }
        if (authors.isEmpty()) {
            return HqPlan()
        }

        val oneCommandPlan = planAssignmentsForTarget(
            target = target,
            authors = authors,
            context = context,
            outputUsage = outputUsage,
            maximize = false
        )
        val maxCommandPlan = planAssignmentsForTarget(
            target = target,
            authors = authors,
            context = context,
            outputUsage = outputUsage,
            maximize = true
        )

        val safetyMargin = 1 + if (context.isEarthquakeImminent()) 1 else 0
        val dangerWindow = turnsToHqDisappear - safetyMargin
        val turnsWithOne = if (oneCommandPlan.totalPower > 0) {
            ceilDiv(remainingProgress, oneCommandPlan.totalPower)
        } else {
            Int.MAX_VALUE
        }
        val emergency = dangerWindow <= 1 || turnsWithOne >= maxOf(dangerWindow, 1)
        val selectedPlan = if (emergency) maxCommandPlan else oneCommandPlan
        if (selectedPlan.commands.isEmpty()) {
            return HqPlan()
        }

        selectedPlan.commands.forEach { command ->
            usedAuthors += command.author
            outputUsage[command.output] = (outputUsage[command.output] ?: 0) + 1
        }

        val plannedNewTargets = if (target !in context.constructionPositions) setOf(target) else emptySet()
        return HqPlan(commands = selectedPlan.commands, plannedNewTargets = plannedNewTargets)
    }

    private fun chooseHqSupportTarget(context: TurnContext, mainPoint: UiCoordinate): UiCoordinate? {
        val adjacentConstructions = neighborCandidates(mainPoint)
            .filter { it in context.constructionPositions && !isOnCooldown(it) }
            .sortedWith(
                compareByDescending<UiCoordinate> { context.constructionByPosition[it]?.progress ?: 0 }
                    .thenBy { it.x }
                    .thenBy { it.y }
            )

        if (adjacentConstructions.isNotEmpty()) {
            return hqSupportTarget
                ?.takeIf { it in adjacentConstructions }
                ?: adjacentConstructions.first()
        }

        if (!canStartNewConstruction(context, plannedNewTargets = emptySet())) {
            return null
        }

        val candidates = neighborCandidates(mainPoint)
            .filter { !isOnCooldown(it) && it.isBuildable(context) }
        if (candidates.isEmpty()) {
            return null
        }

        val safer = candidates.filter { context.cellProgressAt(it) < HIGH_TERRAFORMATION_PROGRESS }
        val pool = if (safer.isNotEmpty()) safer else candidates
        return pool
            .sortedWith(
                compareByDescending<UiCoordinate> { scoreEmergencySupportTarget(it, context) }
                    .thenBy { it.x }
                    .thenBy { it.y }
            )
            .first()
    }

    private fun chooseMainRelocation(context: TurnContext, mainPoint: UiCoordinate): RelocateChoice? {
        val adjacentOperationalSupports = neighborCandidates(mainPoint)
            .mapNotNull { point ->
                val plantation = context.ownPlantationByPosition[point] ?: return@mapNotNull null
                if (plantation.isMain || plantation.isIsolated) return@mapNotNull null
                point to plantation
            }
        if (adjacentOperationalSupports.isEmpty()) return null

        val target = adjacentOperationalSupports
            .sortedWith(
                compareBy<Pair<UiCoordinate, Plantation>> { context.cellProgressAt(it.first) }
                    .thenBy { boolToOrder(isSandstormRisk(it.first, context)) }
                    .thenBy { boolToOrder(isBeaverDanger(it.first, context)) }
                    .thenByDescending { it.second.hp }
                    .thenBy { it.first.x }
                    .thenBy { it.first.y }
            )
            .first()
            .first

        hqSupportTarget = null
        return RelocateChoice(from = mainPoint, to = target)
    }

    private fun planExpansion(
        context: TurnContext,
        usedAuthors: MutableSet<UiCoordinate>,
        outputUsage: MutableMap<UiCoordinate, Int>,
        plannedNewTargets: MutableSet<UiCoordinate>
    ): List<PlannedBuildCommand> {
        val commands = mutableListOf<PlannedBuildCommand>()
        while (true) {
            val candidate = selectBestExpansionAssignment(
                context = context,
                usedAuthors = usedAuthors,
                outputUsage = outputUsage,
                plannedNewTargets = plannedNewTargets
            ) ?: break

            commands += candidate
            usedAuthors += candidate.author
            outputUsage[candidate.output] = (outputUsage[candidate.output] ?: 0) + 1

            val isExistingConstruction = candidate.target in context.constructionPositions
            if (!isExistingConstruction) {
                plannedNewTargets += candidate.target
            }
        }
        return commands
    }

    private fun selectBestExpansionAssignment(
        context: TurnContext,
        usedAuthors: Set<UiCoordinate>,
        outputUsage: Map<UiCoordinate, Int>,
        plannedNewTargets: Set<UiCoordinate>
    ): PlannedBuildCommand? {
        var best: ScoredBuildCandidate? = null

        for (author in context.operationalAuthors) {
            if (author in usedAuthors) continue

            val outputs = context.operationalOwnPositionsSorted.filter { output ->
                chebyshevDistance(author, output) <= context.signalRange
            }
            for (output in outputs) {
                val reuseCount = outputUsage[output] ?: 0
                val effectivePower = (BASE_CONSTRUCTION_SPEED - reuseCount).coerceAtLeast(0)
                if (effectivePower <= 0) continue

                for (target in neighborCandidates(output)) {
                    if (isOnCooldown(target)) continue
                    if (chebyshevDistance(output, target) > context.state.actionRange) continue

                    val targetType = when {
                        target in context.constructionPositions -> TargetType.EXISTING_CONSTRUCTION
                        target in plannedNewTargets -> TargetType.PLANNED_NEW_CONSTRUCTION
                        target.isBuildable(context) && canStartNewConstruction(context, plannedNewTargets) -> TargetType.NEW_CONSTRUCTION
                        else -> null
                    } ?: continue

                    val score = scoreExpansionTarget(
                        output = output,
                        target = target,
                        targetType = targetType,
                        effectivePower = effectivePower,
                        context = context
                    )

                    val candidate = ScoredBuildCandidate(
                        command = PlannedBuildCommand(author, output, target, effectivePower),
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
        context: TurnContext,
        outputUsage: Map<UiCoordinate, Int>,
        maximize: Boolean
    ): AssignmentPlan {
        val commands = mutableListOf<PlannedBuildCommand>()
        val localUsedAuthors = mutableSetOf<UiCoordinate>()
        val localOutputUsage = outputUsage.toMutableMap()
        var totalPower = 0

        while (true) {
            val candidate = selectBestAssignmentForTarget(
                target = target,
                authors = authors,
                usedAuthors = localUsedAuthors,
                outputUsage = localOutputUsage,
                context = context
            ) ?: break

            commands += candidate
            localUsedAuthors += candidate.author
            localOutputUsage[candidate.output] = (localOutputUsage[candidate.output] ?: 0) + 1
            totalPower += candidate.effectivePower

            if (!maximize) break
        }

        return AssignmentPlan(commands = commands, totalPower = totalPower)
    }

    private fun selectBestAssignmentForTarget(
        target: UiCoordinate,
        authors: List<UiCoordinate>,
        usedAuthors: Set<UiCoordinate>,
        outputUsage: Map<UiCoordinate, Int>,
        context: TurnContext
    ): PlannedBuildCommand? {
        var best: PlannedBuildCommand? = null

        for (author in authors) {
            if (author in usedAuthors) continue

            val outputs = context.operationalOwnPositionsSorted.filter { output ->
                chebyshevDistance(author, output) <= context.signalRange &&
                    chebyshevDistance(output, target) <= context.state.actionRange
            }

            for (output in outputs) {
                val reuseCount = outputUsage[output] ?: 0
                val effectivePower = (BASE_CONSTRUCTION_SPEED - reuseCount).coerceAtLeast(0)
                if (effectivePower <= 0) continue

                val candidate = PlannedBuildCommand(
                    author = author,
                    output = output,
                    target = target,
                    effectivePower = effectivePower
                )
                if (best == null || candidate.isBetterForTargetThan(best!!, outputUsage)) {
                    best = candidate
                }
            }
        }

        return best
    }

    private fun scoreEmergencySupportTarget(target: UiCoordinate, context: TurnContext): Int {
        val mapCenter = UiCoordinate(context.state.size.width / 2, context.state.size.height / 2)
        val centerDist = abs(target.x - mapCenter.x) + abs(target.y - mapCenter.y)
        val terraformProgress = context.cellProgressAt(target)
        var score = remainingCellScore(target, context) * 25
        score += connectedNeighborCount(target, context) * 2_400
        score += futureBranchingPotential(target, context) * 1_100
        score -= centerDist * 8

        if (isBoosted(target)) {
            score += 4_500
        }
        if (terraformProgress >= HIGH_TERRAFORMATION_PROGRESS) {
            score -= 10_000
        } else if (terraformProgress >= MID_TERRAFORMATION_PROGRESS) {
            score -= 3_000
        } else {
            score += (HIGH_TERRAFORMATION_PROGRESS - terraformProgress) * 45
        }

        if (isBeaverDanger(target, context)) score -= 2_500
        if (isSandstormRisk(target, context)) score -= 2_000
        return score
    }

    private fun scoreExpansionTarget(
        output: UiCoordinate,
        target: UiCoordinate,
        targetType: TargetType,
        effectivePower: Int,
        context: TurnContext
    ): Int {
        val mapCenter = UiCoordinate(context.state.size.width / 2, context.state.size.height / 2)
        val centerDist = abs(target.x - mapCenter.x) + abs(target.y - mapCenter.y)
        val terraformProgress = context.cellProgressAt(target)
        val constructionProgress = context.constructionByPosition[target]?.progress ?: 0
        var score = remainingCellScore(target, context) * 25
        score += connectedNeighborCount(target, context) * 1_800
        score += futureBranchingPotential(target, context) * 800
        score -= centerDist * 6

        when (targetType) {
            TargetType.EXISTING_CONSTRUCTION -> {
                score += 16_000
                score += constructionProgress * 320
                score += (BUILD_COMPLETION_HP - (BUILD_COMPLETION_HP - constructionProgress).coerceAtLeast(0)) * 80
            }

            TargetType.PLANNED_NEW_CONSTRUCTION -> {
                score += 10_000
            }

            TargetType.NEW_CONSTRUCTION -> {
                score -= terraformProgress * 110
                if (terraformProgress >= HIGH_TERRAFORMATION_PROGRESS) {
                    score -= 12_000
                } else if (terraformProgress >= MID_TERRAFORMATION_PROGRESS) {
                    score -= 4_000
                }
            }
        }
        if (isBoosted(target)) {
            score += 9_000
        }
        if (isBeaverDanger(target, context)) score -= 2_500
        if (isSandstormRisk(target, context)) score -= 2_000
        if (target == lastConstructionTarget) score += 1_200

        score += effectivePower * 650
        if (chebyshevDistance(output, target) > context.state.actionRange) {
            score -= 100_000
        }
        return score
    }

    private fun updateConstructionStallState(context: TurnContext) {
        val trackedTarget = lastConstructionTarget ?: return
        val currentProgress = context.constructionByPosition[trackedTarget]?.progress

        when {
            currentProgress == null -> {
                val finishedSuccessfully = trackedTarget in context.ownPositions
                if (!finishedSuccessfully) {
                    maybePutOnCooldown(trackedTarget, context)
                }
                clearTrackedConstruction()
            }

            lastConstructionProgress == null -> {
                lastConstructionProgress = currentProgress
            }

            currentProgress > lastConstructionProgress!! -> {
                stalledTurns = 0
                lastConstructionProgress = currentProgress
            }

            else -> {
                stalledTurns += 1
                if (stalledTurns >= STALLED_TURN_THRESHOLD) {
                    maybePutOnCooldown(trackedTarget, context)
                    clearTrackedConstruction()
                }
            }
        }
    }

    private fun rememberIssuedTarget(target: UiCoordinate, context: TurnContext) {
        if (lastConstructionTarget != target) {
            stalledTurns = 0
        }
        lastConstructionTarget = target
        lastConstructionProgress = context.constructionByPosition[target]?.progress
    }

    private fun clearTrackedConstruction() {
        lastConstructionTarget = null
        lastConstructionProgress = null
        stalledTurns = 0
    }

    private fun maybePutOnCooldown(target: UiCoordinate, context: TurnContext) {
        if (shouldCooldownTarget(target, context)) {
            putOnCooldown(target)
        }
    }

    private fun shouldCooldownTarget(target: UiCoordinate, context: TurnContext): Boolean {
        if (target == hqSupportTarget) return false
        val mainPoint = context.mainPlantation()?.position?.toUi() ?: return true
        return target !in neighborCandidates(mainPoint)
    }

    private fun putOnCooldown(target: UiCoordinate) {
        cooldownByTarget[target] = TARGET_COOLDOWN_TURNS
        if (hqSupportTarget == target) {
            hqSupportTarget = null
        }
    }

    private fun isOnCooldown(target: UiCoordinate): Boolean {
        return cooldownByTarget[target]?.let { it > 0 } == true
    }

    private fun decayCooldowns() {
        val iterator = cooldownByTarget.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val remaining = entry.value - 1
            if (remaining <= 0) {
                iterator.remove()
            } else {
                entry.setValue(remaining)
            }
        }
    }

    private fun canStartNewConstruction(context: TurnContext, plannedNewTargets: Set<UiCoordinate>): Boolean {
        return context.activeSettlementCount + plannedNewTargets.size < context.effectiveSettlementLimit
    }

    private fun isBeaverDanger(target: UiCoordinate, context: TurnContext): Boolean {
        return context.beavers.any {
            abs(it.x - target.x) <= 2 && abs(it.y - target.y) <= 2
        }
    }

    private fun isSandstormRisk(target: UiCoordinate, context: TurnContext): Boolean {
        return context.state.meteoForecasts.any { forecast ->
            if (forecast.kind != "sandstorm") return@any false
            if (forecast.forming == true) return@any false
            val position = forecast.nextPosition ?: forecast.position ?: return@any false
            val radius = forecast.radius ?: return@any false
            abs(target.x - position.x) <= radius && abs(target.y - position.y) <= radius
        }
    }

    private fun turnsUntilDisappears(progress: Int, terraformingSpeed: Int): Int {
        val remaining = (100 - progress).coerceAtLeast(0)
        return if (remaining == 0) 0 else ceilDiv(remaining, terraformingSpeed.coerceAtLeast(1))
    }

    private fun boolToOrder(value: Boolean): Int = if (value) 1 else 0

    private fun remainingCellScore(target: UiCoordinate, context: TurnContext): Int {
        return ((100 - context.cellProgressAt(target)).coerceAtLeast(0)) * cellPointValue(target)
    }

    private fun connectedNeighborCount(target: UiCoordinate, context: TurnContext): Int {
        return neighborCandidates(target).count { it in context.ownPositions || it in context.constructionPositions }
    }

    private fun futureBranchingPotential(target: UiCoordinate, context: TurnContext): Int {
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
}

private data class TurnContext(
    val state: ArenaState
) {
    val ownPlantationByPosition: Map<UiCoordinate, Plantation> =
        state.plantations.associateBy { it.position.toUi() }
    val ownPositions: Set<UiCoordinate> = ownPlantationByPosition.keys
    val operationalPlantations: List<Plantation> = state.plantations
        .asSequence()
        .filter { !it.isIsolated }
        .sortedWith(compareBy({ it.position.x }, { it.position.y }, { it.id }))
        .toList()
    val operationalAuthors: List<UiCoordinate> = operationalPlantations.map { it.position.toUi() }
    val operationalOwnPositions: Set<UiCoordinate> = operationalAuthors.toSet()
    val operationalOwnPositionsSorted: List<UiCoordinate> = operationalAuthors.sortedWith(compareBy({ it.x }, { it.y }))

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

    val signalRange: Int = BASE_SIGNAL_RANGE + (
        state.plantationUpgrades
            ?.tiers
            ?.firstOrNull { it.name == "signal_range" }
            ?.current
            ?: 0
        )

    val effectiveSettlementLimit: Int = BASE_SETTLEMENT_LIMIT + (
        state.plantationUpgrades
            ?.tiers
            ?.firstOrNull { it.name == "settlement_limit" }
            ?.current
            ?: 0
        )
    val activeSettlementCount: Int = state.plantations.size + state.construction.size

    fun cellProgressAt(point: UiCoordinate): Int = cellByPosition[point]?.terraformationProgress ?: 0

    fun isEarthquakeImminent(): Boolean {
        return state.meteoForecasts.any { it.kind == "earthquake" && (it.turnsUntil ?: Int.MAX_VALUE) <= 1 }
    }

    fun mainPlantation(): Plantation? = state.plantations.firstOrNull { it.isMain && !it.isIsolated }
}

private data class PlannedBuildCommand(
    val author: UiCoordinate,
    val output: UiCoordinate,
    val target: UiCoordinate,
    val effectivePower: Int
)

private data class RelocateChoice(
    val from: UiCoordinate,
    val to: UiCoordinate
)

private data class HqPlan(
    val commands: List<PlannedBuildCommand> = emptyList(),
    val relocate: RelocateChoice? = null,
    val plannedNewTargets: Set<UiCoordinate> = emptySet()
)

private data class AssignmentPlan(
    val commands: List<PlannedBuildCommand>,
    val totalPower: Int
)

private data class ScoredBuildCandidate(
    val command: PlannedBuildCommand,
    val score: Int
) : Comparable<ScoredBuildCandidate> {
    override fun compareTo(other: ScoredBuildCandidate): Int {
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

private enum class TargetType {
    NEW_CONSTRUCTION,
    PLANNED_NEW_CONSTRUCTION,
    EXISTING_CONSTRUCTION
}

private fun PlannedBuildCommand.isBetterForTargetThan(
    other: PlannedBuildCommand,
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

private fun UiCoordinate.isBuildable(context: TurnContext): Boolean {
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
