import React from 'react';
import { motion } from 'framer-motion';
import { Check } from 'lucide-react';

interface BingoCardProps {
  numbers: string; // "1,16,31,46,61,..."
  calledNumbers: number[];
}

const BingoCard: React.FC<BingoCardProps> = ({ numbers, calledNumbers }) => {
  const tokens = numbers.split(',').map(t => t.trim());

  return (
    <div className="glass-card p-4 bg-white/5 border-white/10 select-none">
      <div className="grid grid-cols-5 gap-2 mb-4">
        {['B', 'I', 'N', 'G', 'O'].map((letter) => (
          <div key={letter} className="text-center font-black text-purple-500 text-lg py-2">
            {letter}
          </div>
        ))}
      </div>
      
      <div className="grid grid-cols-5 gap-2">
        {tokens.map((token, index) => {
          const isFree = token.toLowerCase() === 'free';
          const num = parseInt(token);
          const isMarked = isFree || calledNumbers.includes(num);

          return (
            <motion.div
              key={index}
              initial={false}
              animate={{
                scale: isMarked ? [1, 1.1, 1] : 1,
                backgroundColor: isMarked ? 'rgba(139, 92, 246, 0.2)' : 'rgba(255, 255, 255, 0.03)',
                borderColor: isMarked ? 'rgba(139, 92, 246, 0.4)' : 'rgba(255, 255, 255, 0.05)',
              }}
              className={`
                aspect-square rounded-xl border flex items-center justify-center relative overflow-hidden
                ${isMarked ? 'text-white font-bold' : 'text-slate-500'}
              `}
            >
              <span className="relative z-10 text-xs sm:text-sm">
                {isFree ? 'FREE' : token}
              </span>
              
              {isMarked && (
                <motion.div 
                  initial={{ opacity: 0, scale: 0 }}
                  animate={{ opacity: 1, scale: 1 }}
                  className="absolute inset-0 flex items-center justify-center opacity-10"
                >
                  <Check size={24} className="text-purple-500" />
                </motion.div>
              )}

              {isMarked && !isFree && (
                 <motion.div 
                  layoutId={`mark-${num}`}
                  className="absolute inset-0 bg-purple-500/10 pointer-events-none"
                 />
              )}
            </motion.div>
          );
        })}
      </div>
    </div>
  );
};

export default BingoCard;
