import React, { useState } from 'react';
import { Play, QrCode } from 'lucide-react';
import { AnimatePresence } from 'framer-motion';
import BingoCard from '../../components/BingoCard';
import CardPicker from './CardPicker';
import type { Game, GameCard, Card } from '../../types';

interface GameAreaProps {
  currentGame: Game | null;
  activeGameCard: GameCard | undefined | null;
  cardDetails: Record<number, Card>;
  calledNumbers: number[];
  gameStatus: string | null;
  gameLoading: boolean;
  onClaim: () => void;
  onJoinWithCard: (cardId: number) => Promise<void>;
}

const GameArea: React.FC<GameAreaProps> = ({
  currentGame,
  activeGameCard,
  cardDetails,
  calledNumbers,
  gameStatus,
  gameLoading,
  onClaim,
  onJoinWithCard
}) => {
  const [showPicker, setShowPicker] = useState(false);

  if (!currentGame) {
    return (
      <div className="glass-card p-8 text-center border-dashed border-slate-700">
        <p className="text-slate-400 text-sm">No active games found.</p>
      </div>
    );
  }

  const liveStatus = gameStatus === 'IN_PROGRESS' || gameStatus === 'CLAIM_PENDING';
  const registrationStatus = gameStatus === 'REGISTRATION_OPEN';

  const handleJoinClick = () => {
    setShowPicker(true);
  };

  const handleCardSelected = async (cardId: number) => {
    await onJoinWithCard(cardId);
    setShowPicker(false);
  };

  return (
    <div className="mb-10 text-white">
      <div className="flex justify-between items-end mb-4 px-1">
        <h3 className="text-lg font-bold">
          {activeGameCard ? 'Your Bingo Card' : 'Game Status'}
        </h3>
        <div className="flex items-center gap-2">
          <div
            className={`h-2 w-2 rounded-full ${
              liveStatus ? 'bg-green-500 animate-pulse' : registrationStatus ? 'bg-yellow-500' : 'bg-slate-500'
            }`}
          />
          <span className="text-xs font-bold uppercase tracking-widest text-slate-400">
            {gameStatus || currentGame.status}
          </span>
        </div>
      </div>

      {activeGameCard ? (
        <div className="space-y-4">
          {liveStatus && (
            <button onClick={onClaim} className="btn-primary w-full py-4">
              Claim Bingo
            </button>
          )}
          <BingoCard
            numbers={cardDetails[activeGameCard.cardId]?.numbers || ''}
            calledNumbers={calledNumbers}
          />
        </div>
      ) : (
        <div className="glass-card p-8 text-center bg-purple-500/5">
          <QrCode size={40} className="mx-auto mb-4 text-slate-600" />
          <h4 className="font-bold mb-2">Join the Arena</h4>
          <p className="text-sm text-slate-400 mb-6">
            Arena #{currentGame.id} is waiting for players. Pick your card!
          </p>
          <button
            onClick={handleJoinClick}
            disabled={gameLoading}
            className="btn-primary w-full py-4 flex items-center justify-center gap-2"
          >
            <Play size={18} fill="currentColor" />
            {gameLoading ? 'Loading...' : `Pick a Card • ${currentGame.entryFee} PTS`}
          </button>
        </div>
      )}

      <AnimatePresence>
        {showPicker && (
          <CardPicker
            gameId={currentGame.id}
            onSelect={handleCardSelected}
            onClose={() => setShowPicker(false)}
          />
        )}
      </AnimatePresence>
    </div>
  );
};

export default GameArea;
