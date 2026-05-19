import React from 'react';
import GameStatusIndicator from '../features/game/GameStatusIndicator';

interface LayoutProps {
  children: React.ReactNode;
  connected: boolean;
}

const MainLayout: React.FC<LayoutProps> = ({ children, connected }) => {
  return (
    <div className="min-h-screen bg-slate-950 text-white overflow-hidden selection:bg-purple-500/30">
      <GameStatusIndicator connected={connected} />
      <div className="relative z-10 p-6 max-w-lg mx-auto">
        {children}
      </div>

      <div
        className="fixed inset-0 pointer-events-none -z-0 opacity-40"
        style={{
          backgroundImage:
            'linear-gradient(rgba(148, 163, 184, 0.08) 1px, transparent 1px), linear-gradient(90deg, rgba(148, 163, 184, 0.08) 1px, transparent 1px)',
          backgroundSize: '32px 32px',
        }}
      />
    </div>
  );
};

export default MainLayout;
