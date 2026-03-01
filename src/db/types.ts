export interface Profile {
  id: string;
  name: string;
  emoji: string;
  user_id: string | null;
  created_at: Date;
  updated_at: Date;
}

export interface Round {
  id: string;
  profile_id: string;
  name: string;
  duration_seconds: number;
  warn10sec: boolean;
  position: number;
}

export interface ProfileWithRounds extends Profile {
  rounds: Round[];
}

export interface CreateProfileInput {
  id?: string;
  name: string;
  emoji: string;
  user_id: string;
}

export interface UpdateProfileInput {
  name?: string;
  emoji?: string;
}

export interface CreateRoundInput {
  name: string;
  duration_seconds: number;
  warn10sec?: boolean;
  position: number;
}

export interface UpdateRoundInput {
  name?: string;
  duration_seconds?: number;
  warn10sec?: boolean;
  position?: number;
}
