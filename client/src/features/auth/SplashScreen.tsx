import React from 'react';
import { motion } from 'framer-motion';
import { Trophy } from 'lucide-react';

const SplashScreen: React.FC = () => {
  return (
    <motion.div
      key="splash"
      initial={{ opacity: 1 }}
      exit={{ opacity: 0, scale: 1.1 }}
      transition={{ duration: 0.8 }}
      className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-gradient-to-br from-[#0f172a] to-[#1e1b4b]"
    >
      <motion.div
        initial={{ scale: 0.5, rotate: -10 }}
        animate={{ scale: 1, rotate: 0 }}
        transition={{ 
          type: "spring", 
          stiffness: 260, 
          damping: 20 
        }}
        className="relative"
      >
        <div className="absolute -inset-4 bg-purple-500 rounded-full blur-2xl opacity-20 animate-pulse"></div>
        <Trophy size={80} className="text-purple-500 relative z-10" />
      </motion.div>
      
      <motion.h1 
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.5 }}
        className="mt-8 text-4xl font-bold tracking-tighter text-white"
      >
        BINGO <span className="text-purple-500">PLATFORM</span>
      </motion.h1>
      
      <motion.div 
        initial={{ width: 0 }}
        animate={{ width: 120 }}
        className="h-1 bg-purple-500 mt-4 rounded-full"
      />
    </motion.div>
  );
};

export default SplashScreen;
