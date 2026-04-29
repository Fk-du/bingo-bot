import React from 'react';
import GameStatusIndicator from '../features/game/GameStatusIndicator';

interface LayoutProps {
  children: React.ReactNode;
  connected: boolean;
}

const MainLayout: React.FC<LayoutProps> = ({ children, connected }) => {
  return (
    <div className="min-h-screen bg-[#0f172a] text-white overflow-hidden selection:bg-purple-500/30">
      <GameStatusIndicator connected={connected} />
      <div className="relative z-10 p-6 max-w-lg mx-auto">
        {children}
      </div>
      
      {/* Background blobs for premium feel */}
      <div className="fixed top-0 left-0 w-full h-full pointer-events-none -z-0">
        <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-purple-500/10 rounded-full blur-[120px]" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-pink-500/10 rounded-full blur-[120px]" />
      </div>
    </div>
  );
};

export default MainLayout;
