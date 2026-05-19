import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { RefreshCw, Pause, Plus, Users, Power, Wallet, Link as LinkIcon, AlertTriangle, ArrowUpFromLine, Check, X } from 'lucide-react';
import { adminApi } from '../../api/services';
import type { Game, Transaction, TopUpRequest } from '../../types';
import { useToast } from '../../components/Toast';
import { copyTextWithFallback } from '../../utils/copy';

interface AdminDashboardProps {
  currentGame: Game | null;
  connected: boolean;
  onRefresh: () => void;
}

const AdminDashboard: React.FC<AdminDashboardProps> = ({ currentGame, connected, onRefresh }) => {
  const [busy, setBusy] = useState(false);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [pendingTopUps, setPendingTopUps] = useState<TopUpRequest[]>([]);
  const { toast } = useToast();

  const refreshTransactions = async () => {
    try {
      setTransactions(await adminApi.getTransactions());
    } catch (error) {
      console.error('Failed to load admin transactions', error);
    }
  };

  useEffect(() => {
    refreshTransactions();
  }, [currentGame?.id]);

  const run = async (task: () => Promise<void>) => {
    try {
      setBusy(true);
      await task();
      await onRefresh();
      await refreshTransactions();
    } catch (error: any) {
      toast('error', error?.response?.data?.message || error?.message || 'Action failed');
    } finally {
      setBusy(false);
    }
  };

  const createGame = async () => {
    const raw = window.prompt('Entry fee', String(currentGame?.entryFee ?? 10));
    if (raw == null) return;
    const entryFee = Number(raw);
    if (Number.isNaN(entryFee)) return toast('error', 'Invalid entry fee');

    await run(async () => {
      await adminApi.createGame(entryFee);
    });
  };

  const startPauseOrResume = async () => {
    if (!currentGame) return;
    await run(async () => {
      if (currentGame.status === 'REGISTRATION_OPEN') {
        await adminApi.startGame(currentGame.id);
      } else if (currentGame.status === 'IN_PROGRESS') {
        await adminApi.pauseGame(currentGame.id);
      } else if (currentGame.status === 'CLAIM_PENDING') {
        await adminApi.resumeGame(currentGame.id);
      }
    });
  };

  const endGame = async () => {
    if (!currentGame) return;
    await run(async () => {
      await adminApi.endGame(currentGame.id);
    });
  };

  const showPlayers = async () => {
    try {
      setBusy(true);
      const players = await adminApi.getPlayers();
      toast('info', players.length ? `${players.length} players found` : 'No players yet');
    } catch (error: any) {
      toast('error', error?.response?.data?.message || error?.message || 'Failed to load players');
    } finally {
      setBusy(false);
    }
  };

  const inviteLink = async () => {
    const botUsername = window.prompt('Bot username', import.meta.env.VITE_BOT_USERNAME || '');
    if (!botUsername) return;

    await run(async () => {
      const link = await adminApi.getInviteLink(botUsername);
      const copied = await copyTextWithFallback(link, 'Invite link');
      if (copied) {
        toast('success', 'Invite link copied');
      }
    });
  };

  const requestCoinsFromSuperAdmin = async () => {
    const amount = Number(window.prompt('Coin amount', '100'));
    if (Number.isNaN(amount)) return toast('error', 'Invalid amount');

    const addScreenshot = window.confirm('Attach a payment screenshot?');
    let proofImageFileId: string | undefined;
    if (addScreenshot) {
      proofImageFileId = window.prompt('Telegram file ID of payment screenshot') || undefined;
    }

    await run(async () => {
      await adminApi.requestTopUp(amount, proofImageFileId);
      toast('success', 'Coin request submitted to super admin.');
    });
  };

  const refreshPendingTopUps = async () => {
    try {
      setPendingTopUps(await adminApi.getPendingTopUps());
    } catch (error) {
      console.error('Failed to load pending top-ups', error);
    }
  };

  useEffect(() => {
    refreshPendingTopUps();
  }, []);

  const approveTopUp = async (id: number) => {
    await run(async () => {
      await adminApi.approveTopUp(id);
      await refreshPendingTopUps();
    });
  };

  const rejectTopUp = async (id: number) => {
    await run(async () => {
      await adminApi.rejectTopUp(id);
      await refreshPendingTopUps();
    });
  };

  const showWithdrawals = async () => {
    try {
      setBusy(true);
      const withdrawals = await adminApi.getWithdrawals();
      if (!withdrawals.length) {
        toast('info', 'No pending withdrawals');
        return;
      }

      const selected = Number(window.prompt(
        `Pending withdrawals:\n${withdrawals.map((tx) => `#${tx.id} player=${tx.userId} amount=${tx.amount}`).join('\n')}\n\nEnter request ID to approve`
      ));
      if (Number.isNaN(selected)) return;

      await adminApi.approveWithdrawal(selected);
      await onRefresh();
      toast('success', 'Withdrawal approved');
    } catch (error: any) {
      toast('error', error?.response?.data?.message || error?.message || 'Failed to review withdrawals');
    } finally {
      setBusy(false);
    }
  };

  const adminActions = [
    { name: 'My Players', icon: <Users size={18} />, color: 'bg-purple-500/10 text-purple-500', onClick: showPlayers },
    { name: 'Request Coins', icon: <Wallet size={18} />, color: 'bg-green-500/10 text-green-500', onClick: requestCoinsFromSuperAdmin },
    { name: 'Invite Link', icon: <LinkIcon size={18} />, color: 'bg-blue-500/10 text-blue-500', onClick: inviteLink },
    { name: 'Withdrawals', icon: <AlertTriangle size={18} />, color: 'bg-yellow-500/10 text-yellow-500', onClick: showWithdrawals },
  ];

  return (
    <div className="space-y-6 text-white pb-20">
      <div className="flex items-center justify-between mb-8">
        <div>
          <span className="text-[10px] font-bold uppercase tracking-widest text-purple-500">Agent Command</span>
          <h2 className="text-2xl font-black italic tracking-tighter uppercase">ADMIN <span className="text-purple-500">PANEL</span></h2>
        </div>
        <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={onRefresh}
            className="h-10 w-10 rounded-xl glass-card flex items-center justify-center text-slate-400"
            aria-label="Refresh data"
            disabled={busy}
            >
            <RefreshCw size={18} />
          </button>
          <div className={`h-3 w-3 rounded-full ${connected ? 'bg-green-500 shadow-[0_0_10px_rgba(34,197,94,0.5)]' : 'bg-red-500'}`} />
        </div>
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
             <button onClick={createGame} disabled={busy} className="btn-primary w-full mt-6 py-4">
               {busy ? 'Working...' : 'Create New Game'}
             </button>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex justify-between items-center">
              <span className="px-2 py-1 rounded-md bg-purple-500/20 text-purple-500 text-[10px] font-black uppercase">Active Game #{currentGame.id}</span>
              <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">{currentGame.status}</span>
            </div>
            
            <div className="flex gap-4">
              <button onClick={startPauseOrResume} disabled={busy} className="flex-1 py-4 glass-card border-purple-500/20 flex items-center justify-center gap-2">
                <Pause size={18} fill="currentColor" />
                <span className="font-bold text-sm uppercase">
                  {currentGame.status === 'REGISTRATION_OPEN' ? 'Start' : currentGame.status === 'IN_PROGRESS' ? 'Pause' : currentGame.status === 'CLAIM_PENDING' ? 'Resume' : '—'}
                </span>
              </button>
              <button onClick={endGame} disabled={busy} className="flex-1 py-4 bg-red-500/10 text-red-500 border border-red-500/20 rounded-2xl flex items-center justify-center gap-2">
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
            onClick={action.onClick}
          >
            <div className={`h-10 w-10 rounded-xl ${action.color} flex items-center justify-center`}>
              {action.icon}
            </div>
            <div className="font-bold text-xs uppercase tracking-tight">{action.name}</div>
          </motion.div>
        ))}
      </div>

      {/* Pending Player Top-Ups */}
      {pendingTopUps.length > 0 && (
        <div className="glass-card p-4 border-yellow-500/20">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-[10px] font-bold text-yellow-500 uppercase tracking-widest">Pending Coin Requests ({pendingTopUps.length})</h3>
            <ArrowUpFromLine size={14} className="text-yellow-500" />
          </div>
          <div className="space-y-3">
            {pendingTopUps.map((req) => (
              <div key={req.id} className="flex items-center justify-between py-2 border-b border-white/5 last:border-0">
                <div>
                  <div className="text-sm font-bold">Player #{req.requesterId}</div>
                  <div className="text-[10px] text-slate-500">Amount: <span className="text-yellow-400 font-bold">{req.amount}</span></div>
                </div>
                <div className="flex gap-2">
                  <button onClick={() => approveTopUp(req.id)} disabled={busy} className="h-8 w-8 rounded-lg bg-green-500/20 text-green-500 flex items-center justify-center">
                    <Check size={14} />
                  </button>
                  <button onClick={() => rejectTopUp(req.id)} disabled={busy} className="h-8 w-8 rounded-lg bg-red-500/20 text-red-500 flex items-center justify-center">
                    <X size={14} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Stats Table Placeholder */}
      <div className="glass-card p-4">
        <h3 className="text-[10px] font-bold text-slate-500 uppercase tracking-widest mb-4">Recent Activity</h3>
        <div className="space-y-3">
          {transactions.slice(0, 5).map((tx) => (
            <div key={tx.id} className="flex items-center justify-between py-2 border-b border-white/5 last:border-0">
              <div>
                <div className="text-[11px] font-bold">Player #{tx.userId}</div>
                <div className="text-[9px] text-slate-500 uppercase">{tx.type.replace(/_/g, ' ')}</div>
              </div>
              <div className="text-[11px] font-bold text-purple-400">{tx.amount}</div>
            </div>
          ))}
          {!transactions.length && <div className="text-sm text-slate-500">No transactions yet.</div>}
        </div>
      </div>
    </div>
  );
};

export default AdminDashboard;
