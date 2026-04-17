package com.gameton.app.ui.sample

import androidx.compose.ui.geometry.Offset
import com.gameton.app.ui.model.AlertViewModel
import com.gameton.app.ui.model.ArenaViewState
import com.gameton.app.ui.model.EntityKind
import com.gameton.app.ui.model.EntityViewModel
import com.gameton.app.ui.model.HighlightQuery
import com.gameton.app.ui.model.LayerToggle
import com.gameton.app.ui.model.LegendGroup
import com.gameton.app.ui.model.LegendItemViewModel
import com.gameton.app.ui.model.LegendKind
import com.gameton.app.ui.model.MapCellViewModel
import com.gameton.app.ui.model.MapLayerToggleState
import com.gameton.app.ui.model.RiskSeverity
import com.gameton.app.ui.model.TerrainType
import com.gameton.app.ui.theme.DashboardPalette
import com.gameton.app.ui.theme.iconStyle

object SampleArenaState {
    fun create(): ArenaViewState {
        val width = 22
        val height = 16
        val mountains = setOf(
            5 to 4, 6 to 4, 7 to 4, 13 to 9, 13 to 10, 14 to 10, 15 to 10, 17 to 4, 17 to 5
        )
        val terraformed = mapOf(
            (3 to 7) to 100,
            (4 to 7) to 75,
            (5 to 7) to 45,
            (10 to 11) to 60,
            (11 to 11) to 20,
            (16 to 6) to 90,
            (16 to 7) to 55
        )
        val degradationTurns = mapOf(
            (3 to 7) to 52,
            (4 to 7) to 14,
            (10 to 11) to 6,
            (16 to 6) to 3
        )

        val cells = buildList {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val terrain = when {
                        mountains.contains(x to y) -> TerrainType.Mountain
                        terraformed.containsKey(x to y) -> TerrainType.Oasis
                        else -> TerrainType.Desert
                    }
                    val progress = terraformed[x to y] ?: 0
                    val boosted = x % 7 == 0 && y % 7 == 0
                    val risk = when {
                        degradationTurns[x to y] != null && degradationTurns.getValue(x to y) <= 3 -> RiskSeverity.Critical
                        degradationTurns[x to y] != null && degradationTurns.getValue(x to y) <= 8 -> RiskSeverity.High
                        boosted -> RiskSeverity.Warning
                        else -> RiskSeverity.None
                    }
                    add(
                        MapCellViewModel(
                            x = x,
                            y = y,
                            terrainType = terrain,
                            terraformationProgress = progress,
                            turnsUntilDegradation = degradationTurns[x to y],
                            isBoosted = boosted,
                            riskLevel = risk
                        )
                    )
                }
            }
        }

        val entities = listOf(
            EntityViewModel(
                id = "hq-1",
                kind = EntityKind.MainPlantation,
                label = "HQ",
                position = Offset(3f, 7f),
                hp = 42,
                maxHp = 50,
                riskLevel = RiskSeverity.High,
                details = listOf("ЦУ сети", "2 клетки под угрозой бобров", "Штраф при потере всей сети")
            ),
            EntityViewModel(
                id = "own-1",
                kind = EntityKind.OwnPlantation,
                label = "P-04",
                position = Offset(4f, 7f),
                hp = 38,
                maxHp = 50,
                isImmune = true,
                turnsLeft = 2,
                riskLevel = RiskSeverity.Warning,
                details = listOf("Иммунитет после постройки", "Держит связность ветки")
            ),
            EntityViewModel(
                id = "iso-1",
                kind = EntityKind.IsolatedPlantation,
                label = "ISO-3",
                position = Offset(11f, 11f),
                hp = 19,
                maxHp = 50,
                riskLevel = RiskSeverity.Critical,
                details = listOf("Нет связи с ЦУ", "Очки не начисляются", "Деградация 10 HP/ход")
            ),
            EntityViewModel(
                id = "construction-1",
                kind = EntityKind.Construction,
                label = "Build",
                position = Offset(10f, 10f),
                progress = 34,
                riskLevel = RiskSeverity.High,
                details = listOf("Отдельный тип юнита", "Нужен прогресс в этот ход")
            ),
            EntityViewModel(
                id = "enemy-1",
                kind = EntityKind.EnemyPlantation,
                label = "EN-12",
                position = Offset(17f, 7f),
                hp = 28,
                maxHp = 50,
                riskLevel = RiskSeverity.Warning,
                details = listOf("В зоне boosted-клеток", "Потенциальная диверсия")
            ),
            EntityViewModel(
                id = "enemy-2",
                kind = EntityKind.EnemyPlantation,
                label = "EN-18",
                position = Offset(16f, 6f),
                hp = 41,
                maxHp = 50,
                riskLevel = RiskSeverity.None,
                details = listOf("Контролирует доходную клетку")
            ),
            EntityViewModel(
                id = "beaver-1",
                kind = EntityKind.BeaverLair,
                label = "Beaver Lair",
                position = Offset(6f, 8f),
                hp = 76,
                maxHp = 100,
                riskLevel = RiskSeverity.High,
                details = listOf("AR = 2", "Урон 15 HP/ход", "Не любит соседей")
            ),
            EntityViewModel(
                id = "storm-1",
                kind = EntityKind.Sandstorm,
                label = "Sandstorm Kappa",
                position = Offset(14f, 4f),
                turnsLeft = 1,
                riskLevel = RiskSeverity.Critical,
                details = listOf("Формируется 1 ход", "Затем пройдёт через центр карты")
            ),
            EntityViewModel(
                id = "quake-1",
                kind = EntityKind.Earthquake,
                label = "Earthquake",
                position = Offset(10f, 8f),
                turnsLeft = 4,
                riskLevel = RiskSeverity.Warning,
                details = listOf("Минус 10 HP всем постройкам и плантациям")
            )
        )

        val alerts = listOf(
            AlertViewModel(
                id = "alert-iso",
                severity = RiskSeverity.Critical,
                title = "Изолированная плантация",
                description = "ISO-3 без связи с ЦУ и теряет HP каждый ход.",
                target = Offset(11f, 11f),
                sourceKind = EntityKind.IsolatedPlantation
            ),
            AlertViewModel(
                id = "alert-storm",
                severity = RiskSeverity.High,
                title = "Буря рядом с центром",
                description = "Формирование завершится через 1 ход.",
                target = Offset(14f, 4f),
                sourceKind = EntityKind.Sandstorm
            ),
            AlertViewModel(
                id = "alert-build",
                severity = RiskSeverity.High,
                title = "Стройка под риском",
                description = "Если прогресса не будет, начнётся деградация.",
                target = Offset(10f, 10f),
                sourceKind = EntityKind.Construction
            ),
            AlertViewModel(
                id = "alert-beavers",
                severity = RiskSeverity.Warning,
                title = "Бобры давят на ветку HQ",
                description = "Две свои клетки входят в радиус атаки логова.",
                target = Offset(6f, 8f),
                sourceKind = EntityKind.BeaverLair
            )
        )

        val toggles = listOf(
            MapLayerToggleState("Сетка", true, LayerToggle.Grid),
            MapLayerToggleState("Терраформация", true, LayerToggle.Terraforming),
            MapLayerToggleState("Риски", true, LayerToggle.Risks),
            MapLayerToggleState("Boosted", true, LayerToggle.BoostedCells),
            MapLayerToggleState("AR", false, LayerToggle.RangeAr),
            MapLayerToggleState("SR", false, LayerToggle.RangeSr),
            MapLayerToggleState("VR", false, LayerToggle.RangeVr),
            MapLayerToggleState("Только свои", false, LayerToggle.OnlyOwn),
            MapLayerToggleState("Только враги", false, LayerToggle.OnlyEnemy),
            MapLayerToggleState("Только угрозы", false, LayerToggle.OnlyThreats),
            MapLayerToggleState("Только метео", false, LayerToggle.OnlyMeteo),
            MapLayerToggleState("Изоляция", false, LayerToggle.Isolated)
        )

        return ArenaViewState(
            turnNo = 184,
            nextTurnInSeconds = 0.42f,
            mapSize = width to height,
            connectionState = "Sync OK",
            errors = 1,
            warnings = 4,
            cells = cells,
            entities = entities,
            alerts = alerts,
            layerToggles = toggles,
            legendItems = legendItems()
        )
    }

    private fun legendItems(): List<LegendItemViewModel> = listOf(
        LegendItemViewModel(
            kind = LegendKind.Desert,
            group = LegendGroup.Terrain,
            label = "Пустыня",
            description = "Базовая клетка без прогресса.",
            iconStyle = iconStyle(DashboardPalette.DesertSoft, DashboardPalette.Desert),
            highlightQuery = HighlightQuery(terrainTypes = setOf(TerrainType.Desert))
        ),
        LegendItemViewModel(
            kind = LegendKind.Mountains,
            group = LegendGroup.Terrain,
            label = "Горы",
            description = "Нельзя строить и терраформировать.",
            iconStyle = iconStyle(DashboardPalette.Mountain),
            highlightQuery = HighlightQuery(terrainTypes = setOf(TerrainType.Mountain))
        ),
        LegendItemViewModel(
            kind = LegendKind.Terraforming,
            group = LegendGroup.Terrain,
            label = "Терраформация",
            description = "Заполнение клетки показывает прогресс.",
            iconStyle = iconStyle(DashboardPalette.Oasis, DashboardPalette.Desert),
            highlightQuery = HighlightQuery(terrainTypes = setOf(TerrainType.Oasis))
        ),
        LegendItemViewModel(
            kind = LegendKind.BoostedCell,
            group = LegendGroup.Terrain,
            label = "Усиленная клетка",
            description = "Даёт больше очков.",
            iconStyle = iconStyle(DashboardPalette.Boosted, DashboardPalette.Desert),
            severity = RiskSeverity.Warning,
            highlightQuery = HighlightQuery(boostedCells = true)
        ),
        LegendItemViewModel(
            kind = LegendKind.OwnPlantation,
            group = LegendGroup.Entities,
            label = "Своя плантация",
            description = "Основная рабочая единица сети.",
            iconStyle = iconStyle(DashboardPalette.Own),
            highlightQuery = HighlightQuery(entityKinds = setOf(EntityKind.OwnPlantation))
        ),
        LegendItemViewModel(
            kind = LegendKind.MainPlantation,
            group = LegendGroup.Entities,
            label = "ЦУ",
            description = "Потеря уничтожает всю сеть и даёт штраф.",
            iconStyle = iconStyle(DashboardPalette.Main, badge = DashboardPalette.Boosted),
            severity = RiskSeverity.High,
            highlightQuery = HighlightQuery(entityKinds = setOf(EntityKind.MainPlantation))
        ),
        LegendItemViewModel(
            kind = LegendKind.IsolatedPlantation,
            group = LegendGroup.Entities,
            label = "Изоляция",
            description = "Нет связи с ЦУ, очки не начисляются.",
            iconStyle = iconStyle(DashboardPalette.Own, stroke = DashboardPalette.Boosted),
            severity = RiskSeverity.Critical,
            highlightQuery = HighlightQuery(entityKinds = setOf(EntityKind.IsolatedPlantation))
        ),
        LegendItemViewModel(
            kind = LegendKind.EnemyPlantation,
            group = LegendGroup.Entities,
            label = "Враг",
            description = "Замеченная вражеская плантация.",
            iconStyle = iconStyle(DashboardPalette.Enemy),
            highlightQuery = HighlightQuery(entityKinds = setOf(EntityKind.EnemyPlantation))
        ),
        LegendItemViewModel(
            kind = LegendKind.Construction,
            group = LegendGroup.Entities,
            label = "Стройка",
            description = "Отдельный юнит, уязвим к деградации.",
            iconStyle = iconStyle(DashboardPalette.Construction),
            highlightQuery = HighlightQuery(entityKinds = setOf(EntityKind.Construction))
        ),
        LegendItemViewModel(
            kind = LegendKind.BeaverLair,
            group = LegendGroup.Entities,
            label = "Логово бобров",
            description = "Атакует всё в радиусе 2.",
            iconStyle = iconStyle(DashboardPalette.Beaver),
            severity = RiskSeverity.High,
            highlightQuery = HighlightQuery(entityKinds = setOf(EntityKind.BeaverLair))
        ),
        LegendItemViewModel(
            kind = LegendKind.Immunity,
            group = LegendGroup.Statuses,
            label = "Иммунитет",
            description = "Новая плантация защищена 3 хода.",
            iconStyle = iconStyle(DashboardPalette.Main, badge = DashboardPalette.Oasis),
            highlightQuery = HighlightQuery(entityKinds = setOf(EntityKind.OwnPlantation, EntityKind.MainPlantation))
        ),
        LegendItemViewModel(
            kind = LegendKind.LowHp,
            group = LegendGroup.Risks,
            label = "Низкий HP",
            description = "Пора лечить или отступать.",
            iconStyle = iconStyle(DashboardPalette.Enemy),
            severity = RiskSeverity.High,
            highlightQuery = HighlightQuery(riskAtLeast = RiskSeverity.High)
        ),
        LegendItemViewModel(
            kind = LegendKind.DegradationRisk,
            group = LegendGroup.Risks,
            label = "Риск деградации",
            description = "Без прогресса клетка или стройка проседает.",
            iconStyle = iconStyle(DashboardPalette.Desert, stroke = DashboardPalette.Earthquake),
            severity = RiskSeverity.Critical,
            highlightQuery = HighlightQuery(riskAtLeast = RiskSeverity.High)
        ),
        LegendItemViewModel(
            kind = LegendKind.FormingSandstorm,
            group = LegendGroup.Meteo,
            label = "Формирующаяся буря",
            description = "Скоро пойдёт по карте через центр.",
            iconStyle = iconStyle(DashboardPalette.Sandstorm, badge = DashboardPalette.Boosted),
            severity = RiskSeverity.High,
            highlightQuery = HighlightQuery(entityKinds = setOf(EntityKind.Sandstorm))
        ),
        LegendItemViewModel(
            kind = LegendKind.MovingSandstorm,
            group = LegendGroup.Meteo,
            label = "Движущаяся буря",
            description = "Урон по пути, но не убивает до 0.",
            iconStyle = iconStyle(DashboardPalette.Sandstorm, stroke = DashboardPalette.Desert),
            severity = RiskSeverity.Critical,
            highlightQuery = HighlightQuery(entityKinds = setOf(EntityKind.Sandstorm))
        ),
        LegendItemViewModel(
            kind = LegendKind.Earthquake,
            group = LegendGroup.Meteo,
            label = "Землетрясение",
            description = "Минус 10 HP всем постройкам и плантациям.",
            iconStyle = iconStyle(DashboardPalette.Earthquake),
            severity = RiskSeverity.Warning,
            highlightQuery = HighlightQuery(entityKinds = setOf(EntityKind.Earthquake))
        ),
        LegendItemViewModel(
            kind = LegendKind.GridLayer,
            group = LegendGroup.Layers,
            label = "Сетка",
            description = "Базовая навигация по полю.",
            iconStyle = iconStyle(DashboardPalette.Grid),
            highlightQuery = HighlightQuery(layer = LayerToggle.Grid)
        ),
        LegendItemViewModel(
            kind = LegendKind.RiskLayer,
            group = LegendGroup.Layers,
            label = "Слой рисков",
            description = "Угрозы читаются раньше доходности.",
            iconStyle = iconStyle(DashboardPalette.Earthquake),
            severity = RiskSeverity.High,
            highlightQuery = HighlightQuery(layer = LayerToggle.Risks)
        ),
        LegendItemViewModel(
            kind = LegendKind.BoostedLayer,
            group = LegendGroup.Layers,
            label = "Boosted overlay",
            description = "Подсветка доходных клеток.",
            iconStyle = iconStyle(DashboardPalette.Boosted),
            highlightQuery = HighlightQuery(layer = LayerToggle.BoostedCells)
        )
    )
}
