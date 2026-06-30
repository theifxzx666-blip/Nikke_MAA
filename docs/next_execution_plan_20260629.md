# MaaNikke Android Next Execution Plan

Date: 2026-06-29
Baseline evidence: `outputs/android_probe/workflow_debug_stable_timing_20260629-120237/`

## Current Baseline

- Debug workflow passed with slower timing.
- Final state: `workflow_daily_safe_completed`
- Subtasks: 14 completed, 0 failed.
- Safety boundary held: no final claim, purchase confirmation, batch consult confirmation, gift send confirmation, sweep, challenge boss, or enter battle action was executed.
- Because some rewards can only be claimed once per day, do not use today's state for a full normal-mode validation.

## Proposed Execution Strategy

### Phase 1: Add Daily-Once Guard Before Normal Mode

Status: completed on 2026-06-29.

Before enabling normal-mode real actions, a small local guard has been added for final actions:

- Record `date + task + final_action` after a normal-mode final click succeeds.
- If the same final action is requested again on the same date, skip it and log `already_done_today`.
- Keep a manual reset/ignore option for debugging, but default to safe skip.
- Apply first to mail, daily/weekly rewards, friend points, outpost reward, free shop final action, dispatch, inquiry batch/gift, Pass, interception sweep, and any battle-related action.

This prevents accidental second runs after an interrupted workflow or repeated button press.

Debug validation:

- `outputs/android_probe/workflow_debug_daily_guard_20260629-123128/`
- `outputs/android_probe/debug_interception_daily_guard_20260629-123401/`

Debug mode does not write `daily-action-ledger.tsv`.

### Phase 2: Normal-Mode Validation One Task At A Time

Run on a fresh daily reset, not immediately after today's debug checks.

Recommended order:

1. `start_game` and `back_to_home`
   - Purpose: confirm launch, attach, home convergence.
   - Risk: none.

2. Low-risk daily claims
   - Tasks: mail, friend points, daily/weekly rewards, outpost accumulated reward without diamonds.
   - Rule: run one task, pull log/evidence, confirm final state, then continue to next.

3. Dispatch board
   - Validate `all receive` and `all dispatch` as a single-task normal run.
   - Stop if either button is not clearly detected.

4. Free shop
   - Only enable clearly free refresh/free item paths first.
   - Paid/currency item purchases remain disabled until price/confirm OCR is explicit.

5. Inquiry and gift
   - Normal run only after user confirms gift consumption is acceptable.
   - Keep batch consult and gift as separate logged final actions in the daily guard.

6. Interception
   - Keep yellow/unverified for normal mode.
   - Do not enable normal sweep until there is clear evidence of a visible sweep button or remaining attempts.
   - Never click challenge boss as sweep fallback.

7. Climb tower / sim room / battle-like flows
   - Keep in debug/preview mode until battle entry and exit evidence is complete.

### Phase 3: Workflow-Level Normal Run

Only after Phase 2 tasks pass individually:

- Enable a conservative normal workflow with green tasks only.
- Keep yellow tasks in debug/preview or disabled.
- Run once after reset, pull evidence, audit for `SubTask.Failed`, repeated daily guard skips, and unexpected high-risk taps.

## Acceptance Criteria

- Normal single-task result has expected final state.
- No `SubTask.Failed`.
- No unknown page repeated taps.
- Daily-once guard records final actions and prevents a same-day second execution.
- Each validated task has evidence under `outputs/android_probe/`.

## Recommendation

Keep the current slower timing for at least three successful daily runs before considering any speed-up.
