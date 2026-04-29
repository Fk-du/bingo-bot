import React from 'react';
import { motion } from 'framer-motion';
import { Wallet, History, Gift, ShoppingCart, User as UserIcon } from 'lucide-react';
import BalanceCard from '../dashboard/BalanceCard';
import LiveNumberDisplay from '../game/LiveNumberDisplay';
import GameArea from '../game/GameArea';
import type { Game, GameCard, CardDetail } from '../../types';

interface PlayerDashboardProps {
  user: any;
  currentGame: Game | null;
  activeGameCard: GameCard | undefined;
  cardDetails: CardDetail[];
  calledNumbers: number[];
  gameStatus: string;
  gameLoading: boolean;
  lastNumber: number | null;
  onJoin: () => void;
}

const PlayerDashboard: React.FC<PlayerDashboardProps> = ({
  user,
  currentGame,
  activeGameCard,
  cardDetails,
  calledNumbers,
  gameStatus,
  gameLoading,
  lastNumber,
  onJoin
}) => {
  return (
    <div className="space-y-6 pb-20">
      <div className="flex items-center justify-between mb-2 px-1">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-full bg-gradient-to-tr from-purple-500 to-pink-500 p-[2px]">
            <div className="h-full w-full rounded-full bg-slate-900 flex items-center justify-center">
              <UserIcon size={18} className="text-white" />
            </div>
          </div>
          <div>
            <div className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Welcome Back</div>
            <div className="text-sm font-bold text-white">Player_{user?.telegramId}</div>
          </div>
        </div>
        <div className="flex gap-2">
          <button className="h-10 w-10 rounded-xl glass-card flex items-center justify-center text-slate-400">
             <History size={18} />
          </button>
          <button className="h-10 w-10 rounded-xl glass-card flex items-center justify-center text-slate-400">
             <ShoppingCart size={18} />
          </button>
        </div>
      </div>

      <LiveNumberDisplay lastNumber={lastNumber} />
      
      <BalanceCard balance={user?.balance || 0} />
      
      <GameArea 
        currentGame={currentGame}
        activeGameCard={activeGameCard}
        cardDetails={cardDetails}
        calledNumbers={calledNumbers}
        gameStatus={gameStatus}
        gameLoading={gameLoading}
        onJoin={onJoin}
      />

      {/* Player Actions */}
      <div className="grid grid-cols-2 gap-3 mt-4">
        <button className="glass-card p-4 flex items-center justify-between group">
           <span className="text-[10px] font-black uppercase text-slate-400 group-hover:text-purple-400">Buy Points</span>
           <ShoppingCart size={16} className="text-purple-500" />
        </button>
        <button className="glass-card p-4 flex items-center justify-between group">
           <span className="text-[10px] font-black uppercase text-slate-400 group-hover:text-green-400">Withdraw</span>
           <Wallet size={16} className="text-green-500" />
        </button>
      </div>
    </div>
  );
};

export default PlayerDashboard;
