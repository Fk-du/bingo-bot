import React, { useState, useEffect, useMemo } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useAuth } from './hooks/useAuth';
import { useGame } from './hooks/useGame';
import { useWebSocket } from './hooks/useWebSocket';

// Layouts & Components
import MainLayout from './layouts/MainLayout';
// Features
import SplashScreen from './features/auth/SplashScreen';
import ErrorScreen from './features/auth/ErrorScreen';
import SuperAdminDashboard from './features/super-admin/SuperAdminDashboard';
import AdminDashboard from './features/admin/AdminDashboard';
import PlayerDashboard from './features/player/PlayerDashboard';
import WinnerAnnouncement from './features/game/WinnerAnnouncement';

const App: React.FC = () => {
  const { user, loading: authLoading, error: authError } = useAuth();
  const { currentGame, myCards, cardDetails, loading: gameLoading, joinGame, fetchGameData } = useGame();
  const { connected, lastNumber, gameStatus, winner } = useWebSocket(currentGame?.id);
  const [showSplash, setShowSplash] = useState(true);
  const [calledNumbers, setCalledNumbers] = useState<number[]>([]);

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
    if (gameStatus === 'WAITING' || gameStatus === 'STARTED') {
      setCalledNumbers([]);
    }
    if (gameStatus) {
      fetchGameData();
    }
  }, [gameStatus]);

  const activeGameCard = useMemo(() => {
    if (!currentGame) return null;
    return myCards.find(c => c.gameId === currentGame.id);
  }, [myCards, currentGame]);

  const handleJoin = async () => {
    if (currentGame) {
      try {
        await joinGame(currentGame.id);
      } catch (e: any) {
        alert(e.response?.data?.message || 'Failed to join game');
      }
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
            onJoin={handleJoin}
          />
        );
    }
  };

  return (
    <>
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
            <WinnerAnnouncement winner={winner} />
          </motion.div>
        </MainLayout>
      )}
    </>
  );
};

export default App;
