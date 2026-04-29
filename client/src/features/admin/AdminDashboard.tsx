import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { Play, Pause, Plus, Users, Radio, Power, Wallet, Link as LinkIcon, AlertTriangle } from 'lucide-react';
import type { Game } from '../../types';

interface AdminDashboardProps {
  currentGame: Game | null;
  connected: boolean;
  onRefresh: () => void;
}

const AdminDashboard: React.FC<AdminDashboardProps> = ({ currentGame, connected, onRefresh }) => {
  const [loading, setLoading] = useState(false);

  const adminActions = [
    { name: 'My Players', icon: <Users size={18} />, color: 'bg-purple-500/10 text-purple-500' },
    { name: 'Fund Wallet', icon: <Wallet size={18} />, color: 'bg-green-500/10 text-green-500' },
    { name: 'Invite Link', icon: <LinkIcon size={18} />, color: 'bg-blue-500/10 text-blue-500' },
    { name: 'Withdrawals', icon: <AlertTriangle size={18} />, color: 'bg-yellow-500/10 text-yellow-500' },
  ];

  return (
    <div className="space-y-6 text-white pb-20">
      <div className="flex items-center justify-between mb-8">
        <div>
          <span className="text-[10px] font-bold uppercase tracking-widest text-purple-500">Agent Command</span>
          <h2 className="text-2xl font-black italic tracking-tighter uppercase">ADMIN <span className="text-purple-500">PANEL</span></h2>
        </div>
        <div className={`h-3 w-3 rounded-full ${connected ? 'bg-green-500 shadow-[0_0_10px_rgba(34,197,94,0.5)]' : 'bg-red-500'}`} />
      </div>

      {/* Game Control Section */}
      <div className="glass-card p-6 bg-gradient-to-br from-slate-800/50 to-slate-900/50 border-purple-500/20">
        {!currentGame ? (
          <div className="text-center py-4">
             <div className="h-12 w-12 rounded-full bg-purple-500/10 flex items-center justify-center text-purple-500 mx-auto mb-4">
               <Plus size={24} />
             </div>
             <h3 className="text-lg font-bold">No Active Arena</h3>
             <p className="text-[10px] text-slate-500 font-bold uppercase mt-1">Ready to host a match?</p>
             <button className="btn-primary w-full mt-6 py-4">Create New Game</button>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex justify-between items-center">
              <span className="px-2 py-1 rounded-md bg-purple-500/20 text-purple-500 text-[10px] font-black uppercase">Active Game #{currentGame.id}</span>
              <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">{currentGame.status}</span>
            </div>
            
            <div className="flex gap-4">
              <button className="flex-1 py-4 glass-card border-purple-500/20 flex items-center justify-center gap-2">
                <Pause size={18} fill="currentColor" />
                <span className="font-bold text-sm uppercase">Pause</span>
              </button>
              <button className="flex-1 py-4 bg-red-500/10 text-red-500 border border-red-500/20 rounded-2xl flex items-center justify-center gap-2">
                <Power size={18} />
                <span className="font-bold text-sm uppercase">End</span>
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Admin Quick Actions */}
      <div className="grid grid-cols-2 gap-3">
        {adminActions.map((action, i) => (
          <motion.div
            key={i}
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            className="glass-card p-4 flex flex-col gap-3 cursor-pointer border-white/5"
          >
            <div className={`h-10 w-10 rounded-xl ${action.color} flex items-center justify-center`}>
              {action.icon}
            </div>
            <div className="font-bold text-xs uppercase tracking-tight">{action.name}</div>
          </motion.div>
        ))}
      </div>

      {/* Stats Table Placeholder */}
      <div className="glass-card p-4">
        <h3 className="text-[10px] font-bold text-slate-500 uppercase tracking-widest mb-4">Recent Activity</h3>
        <div className="space-y-3">
          {[1, 2, 3].map((_, i) => (
            <div key={i} className="flex items-center justify-between py-2 border-b border-white/5 last:border-0">
              <div className="flex items-center gap-3">
                <div className="h-8 w-8 rounded-full bg-slate-800 border border-white/5" />
                <div>
                  <div className="text-[11px] font-bold">Player_1203</div>
                  <div className="text-[9px] text-slate-500 uppercase">Joined Game #42</div>
                </div>
              </div>
              <div className="text-[11px] font-bold text-purple-400">+$10.00</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default AdminDashboard;
