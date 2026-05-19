package com.bingo.app.modules.game.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.bingo.app.modules.game.entity.Game;
import com.bingo.app.modules.game.repository.GameRepository;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.repository.UserRepository;
import com.bingo.app.modules.wallet.enums.TransactionStatus;
import com.bingo.app.modules.wallet.enums.TransactionType;
import com.bingo.app.modules.wallet.service.WalletService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.bingo.app.modules.game.entity.GameCard;
import com.bingo.app.modules.game.entity.Winner;
import com.bingo.app.modules.game.repository.WinnerRepository;

@Service
@RequiredArgsConstructor
public class WinnerService {

    private final WinnerRepository winnerRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;

    @Value("${bingo.fees.platform-fee-rate:0.10}")
    private BigDecimal platformFeeRate;

    @Value("${bingo.fees.agent-commission-rate:0.05}")
    private BigDecimal agentCommissionRate;

    public Winner createWinner(Long gameId, GameCard gc) {

        Winner winner = Winner.builder()
                .gameId(gameId)
                .playerId(gc.getPlayerId())
                .cardId(gc.getCardId())
                .rewardAmount(BigDecimal.ZERO)
                .build();

        return winnerRepository.save(winner);
    }

    @Transactional("tenantTransactionManager")
    public void distributeRewards(Long gameId, BigDecimal totalPool, List<Winner> winners) {

        if (winners.isEmpty()) return;

        Game game = gameRepository.findById(gameId).orElseThrow();

        // 1. Deduct platform fee
        BigDecimal platformFee = totalPool.multiply(platformFeeRate).setScale(2, RoundingMode.HALF_UP);
        if (platformFee.compareTo(BigDecimal.ZERO) > 0) {
            User superAdmin = userRepository.findByRole(com.bingo.app.modules.user.enums.Role.SUPER_ADMIN)
                    .stream().findFirst().orElse(null);
            if (superAdmin != null) {
                walletService.creditSystem(superAdmin.getId(), platformFee, TransactionType.PLATFORM_FEE);
            }
        }

        // 2. Deduct agent commission from remaining pool
        BigDecimal afterPlatformFee = totalPool.subtract(platformFee);
        BigDecimal agentCommission = afterPlatformFee.multiply(agentCommissionRate).setScale(2, RoundingMode.HALF_UP);
        if (agentCommission.compareTo(BigDecimal.ZERO) > 0 && game.getAdminId() != null) {
            walletService.creditSystem(game.getAdminId(), agentCommission, TransactionType.AGENT_COMMISSION);
        }

        // 3. Split remainder among winners
        BigDecimal winnerPool = afterPlatformFee.subtract(agentCommission);
        BigDecimal share = winnerPool.divide(
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
