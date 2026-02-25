import { describe, it, expect } from 'vitest';
import { parseTemplate, parseCustomRounds, parseTimerSpec } from '../../src/bot/parser.js';

describe('parser parseTemplate', () => {
  it('parses "3x5 1" as 3 pairs of work 5min rest 1min', () => {
    const profile = parseTemplate('3x5 1');
    expect(profile).not.toBeNull();
    expect(profile!.rounds).toHaveLength(6);
    expect(profile!.rounds[0]).toEqual({ name: 'Work', durationSeconds: 300, warn10sec: true });
    expect(profile!.rounds[1]).toEqual({ name: 'Rest', durationSeconds: 60, warn10sec: false });
  });

  it('parses "1x2 3" with spaces', () => {
    const profile = parseTemplate('1 x 2  3');
    expect(profile).not.toBeNull();
    expect(profile!.rounds).toHaveLength(2);
    expect(profile!.rounds[0].durationSeconds).toBe(120);
    expect(profile!.rounds[1].durationSeconds).toBe(180);
  });

  it('returns null for invalid format', () => {
    expect(parseTemplate('abc')).toBeNull();
    expect(parseTemplate('3x5')).toBeNull();
    expect(parseTemplate('x5 1')).toBeNull();
  });

  it('returns null for n=0', () => {
    const profile = parseTemplate('0x5 1');
    expect(profile).toBeNull();
  });
});

describe('parser parseCustomRounds', () => {
  it('parses "5 run, 5 walk"', () => {
    const rounds = parseCustomRounds('5 run, 5 walk');
    expect(rounds).not.toBeNull();
    expect(rounds!).toHaveLength(2);
    expect(rounds![0]).toEqual({ name: 'run', durationSeconds: 300, warn10sec: true });
    expect(rounds![1]).toEqual({ name: 'walk', durationSeconds: 300, warn10sec: true });
  });

  it('parses single round', () => {
    const rounds = parseCustomRounds('10 work');
    expect(rounds).toHaveLength(1);
    expect(rounds![0].name).toBe('work');
    expect(rounds![0].durationSeconds).toBe(600);
  });

  it('returns null for invalid part', () => {
    expect(parseCustomRounds('run')).toBeNull();
    expect(parseCustomRounds('5')).toBeNull();
  });
});

describe('parser parseTimerSpec', () => {
  it('prefers template over custom', () => {
    const profile = parseTimerSpec('3x5 1');
    expect(profile).not.toBeNull();
    expect(profile!.rounds[0].name).toBe('Work');
  });

  it('returns custom profile for comma-separated', () => {
    const profile = parseTimerSpec('5 run, 5 walk');
    expect(profile).not.toBeNull();
    expect(profile!.rounds).toHaveLength(2);
  });

  it('returns null for empty or unknown', () => {
    expect(parseTimerSpec('')).toBeNull();
    expect(parseTimerSpec('  ')).toBeNull();
  });
});
