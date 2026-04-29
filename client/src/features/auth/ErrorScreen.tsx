import React from 'react';
import { AlertCircle, RefreshCw } from 'lucide-react';

interface ErrorProps {
  error: string;
}

const ErrorScreen: React.FC<ErrorProps> = ({ error }) => {
  return (
    <div className="min-h-screen bg-[#0f172a] flex items-center justify-center p-6 text-center">
      <div className="glass-card p-8 max-w-sm text-white">
        <AlertCircle size={48} className="text-red-500 mx-auto mb-4" />
        <h2 className="text-xl font-bold mb-2">Authentication Error</h2>
        <p className="text-slate-400 mb-6">{error}</p>
        <button 
          onClick={() => window.location.reload()}
          className="btn-primary w-full flex items-center justify-center gap-2"
        >
          <RefreshCw size={18} />
          Try Again
        </button>
      </div>
    </div>
  );
};

export default ErrorScreen;
