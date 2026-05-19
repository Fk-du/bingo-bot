import React, { useState, useEffect, useMemo, useRef } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useAuth } from './hooks/useAuth';
import { useGame } from './hooks/useGame';
import { useWebSocket } from './hooks/useWebSocket';
import { ErrorBoundary } from './components/ErrorBoundary';
import { ToastProvider } from './components/Toast';

import MainLayout from './layouts/MainLayout';
import SplashScreen from './features/auth/SplashScreen';
import ErrorScreen from './features/auth/ErrorScreen';
import SuperAdminDashboard from './features/super-admin/SuperAdminDashboard';
import AdminDashboard from './features/admin/AdminDashboard';
import PlayerDashboard from './features/player/PlayerDashboard';
import WinnerAnnouncement from './features/game/WinnerAnnouncement';

const App: React.FC = () => {
  const { user, loading: authLoading, error: authError, refreshUser } = useAuth();
  const { currentGame, myCards, cardDetails, loading: gameLoading, joinGame, fetchGameData } = useGame(user?.role);
  const { connected, lastNumber, gameStatus, winner } = useWebSocket(currentGame?.id);
  const [showSplash, setShowSplash] = useState(true);
  const [calledNumbers, setCalledNumbers] = useState<number[]>([]);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => setShowSplash(false), 2000);
    return () => clearTimeout(timer);
  }, []);

  useEffect(() => {
    if (lastNumber) {
      setCalledNumbers(prev => Array.from(new Set([...prev, lastNumber])));
    }
  }, [lastNumber]);

  useEffect(() => {
    if (gameStatus === 'REGISTRATION_OPEN' || gameStatus === 'IN_PROGRESS') {
      setCalledNumbers([]);
    }
    if (gameStatus) {
      if (debounceRef.current) clearTimeout(debounceRef.current);
      debounceRef.current = setTimeout(() => fetchGameData(), 300);
    }
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [gameStatus]);

  const activeGameCard = useMemo(() => {
    if (!currentGame) return null;
    return myCards.find(c => c.gameId === currentGame.id);
  }, [myCards, currentGame]);

  const handleJoinWithCard = async (cardId: number) => {
    if (currentGame) {
      await joinGame(currentGame.id, cardId);
    }
  };

  if (authError) return <ErrorScreen error={authError} />;

  const renderDashboard = () => {
    switch (user?.role) {
      case 'SUPER_ADMIN':
        return <SuperAdminDashboard />;
      case 'ADMIN':
        return (
          <AdminDashboard 
            currentGame={currentGame} 
            connected={connected} 
            onRefresh={fetchGameData} 
          />
        );
      case 'PLAYER':
      default:
        return (
          <PlayerDashboard 
            user={user}
            currentGame={currentGame}
            activeGameCard={activeGameCard || undefined}
            cardDetails={cardDetails}
            calledNumbers={calledNumbers}
            gameStatus={gameStatus}
            gameLoading={gameLoading}
            lastNumber={lastNumber}
            onRefresh={fetchGameData}
            onRefreshUser={refreshUser}
            onJoinWithCard={handleJoinWithCard}
          />
        );
    }
  };

  return (
    <ErrorBoundary>
      <ToastProvider>
        <AnimatePresence mode="wait">
          {(showSplash || authLoading) && <SplashScreen key="splash" />}
        </AnimatePresence>

        {!authLoading && !showSplash && (
          <MainLayout connected={connected}>
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="w-full max-w-md mx-auto"
            >
              {renderDashboard()}
              <WinnerAnnouncement winner={winner} onNewGame={fetchGameData} />
            </motion.div>
          </MainLayout>
        )}
      </ToastProvider>
    </ErrorBoundary>
  );
};

export default App;
