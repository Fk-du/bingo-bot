import { useState, useEffect } from 'react';
import { retrieveLaunchParams } from '@telegram-apps/sdk';
import { authApi, userApi } from '../api/services';
import type { User } from '../types';

export const useAuth = () => {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const login = async () => {
    try {
      setLoading(true);
      
      // In a real Telegram environment, we get this from the SDK
      let telegramId = 0;
      let initData = '';

      try {
        const { initDataRaw, initData: data } = retrieveLaunchParams();
        telegramId = data?.user?.id || 0;
        initData = initDataRaw || '';
      } catch (e) {
        console.warn('Telegram SDK not available, using dev mock');
        // Dev mock - for testing outside Telegram
        telegramId = 123456789; // Should match a user in your DB
        initData = 'mock_init_data';
      }

      if (!telegramId) throw new Error('Could not retrieve Telegram ID');

      const { token, user: userData } = await authApi.login(telegramId, initData);
      
      localStorage.setItem('bingo_token', token);
      setUser(userData);
      setError(null);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Authentication failed');
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
