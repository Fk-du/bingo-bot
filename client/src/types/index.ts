export type Role = 'PLAYER' | 'ADMIN' | 'SUPER_ADMIN';
export type GameStatus = 'REGISTRATION_OPEN' | 'IN_PROGRESS' | 'CLAIM_PENDING' | 'ENDED';

export interface User {
  id: number;
  telegramId: number;
  role: Role;
  balance: number;
  active: boolean;
}

export interface Game {
  id: number;
  adminId: number;
  status: GameStatus;
  entryFee: number;
  maxPlayers: number;
  startTime?: string;
  createdAt: string;
}

export interface GameCard {
  id: number;
  gameId: number;
  playerId: number;
  cardId: number;
  winner: boolean;
}

export interface Card {
  id: number;
  numbers: string;
  used: boolean;
}

export interface Transaction {
  id: number;
  userId: number;
  type: string;
  amount: number;
  status: string;
  approvedBy?: number | null;
  proofImageFileId?: string | null;
  createdAt: string;
}

export interface Winner {
  id: number;
  gameId: number;
  playerId: number;
  cardId: number;
  rewardAmount: number;
}

export interface AuthResponse {
  user: User;
}

export type TopUpStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface CardPage {
  content: Card[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface TopUpRequest {
  id: number;
  requesterId: number;
  approverId: number;
  amount: number;
  status: TopUpStatus;
  proofImageFileId?: string;
  createdAt: string;
  updatedAt?: string;
}
