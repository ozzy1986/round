import type { TimerProfile, TimerEvent, TimerEventRoundStart, TimerEventTick, TimerEventWarn10, TimerEventRoundEnd, TimerEventTrainingEnd } from './types.js';

export type { TimerProfile, TimerRound, TimerEvent, TimerEventType, TimerEventCallback } from './types.js';

export interface TimerState {
  roundIndex: number;
  remainingSeconds: number;
}

/**
 * Returns initial state for the first round, or null if profile has no rounds.
 */
export function getInitialState(profile: TimerProfile): TimerState | null {
  if (!profile.rounds || profile.rounds.length === 0) return null;
  return {
    roundIndex: 0,
    remainingSeconds: profile.rounds[0].durationSeconds,
  };
}

/**
 * Advances the timer by one second. Returns events for this second and the next state.
 * If nextState is null, training has ended (caller should stop).
 */
export function advance(
  profile: TimerProfile,
  state: TimerState
): { events: TimerEvent[]; nextState: TimerState | null } {
  const rounds = profile.rounds;
  if (!rounds.length) {
    return { events: [{ type: 'training_end' }], nextState: null };
  }

  const { roundIndex, remainingSeconds } = state;
  if (roundIndex >= rounds.length) {
    return { events: [{ type: 'training_end' }], nextState: null };
  }

  const round = rounds[roundIndex];
  const totalRounds = rounds.length;
  const events: TimerEvent[] = [];

  const isStartOfRound = remainingSeconds === round.durationSeconds;
  if (isStartOfRound) {
    events.push({
      type: 'round_start',
      roundIndex,
      round,
      totalRounds,
    } as TimerEventRoundStart);
  }

  events.push({
    type: 'tick',
    roundIndex,
    round,
    remainingSeconds,
    totalRounds,
  } as TimerEventTick);

  if (round.warn10sec && remainingSeconds === 10) {
    events.push({
      type: 'warn10',
      roundIndex,
      round,
      totalRounds,
    } as TimerEventWarn10);
  }

  if (remainingSeconds === 0) {
    events.push({
      type: 'round_end',
      roundIndex,
      round,
      totalRounds,
    } as TimerEventRoundEnd);

    const nextRoundIndex = roundIndex + 1;
    if (nextRoundIndex >= rounds.length) {
      events.push({ type: 'training_end' } as TimerEventTrainingEnd);
      return { events, nextState: null };
    }

    const nextRound = rounds[nextRoundIndex];
    return {
      events,
      nextState: {
        roundIndex: nextRoundIndex,
        remainingSeconds: nextRound.durationSeconds,
      },
    };
  }

  return {
    events,
    nextState: {
      roundIndex,
      remainingSeconds: remainingSeconds - 1,
    },
  };
}

/**
 * Runs the full timer synchronously, calling onEvent for every event.
 * Used for tests and for runners that drive the engine with a real clock (they call advance in a loop).
 */
export function runToCompletion(
  profile: TimerProfile,
  onEvent: (event: TimerEvent) => void
): void {
  let state = getInitialState(profile);
  if (!state) {
    onEvent({ type: 'training_end' });
    return;
  }
  while (state) {
    const { events, nextState } = advance(profile, state);
    for (const event of events) {
      onEvent(event);
    }
    state = nextState;
  }
}
