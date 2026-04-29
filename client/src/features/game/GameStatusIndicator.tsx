import React from 'react';

interface StatusProps {
  connected: boolean;
}

const GameStatusIndicator: React.FC<StatusProps> = ({ connected }) => {
  return (
    <div className="fixed top-4 left-1/2 -translate-x-1/2 z-[60] pointer-events-none">
      <div className={`px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-widest flex items-center gap-2 ${connected ? 'bg-green-500/10 text-green-500 border border-green-500/20' : 'bg-red-500/10 text-red-500 border border-red-500/20'}`}>
        <div className={`h-1.5 w-1.5 rounded-full ${connected ? 'bg-green-500 animate-pulse' : 'bg-red-500'}`} />
        {connected ? 'Live Sync Active' : 'Connecting...'}
      </div>
    </div>
  );
};

export default GameStatusIndicator;
