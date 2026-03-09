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

export interface BugReport {
  id: string;
  user_id: string;
  message: string;
  screen: string | null;
  device_manufacturer: string;
  device_model: string;
  os_version: string;
  sdk_int: number;
  app_version: string;
  app_build: string;
  created_at: Date;
}

export interface CreateBugReportInput {
  user_id: string;
  message: string;
  screen?: string | null;
  device_manufacturer: string;
  device_model: string;
  os_version: string;
  sdk_int: number;
  app_version: string;
  app_build: string;
}
