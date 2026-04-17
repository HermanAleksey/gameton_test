package com.gameton.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gameton.app.di.AppContainer
import com.gameton.app.ui.model.AlertViewModel
import com.gameton.app.ui.model.ArenaViewState
import com.gameton.app.ui.model.EntityKind
import com.gameton.app.ui.model.EntityViewModel
import com.gameton.app.ui.model.HighlightQuery
import com.gameton.app.ui.model.LayerToggle
import com.gameton.app.ui.model.LegendGroup
import com.gameton.app.ui.model.LegendItemViewModel
import com.gameton.app.ui.model.MapCellViewModel
import com.gameton.app.ui.model.MapLayerToggleState
import com.gameton.app.ui.model.RiskSeverity
import com.gameton.app.ui.model.TerrainType
import com.gameton.app.ui.theme.DashboardPalette
import com.gameton.app.ui.theme.DashboardTheme
import com.gameton.app.ui.theme.panelAlt
import com.gameton.app.ui.theme.severityColor
import kotlin.math.floor
import kotlin.math.min

private const val DESERT_TILE_RESOURCE = "/sprites/StoneBlock.png"
private const val MAP_VIEWPORT_WIDTH = 900f
private const val MAP_VIEWPORT_HEIGHT = 620f
private const val MIN_FOCUSED_ENTITY_ZOOM = 1.3f
private const val MAX_CAMERA_ZOOM = 8f
private const val TARGET_VISIBLE_CELLS_ON_FOCUS = 48f

