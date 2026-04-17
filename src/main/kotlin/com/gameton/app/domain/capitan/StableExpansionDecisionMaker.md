# StableExpansionDecisionMaker

## Goal
Build a survival-first expansion strategy that prevents HQ-loss respawns by accelerating critical adjacent construction and relocating HQ in time.

## Turn Sync Contract
This strategy expects command sending to be synchronized by controller:
- process command logic once per new `turnNo`,
- schedule polling from `nextTurnIn` with a small safety buffer.

## Decision Priority Per Turn
1. Apply anti-stall state updates and cooldown decay.
2. Select `plantationUpgrade` when upgrade points are available.
3. Preserve HQ continuity (keep or finish adjacent support).
4. Relocate HQ with `relocateMain` when deadline is critical.
5. Spend remaining free authors on expansion.

## Upgrade Logic
When `plantationUpgrades.points > 0`, choose the first available tier from this order:
1. `decay_mitigation`
2. `earthquake_mitigation`
3. `max_hp`
4. `settlement_limit`
5. `beaver_damage_mitigation`
6. `repair_power`
7. `signal_range`
8. `vision_range`

A tier is available when `current < max`.

## HQ Continuity Logic
HQ is detected as `plantation.isMain == true` and not isolated.

When no adjacent operational support plantation exists:
1. Keep sticky target `hqSupportTarget` to avoid target oscillation.
2. Prefer adjacent existing construction (highest progress, tie by `(x, y)`).
3. If none exists, start new adjacent support construction.
4. Prefer safer adjacent tiles with `terraformationProgress < 80`.
5. Compute dynamic deadline:
   - `turnsToHQDisappear = ceil((100 - mainProgress) / 5)`
   - `remainingSupportHp = 50 - currentSupportProgress`
   - `safetyMargin = 1 (+1 if earthquake turnsUntil <= 1)`
6. If one-command tempo is insufficient for deadline, switch to emergency acceleration:
   - send multiple build commands to the same support target in the same turn,
   - each author is used at most once,
   - only commands with positive effective `CS` are used.

## HQ Relocation Logic (`relocateMain`)
Relocation mode activates when:
- `turnsToHQDisappear <= 1`, and
- there is at least one adjacent operational support plantation.

Behavior:
1. Send `relocateMain = [[hqX, hqY], [supportX, supportY]]`.
2. Do not send build commands in the same turn.
3. `plantationUpgrade` may be sent together with relocation.
4. Choose relocation target by survival preference:
   - lower terraformation progress,
   - lower storm/beaver exposure,
   - higher HP,
   - `(x, y)` tie-break.

## Expansion Logic
Used only when HQ continuity logic does not consume all authors.

Source authors:
- all non-isolated plantations, deterministic order by `(x, y, id)`.

Action candidates:
1. Continue existing own construction.
2. Continue construction planned earlier in this same turn.
3. Start new adjacent buildable construction if settlement limit allows.

Scoring factors:
- closer to map center is better,
- `x % 7 == 0 && y % 7 == 0` bonus,
- existing/planned construction gets strong bonus,
- high terraformation progress (`>= 80`) on new targets gets penalty,
- beaver danger and active sandstorm coverage get penalties,
- higher effective `CS` gets bonus.

Output-point reuse is accounted for by:
- `effectiveCS = max(5 - reuseCount(output), 0)`.

## Settlement Limit Safety
Before starting a new construction:
- compute `effectiveLimit = 30 + settlement_limit.current`,
- compute `active = plantations.size + construction.size`,
- allow new construction only if `active + plannedNewTargets.size < effectiveLimit`.

If limit is reached:
- do not start new construction,
- but continuing existing/planned construction is still allowed.

## Anti-Stall Logic
State is tracked across turns:
- `lastConstructionTarget`
- `lastConstructionProgress`
- `stalledTurns`

If progress on tracked construction does not increase for 2+ turns, target enters cooldown for 6 turns.

Cooldown is also applied when tracked construction disappears before completion
(it vanished and did not become own plantation on that position).

Targets on cooldown are excluded from expansion and HQ emergency target selection.
If cooldown is applied to `hqSupportTarget`, sticky emergency target is cleared.

## Command Shape
Build command format:
- `path = [author, output, target]`

Request payload may include:
- multiple build commands,
- one `plantationUpgrade`,
- optional `relocateMain = [[fromX, fromY], [toX, toY]]`.

## Known Limits (v1)
- No explicit sabotage/repair behavior.
- Only adjacent build targets are considered (no long-range target search).
- Strategy is intentionally survival-first over pure scoring tempo.
