/**
 * Demo runner for the timer engine: runs a short profile and logs events.
 * Usage: tsx scripts/run-timer-demo.ts
 */
import {
  getInitialState,
  advance,
  type TimerProfile,
  type TimerState,
  type TimerEvent,
} from '../src/timer-engine/index.js';

const demoProfile: TimerProfile = {
  name: 'Demo',
  emoji: '🥊',
  rounds: [
    { name: 'Work', durationSeconds: 3, warn10sec: true },
    { name: 'Rest', durationSeconds: 2, warn10sec: false },
  ],
};

function logEvent(event: TimerEvent): void {
  const t = new Date().toISOString().slice(11, 23);
  switch (event.type) {
    case 'round_start':
      console.log(`[${t}] round_start: ${event.round.name} (${event.roundIndex + 1}/${event.totalRounds})`);
      break;
    case 'tick':
      console.log(`[${t}] tick: ${event.remainingSeconds}s left in ${event.round.name}`);
      break;
    case 'warn10':
      console.log(`[${t}] warn10: ${event.round.name} in 10 seconds`);
      break;
    case 'round_end':
      console.log(`[${t}] round_end: ${event.round.name}`);
      break;
    case 'training_end':
      console.log(`[${t}] training_end`);
      break;
  }
}

async function runWithRealTimeDelay(): Promise<void> {
  let state: TimerState | null = getInitialState(demoProfile);
  if (!state) {
    console.log('No rounds');
    return;
  }
  console.log('Timer started (1 second per tick). Press Ctrl+C to stop.\n');
  while (state) {
    const { events, nextState } = advance(demoProfile, state);
    for (const e of events) logEvent(e);
    state = nextState;
    if (state) await new Promise((r) => setTimeout(r, 1000));
  }
  console.log('\nDone.');
}

runWithRealTimeDelay().catch(console.error);
