import { useState, useEffect } from 'react';
import { gameApi, cardApi } from '../api/services';
import type { Game, GameCard, Card } from '../types';

export const useGame = () => {
  const [currentGame, setCurrentGame] = useState<Game | null>(null);
  const [myCards, setMyCards] = useState<GameCard[]>([]);
  const [cardDetails, setCardDetails] = useState<Record<number, Card>>({});
  const [loading, setLoading] = useState(false);

  const fetchGameData = async () => {
    try {
      setLoading(true);
      const [game, cards] = await Promise.all([
        gameApi.getCurrentGame(),
        gameApi.getMyCards()
      ]);
      setCurrentGame(game);
      setMyCards(cards);

      // Fetch details for all cards
      const details: Record<number, Card> = {};
      await Promise.all(cards.map(async (gc) => {
        const detail = await cardApi.getCard(gc.cardId);
        details[gc.cardId] = detail;
      }));
      setCardDetails(details);
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
  }, []);

  return { currentGame, myCards, cardDetails, loading, fetchGameData, joinGame };
};

