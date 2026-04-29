import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Trophy } from 'lucide-react';

interface WinnerProps {
  winner: any;
}

const WinnerAnnouncement: React.FC<WinnerProps> = ({ winner }) => {
  if (!winner) return null;

  return (
    <motion.div 
      initial={{ opacity: 0, scale: 0.9, y: 20 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.9, y: 20 }}
      className="fixed inset-x-6 bottom-10 z-[100] glass-card p-4 bg-yellow-500/20 border-yellow-500/50 flex items-center gap-4 shadow-2xl"
    >
       <div className="h-12 w-12 rounded-full bg-yellow-500 flex items-center justify-center shrink-0">
          <Trophy size={24} className="text-black" />
       </div>
       <div>
          <h4 className="text-sm font-black text-yellow-500 uppercase italic">Bingo Winner!</h4>
          <p className="text-xs text-white">Player #{winner.playerId} won the game!</p>
       </div>
       <button 
        onClick={() => window.location.reload()}
        className="ml-auto text-xs font-bold underline text-white"
       >
         New Game
       </button>
    </motion.div>
  );
};

export default WinnerAnnouncement;
