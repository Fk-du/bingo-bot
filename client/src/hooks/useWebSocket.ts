import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws';

export const useWebSocket = (gameId?: number) => {
  const stompClient = useRef<Client | null>(null);
  const [connected, setConnected] = useState(false);
  const [lastNumber, setLastNumber] = useState<number | null>(null);
  const [gameStatus, setGameStatus] = useState<string | null>(null);
  const [winner, setWinner] = useState<any>(null);

  useEffect(() => {
    if (!gameId) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      debug: (str) => console.log('STOMP:', str),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    client.onConnect = (frame) => {
      console.log('Connected to WS');
      setConnected(true);

      // Subscribe to number calls
      client.subscribe(`/topic/game/${gameId}/numbers`, (message: IMessage) => {
        const num = JSON.parse(message.body);
        setLastNumber(num);
      });

      // Subscribe to game status
      client.subscribe(`/topic/game/${gameId}/status`, (message: IMessage) => {
        setGameStatus(message.body.replace(/"/g, ''));
      });

      // Subscribe to winner
      client.subscribe(`/topic/game/${gameId}/winner`, (message: IMessage) => {
        const winnerData = JSON.parse(message.body);
        setWinner(winnerData);
      });
    };

    client.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
    };

    client.onDisconnect = () => {
      setConnected(false);
    };

    client.activate();
    stompClient.current = client;

    return () => {
      if (stompClient.current) {
        stompClient.current.deactivate();
      }
    };
  }, [gameId]);

  return { connected, lastNumber, gameStatus, winner };
};
