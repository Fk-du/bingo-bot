import React from 'react';
import { Settings } from 'lucide-react';
import type { User } from '../../types';

interface HeaderProps {
  user: User | null;
}

const DashboardHeader: React.FC<HeaderProps> = ({ user }) => {
  return (
    <div className="flex justify-between items-center mb-10">
      <div>
        <h2 className="text-sm text-slate-400 font-medium uppercase tracking-wider">Welcome back,</h2>
        <h1 className="text-2xl font-bold">User #{user?.telegramId}</h1>
      </div>
      <div className="h-12 w-12 rounded-2xl glass-card flex items-center justify-center relative">
        <div className="absolute -top-1 -right-1 h-3 w-3 bg-green-500 rounded-full border-2 border-[#0f172a]" />
        <Settings size={20} className="text-slate-400" />
      </div>
    </div>
  );
};

export default DashboardHeader;
