import apiClient from './client';
import type { User, Game, GameCard, Card, CardPage, Transaction, TopUpRequest } from '../types';

export const authApi = {
  login: async (initData: string): Promise<User> => {
    const response = await apiClient.post<User>(
      '/auth/login',
      {},
      { headers: { Authorization: initData } }
    );
    return response.data;
  },
};

export const userApi = {
  getDashboard: async (): Promise<User> => {
    const response = await apiClient.get<User>('/users/me');
    return response.data;
  },
};

export const gameApi = {
  getCurrentGame: async (): Promise<Game | null> => {
    const response = await apiClient.get<Game>('/games/active');
    return response.status === 204 ? null : response.data;
  },
  joinGame: async (gameId: number, cardId: number): Promise<GameCard> => {
    const response = await apiClient.post<GameCard>(`/games/${gameId}/register?cardId=${cardId}`);
    return response.data;
  },
  getMyCards: async (): Promise<GameCard[]> => {
    const response = await apiClient.get<GameCard[]>('/player/my-cards');
    return response.data;
  },
};

export const adminApi = {
  requestTopUp: async (amount: number, proofImageFileId?: string): Promise<TopUpRequest> => {
    const params = new URLSearchParams({ amount: String(amount) });
    if (proofImageFileId) params.append('proofImageFileId', proofImageFileId);
    const response = await apiClient.post<TopUpRequest>('/admin/topup/request', null, { params });
    return response.data;
  },
  getPendingTopUps: async (): Promise<TopUpRequest[]> => {
    const response = await apiClient.get<TopUpRequest[]>('/admin/topup/pending');
    return response.data;
  },
  approveTopUp: async (id: number): Promise<TopUpRequest> => {
    const response = await apiClient.post<TopUpRequest>(`/admin/topup/${id}/approve`);
    return response.data;
  },
  rejectTopUp: async (id: number): Promise<TopUpRequest> => {
    const response = await apiClient.post<TopUpRequest>(`/admin/topup/${id}/reject`);
    return response.data;
  },
  createGame: async (entryFee: number, maxPlayers?: number): Promise<Game> => {
    const response = await apiClient.post<Game>('/admin/games/create', { entryFee, maxPlayers });
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
  listAvailable: async (page: number = 0, size: number = 20): Promise<CardPage> => {
    const response = await apiClient.get<CardPage>(`/cards/available?page=${page}&size=${size}`);
    return response.data;
  },
};

export const playerApi = {
  requestTopUp: async (amount: number, proofImageFileId?: string): Promise<TopUpRequest> => {
    const params = new URLSearchParams({ amount: String(amount) });
    if (proofImageFileId) params.append('proofImageFileId', proofImageFileId);
    const response = await apiClient.post<TopUpRequest>('/player/topup/request', null, { params });
    return response.data;
  },
  claimBingo: async (gameId: number): Promise<void> => {
    await apiClient.post(`/games/${gameId}/claim`);
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
  getPendingTopUps: async (): Promise<TopUpRequest[]> => {
    const response = await apiClient.get<TopUpRequest[]>('/super-admin/topup/pending');
    return response.data;
  },
  approveTopUp: async (id: number): Promise<TopUpRequest> => {
    const response = await apiClient.post<TopUpRequest>(`/super-admin/topup/${id}/approve`);
    return response.data;
  },
  rejectTopUp: async (id: number): Promise<TopUpRequest> => {
    const response = await apiClient.post<TopUpRequest>(`/super-admin/topup/${id}/reject`);
    return response.data;
  },
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
  getReports: async (): Promise<Record<string, number>> => {
    const response = await apiClient.get<Record<string, number>>('/super-admin/reports');
    return response.data;
  },
  getGames: async (): Promise<Game[]> => {
    const response = await apiClient.get<Game[]>('/super-admin/games/active');
    return response.data;
  },
  getWithdrawals: async (): Promise<Transaction[]> => {
    const response = await apiClient.get<Transaction[]>('/super-admin/withdrawals');
    return response.data;
  },
  payWithdrawal: async (id: number): Promise<void> => {
    await apiClient.post(`/super-admin/withdrawals/${id}/pay`);
  },
  getSettings: async (): Promise<Record<string, string>> => {
    const response = await apiClient.get<Record<string, string>>('/super-admin/settings');
    return response.data;
  },
  updateSettings: async (settings: Record<string, string>): Promise<void> => {
    await apiClient.put('/super-admin/settings', settings);
  },
};
