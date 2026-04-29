import React from 'react';
import { Play, QrCode } from 'lucide-react';
import BingoCard from '../../components/BingoCard';
import type { Game, GameCard, Card } from '../../types';

interface GameAreaProps {
  currentGame: Game | null;
  activeGameCard: GameCard | undefined | null;
  cardDetails: Record<number, Card>;
  calledNumbers: number[];
  gameStatus: string | null;
  gameLoading: boolean;
  onJoin: () => void;
}

const GameArea: React.FC<GameAreaProps> = ({ 
  currentGame, 
  activeGameCard, 
  cardDetails, 
  calledNumbers, 
  gameStatus,
  gameLoading,
  onJoin 
}) => {
  if (!currentGame) {
    return (
      <div className="glass-card p-8 text-center border-dashed border-slate-700">
        <p className="text-slate-400 text-sm">No active games found.</p>
      </div>
    );
  }

  return (
    <div className="mb-10 text-white">
      <div className="flex justify-between items-end mb-4 px-1">
        <h3 className="text-lg font-bold">
          {activeGameCard ? 'Your Bingo Card' : 'Game Status'}
        </h3>
        <div className="flex items-center gap-2">
           <div className={`h-2 w-2 rounded-full ${currentGame.status === 'STARTED' ? 'bg-green-500 animate-pulse' : 'bg-yellow-500'}`} />
           <span className="text-xs font-bold uppercase tracking-widest text-slate-400">
             {gameStatus || currentGame.status}
           </span>
        </div>
      </div>

      {activeGameCard ? (
        <BingoCard 
          numbers={cardDetails[activeGameCard.cardId]?.numbers || ''} 
          calledNumbers={calledNumbers} 
        />
      ) : (
        <div className="glass-card p-8 text-center bg-purple-500/5">
          <QrCode size={40} className="mx-auto mb-4 text-slate-600" />
          <h4 className="font-bold mb-2">Join the Arena</h4>
          <p className="text-sm text-slate-400 mb-6">Arena #{currentGame.id} is waiting for players. Join now to get your card!</p>
          <button 
            onClick={onJoin}
            disabled={gameLoading}
            className="btn-primary w-full py-4 flex items-center justify-center gap-2"
          >
            <Play size={18} fill="currentColor" />
            {gameLoading ? 'Joining...' : 'Get My Card • ' + currentGame.entryFee + ' PTS'}
          </button>
        </div>
      )}
    </div>
  );
};

export default GameArea;
