import { useState, useEffect } from 'react';
import { adminGameApi, cardApi, gameApi } from '../api/services';
import type { Game, GameCard, Card, Role } from '../types';

export const useGame = (role?: Role | null) => {
  const [currentGame, setCurrentGame] = useState<Game | null>(null);
  const [myCards, setMyCards] = useState<GameCard[]>([]);
  const [cardDetails, setCardDetails] = useState<Record<number, Card>>({});
  const [loading, setLoading] = useState(false);

  const fetchGameData = async () => {
    try {
      setLoading(true);
      if (role === 'PLAYER') {
        const [game, cards] = await Promise.all([
          gameApi.getCurrentGame(),
          gameApi.getMyCards()
        ]);
        setCurrentGame(game);
        setMyCards(cards);

        const details: Record<number, Card> = {};
        await Promise.all(cards.map(async (gc) => {
          const detail = await cardApi.getCard(gc.cardId);
          details[gc.cardId] = detail;
        }));
        setCardDetails(details);
        return;
      }

      if (role === 'ADMIN') {
        const game = await adminGameApi.getCurrentGame();
        setCurrentGame(game);
        setMyCards([]);
        setCardDetails({});
        return;
      }

      setCurrentGame(null);
      setMyCards([]);
      setCardDetails({});
    } catch (err) {
      console.error('Failed to fetch game data', err);
    } finally {
      setLoading(false);
    }
  };

  const joinGame = async (gameId: number) => {
    try {
      setLoading(true);
      const newGameCard = await gameApi.joinGame(gameId);
      setMyCards(prev => [...prev, newGameCard]);
      
      const detail = await cardApi.getCard(newGameCard.cardId);
      setCardDetails(prev => ({ ...prev, [newGameCard.cardId]: detail }));
      
      return newGameCard;
    } catch (err) {
      console.error('Failed to join game', err);
      throw err;
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchGameData();
  }, [role]);

  return { currentGame, myCards, cardDetails, loading, fetchGameData, joinGame };
};
