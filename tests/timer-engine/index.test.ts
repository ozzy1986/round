import { describe, it, expect } from 'vitest';
import {
  getInitialState,
  advance,
  runToCompletion,
  type TimerProfile,
  type TimerState,
  type TimerEvent,
} from '../../src/timer-engine/index.js';

describe('timer-engine getInitialState', () => {
  it('returns null for empty rounds', () => {
    const profile: TimerProfile = { name: 'P', emoji: '⏱', rounds: [] };
    expect(getInitialState(profile)).toBeNull();
  });

  it('returns state for first round', () => {
    const profile: TimerProfile = {
      name: 'P',
      emoji: '⏱',
      rounds: [{ name: 'R1', durationSeconds: 60, warn10sec: false }],
    };
    const state = getInitialState(profile);
    expect(state).toEqual({ roundIndex: 0, remainingSeconds: 60 });
  });
});

describe('timer-engine advance', () => {
  it('emits training_end immediately when no rounds', () => {
    const profile: TimerProfile = { name: 'P', emoji: '⏱', rounds: [] };
    const result = advance(profile, { roundIndex: 0, remainingSeconds: 0 });
    expect(result.events).toHaveLength(1);
    expect(result.events[0]).toEqual({ type: 'training_end' });
    expect(result.nextState).toBeNull();
  });

  it('emits training_end when roundIndex >= rounds.length', () => {
    const profile: TimerProfile = {
      name: 'P',
      emoji: '⏱',
      rounds: [{ name: 'R1', durationSeconds: 1, warn10sec: false }],
    };
    const result = advance(profile, { roundIndex: 1, remainingSeconds: 0 });
    expect(result.events[0].type).toBe('training_end');
    expect(result.nextState).toBeNull();
  });

  it('single round: emits round_start, tick, then round_end and training_end', () => {
    const profile: TimerProfile = {
      name: 'P',
      emoji: '⏱',
      rounds: [{ name: 'Work', durationSeconds: 2, warn10sec: false }],
    };
    let state: TimerState | null = getInitialState(profile)!;

    let result = advance(profile, state);
    expect(result.events.map((e) => e.type)).toEqual(['round_start', 'tick']);
    expect(result.events[0].type).toBe('round_start');
    expect((result.events[0] as { round: { name: string } }).round.name).toBe('Work');
    expect((result.events[1] as { remainingSeconds: number }).remainingSeconds).toBe(2);
    state = result.nextState!;
    expect(state).toEqual({ roundIndex: 0, remainingSeconds: 1 });

    result = advance(profile, state);
    expect(result.events.map((e) => e.type)).toEqual(['tick']);
    expect((result.events[0] as { remainingSeconds: number }).remainingSeconds).toBe(1);
    state = result.nextState!;

    result = advance(profile, state);
    expect(result.events.map((e) => e.type)).toEqual(['tick', 'round_end', 'training_end']);
    expect((result.events[0] as { remainingSeconds: number }).remainingSeconds).toBe(0);
    expect(result.nextState).toBeNull();
  });

  it('emits warn10 when remainingSeconds is 10 and warn10sec is true', () => {
    const profile: TimerProfile = {
      name: 'P',
      emoji: '⏱',
      rounds: [{ name: 'R', durationSeconds: 12, warn10sec: true }],
    };
    let state: TimerState | null = getInitialState(profile)!;
    for (let i = 0; i < 2; i++) {
      const result = advance(profile, state);
      state = result.nextState!;
    }
    const result = advance(profile, state);
    const warn10 = result.events.find((e) => e.type === 'warn10');
    expect(warn10).toBeDefined();
    expect((result.events.find((e) => e.type === 'tick') as { remainingSeconds: number }).remainingSeconds).toBe(10);
  });

  it('does not emit warn10 when warn10sec is false', () => {
    const profile: TimerProfile = {
      name: 'P',
      emoji: '⏱',
      rounds: [{ name: 'R', durationSeconds: 15, warn10sec: false }],
    };
    const events: TimerEvent[] = [];
    runToCompletion(profile, (e) => events.push(e));
    const warn10s = events.filter((e) => e.type === 'warn10');
    expect(warn10s).toHaveLength(0);
  });

  it('multiple rounds: emits round_start for each round', () => {
    const profile: TimerProfile = {
      name: 'P',
      emoji: '⏱',
      rounds: [
        { name: 'R1', durationSeconds: 1, warn10sec: false },
        { name: 'R2', durationSeconds: 1, warn10sec: false },
      ],
    };
    const events: TimerEvent[] = [];
    runToCompletion(profile, (e) => events.push(e));
    const roundStarts = events.filter((e) => e.type === 'round_start');
    expect(roundStarts).toHaveLength(2);
    expect((roundStarts[0] as { round: { name: string } }).round.name).toBe('R1');
    expect((roundStarts[1] as { round: { name: string } }).round.name).toBe('R2');
  });

  it('round with duration 0: emits round_start, tick(0), round_end', () => {
    const profile: TimerProfile = {
      name: 'P',
      emoji: '⏱',
      rounds: [{ name: 'Zero', durationSeconds: 0, warn10sec: false }],
    };
    const events: TimerEvent[] = [];
    runToCompletion(profile, (e) => events.push(e));
    expect(events.map((e) => e.type)).toEqual([
      'round_start',
      'tick',
      'round_end',
      'training_end',
    ]);
    expect((events[1] as { remainingSeconds: number }).remainingSeconds).toBe(0);
  });

  it('runToCompletion with no rounds emits only training_end', () => {
    const profile: TimerProfile = { name: 'P', emoji: '⏱', rounds: [] };
    const events: TimerEvent[] = [];
    runToCompletion(profile, (e) => events.push(e));
    expect(events).toEqual([{ type: 'training_end' }]);
  });

  it('every tick has correct totalRounds and roundIndex', () => {
    const profile: TimerProfile = {
      name: 'P',
      emoji: '⏱',
      rounds: [
        { name: 'A', durationSeconds: 1, warn10sec: false },
        { name: 'B', durationSeconds: 1, warn10sec: false },
      ],
    };
    const events: TimerEvent[] = [];
    runToCompletion(profile, (e) => events.push(e));
    const ticks = events.filter((e) => e.type === 'tick') as Array<{ roundIndex: number; totalRounds: number }>;
    expect(ticks.every((t) => t.totalRounds === 2)).toBe(true);
    const roundStarts = events.filter((e) => e.type === 'round_start');
    expect((roundStarts[0] as { roundIndex: number }).roundIndex).toBe(0);
    expect((roundStarts[1] as { roundIndex: number }).roundIndex).toBe(1);
  });
});
