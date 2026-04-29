package com.bingo.app.modules.game.service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.bingo.app.modules.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import com.bingo.app.modules.game.entity.GameCard;
import com.bingo.app.modules.game.entity.Winner;
import com.bingo.app.modules.game.repository.WinnerRepository;

@Service
@RequiredArgsConstructor
public class WinnerService {

    private final WinnerRepository winnerRepository;
    private final WalletService walletService;

    public Winner createWinner(Long gameId, GameCard gc) {

        Winner winner = Winner.builder()
                .gameId(gameId)
                .playerId(gc.getPlayerId())
                .cardId(gc.getCardId())
                .rewardAmount(BigDecimal.ZERO)
                .build();

        return winnerRepository.save(winner);
    }

    public void distributeRewards(Long gameId, BigDecimal totalPool, List<Winner> winners) {

        if (winners.isEmpty()) return;

        BigDecimal share = totalPool.divide(
                BigDecimal.valueOf(winners.size()),
                RoundingMode.HALF_UP
        );

        for (Winner w : winners) {
            w.setRewardAmount(share);
            winnerRepository.save(w);

            walletService.creditWin(w.getPlayerId(), share);
        }
    }
}
