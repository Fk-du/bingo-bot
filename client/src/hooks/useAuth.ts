import { useState, useEffect } from 'react';
import { retrieveRawInitData } from '@telegram-apps/sdk';
import { authApi, userApi } from '../api/services';
import type { User } from '../types';

export const useAuth = () => {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const login = async () => {
    try {
      setLoading(true);

      const webApp = (window as typeof window & {
        Telegram?: {
          WebApp?: {
            initData?: string;
          };
        };
      }).Telegram?.WebApp;

      const initData = retrieveRawInitData() || webApp?.initData || '';

      if (!initData) {
        throw new Error('Telegram initData is unavailable');
      }

      const userData = await authApi.login(initData);
      setUser(userData);
      setError(null);
    } catch (err: any) {
      setError(err.response?.data?.userMessage || err.response?.data?.message || err.message || 'Authentication failed');
      console.error('Auth error:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    login();
  }, []);

  const refreshUser = async () => {
    try {
      const data = await userApi.getDashboard();
      setUser(data);
    } catch (err) {
      console.error('Failed to refresh user data', err);
    }
  };

  return { user, loading, error, refreshUser, login };
};
