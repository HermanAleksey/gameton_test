package com.gameton.app.domain.capitan

import com.gameton.app.domain.model.ArenaState
import com.gameton.app.ui.model.CommandActionUi
import com.gameton.app.ui.model.CommandRequestUi
import com.gameton.app.ui.model.UiCoordinate

class SimpleExpansionDecisionMaker : DecisionMaker {
    override fun makeTurn(state: ArenaState): CommandRequestUi {
        val constructionPositions = state.construction.mapTo(hashSetOf()) { it.position.toUi() }
        val occupied = buildSet {
            state.plantations.forEach { add(it.position.toUi()) }
            state.enemy.forEach { add(it.position.toUi()) }
            state.construction.forEach { add(it.position.toUi()) }
            state.beavers.forEach { add(it.position.toUi()) }
        }
        val mountains = state.mountains.mapTo(hashSetOf()) { it.toUi() }

        val sortedPlantations = state.plantations
            .asSequence()
            .filter { !it.isIsolated }
            .sortedWith(compareBy({ it.position.x }, { it.position.y }, { it.id }))
            .toList()

        val source = sortedPlantations.firstNotNullOfOrNull { plantation ->
            val start = plantation.position.toUi()
            neighborCandidates(start)
                .firstOrNull { target -> target in constructionPositions }
                ?.let { target -> start to target }
        } ?: sortedPlantations.firstNotNullOfOrNull { plantation ->
            val start = plantation.position.toUi()
            neighborCandidates(start)
                .firstOrNull { target ->
                    target.isInside(state) &&
                        target !in mountains &&
                        target !in occupied
                }
                ?.let { target -> start to target }
        } ?: return CommandRequestUi()

        val (start, target) = source
        return CommandRequestUi(
            command = listOf(
                CommandActionUi(
                    path = listOf(start, start, target)
                )
            )
        )
    }
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

private fun com.gameton.app.domain.model.Coordinate.toUi(): UiCoordinate = UiCoordinate(x, y)
