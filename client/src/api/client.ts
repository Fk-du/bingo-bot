import axios from 'axios';
import { retrieveRawInitData } from '@telegram-apps/sdk';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

const getTelegramInitData = () => {
  const rawInitData = retrieveRawInitData();
  if (rawInitData) {
    return rawInitData;
  }

  const webApp = (window as typeof window & {
    Telegram?: {
      WebApp?: {
        initData?: string;
      };
    };
  }).Telegram?.WebApp;

  return webApp?.initData || '';
};

apiClient.interceptors.request.use((config) => {
  const initData = getTelegramInitData();
  if (initData) {
    config.headers.Authorization = initData;
  }
  return config;
});

export default apiClient;
