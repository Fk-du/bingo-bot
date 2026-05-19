import React, { useEffect, useState, useCallback } from 'react';
import { motion } from 'framer-motion';
import { Shield, Users, BarChart3, Settings, Database, Activity, Check, X, ArrowUpFromLine, DollarSign, Gamepad2 } from 'lucide-react';
import { superAdminApi } from '../../api/services';
import type { Game, Transaction, TopUpRequest, User } from '../../types';
import { copyTextWithFallback } from '../../utils/copy';

const SuperAdminDashboard: React.FC = () => {
  const [busy, setBusy] = useState(false);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [pendingTopUps, setPendingTopUps] = useState<TopUpRequest[]>([]);
  const [agents, setAgents] = useState<User[]>([]);
  const [activeGames, setActiveGames] = useState<Game[]>([]);
  const [withdrawals, setWithdrawals] = useState<Transaction[]>([]);
  const [reports, setReports] = useState<Record<string, number> | null>(null);
  const [showSettings, setShowSettings] = useState(false);
  const [settings, setSettings] = useState<Record<string, string>>({});
  const [settingsDirty, setSettingsDirty] = useState<Record<string, string>>({});

  const loadData = useCallback(async () => {
    try {
      const [txns, topups, ags, games, wds, rpts] = await Promise.all([
        superAdminApi.getTransactions(),
        superAdminApi.getPendingTopUps(),
        superAdminApi.getAgents(),
        superAdminApi.getGames(),
        superAdminApi.getWithdrawals(),
        superAdminApi.getReports(),
      ]);
      setTransactions(txns);
      setPendingTopUps(topups);
      setAgents(ags);
      setActiveGames(games);
      setWithdrawals(wds);
      setReports(rpts);
    } catch (error) {
      console.error('Failed to load super admin data', error);
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  const run = async (task: () => Promise<void>) => {
    try {
      setBusy(true);
      await task();
    } catch (error: any) {
      alert(error?.response?.data?.message || error?.message || 'Action failed');
    } finally {
      setBusy(false);
    }
  };

  const createAgent = async () => {
    const botUsername = window.prompt('Bot username');
    if (!botUsername) return;
    await run(async () => {
      const link = await superAdminApi.createAgent(botUsername);
      const copied = await copyTextWithFallback(link, 'Agent invite');
      if (copied) alert('Agent invite copied');
    });
  };

  const fundAgent = async () => {
    const agentId = Number(window.prompt('Agent ID'));
    const amount = Number(window.prompt('Amount', '10'));
    if (Number.isNaN(agentId) || Number.isNaN(amount)) return alert('Invalid agent id or amount');
    await run(async () => {
      await superAdminApi.fundAgent(agentId, amount);
      alert('Agent funded');
    });
  };

  const approveTopUp = async (id: number) => {
    await run(async () => {
      await superAdminApi.approveTopUp(id);
      await loadData();
    });
  };

  const rejectTopUp = async (id: number) => {
    await run(async () => {
      await superAdminApi.rejectTopUp(id);
      await loadData();
    });
  };

  const payWithdrawal = async (id: number) => {
    await run(async () => {
      await superAdminApi.payWithdrawal(id);
      await loadData();
    });
  };

  const openSettings = async () => {
    try {
      const s = await superAdminApi.getSettings();
      setSettings(s);
      setSettingsDirty({ ...s });
      setShowSettings(true);
    } catch (error: any) {
      alert(error?.response?.data?.message || 'Failed to load settings');
    }
  };

  const saveSettings = async () => {
    await run(async () => {
      await superAdminApi.updateSettings(settingsDirty);
      setSettings({ ...settingsDirty });
      alert('Settings updated');
    });
  };

  const stats = reports ? [
    { label: 'Total Agents', value: String(reports.totalAgents ?? '—'), icon: <Users size={16} />, color: 'text-blue-500' },
    { label: 'Total Players', value: String(reports.totalPlayers ?? '—'), icon: <Users size={16} />, color: 'text-green-500' },
    { label: 'Total Games', value: String(reports.totalGames ?? '—'), icon: <Gamepad2 size={16} />, color: 'text-purple-500' },
    { label: 'Transactions', value: String(reports.totalTransactions ?? '—'), icon: <DollarSign size={16} />, color: 'text-yellow-500' },
  ] : [];

  const pendingCount = pendingTopUps.length + withdrawals.length;

  return (
    <div className="space-y-6 text-white pb-20">
      <div className="flex items-center justify-between mb-8">
        <div>
          <span className="text-[10px] font-bold uppercase tracking-widest text-blue-500">System Oversight</span>
          <h2 className="text-2xl font-black italic tracking-tighter uppercase">SUPER <span className="text-blue-500">ADMIN</span></h2>
        </div>
        <div className="relative">
          <div className="h-10 w-10 rounded-full bg-blue-500/10 flex items-center justify-center border border-blue-500/20">
            <Shield size={20} className="text-blue-500" />
          </div>
          {pendingCount > 0 && (
            <span className="absolute -top-1 -right-1 h-5 w-5 rounded-full bg-red-500 text-[10px] font-bold flex items-center justify-center">
              {pendingCount}
            </span>
          )}
        </div>
      </div>

      {/* Quick Stats */}
      {reports && (
        <div className="grid grid-cols-2 gap-3">
          {stats.map((stat, i) => (
            <div key={i} className="glass-card p-3 flex flex-col gap-1">
              <div className={`${stat.color} mb-1`}>{stat.icon}</div>
              <div className="text-lg font-bold tracking-tighter">{stat.value}</div>
              <div className="text-[8px] text-slate-500 font-bold uppercase">{stat.label}</div>
            </div>
          ))}
        </div>
      )}

      {/* Main Actions */}
      <div className="grid grid-cols-1 gap-4 mt-8">
        <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}
          className="glass-card p-4 flex items-center gap-4 cursor-pointer hover:bg-blue-500/5 transition-colors border border-white/5 hover:border-blue-500/20"
          onClick={createAgent}>
          <div className="h-12 w-12 rounded-2xl bg-blue-500/10 flex items-center justify-center text-blue-500"><Users size={20} /></div>
          <div><h3 className="font-bold text-sm">Create Agent</h3><p className="text-[10px] text-slate-500 font-bold uppercase">Add new admin/agent</p></div>
        </motion.div>
        <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}
          className="glass-card p-4 flex items-center gap-4 cursor-pointer hover:bg-blue-500/5 transition-colors border border-white/5 hover:border-blue-500/20"
          onClick={fundAgent}>
          <div className="h-12 w-12 rounded-2xl bg-blue-500/10 flex items-center justify-center text-blue-500"><Database size={20} /></div>
          <div><h3 className="font-bold text-sm">Fund Agent</h3><p className="text-[10px] text-slate-500 font-bold uppercase">Add points to agent wallets</p></div>
        </motion.div>
        <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}
          className="glass-card p-4 flex items-center gap-4 cursor-pointer hover:bg-blue-500/5 transition-colors border border-white/5 hover:border-blue-500/20"
          onClick={openSettings}>
          <div className="h-12 w-12 rounded-2xl bg-blue-500/10 flex items-center justify-center text-blue-500"><Settings size={20} /></div>
          <div><h3 className="font-bold text-sm">System Settings</h3><p className="text-[10px] text-slate-500 font-bold uppercase">Platform configuration</p></div>
        </motion.div>
      </div>

      {/* Pending Withdrawals */}
      {withdrawals.length > 0 && (
        <div className="glass-card p-4 border-red-500/20">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-[10px] font-bold text-red-500 uppercase tracking-widest">Pending Withdrawals ({withdrawals.length})</h3>
            <DollarSign size={14} className="text-red-500" />
          </div>
          <div className="space-y-3">
            {withdrawals.map((tx) => (
              <div key={tx.id} className="flex items-center justify-between py-2 border-b border-white/5 last:border-0">
                <div>
                  <div className="text-sm font-bold">Player #{tx.userId}</div>
                  <div className="text-[10px] text-slate-500">Amount: <span className="text-red-400 font-bold">{tx.amount}</span></div>
                </div>
                <button onClick={() => payWithdrawal(tx.id)} disabled={busy}
                  className="h-8 px-3 rounded-lg bg-green-500/20 text-green-500 text-xs font-bold flex items-center gap-1">
                  <Check size={14} /> Pay
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Pending Agent Top-Ups */}
      {pendingTopUps.length > 0 && (
        <div className="glass-card p-4 border-yellow-500/20">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-[10px] font-bold text-yellow-500 uppercase tracking-widest">Pending Agent Requests ({pendingTopUps.length})</h3>
            <ArrowUpFromLine size={14} className="text-yellow-500" />
          </div>
          <div className="space-y-3">
            {pendingTopUps.map((req) => (
              <div key={req.id} className="flex items-center justify-between py-2 border-b border-white/5 last:border-0">
                <div>
                  <div className="text-sm font-bold">Admin #{req.requesterId}</div>
                  <div className="text-[10px] text-slate-500">Amount: <span className="text-yellow-400 font-bold">{req.amount}</span></div>
                </div>
                <div className="flex gap-2">
                  <button onClick={() => approveTopUp(req.id)} disabled={busy}
                    className="h-8 w-8 rounded-lg bg-green-500/20 text-green-500 flex items-center justify-center"><Check size={14} /></button>
                  <button onClick={() => rejectTopUp(req.id)} disabled={busy}
                    className="h-8 w-8 rounded-lg bg-red-500/20 text-red-500 flex items-center justify-center"><X size={14} /></button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Active Games */}
      {activeGames.length > 0 && (
        <div className="glass-card p-4">
          <h3 className="text-[10px] font-bold text-slate-500 uppercase tracking-widest mb-4">Active Games ({activeGames.length})</h3>
          <div className="space-y-3">
            {activeGames.map((g) => (
              <div key={g.id} className="flex items-center justify-between py-2 border-b border-white/5 last:border-0">
                <div>
                  <div className="text-sm font-bold">Game #{g.id}</div>
                  <div className="text-[10px] text-slate-500 uppercase">Admin #{g.adminId} · {g.status}</div>
                </div>
                <div className="text-sm font-bold text-purple-400">{g.entryFee} PTS</div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Recent Platform Activity */}
      <div className="glass-card p-4">
        <h3 className="text-[10px] font-bold text-slate-500 uppercase tracking-widest mb-4">Recent Platform Activity</h3>
        <div className="space-y-3">
          {transactions.slice(0, 5).map((tx) => (
            <div key={tx.id} className="flex items-center justify-between py-2 border-b border-white/5 last:border-0">
              <div>
                <div className="text-[11px] font-bold">User #{tx.userId}</div>
                <div className="text-[9px] text-slate-500 uppercase">{tx.type.replace(/_/g, ' ')}</div>
              </div>
              <div className="text-[11px] font-bold text-blue-400">{tx.amount}</div>
            </div>
          ))}
          {!transactions.length && <div className="text-sm text-slate-500">No transactions yet.</div>}
        </div>
      </div>

      {/* Agents List */}
      {agents.length > 0 && (
        <div className="glass-card p-4">
          <h3 className="text-[10px] font-bold text-slate-500 uppercase tracking-widest mb-4">Agents ({agents.length})</h3>
          <div className="space-y-3">
            {agents.map((a) => (
              <div key={a.id} className="flex items-center justify-between py-2 border-b border-white/5 last:border-0">
                <div>
                  <div className="text-sm font-bold">Admin #{a.id}</div>
                  <div className="text-[10px] text-slate-500">TG: {a.telegramId}</div>
                </div>
                <div className="text-sm font-bold text-green-400">{a.balance} PTS</div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Settings Modal */}
      {showSettings && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
          onClick={() => setShowSettings(false)}>
          <div className="bg-slate-900 border border-slate-700 rounded-3xl w-full max-w-md p-5"
            onClick={e => e.stopPropagation()}>
            <h3 className="text-lg font-bold mb-4">System Settings</h3>
            <div className="space-y-4 max-h-64 overflow-y-auto">
              {Object.entries(settingsDirty).map(([key, value]) => (
                <div key={key}>
                  <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">{key}</label>
                  <input
                    value={value}
                    onChange={e => setSettingsDirty(prev => ({ ...prev, [key]: e.target.value }))}
                    className="w-full mt-1 px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-white text-sm"
                  />
                </div>
              ))}
            </div>
            <div className="flex gap-3 mt-5">
              <button onClick={() => setShowSettings(false)}
                className="flex-1 py-3 rounded-2xl bg-slate-800 text-slate-300 text-sm font-bold">Cancel</button>
              <button onClick={saveSettings} disabled={busy}
                className="flex-1 py-3 rounded-2xl bg-blue-600 text-white text-sm font-bold disabled:opacity-40">
                {busy ? 'Saving...' : 'Save'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SuperAdminDashboard;
