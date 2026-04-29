import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Radio } from 'lucide-react';

interface LiveNumberProps {
  lastNumber: number | null;
}

const LiveNumberDisplay: React.FC<LiveNumberProps> = ({ lastNumber }) => {
  return (
    <AnimatePresence>
      {lastNumber && (
        <motion.div 
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -20 }}
          className="glass-card mb-8 p-4 bg-purple-500/10 border-purple-500/30 flex items-center justify-between text-white"
        >
          <div className="flex items-center gap-3">
            <div className="h-12 w-12 rounded-full bg-purple-500 flex items-center justify-center text-2xl font-black shadow-[0_0_20px_rgba(139,92,246,0.5)]">
              {lastNumber}
            </div>
            <div>
              <h4 className="text-xs font-bold uppercase tracking-widest text-purple-400">Current Number</h4>
              <p className="text-sm text-slate-300">New number called!</p>
            </div>
          </div>
          <Radio className="text-purple-500 animate-pulse" size={24} />
        </motion.div>
      )}
    </AnimatePresence>
  );
};

export default LiveNumberDisplay;
