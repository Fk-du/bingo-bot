import React, { useEffect, useState } from 'react';
import { Wallet, History, ShoppingCart, User as UserIcon, Link as LinkIcon, X } from 'lucide-react';
import BalanceCard from '../dashboard/BalanceCard';
import LiveNumberDisplay from '../game/LiveNumberDisplay';
import GameArea from '../game/GameArea';
import { playerApi } from '../../api/services';
import type { Game, GameCard, Card, Transaction } from '../../types';
import { useToast } from '../../components/Toast';
import { copyTextWithFallback } from '../../utils/copy';

interface PlayerDashboardProps {
  user: any;
  currentGame: Game | null;
  activeGameCard: GameCard | undefined;
  cardDetails: Record<number, Card>;
  calledNumbers: number[];
  gameStatus: string | null;
  gameLoading: boolean;
  lastNumber: number | null;
  onRefresh: () => void;
  onRefreshUser: () => Promise<void>;
  onJoinWithCard: (cardId: number) => Promise<void>;
}

const PlayerDashboard: React.FC<PlayerDashboardProps> = ({
  user,
  currentGame,
  activeGameCard,
  cardDetails,
  calledNumbers,
  gameStatus,
  gameLoading,
  lastNumber,
  onRefresh,
  onRefreshUser,
  onJoinWithCard
}) => {
  const [history, setHistory] = useState<Transaction[]>([]);
  const [showHistory, setShowHistory] = useState(false);
  const [showShop, setShowShop] = useState(false);
  const [buyAmount, setBuyAmount] = useState('10');
  const [buying, setBuying] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    const loadHistory = async () => {
      try {
        setHistory(await playerApi.getHistory());
      } catch (error) {
        console.error('Failed to load player history', error);
      }
    };
    loadHistory();
  }, [user?.telegramId, currentGame?.id]);

  const handleRequestCoins = async () => {
    const amount = Number(window.prompt('Coin amount', '10'));
    if (Number.isNaN(amount)) return toast('error', 'Invalid amount');

    const addScreenshot = window.confirm('Attach a payment screenshot?');
    let proofImageFileId: string | undefined;
    if (addScreenshot) {
      proofImageFileId = window.prompt('Telegram file ID of payment screenshot') || undefined;
    }

    try {
      await playerApi.requestTopUp(amount, proofImageFileId);
      await onRefreshUser();
      toast('success', 'Coin request submitted. Waiting for admin approval.');
    } catch (error: any) {
      toast('error', error?.response?.data?.message || error?.message || 'Failed to request coins');
    }
  };

  const handleWithdraw = async () => {
    const amount = Number(window.prompt('Withdraw amount', '10'));
    if (Number.isNaN(amount)) return toast('error', 'Invalid amount');

    try {
      await playerApi.withdrawRequest(amount);
      await onRefreshUser();
      toast('success', 'Withdraw request created');
    } catch (error: any) {
      toast('error', error?.response?.data?.message || error?.message || 'Failed to request withdraw');
    }
  };

  const handleClaim = async () => {
    if (!currentGame) return;

    try {
      await playerApi.claimBingo(currentGame.id);
      await onRefresh();
      await onRefreshUser();
    } catch (error: any) {
      toast('error', error?.response?.data?.message || error?.message || 'Failed to claim bingo');
    }
  };

  const handleInvite = async () => {
    const botUsername = window.prompt('Bot username', import.meta.env.VITE_BOT_USERNAME || '');
    if (!botUsername) return;

    try {
      const link = await playerApi.getInviteLink(botUsername);
      const copied = await copyTextWithFallback(link, 'Player invite');
      if (copied) toast('success', 'Player invite copied');
    } catch (error: any) {
      toast('error', error?.response?.data?.message || error?.message || 'Failed to create invite link');
    }
  };

  const handleBuyPoints = async () => {
    const amount = Number(buyAmount);
    if (Number.isNaN(amount) || amount <= 0) return toast('error', 'Invalid amount');
    setBuying(true);
    try {
      await playerApi.buyPoints(amount);
      await onRefreshUser();
      toast('success', `Purchased ${amount} points`);
      setShowShop(false);
    } catch (error: any) {
      toast('error', error?.response?.data?.message || error?.message || 'Purchase failed');
    } finally {
      setBuying(false);
    }
  };

  return (
    <div className="space-y-6 pb-20">
      <div className="flex items-center justify-between mb-2 px-1">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-full bg-gradient-to-tr from-purple-500 to-pink-500 p-[2px]">
            <div className="h-full w-full rounded-full bg-slate-900 flex items-center justify-center">
              <UserIcon size={18} className="text-white" />
            </div>
          </div>
          <div>
            <div className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Welcome Back</div>
            <div className="text-sm font-bold text-white">Player_{user?.telegramId}</div>
          </div>
        </div>
        <div className="flex gap-2">
          <button onClick={() => setShowHistory(true)} className="h-10 w-10 rounded-xl glass-card flex items-center justify-center text-slate-400">
             <History size={18} />
          </button>
          <button onClick={() => setShowShop(true)} className="h-10 w-10 rounded-xl glass-card flex items-center justify-center text-slate-400">
             <ShoppingCart size={18} />
          </button>
        </div>
      </div>

      <LiveNumberDisplay lastNumber={lastNumber} />
      
      <BalanceCard balance={user?.balance || 0} />
      
      <GameArea 
        currentGame={currentGame}
        activeGameCard={activeGameCard}
        cardDetails={cardDetails}
        calledNumbers={calledNumbers}
        gameStatus={gameStatus}
        gameLoading={gameLoading}
        onClaim={handleClaim}
        onJoinWithCard={onJoinWithCard}
      />

      <div className="grid grid-cols-2 gap-3 mt-4">
        <button onClick={handleRequestCoins} className="glass-card p-4 flex items-center justify-between group">
           <span className="text-[10px] font-black uppercase text-slate-400 group-hover:text-purple-400">Request Coins</span>
           <ShoppingCart size={16} className="text-purple-500" />
        </button>
        <button onClick={handleWithdraw} className="glass-card p-4 flex items-center justify-between group">
           <span className="text-[10px] font-black uppercase text-slate-400 group-hover:text-green-400">Withdraw</span>
           <Wallet size={16} className="text-green-500" />
        </button>
        <button onClick={handleInvite} className="glass-card p-4 flex items-center justify-between group">
           <span className="text-[10px] font-black uppercase text-slate-400 group-hover:text-blue-400">Invite Friend</span>
           <LinkIcon size={16} className="text-blue-500" />
        </button>
      </div>

      <div className="glass-card p-4">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Recent Activity</h3>
          <History size={14} className="text-slate-500" />
        </div>
        <div className="space-y-3">
          {history.slice(0, 5).map((tx) => (
            <div key={tx.id} className="flex items-center justify-between border-b border-white/5 pb-2 last:border-0 last:pb-0">
              <div>
                <div className="text-sm font-bold">{tx.type.replace(/_/g, ' ')}</div>
                <div className="text-[10px] text-slate-500 uppercase">{tx.status}</div>
              </div>
              <div className="text-sm font-bold text-purple-400">{tx.amount > 0 ? '+' : ''}{tx.amount}</div>
            </div>
          ))}
          {!history.length && <div className="text-sm text-slate-500">No transactions yet.</div>}
        </div>
      </div>

      {/* History Modal */}
      {showHistory && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
          onClick={() => setShowHistory(false)}>
          <div className="bg-slate-900 border border-slate-700 rounded-3xl w-full max-w-md max-h-[70vh] overflow-y-auto p-5"
            onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-bold text-white">Transaction History</h3>
              <button onClick={() => setShowHistory(false)}
                className="h-8 w-8 rounded-lg bg-slate-800 text-slate-400 flex items-center justify-center">
                <X size={16} />
              </button>
            </div>
            {history.length === 0 ? (
              <p className="text-sm text-slate-500 text-center py-8">No transactions yet.</p>
            ) : (
              <div className="space-y-3">
                {history.map((tx) => (
                  <div key={tx.id} className="flex items-center justify-between py-2 border-b border-white/5">
                    <div>
                      <div className="text-sm font-bold">{tx.type.replace(/_/g, ' ')}</div>
                      <div className="text-[10px] text-slate-500 uppercase">{tx.status} · {new Date(tx.createdAt).toLocaleDateString()}</div>
                    </div>
                    <div className="text-sm font-bold text-purple-400">{tx.amount > 0 ? '+' : ''}{tx.amount}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Shop Modal */}
      {showShop && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
          onClick={() => setShowShop(false)}>
          <div className="bg-slate-900 border border-slate-700 rounded-3xl w-full max-w-md p-5"
            onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-bold text-white">Buy Points</h3>
              <button onClick={() => setShowShop(false)}
                className="h-8 w-8 rounded-lg bg-slate-800 text-slate-400 flex items-center justify-center">
                <X size={16} />
              </button>
            </div>
            <input
              type="number"
              value={buyAmount}
              onChange={e => setBuyAmount(e.target.value)}
              min="1"
              className="w-full px-4 py-3 rounded-2xl bg-slate-800 border border-slate-700 text-white text-lg font-bold text-center"
              placeholder="Amount"
            />
            <button
              onClick={handleBuyPoints}
              disabled={buying}
              className="btn-primary w-full mt-4 py-4 disabled:opacity-40"
            >
              {buying ? 'Processing...' : `Buy ${buyAmount || '0'} Points`}
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default PlayerDashboard;
