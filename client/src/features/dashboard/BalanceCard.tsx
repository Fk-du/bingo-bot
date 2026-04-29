import React from 'react';
import { motion } from 'framer-motion';
import { Wallet } from 'lucide-react';

interface BalanceCardProps {
  balance: number;
}

const BalanceCard: React.FC<BalanceCardProps> = ({ balance }) => {
  return (
    <motion.div 
      whileHover={{ scale: 1.02 }}
      className="glass-card p-6 mb-8 bg-gradient-to-br from-purple-500/20 to-pink-500/20 relative overflow-hidden"
    >
      <div className="relative z-10 text-white">
        <div className="flex items-center gap-2 mb-2">
          <Wallet size={16} className="text-purple-400" />
          <span className="text-sm font-medium text-purple-300 uppercase tracking-widest">Available Balance</span>
        </div>
        <div className="text-4xl font-bold">
          {balance.toFixed(2)} <span className="text-lg font-normal text-slate-400">PTS</span>
        </div>
      </div>
      <div className="absolute top-0 right-0 p-4 opacity-10 text-white">
        <Wallet size={100} />
      </div>
    </motion.div>
  );
};

export default BalanceCard;
