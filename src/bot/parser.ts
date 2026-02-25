import type { TimerProfile, TimerRound } from '../timer-engine/types.js';

/**
 * Parses "NxW R" (e.g. "3x5 1") into a profile: N pairs of (work W min, rest R min).
 * Minutes are converted to seconds.
 */
export function parseTemplate(spec: string): TimerProfile | null {
  const trimmed = spec.trim().replace(/\s+/g, ' ');
  const match = trimmed.match(/^(\d+)\s*[x×]\s*(\d+)\s+(\d+)$/i);
  if (!match) return null;
  const n = parseInt(match[1], 10);
  const workMin = parseInt(match[2], 10);
  const restMin = parseInt(match[3], 10);
  if (n < 1 || workMin < 0 || restMin < 0) return null;
  const workSec = workMin * 60;
  const restSec = restMin * 60;
  const rounds: TimerRound[] = [];
  for (let i = 0; i < n; i++) {
    rounds.push({ name: 'Work', durationSeconds: workSec, warn10sec: true });
    rounds.push({ name: 'Rest', durationSeconds: restSec, warn10sec: false });
  }
  return {
    name: `Template ${trimmed}`,
    emoji: '⏱',
    rounds,
  };
}

/**
 * Parses custom rounds like "5 run, 5 walk, 4 run, 15 walk" (duration in minutes, then name).
 */
export function parseCustomRounds(spec: string): TimerRound[] | null {
  const parts = spec.split(',').map((p) => p.trim()).filter(Boolean);
  if (parts.length === 0) return null;
  const rounds: TimerRound[] = [];
  for (const part of parts) {
    const match = part.match(/^(\d+)\s+(.+)$/);
    if (!match) return null;
    const durationMin = parseInt(match[1], 10);
    const name = match[2].trim();
    if (durationMin < 0 || !name) return null;
    rounds.push({
      name,
      durationSeconds: durationMin * 60,
      warn10sec: true,
    });
  }
  return rounds;
}

/**
 * Tries template first ("3x5 1"), then custom ("5 run, 5 walk"). Returns profile or null.
 */
export function parseTimerSpec(spec: string): TimerProfile | null {
  const template = parseTemplate(spec);
  if (template) return template;
  const rounds = parseCustomRounds(spec);
  if (rounds) return { name: `Custom: ${spec.slice(0, 30)}`, emoji: '⏱', rounds };
  return null;
}
