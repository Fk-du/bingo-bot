import apiClient from './client';
import type { User, Game, GameCard, AuthResponse, Card } from '../types';

export const authApi = {
  login: async (telegramId: number, initData: string): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/login', { telegramId, initData });
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
    const response = await apiClient.get<Game>('/games/current');
    return response.status === 204 ? null : response.data;
  },
  joinGame: async (gameId: number): Promise<GameCard> => {
    const response = await apiClient.post<GameCard>(`/games/${gameId}/join`);
    return response.data;
  },
  getMyCards: async (): Promise<GameCard[]> => {
    const response = await apiClient.get<GameCard[]>('/games/my-cards');
    return response.data;
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
};

export const cardApi = {
  getCard: async (id: number): Promise<Card> => {
    const response = await apiClient.get<Card>(`/cards/${id}`);
    return response.data;
  },
};
