export interface TimerRound {
  name: string;
  durationSeconds: number;
  warn10sec: boolean;
}

export interface TimerProfile {
  name: string;
  emoji: string;
  rounds: TimerRound[];
}

export type TimerEventType =
  | 'round_start'
  | 'tick'
  | 'warn10'
  | 'round_end'
  | 'training_end';

export interface TimerEventRoundStart {
  type: 'round_start';
  roundIndex: number;
  round: TimerRound;
  totalRounds: number;
}

export interface TimerEventTick {
  type: 'tick';
  roundIndex: number;
  round: TimerRound;
  remainingSeconds: number;
  totalRounds: number;
}

export interface TimerEventWarn10 {
  type: 'warn10';
  roundIndex: number;
  round: TimerRound;
  totalRounds: number;
}

export interface TimerEventRoundEnd {
  type: 'round_end';
  roundIndex: number;
  round: TimerRound;
  totalRounds: number;
}

export interface TimerEventTrainingEnd {
  type: 'training_end';
}

export type TimerEvent =
  | TimerEventRoundStart
  | TimerEventTick
  | TimerEventWarn10
  | TimerEventRoundEnd
  | TimerEventTrainingEnd;

export type TimerEventCallback = (event: TimerEvent) => void;
