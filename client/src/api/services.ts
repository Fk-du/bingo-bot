import apiClient from './client';
import type { User, Game, GameCard, AuthResponse, Card, Transaction } from '../types';

export const authApi = {
  login: async (initData: string): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>(
      '/auth/login',
      {},
      { headers: { Authorization: initData } }
    );
    return response.data;
  },
};

export const userApi = {
  getDashboard: async (): Promise<User> => {
    const response = await apiClient.get<User>('/dashboard');
    return response.data;
  },
};

export const gameApi = {
  getCurrentGame: async (): Promise<Game | null> => {
    const response = await apiClient.get<Game>('/player/game/current');
    return response.status === 204 ? null : response.data;
  },
  joinGame: async (gameId: number): Promise<GameCard> => {
    const response = await apiClient.post<GameCard>(`/player/game/${gameId}/join`);
    return response.data;
  },
  getMyCards: async (): Promise<GameCard[]> => {
    const response = await apiClient.get<GameCard[]>('/player/my-cards');
    return response.data;
  },
};

export const adminGameApi = {
  getCurrentGame: async (): Promise<Game | null> => {
    const response = await apiClient.get<Game>('/admin/game/current');
    return response.status === 204 ? null : response.data;
  },
};

export const adminApi = {
  createGame: async (entryFee: number): Promise<Game> => {
    const response = await apiClient.post<Game>(`/admin/games/create?entryFee=${entryFee}`);
    return response.data;
  },
  startGame: async (id: number): Promise<void> => {
    await apiClient.post(`/admin/games/${id}/start`);
  },
  pauseGame: async (id: number): Promise<void> => {
    await apiClient.post(`/admin/games/${id}/pause`);
  },
  resumeGame: async (id: number): Promise<void> => {
    await apiClient.post(`/admin/games/${id}/resume`);
  },
  endGame: async (id: number): Promise<Game> => {
    const response = await apiClient.post<Game>(`/admin/games/${id}/end`);
    return response.data;
  },
  getPlayers: async (): Promise<User[]> => {
    const response = await apiClient.get<User[]>('/admin/players');
    return response.data;
  },
  getInviteLink: async (botUsername: string): Promise<string> => {
    const response = await apiClient.get<string>(`/admin/invite-link?botUsername=${encodeURIComponent(botUsername)}`);
    return response.data;
  },
  fundPlayer: async (playerId: number, amount: number): Promise<void> => {
    await apiClient.post(`/admin/players/${playerId}/fund?amount=${amount}`);
  },
  getWithdrawals: async (): Promise<Transaction[]> => {
    const response = await apiClient.get<Transaction[]>('/admin/withdrawals');
    return response.data;
  },
  getTransactions: async (): Promise<Transaction[]> => {
    const response = await apiClient.get<Transaction[]>('/admin/transactions');
    return response.data;
  },
  approveWithdrawal: async (id: number): Promise<void> => {
    await apiClient.post(`/admin/withdrawals/${id}/approve`);
  },
};

export const cardApi = {
  getCard: async (id: number): Promise<Card> => {
    const response = await apiClient.get<Card>(`/cards/${id}`);
    return response.data;
  },
};

export const playerApi = {
  claimBingo: async (gameId: number): Promise<void> => {
    await apiClient.post(`/player/game/${gameId}/bingo/claim`);
  },
  getInviteLink: async (botUsername: string): Promise<string> => {
    const response = await apiClient.get<string>(`/player/invite-link?botUsername=${encodeURIComponent(botUsername)}`);
    return response.data;
  },
  getHistory: async (): Promise<Transaction[]> => {
    const response = await apiClient.get<Transaction[]>('/player/history');
    return response.data;
  },
  buyPoints: async (amount: number): Promise<Transaction> => {
    const response = await apiClient.post<Transaction>(`/player/points/buy?amount=${amount}`);
    return response.data;
  },
  withdrawRequest: async (amount: number): Promise<Transaction> => {
    const response = await apiClient.post<Transaction>(`/player/withdraw?amount=${amount}`);
    return response.data;
  },
};

export const superAdminApi = {
  createAgent: async (botUsername: string): Promise<string> => {
    const response = await apiClient.post<string>(`/super-admin/agents/create?botUsername=${encodeURIComponent(botUsername)}`);
    return response.data;
  },
  fundAgent: async (agentId: number, amount: number): Promise<void> => {
    await apiClient.post(`/super-admin/agents/${agentId}/fund?amount=${amount}`);
  },
  getAgents: async (): Promise<User[]> => {
    const response = await apiClient.get<User[]>('/super-admin/agents');
    return response.data;
  },
  getTransactions: async (): Promise<Transaction[]> => {
    const response = await apiClient.get<Transaction[]>('/super-admin/transactions');
    return response.data;
  },
};
