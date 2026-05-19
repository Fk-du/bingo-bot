import React, { useEffect, useState, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronLeft, ChevronRight, Loader2, AlertCircle, Ticket } from 'lucide-react';
import { cardApi } from '../../api/services';
import BingoCard from '../../components/BingoCard';
import type { Card } from '../../types';

interface CardPickerProps {
  gameId: number;
  onSelect: (cardId: number) => Promise<void>;
  onClose: () => void;
}

const PAGE_SIZE = 6;

const CardPicker: React.FC<CardPickerProps> = ({ gameId, onSelect, onClose }) => {
  const [cards, setCards] = useState<Card[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [selecting, setSelecting] = useState<number | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const fetchPage = useCallback(async (p: number) => {
    setLoading(true);
    setError(null);
    try {
      const res = await cardApi.listAvailable(p, PAGE_SIZE);
      setCards(res.content);
      setTotalPages(res.totalPages);
      setSelectedId(null);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to load cards');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPage(0);
  }, [fetchPage]);

  const handlePrev = () => {
    if (page > 0) {
      const next = page - 1;
      setPage(next);
      fetchPage(next);
    }
  };

  const handleNext = () => {
    if (page < totalPages - 1) {
      const next = page + 1;
      setPage(next);
      fetchPage(next);
    }
  };

  const handleConfirm = async () => {
    if (selectedId === null) return;
    setSelecting(selectedId);
    setError(null);
    try {
      await onSelect(selectedId);
      setSelecting(null);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to claim card');
      setSelecting(null);
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.9, y: 30 }}
        animate={{ scale: 1, y: 0 }}
        exit={{ scale: 0.9, y: 30 }}
        className="bg-slate-900 border border-slate-700 rounded-3xl w-full max-w-md max-h-[90vh] overflow-y-auto shadow-2xl"
        onClick={e => e.stopPropagation()}
      >
        <div className="p-5">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <Ticket size={18} className="text-purple-500" />
              <h3 className="text-lg font-bold text-white">Pick Your Card</h3>
            </div>
            <button
              onClick={onClose}
              className="h-8 w-8 rounded-lg bg-slate-800 text-slate-400 flex items-center justify-center hover:bg-slate-700"
            >
              &#x2715;
            </button>
          </div>

          {error && (
            <div className="flex items-center gap-2 text-sm text-red-400 bg-red-500/10 rounded-xl p-3 mb-4">
              <AlertCircle size={16} />
              <span>{error}</span>
            </div>
          )}

          <p className="text-xs text-slate-500 mb-4">
            Browse available cards and tap one to select it.
          </p>

          {loading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 size={28} className="animate-spin text-purple-500" />
            </div>
          ) : cards.length === 0 ? (
            <div className="text-center py-12 text-slate-500">
              <Ticket size={36} className="mx-auto mb-3 opacity-30" />
              <p className="text-sm font-bold">No cards available</p>
              <p className="text-xs mt-1">All cards are in use. Check back later.</p>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-3">
                {cards.map(card => {
                  const isSelected = selectedId === card.id;
                  const isSelecting = selecting === card.id;
                  return (
                    <motion.button
                      key={card.id}
                      whileHover={{ scale: 1.02 }}
                      whileTap={{ scale: 0.97 }}
                      onClick={() => setSelectedId(card.id)}
                      className={`
                        relative rounded-2xl border-2 overflow-hidden transition-colors text-left
                        ${isSelected
                          ? 'border-purple-500 bg-purple-500/10'
                          : 'border-slate-700/50 bg-slate-800/50 hover:border-slate-600'
                        }
                      `}
                      disabled={isSelecting}
                    >
                      <div className="p-2">
                        <div className="scale-[0.45] origin-top-left w-[222%] h-[222%] pointer-events-none">
                          <BingoCard numbers={card.numbers} calledNumbers={[]} />
                        </div>
                      </div>
                      <div className="px-2 pb-2 flex items-center justify-between">
                        <span className="text-[10px] font-bold text-slate-400">#{card.id}</span>
                        {isSelected && (
                          <span className="text-[10px] font-bold text-purple-500">SELECTED</span>
                        )}
                      </div>
                      {isSelecting && (
                        <div className="absolute inset-0 bg-black/50 flex items-center justify-center rounded-2xl">
                          <Loader2 size={20} className="animate-spin text-purple-500" />
                        </div>
                      )}
                    </motion.button>
                  );
                })}
              </div>

              <div className="flex items-center justify-between mt-4">
                <button
                  onClick={handlePrev}
                  disabled={page <= 0}
                  className="h-9 px-3 rounded-xl bg-slate-800 text-slate-300 disabled:opacity-30 flex items-center gap-1 text-xs font-bold"
                >
                  <ChevronLeft size={14} />
                  Prev
                </button>
                <span className="text-[11px] text-slate-500 font-bold">
                  Page {page + 1} of {totalPages}
                </span>
                <button
                  onClick={handleNext}
                  disabled={page >= totalPages - 1}
                  className="h-9 px-3 rounded-xl bg-slate-800 text-slate-300 disabled:opacity-30 flex items-center gap-1 text-xs font-bold"
                >
                  Next
                  <ChevronRight size={14} />
                </button>
              </div>
            </>
          )}

          <div className="flex gap-3 mt-5">
            <button
              onClick={onClose}
              className="flex-1 py-3 rounded-2xl bg-slate-800 text-slate-300 text-sm font-bold"
            >
              Cancel
            </button>
            <button
              onClick={handleConfirm}
              disabled={selectedId === null || selecting !== null}
              className="flex-1 py-3 rounded-2xl bg-purple-600 text-white text-sm font-bold disabled:opacity-40"
            >
              {selecting !== null ? 'Claiming...' : 'Play This Card'}
            </button>
          </div>
        </div>
      </motion.div>
    </motion.div>
  );
};

export default CardPicker;
