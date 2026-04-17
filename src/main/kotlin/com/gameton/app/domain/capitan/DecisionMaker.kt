package com.gameton.app.domain.capitan

import com.gameton.app.domain.model.ArenaState
import com.gameton.app.ui.model.CommandRequestUi

interface DecisionMaker {
    fun makeTurn(state: ArenaState): CommandRequestUi
}
