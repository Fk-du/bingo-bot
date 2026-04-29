import React from 'react';
import { motion } from 'framer-motion';
import { Shield, Users, BarChart3, Settings, Database, Activity } from 'lucide-react';

const SuperAdminDashboard: React.FC = () => {
  const stats = [
    { label: 'Total Agents', value: '24', icon: <Users size={16} />, color: 'text-blue-500' },
    { label: 'Total Revenue', value: '$12,450', icon: <BarChart3 size={16} />, color: 'text-green-500' },
    { label: 'Active Games', value: '8', icon: <Activity size={16} />, color: 'text-purple-500' },
  ];

  const actions = [
    { name: 'Create Agent', icon: <Users />, desc: 'Add new admin/agent' },
    { name: 'Fund Agents', icon: <Database />, desc: 'Add points to agent wallets' },
    { name: 'System Reports', icon: <BarChart3 />, desc: 'View global analytics' },
    { name: 'Control Center', icon: <Settings />, desc: 'System configuration' },
  ];

  return (
    <div className="space-y-6 text-white pb-20">
      <div className="flex items-center justify-between mb-8">
        <div>
          <span className="text-[10px] font-bold uppercase tracking-widest text-blue-500">System Oversight</span>
          <h2 className="text-2xl font-black italic tracking-tighter uppercase">SUPER <span className="text-blue-500">ADMIN</span></h2>
        </div>
        <div className="h-10 w-10 rounded-full bg-blue-500/10 flex items-center justify-center border border-blue-500/20">
          <Shield size={20} className="text-blue-500" />
        </div>
      </div>

      {/* Quick Stats */}
      <div className="grid grid-cols-3 gap-3">
        {stats.map((stat, i) => (
          <div key={i} className="glass-card p-3 flex flex-col gap-1">
            <div className={`${stat.color} mb-1`}>{stat.icon}</div>
            <div className="text-lg font-bold tracking-tighter">{stat.value}</div>
            <div className="text-[8px] text-slate-500 font-bold uppercase">{stat.label}</div>
          </div>
        ))}
      </div>

      {/* Main Actions */}
      <div className="grid grid-cols-1 gap-4 mt-8">
        {actions.map((action, i) => (
          <motion.div
            key={i}
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            className="glass-card p-4 flex items-center gap-4 cursor-pointer hover:bg-blue-500/5 transition-colors border border-white/5 hover:border-blue-500/20"
          >
            <div className="h-12 w-12 rounded-2xl bg-blue-500/10 flex items-center justify-center text-blue-500">
              {action.icon}
            </div>
            <div>
              <h3 className="font-bold text-sm">{action.name}</h3>
              <p className="text-[10px] text-slate-500 font-bold uppercase">{action.desc}</p>
            </div>
          </motion.div>
        ))}
      </div>

      <div className="mt-8 p-6 glass-card bg-gradient-to-br from-blue-500/10 to-transparent border-blue-500/20">
        <h4 className="text-sm font-bold mb-2">Network Health</h4>
        <div className="h-2 w-full bg-white/5 rounded-full overflow-hidden">
          <div className="h-full w-[94%] bg-blue-500" />
        </div>
        <div className="flex justify-between mt-2 text-[10px] font-bold text-slate-500 uppercase">
          <span>94% System Efficiency</span>
          <span>Latency: 24ms</span>
        </div>
      </div>
    </div>
  );
};

export default SuperAdminDashboard;
