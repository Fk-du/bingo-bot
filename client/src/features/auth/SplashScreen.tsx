import React from 'react';
import { motion } from 'framer-motion';
import { Trophy } from 'lucide-react';
import heroImage from '../../assets/hero.png';

const SplashScreen: React.FC = () => {
  return (
    <motion.div
      key="splash"
      initial={{ opacity: 1 }}
      exit={{ opacity: 0, scale: 1.1 }}
      transition={{ duration: 0.8 }}
      className="fixed inset-0 z-50 overflow-hidden bg-slate-950"
    >
      <img
        src={heroImage}
        alt=""
        aria-hidden="true"
        className="absolute inset-0 h-full w-full object-cover opacity-65"
      />
      <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/80 to-slate-950/35" />

      <div className="relative z-10 flex h-full w-full flex-col justify-end px-6 pb-14 sm:px-10">
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
          className="mb-4 inline-flex items-center gap-3 self-start rounded-full border border-white/10 bg-slate-950/60 px-4 py-2 text-[10px] font-bold uppercase tracking-[0.2em] text-slate-200 backdrop-blur"
        >
          <Trophy size={16} className="text-purple-400" />
          BingoPlus Mini App
        </motion.div>

        <motion.h1
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.15 }}
          className="text-4xl font-black uppercase tracking-tight text-white sm:text-5xl"
        >
          BingoPlus
        </motion.h1>

        <motion.p
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.25 }}
          className="mt-2 max-w-sm text-sm text-slate-300"
        >
          Telegram-native bingo with tenant wallets, live number calls, and audited claims.
        </motion.p>

        <motion.div
          initial={{ width: 0 }}
          animate={{ width: 160 }}
          transition={{ delay: 0.35, duration: 0.4 }}
          className="mt-6 h-1 bg-purple-500"
        />
      </div>
    </motion.div>
  );
};

export default SplashScreen;