@Composable
fun GametonDesktopApp(appContainer: AppContainer) {
    DashboardTheme {
        val arena by appContainer.gametonController.arenaState.collectAsState()
        var toggles by remember { mutableStateOf(arena.layerToggles.associate { it.kind to it.enabled }) }
        var selectedId by remember { mutableStateOf(arena.entities.firstOrNull { it.kind == EntityKind.MainPlantation }?.id) }
        var hoveredCell by remember { mutableStateOf<MapCellViewModel?>(null) }
        var hoveredEntityId by remember { mutableStateOf<String?>(null) }
        var legendHover by remember { mutableStateOf<HighlightQuery?>(null) }
        var pinnedLegend by remember { mutableStateOf<HighlightQuery?>(null) }
        var cameraScale by remember { mutableStateOf(1f) }
        var cameraOffset by remember { mutableStateOf(Offset.Zero) }
        var alertIndex by remember { mutableStateOf(0) }

        val selectedEntity = arena.entities.firstOrNull { it.id == selectedId }
        val activeHighlight = pinnedLegend ?: legendHover
        val ownEntities = remember(arena.entities) { sortedOwnEntities(arena.entities) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A1E1F), Color(0xFF131718))
                    )
                )
            ) {
                TopStatusBar(arena)
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LegendSidebar(
                        legendItems = arena.legendItems,
                        layerToggles = arena.layerToggles.map { it.copy(enabled = toggles[it.kind] == true) },
                        pinnedLegend = pinnedLegend,
                        onLegendHover = { legendHover = it },
                        onLegendPin = {
                            pinnedLegend = if (pinnedLegend == it) null else it
                        },
                        onToggleChanged = { kind, enabled ->
                            toggles = toggles.toMutableMap().also { it[kind] = enabled }
                        },
                        modifier = Modifier.width(280.dp).fillMaxHeight()
                    )
                    MapPanel(
                        arena = arena,
                        layerToggles = toggles,
                        selectedId = selectedId,
                        hoveredEntityId = hoveredEntityId,
                        hoveredCell = hoveredCell,
                        activeHighlight = activeHighlight,
                        cameraScale = cameraScale,
                        cameraOffset = cameraOffset,
                        onScaleChange = { cameraScale = it },
                        onOffsetChange = { cameraOffset = it },
                        onSelectEntity = { selectedId = it },
                        onHoverEntity = { hoveredEntityId = it },
                        onHoverCell = { hoveredCell = it },
                        ownNavigationEnabled = ownEntities.isNotEmpty(),
                        onHqZoom = {
                            ownEntities.firstOrNull { it.kind == EntityKind.MainPlantation }?.let { entity ->
                                focusEntity(entity, arena, MAP_VIEWPORT_WIDTH, MAP_VIEWPORT_HEIGHT)?.let { focus ->
                                    selectedId = focus.first
                                    cameraOffset = focus.second
                                    cameraScale = focusedZoom(arena)
                                }
                            }
                        },
                        onPrevOwn = {
                            nextOwnEntity(selectedId, ownEntities, direction = -1)?.let { entity ->
                                focusEntity(entity, arena, MAP_VIEWPORT_WIDTH, MAP_VIEWPORT_HEIGHT)?.let { focus ->
                                    selectedId = focus.first
                                    cameraOffset = focus.second
                                    cameraScale = focusedZoom(arena)
                                }
                            }
                        },
                        onNextOwn = {
                            nextOwnEntity(selectedId, ownEntities, direction = 1)?.let { entity ->
                                focusEntity(entity, arena, MAP_VIEWPORT_WIDTH, MAP_VIEWPORT_HEIGHT)?.let { focus ->
                                    selectedId = focus.first
                                    cameraOffset = focus.second
                                    cameraScale = focusedZoom(arena)
                                }
                            }
                        },
                        onFitMap = {
                            cameraScale = 1f
                            cameraOffset = Offset.Zero
                        },
                        onNextAlert = {
                            alertIndex = (alertIndex + 1) % arena.alerts.size
                            arena.alerts.getOrNull(alertIndex)?.target?.let { target ->
                                centerOnPoint(target, arena, MAP_VIEWPORT_WIDTH, MAP_VIEWPORT_HEIGHT)?.let {
                                    cameraOffset = it
                                    selectedId = arena.entities.minByOrNull { distance(it.position, target) }?.id
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    InspectorPanel(
                        entity = selectedEntity,
                        cell = hoveredCell,
                        modifier = Modifier.width(330.dp).fillMaxHeight()
                    )
                }
                AlertRail(
                    alerts = arena.alerts,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun TopStatusBar(arena: ArenaViewState) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(DashboardPalette.Panel)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("DatsSol Tactical Dashboard", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.onSurface)
        StatusPill("Turn ${arena.turnNo}", DashboardPalette.Main)
        StatusPill("Next ${"%.2f".format(arena.nextTurnInSeconds)}s", DashboardPalette.Boosted)
        StatusPill("AR ${arena.actionRange}", DashboardPalette.Construction)
        StatusPill(arena.connectionState, DashboardPalette.Oasis)
        StatusPill("Own ${arena.ownCount}", DashboardPalette.Own)
        StatusPill("Enemy ${arena.enemyCount}", DashboardPalette.Enemy)
        StatusPill("Build ${arena.constructionCount}", DashboardPalette.Construction)
        StatusPill("Beavers ${arena.beaverCount}", DashboardPalette.Beaver)
        StatusPill("Meteo ${arena.meteoCount}", DashboardPalette.Sandstorm)
        StatusPill(
            if (arena.turnsUntilUpgrade != null) "Upgrade ${arena.upgradePoints} in ${arena.turnsUntilUpgrade}t" else "Upgrade ${arena.upgradePoints}",
            DashboardPalette.Boosted
        )
        StatusPill("${arena.warnings} warnings", severityColor(RiskSeverity.Warning))
        StatusPill("${arena.errors} errors", severityColor(RiskSeverity.High))
        Spacer(Modifier.weight(1f))
        Text("Gamethon build", color = DashboardPalette.TextMuted, style = MaterialTheme.typography.body2)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = MaterialTheme.colors.onSurface, style = MaterialTheme.typography.caption)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun LegendSidebar(
    legendItems: List<LegendItemViewModel>,
    layerToggles: List<MapLayerToggleState>,
    pinnedLegend: HighlightQuery?,
    onLegendHover: (HighlightQuery?) -> Unit,
    onLegendPin: (HighlightQuery) -> Unit,
    onToggleChanged: (LayerToggle, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier.background(DashboardPalette.Panel, RoundedCornerShape(22.dp))
            .border(1.dp, Color(0x22FFF1D3), RoundedCornerShape(22.dp))
            .padding(14.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Легенда", style = MaterialTheme.typography.h6)
        Text(
            "Всегда под рукой: hover подсвечивает тип на карте, click фиксирует фильтр.",
            style = MaterialTheme.typography.body2,
            color = DashboardPalette.TextMuted
        )
        LegendGroup.entries.forEach { group ->
            val items = legendItems.filter { it.group == group }
            if (items.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        groupTitle(group),
                        color = DashboardPalette.Boosted,
                        style = MaterialTheme.typography.subtitle2
                    )
                    items.forEach { item ->
                        val pinned = pinnedLegend == item.highlightQuery
                        LegendItemCard(
                            item = item,
                            pinned = pinned,
                            onHover = onLegendHover,
                            onPin = onLegendPin
                        )
                    }
                }
            }
            if (group == LegendGroup.Layers) {
                Divider(color = Color(0x22FFF1D3))
                Text("Toggles", color = DashboardPalette.Boosted, style = MaterialTheme.typography.subtitle2)
                layerToggles.forEach { toggle ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colors.panelAlt, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = toggle.enabled,
                            onCheckedChange = { onToggleChanged(toggle.kind, it) }
                        )
                        Column {
                            Text(toggle.label, style = MaterialTheme.typography.body2)
                            Text(
                                toggleDescription(toggle.kind),
                                style = MaterialTheme.typography.caption,
                                color = DashboardPalette.TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LegendItemCard(
    item: LegendItemViewModel,
    pinned: Boolean,
    onHover: (HighlightQuery?) -> Unit,
    onPin: (HighlightQuery) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(
                if (pinned) MaterialTheme.colors.panelAlt.copy(alpha = 0.95f) else Color(0x0DFFFFFF),
                RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = if (pinned) severityColor(item.severity).copy(alpha = 0.7f) else Color(0x16FFF1D3),
                shape = RoundedCornerShape(14.dp)
            )
            .pointerMoveFilter(
                onEnter = {
                    onHover(item.highlightQuery)
                    false
                },
                onExit = {
                    onHover(null)
                    false
                }
            )
            .clickable { onPin(item.highlightQuery) }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(18.dp)
                .background(item.iconStyle.fill, RoundedCornerShape(6.dp))
                .border(
                    1.dp,
                    item.iconStyle.stroke.takeIf { it != Color.Transparent } ?: Color.Transparent,
                    RoundedCornerShape(6.dp))
        ) {
            item.iconStyle.badge?.let {
                Box(
                    modifier = Modifier.size(6.dp).align(Alignment.TopEnd)
                        .background(it, CircleShape)
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.label, style = MaterialTheme.typography.body2, fontWeight = FontWeight.SemiBold)
                if (item.severity != RiskSeverity.None) {
                    SeverityBadge(item.severity)
                }
            }
            Text(item.description, style = MaterialTheme.typography.caption, color = DashboardPalette.TextMuted)
        }
    }
}

@Composable
private fun SeverityBadge(severity: RiskSeverity) {
    val label = when (severity) {
        RiskSeverity.Warning -> "warn"
        RiskSeverity.High -> "high"
        RiskSeverity.Critical -> "critical"
        RiskSeverity.None -> return
    }
    Box(
        modifier = Modifier.background(severityColor(severity).copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .border(1.dp, severityColor(severity).copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(label, style = MaterialTheme.typography.overline, color = MaterialTheme.colors.onSurface)
    }
}

@Composable
private fun MapPanel(
    arena: ArenaViewState,
    layerToggles: Map<LayerToggle, Boolean>,
    selectedId: String?,
    hoveredEntityId: String?,
    hoveredCell: MapCellViewModel?,
    activeHighlight: HighlightQuery?,
    cameraScale: Float,
    cameraOffset: Offset,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit,
    onSelectEntity: (String?) -> Unit,
    onHoverEntity: (String?) -> Unit,
    onHoverCell: (MapCellViewModel?) -> Unit,
    ownNavigationEnabled: Boolean,
    onHqZoom: () -> Unit,
    onPrevOwn: () -> Unit,
    onNextOwn: () -> Unit,
    onFitMap: () -> Unit,
    onNextAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.background(DashboardPalette.Panel, RoundedCornerShape(22.dp))
            .border(1.dp, Color(0x22FFF1D3), RoundedCornerShape(22.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Карта", style = MaterialTheme.typography.h6)
            StatusPill("${arena.mapSize.first}x${arena.mapSize.second}", DashboardPalette.DesertSoft)
            Spacer(Modifier.weight(1f))
            MiniAction("HQ Zoom", onHqZoom, enabled = ownNavigationEnabled)
            MiniAction("Prev own", onPrevOwn, enabled = ownNavigationEnabled)
            MiniAction("Next own", onNextOwn, enabled = ownNavigationEnabled)
            MiniAction("Fit map", onFitMap)
            MiniAction("Next alert", onNextAlert)
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .background(Color(0xFF171B1C), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0x22FFF1D3), RoundedCornerShape(20.dp))
        ) {
            val tooltipTarget = arena.entities.firstOrNull { it.id == hoveredEntityId }
            TacticalMapCanvas(
                arena = arena,
                layerToggles = layerToggles,
                selectedId = selectedId,
                hoveredEntityId = hoveredEntityId,
                hoveredCell = hoveredCell,
                activeHighlight = activeHighlight,
                cameraScale = cameraScale,
                cameraOffset = cameraOffset,
                onScaleChange = onScaleChange,
                onOffsetChange = onOffsetChange,
                onSelectEntity = onSelectEntity,
                onHoverEntity = onHoverEntity,
                onHoverCell = onHoverCell,
                modifier = Modifier.fillMaxSize().padding(10.dp)
            )
            TooltipOverlay(
                entity = tooltipTarget,
                cell = hoveredCell,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MiniAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier.background(
            if (enabled) MaterialTheme.colors.panelAlt else MaterialTheme.colors.panelAlt.copy(alpha = 0.45f),
            RoundedCornerShape(999.dp)
        )
            .border(
                1.dp,
                if (enabled) Color(0x22FFF1D3) else Color(0x12FFF1D3),
                RoundedCornerShape(999.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.caption,
            color = if (enabled) MaterialTheme.colors.onSurface else DashboardPalette.TextMuted.copy(alpha = 0.8f)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TacticalMapCanvas(
    arena: ArenaViewState,
    layerToggles: Map<LayerToggle, Boolean>,
    selectedId: String?,
    hoveredEntityId: String?,
    hoveredCell: MapCellViewModel?,
    activeHighlight: HighlightQuery?,
    cameraScale: Float,
    cameraOffset: Offset,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit,
    onSelectEntity: (String?) -> Unit,
    onHoverEntity: (String?) -> Unit,
    onHoverCell: (MapCellViewModel?) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val desertTile = remember { loadBundledImage(DESERT_TILE_RESOURCE) }
    val filteredEntities = arena.entities.filter { entity ->
        when {
            layerToggles[LayerToggle.OnlyOwn] == true -> entity.kind in setOf(
                EntityKind.OwnPlantation,
                EntityKind.MainPlantation,
                EntityKind.IsolatedPlantation,
                EntityKind.Construction
            )

            layerToggles[LayerToggle.OnlyEnemy] == true -> entity.kind == EntityKind.EnemyPlantation
            layerToggles[LayerToggle.OnlyThreats] == true -> entity.kind in setOf(
                EntityKind.BeaverLair,
                EntityKind.Sandstorm,
                EntityKind.Earthquake,
                EntityKind.IsolatedPlantation
            )

            layerToggles[LayerToggle.OnlyMeteo] == true -> entity.kind in setOf(
                EntityKind.Sandstorm,
                EntityKind.Earthquake
            )

            layerToggles[LayerToggle.Isolated] == true -> entity.kind == EntityKind.IsolatedPlantation
            else -> true
        }
    }

    Canvas(
        modifier = modifier
            .onPointerEvent(PointerEventType.Scroll) {
                val delta = it.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                val next = (cameraScale * if (delta < 0f) 1.08f else 0.92f).coerceIn(0.65f, MAX_CAMERA_ZOOM)
                onScaleChange(next)
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onOffsetChange(cameraOffset + dragAmount)
                }
            }
            .pointerMoveFilter(
                onMove = { position ->
                    if (canvasSize == Size.Zero) return@pointerMoveFilter false
                    val cellSize = min(
                        canvasSize.width / arena.mapSize.first,
                        canvasSize.height / arena.mapSize.second
                    ) * cameraScale
                    val origin = mapOrigin(canvasSize, arena.mapSize, cellSize, cameraOffset)
                    val gx = floor((position.x - origin.x) / cellSize).toInt()
                    val gy = floor((position.y - origin.y) / cellSize).toInt()
                    val cell = arena.cells.firstOrNull { it.x == gx && it.y == gy }
                    onHoverCell(cell)
                    val entity = filteredEntities.lastOrNull {
                        val left = origin.x + it.position.x * cellSize
                        val top = origin.y + it.position.y * cellSize
                        Rect(Offset(left, top), Size(cellSize, cellSize)).contains(position)
                    }
                    onHoverEntity(entity?.id)
                    false
                },
                onExit = {
                    onHoverCell(null)
                    onHoverEntity(null)
                    false
                }
            )
            .onPointerEvent(PointerEventType.Press) { event ->
                val position = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                val cellSize =
                    min(canvasSize.width / arena.mapSize.first, canvasSize.height / arena.mapSize.second) * cameraScale
                val origin = mapOrigin(canvasSize, arena.mapSize, cellSize, cameraOffset)
                val hit = filteredEntities.lastOrNull {
                    val left = origin.x + it.position.x * cellSize
                    val top = origin.y + it.position.y * cellSize
                    Rect(Offset(left, top), Size(cellSize, cellSize)).contains(position)
                }
                onSelectEntity(hit?.id)
            }
    ) {
        canvasSize = size
        val cellSize = min(size.width / arena.mapSize.first, size.height / arena.mapSize.second) * cameraScale
        val origin = mapOrigin(size, arena.mapSize, cellSize, cameraOffset)

        clipRect {
            drawRoundRect(
                color = DashboardPalette.Desert.copy(alpha = 0.14f),
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f)
            )

            arena.cells.forEach { cell ->
                val topLeft = Offset(origin.x + cell.x * cellSize, origin.y + cell.y * cellSize)
                val baseColor = when (cell.terrainType) {
                    TerrainType.Desert -> DashboardPalette.Desert
                    TerrainType.Mountain -> DashboardPalette.Mountain
                    TerrainType.Oasis -> DashboardPalette.Desert
                }
                if (cell.terrainType == TerrainType.Desert || cell.terrainType == TerrainType.Oasis) {
                    drawDesertTile(
                        tile = desertTile,
                        topLeft = topLeft,
                        cellSize = cellSize,
                        fallbackColor = baseColor.copy(alpha = 0.88f)
                    )
                } else {
                    drawRect(
                        color = baseColor.copy(alpha = 0.78f),
                        topLeft = topLeft,
                        size = Size(cellSize, cellSize)
                    )
                }

                if (layerToggles[LayerToggle.Terraforming] == true && cell.terraformationProgress > 0 && cell.terrainType != TerrainType.Mountain) {
                    val fillHeight = cellSize * (cell.terraformationProgress / 100f)
                    drawRect(
                        color = DashboardPalette.Oasis.copy(alpha = 0.88f),
                        topLeft = Offset(topLeft.x, topLeft.y + cellSize - fillHeight),
                        size = Size(cellSize, fillHeight)
                    )
                }

                if (layerToggles[LayerToggle.BoostedCells] == true && cell.isBoosted) {
                    val inset = cellSize * 0.13f
                    val corner = cellSize * 0.2f
                    drawLine(
                        DashboardPalette.Boosted,
                        Offset(topLeft.x + inset, topLeft.y + inset),
                        Offset(topLeft.x + inset + corner, topLeft.y + inset),
                        2f
                    )
                    drawLine(
                        DashboardPalette.Boosted,
                        Offset(topLeft.x + inset, topLeft.y + inset),
                        Offset(topLeft.x + inset, topLeft.y + inset + corner),
                        2f
                    )
                    drawLine(
                        DashboardPalette.Boosted,
                        Offset(topLeft.x + cellSize - inset, topLeft.y + cellSize - inset),
                        Offset(topLeft.x + cellSize - inset - corner, topLeft.y + cellSize - inset),
                        2f
                    )
                    drawLine(
                        DashboardPalette.Boosted,
                        Offset(topLeft.x + cellSize - inset, topLeft.y + cellSize - inset),
                        Offset(topLeft.x + cellSize - inset, topLeft.y + cellSize - inset - corner),
                        2f
                    )
                }

                if (layerToggles[LayerToggle.Risks] == true && cell.riskLevel != RiskSeverity.None) {
                    drawRect(
                        color = severityColor(cell.riskLevel),
                        topLeft = topLeft + Offset(1f, 1f),
                        size = Size(cellSize - 2f, cellSize - 2f),
                        style = Stroke(
                            width = if (cell.riskLevel == RiskSeverity.Critical) 3f else 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 6f))
                        )
                    )
                }

                val isHighlighted = activeHighlight?.matches(cell) == true
                if (isHighlighted) {
                    drawRect(
                        color = DashboardPalette.Boosted.copy(alpha = 0.22f),
                        topLeft = topLeft,
                        size = Size(cellSize, cellSize)
                    )
                }

                if (hoveredCell?.x == cell.x && hoveredCell.y == cell.y) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.08f),
                        topLeft = topLeft,
                        size = Size(cellSize, cellSize)
                    )
                    drawRect(
                        Color.White.copy(alpha = 0.5f),
                        topLeft = topLeft,
                        size = Size(cellSize, cellSize),
                        style = Stroke(2f)
                    )
                }
            }

            if (layerToggles[LayerToggle.Grid] == true) {
                for (x in 0..arena.mapSize.first) {
                    val dx = origin.x + x * cellSize
                    drawLine(
                        DashboardPalette.Grid,
                        Offset(dx, origin.y),
                        Offset(dx, origin.y + arena.mapSize.second * cellSize),
                        1f
                    )
                }
                for (y in 0..arena.mapSize.second) {
                    val dy = origin.y + y * cellSize
                    drawLine(
                        DashboardPalette.Grid,
                        Offset(origin.x, dy),
                        Offset(origin.x + arena.mapSize.first * cellSize, dy),
                        1f
                    )
                }
            }

            filteredEntities.forEach { entity ->
                val topLeft = Offset(origin.x + entity.position.x * cellSize, origin.y + entity.position.y * cellSize)
                val center = Offset(topLeft.x + cellSize / 2f, topLeft.y + cellSize / 2f)
                drawEntity(entity, center, cellSize, selectedId, hoveredEntityId, activeHighlight, layerToggles)
            }

            if (layerToggles[LayerToggle.RangeAr] == true || layerToggles[LayerToggle.RangeSr] == true || layerToggles[LayerToggle.RangeVr] == true) {
                arena.entities.firstOrNull { it.id == selectedId }?.let { entity ->
                    val center = Offset(
                        origin.x + entity.position.x * cellSize + cellSize / 2f,
                        origin.y + entity.position.y * cellSize + cellSize / 2f
                    )
                    if (layerToggles[LayerToggle.RangeAr] == true) drawRange(
                        center,
                        cellSize,
                        2,
                        DashboardPalette.Construction
                    )
                    if (layerToggles[LayerToggle.RangeSr] == true) drawRange(
                        center,
                        cellSize,
                        3,
                        DashboardPalette.Boosted
                    )
                    if (layerToggles[LayerToggle.RangeVr] == true) drawRange(
                        center,
                        cellSize,
                        3,
                        DashboardPalette.Oasis.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

private fun loadBundledImage(path: String): ImageBitmap? {
    return object {}.javaClass.getResourceAsStream(path)
        ?.buffered()
        ?.use(::loadImageBitmap)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDesertTile(
    tile: ImageBitmap?,
    topLeft: Offset,
    cellSize: Float,
    fallbackColor: Color
) {
    if (tile == null) {
        drawRect(
            color = fallbackColor,
            topLeft = topLeft,
            size = Size(cellSize, cellSize)
        )
        return
    }

    drawImage(
        image = tile,
        dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
        dstSize = IntSize(cellSize.toInt().coerceAtLeast(1), cellSize.toInt().coerceAtLeast(1))
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEntity(
    entity: EntityViewModel,
    center: Offset,
    cellSize: Float,
    selectedId: String?,
    hoveredEntityId: String?,
    activeHighlight: HighlightQuery?,
    layerToggles: Map<LayerToggle, Boolean>
) {
    val radius = cellSize * 0.25f
    val color = when (entity.kind) {
        EntityKind.OwnPlantation -> DashboardPalette.Own
        EntityKind.MainPlantation -> DashboardPalette.Main
        EntityKind.IsolatedPlantation -> DashboardPalette.Own
        EntityKind.EnemyPlantation -> DashboardPalette.Enemy
        EntityKind.Construction -> DashboardPalette.Construction
        EntityKind.BeaverLair -> DashboardPalette.Beaver
        EntityKind.Sandstorm -> DashboardPalette.Sandstorm
        EntityKind.Earthquake -> DashboardPalette.Earthquake
    }

    when (entity.kind) {
        EntityKind.EnemyPlantation -> drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x, center.y - radius)
                lineTo(center.x + radius, center.y)
                lineTo(center.x, center.y + radius)
                lineTo(center.x - radius, center.y)
                close()
            },
            color = color
        )

        EntityKind.Construction -> drawRect(
            color,
            Offset(center.x - radius, center.y - radius),
            Size(radius * 2, radius * 2),
            style = Stroke(3f)
        )

        EntityKind.BeaverLair -> {
            drawCircle(color, radius * 1.1f, center, style = Stroke(5f))
            drawCircle(color.copy(alpha = 0.22f), radius * 1.1f, center)
            if (layerToggles[LayerToggle.Risks] == true) {
                drawCircle(color.copy(alpha = 0.12f), cellSize * 2.15f, center)
            }
        }

        EntityKind.Sandstorm -> {
            drawCircle(color.copy(alpha = 0.18f), cellSize * 1.35f, center)
            drawCircle(color, radius * 1.05f, center, style = Stroke(4f))
        }

        EntityKind.Earthquake -> {
            drawCircle(color.copy(alpha = 0.2f), radius * 1.2f, center)
            drawLine(color, center + Offset(-radius, -radius), center + Offset(radius, radius), 4f)
            drawLine(color, center + Offset(radius, -radius), center + Offset(-radius, radius), 4f)
        }

        else -> drawCircle(color, radius, center)
    }

    entity.maxHp?.let { maxHp ->
        entity.hp?.let { hp ->
            val sweep = 360f * (hp / maxHp.toFloat())
            drawArc(
                color = Color.White.copy(alpha = 0.7f),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius * 1.7f, center.y - radius * 1.7f),
                size = Size(radius * 3.4f, radius * 3.4f),
                style = Stroke(3f)
            )
        }
    }

    entity.progress?.let { progress ->
        val width = radius * 2.3f
        val progressWidth = width * (progress / 100f)
        drawRect(
            Color.White.copy(alpha = 0.18f),
            Offset(center.x - width / 2f, center.y + radius * 1.25f),
            Size(width, 5f)
        )
        drawRect(
            DashboardPalette.Construction,
            Offset(center.x - width / 2f, center.y + radius * 1.25f),
            Size(progressWidth, 5f)
        )
    }

    if (entity.isImmune) {
        drawCircle(DashboardPalette.Oasis.copy(alpha = 0.28f), radius * 1.7f, center)
    }

    if (entity.kind == EntityKind.IsolatedPlantation) {
        drawCircle(
            DashboardPalette.Boosted,
            radius * 1.55f,
            center,
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        )
    }

    if (layerToggles[LayerToggle.Risks] == true && entity.riskLevel != RiskSeverity.None) {
        drawCircle(
            severityColor(entity.riskLevel),
            radius * 2f,
            center,
            style = Stroke(width = if (entity.riskLevel == RiskSeverity.Critical) 4f else 3f)
        )
    }

    if (entity.id == selectedId || entity.id == hoveredEntityId || activeHighlight?.matches(entity) == true) {
        drawCircle(Color.White.copy(alpha = 0.85f), radius * 2.35f, center, style = Stroke(3f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRange(
    center: Offset,
    cellSize: Float,
    range: Int,
    color: Color
) {
    val extent = cellSize * (range * 2 + 1)
    drawRect(
        color = color.copy(alpha = 0.12f),
        topLeft = Offset(center.x - extent / 2f, center.y - extent / 2f),
        size = Size(extent, extent)
    )
    drawRect(
        color = color.copy(alpha = 0.6f),
        topLeft = Offset(center.x - extent / 2f, center.y - extent / 2f),
        size = Size(extent, extent),
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
    )
}

@Composable
private fun TooltipOverlay(entity: EntityViewModel?, cell: MapCellViewModel?, modifier: Modifier = Modifier) {
    if (entity == null && cell == null) return
    Column(
        modifier = modifier.widthIn(max = 240.dp)
            .background(Color(0xEE22282A), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x33FFF1D3), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        entity?.let {
            Text(it.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.body2)
            Text(
                "${entity.kind.readable()} • (${it.position.x.toInt()}, ${it.position.y.toInt()})",
                style = MaterialTheme.typography.caption,
                color = DashboardPalette.TextMuted
            )
            it.hp?.let { hp -> Text("HP $hp/${it.maxHp ?: "?"}", style = MaterialTheme.typography.caption) }
            it.progress?.let { progress -> Text("Progress $progress%", style = MaterialTheme.typography.caption) }
            if (it.isImmune) Text("Иммунитет: ${it.turnsLeft ?: 0} хода", style = MaterialTheme.typography.caption)
            it.details.take(2).forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.caption,
                    color = DashboardPalette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } ?: cell?.let {
            Text("Cell (${it.x}, ${it.y})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.body2)
            Text(
                it.terrainType.readable(),
                style = MaterialTheme.typography.caption,
                color = DashboardPalette.TextMuted
            )
            if (it.terraformationProgress > 0) Text(
                "Terraformation ${it.terraformationProgress}%",
                style = MaterialTheme.typography.caption
            )
            if (it.isBoosted) Text("Boosted cell", style = MaterialTheme.typography.caption)
            it.turnsUntilDegradation?.let { turns ->
                Text(
                    "Degeneration in $turns",
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}

@Composable
private fun InspectorPanel(entity: EntityViewModel?, cell: MapCellViewModel?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(DashboardPalette.Panel, RoundedCornerShape(22.dp))
            .border(1.dp, Color(0x22FFF1D3), RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Inspector", style = MaterialTheme.typography.h6)
        if (entity == null && cell == null) {
            Text(
                "Выберите объект на карте. Hover даст короткий контекст, selection закрепит карточку.",
                color = DashboardPalette.TextMuted
            )
            return
        }

        entity?.let {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(it.label, style = MaterialTheme.typography.h6, color = MaterialTheme.colors.onSurface)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(it.kind.readable(), entityColor(it.kind))
                    StatusPill("(${it.position.x.toInt()}, ${it.position.y.toInt()})", DashboardPalette.DesertSoft)
                    if (it.riskLevel != RiskSeverity.None) StatusPill(
                        it.riskLevel.name.lowercase(),
                        severityColor(it.riskLevel)
                    )
                }
                it.hp?.let { hp -> MetricRow("HP", "$hp / ${it.maxHp ?: "?"}") }
                it.progress?.let { progress -> MetricRow("Прогресс", "$progress%") }
                if (it.isImmune) MetricRow("Иммунитет", "${it.turnsLeft ?: 0} хода")
                if (it.turnsLeft != null && !it.isImmune) MetricRow("До события", "${it.turnsLeft} хода")
                Divider(color = Color(0x22FFF1D3))
                Text("Риски", fontWeight = FontWeight.SemiBold)
                if (it.riskLevel == RiskSeverity.None) {
                    Text(
                        "Явных критичных рисков сейчас нет.",
                        color = DashboardPalette.TextMuted,
                        style = MaterialTheme.typography.body2
                    )
                } else {
                    Text(riskSummary(it), style = MaterialTheme.typography.body2)
                }
                Divider(color = Color(0x22FFF1D3))
                Text("Контекст", fontWeight = FontWeight.SemiBold)
                it.details.forEach { detail ->
                    Text("• $detail", style = MaterialTheme.typography.body2, color = DashboardPalette.TextMuted)
                }
            }
        }

        if (entity == null) {
            cell?.let {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cell (${it.x}, ${it.y})", style = MaterialTheme.typography.h6)
                    MetricRow("Terrain", it.terrainType.readable())
                    MetricRow("Progress", "${it.terraformationProgress}%")
                    MetricRow("Boosted", if (it.isBoosted) "Да" else "Нет")
                    MetricRow("Risk", it.riskLevel.name.lowercase())
                    it.turnsUntilDegradation?.let { turns -> MetricRow("Degradation", "$turns turns") }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = DashboardPalette.TextMuted, style = MaterialTheme.typography.body2)
        Text(value, style = MaterialTheme.typography.body2, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AlertRail(alerts: List<AlertViewModel>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(DashboardPalette.Panel, RoundedCornerShape(22.dp))
            .border(1.dp, Color(0x22FFF1D3), RoundedCornerShape(22.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        alerts.forEach { alert ->
            Column(
                modifier = Modifier.weight(1f).background(
                    severityColor(alert.severity).copy(alpha = 0.12f),
                    RoundedCornerShape(16.dp)
                ).border(1.dp, severityColor(alert.severity).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(10.dp)
            ) {
                Text(alert.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    alert.description,
                    style = MaterialTheme.typography.body2,
                    color = DashboardPalette.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun HighlightQuery.matches(cell: MapCellViewModel): Boolean {
    if (terrainTypes.isNotEmpty() && cell.terrainType !in terrainTypes) return false
    if (boostedCells && !cell.isBoosted) return false
    if (riskAtLeast != null && cell.riskLevel.ordinal < riskAtLeast.ordinal) return false
    return terrainTypes.isNotEmpty() || boostedCells || riskAtLeast != null
}

private fun HighlightQuery.matches(entity: EntityViewModel): Boolean {
    if (entityKinds.isNotEmpty() && entity.kind !in entityKinds) return false
    if (riskAtLeast != null && entity.riskLevel.ordinal < riskAtLeast.ordinal) return false
    return entityKinds.isNotEmpty() || riskAtLeast != null
}

private fun mapOrigin(canvasSize: Size, mapSize: Pair<Int, Int>, cellSize: Float, cameraOffset: Offset): Offset {
    val mapWidth = mapSize.first * cellSize
    val mapHeight = mapSize.second * cellSize
    return Offset(
        x = (canvasSize.width - mapWidth) / 2f + cameraOffset.x,
        y = (canvasSize.height - mapHeight) / 2f + cameraOffset.y
    )
}

private fun centerOnEntity(
    entity: EntityViewModel,
    arena: ArenaViewState,
    viewportWidth: Float,
    viewportHeight: Float
): Offset? =
    centerOnPoint(entity.position, arena, viewportWidth, viewportHeight)

private fun centerOnPoint(point: Offset, arena: ArenaViewState, viewportWidth: Float, viewportHeight: Float): Offset? {
    val cellSize = min(viewportWidth / arena.mapSize.first, viewportHeight / arena.mapSize.second)
    val mapWidth = arena.mapSize.first * cellSize
    val mapHeight = arena.mapSize.second * cellSize
    val mapStart = Offset((viewportWidth - mapWidth) / 2f, (viewportHeight - mapHeight) / 2f)
    val pointCenter =
        Offset(mapStart.x + point.x * cellSize + cellSize / 2f, mapStart.y + point.y * cellSize + cellSize / 2f)
    return Offset(viewportWidth / 2f - pointCenter.x, viewportHeight / 2f - pointCenter.y)
}

private fun focusEntity(
    entity: EntityViewModel,
    arena: ArenaViewState,
    viewportWidth: Float,
    viewportHeight: Float
): Pair<String, Offset>? {
    val offset = centerOnEntity(entity, arena, viewportWidth, viewportHeight) ?: return null
    return entity.id to offset
}

private fun isOwnEntity(kind: EntityKind): Boolean = when (kind) {
    EntityKind.MainPlantation,
    EntityKind.OwnPlantation,
    EntityKind.IsolatedPlantation,
    EntityKind.Construction -> true

    else -> false
}

private fun sortedOwnEntities(entities: List<EntityViewModel>): List<EntityViewModel> {
    val priority = mapOf(
        EntityKind.MainPlantation to 0,
        EntityKind.OwnPlantation to 1,
        EntityKind.IsolatedPlantation to 1,
        EntityKind.Construction to 2
    )
    return entities
        .asSequence()
        .filter { isOwnEntity(it.kind) }
        .sortedWith(
            compareBy<EntityViewModel>(
                { priority[it.kind] ?: Int.MAX_VALUE },
                { it.position.y },
                { it.position.x },
                { it.id }
            )
        )
        .toList()
}

private fun nextOwnEntity(currentId: String?, ownEntities: List<EntityViewModel>, direction: Int): EntityViewModel? {
    if (ownEntities.isEmpty()) return null
    val currentIndex = ownEntities.indexOfFirst { it.id == currentId }
    val startIndex = if (currentIndex == -1) {
        ownEntities.indexOfFirst { it.kind == EntityKind.MainPlantation }.takeIf { it >= 0 } ?: 0
    } else {
        currentIndex
    }
    val nextIndex = (startIndex + direction).mod(ownEntities.size)
    return ownEntities[nextIndex]
}

private fun focusedZoom(arena: ArenaViewState): Float {
    val largestDimension = maxOf(arena.mapSize.first, arena.mapSize.second).toFloat()
    val adaptiveZoom = largestDimension / TARGET_VISIBLE_CELLS_ON_FOCUS
    return adaptiveZoom.coerceIn(MIN_FOCUSED_ENTITY_ZOOM, MAX_CAMERA_ZOOM)
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}

private fun groupTitle(group: LegendGroup): String = when (group) {
    LegendGroup.Terrain -> "Террейн"
    LegendGroup.Entities -> "Сущности"
    LegendGroup.Statuses -> "Статусы"
    LegendGroup.Risks -> "Риски"
    LegendGroup.Meteo -> "Катаклизмы"
    LegendGroup.Layers -> "Слои"
}

private fun toggleDescription(kind: LayerToggle): String = when (kind) {
    LayerToggle.Grid -> "Чёткая привязка по клеткам."
    LayerToggle.Terraforming -> "Прогресс оазисов внутри клетки."
    LayerToggle.Risks -> "Контуры угроз и деградации."
    LayerToggle.BoostedCells -> "Клетки x,y кратны 7."
    LayerToggle.RangeAr -> "AR выбранного объекта."
    LayerToggle.RangeSr -> "SR выбранного объекта."
    LayerToggle.RangeVr -> "VR выбранного объекта."
    LayerToggle.OnlyOwn -> "Оставить свои сущности."
    LayerToggle.OnlyEnemy -> "Оставить замеченных врагов."
    LayerToggle.OnlyThreats -> "Оставить бобров, бури и изоляцию."
    LayerToggle.OnlyMeteo -> "Оставить только катаклизмы."
    LayerToggle.Isolated -> "Оставить изолированные точки."
}

private fun EntityKind.readable(): String = when (this) {
    EntityKind.OwnPlantation -> "Своя плантация"
    EntityKind.MainPlantation -> "ЦУ"
    EntityKind.IsolatedPlantation -> "Изоляция"
    EntityKind.EnemyPlantation -> "Враг"
    EntityKind.Construction -> "Стройка"
    EntityKind.BeaverLair -> "Логово бобров"
    EntityKind.Sandstorm -> "Песчаная буря"
    EntityKind.Earthquake -> "Землетрясение"
}

private fun TerrainType.readable(): String = when (this) {
    TerrainType.Desert -> "Пустыня"
    TerrainType.Mountain -> "Горы"
    TerrainType.Oasis -> "Оазис"
}

private fun entityColor(kind: EntityKind): Color = when (kind) {
    EntityKind.OwnPlantation -> DashboardPalette.Own
    EntityKind.MainPlantation -> DashboardPalette.Main
    EntityKind.IsolatedPlantation -> DashboardPalette.Boosted
    EntityKind.EnemyPlantation -> DashboardPalette.Enemy
    EntityKind.Construction -> DashboardPalette.Construction
    EntityKind.BeaverLair -> DashboardPalette.Beaver
    EntityKind.Sandstorm -> DashboardPalette.Sandstorm
    EntityKind.Earthquake -> DashboardPalette.Earthquake
}

private fun riskSummary(entity: EntityViewModel): String = when (entity.kind) {
    EntityKind.MainPlantation -> "ЦУ под давлением: потеря этой точки разрушит всю сеть и даст штраф по очкам."
    EntityKind.IsolatedPlantation -> "Изоляция уже отрезала точку от ЦУ: команда не проходит, очки не идут, деградация активна."
    EntityKind.Construction -> "Стройка не завершена и требует прогресса в ближайший ход, иначе начнётся деградация."
    EntityKind.BeaverLair -> "Логово держит активную зону урона вокруг себя и ограничивает безопасную экспансию."
    EntityKind.Sandstorm -> "Буря скоро войдёт в активную фазу и пройдёт через заметную часть карты."
    EntityKind.Earthquake -> "Нужно заранее держать запас HP, потому что удар массовый и мгновенный."
    EntityKind.EnemyPlantation -> "Враг удерживает важный участок и может перехватывать доходную зону."
    EntityKind.OwnPlantation -> "Точка в рабочем состоянии, но требует внимания по HP и связности."
}
