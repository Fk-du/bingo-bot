package com.bingo.app.master.service;

import com.bingo.app.master.dto.response.DashboardSummaryResponse;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.FundStatus;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.AdminFundRequestRepository;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.entity.Transaction;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.enums.TransactionType;
import com.bingo.app.tenant.dto.response.GameResponse;
import com.bingo.app.tenant.repository.TransactionRepository;
import com.bingo.app.tenant.service.GameService;
import com.bingo.app.tenant.service.PlayerService;
import com.bingo.app.tenant.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final UserRepository userRepository;
    private final AdminFundRequestRepository adminFundRequestRepository;
    private final PlayerService playerService;
    private final GameService gameService;
    private final WalletService walletService;
    private final TransactionRepository transactionRepository;

    public DashboardSummaryResponse summaryForAdmin(User admin) {
        Long adminId = admin.getId();

        var players = playerService.getPlayersByAdmin(adminId);
        List<com.bingo.app.tenant.dto.response.PlayerResponse> recentPlayers =
                players.stream().limit(3).toList();

        var recentGames = gameService.getAllGamesForAdmin(adminId).stream()
                .map(this::withCommissionEarned)
                .limit(5)
                .toList();

        long totalGames = gameService.countGamesForAdmin(adminId);
        long pendingClaims = gameService.countGamesByStatusForAdmin(adminId, List.of(GameStatus.CLAIM_PENDING));
        long pendingCoinRequests = walletService.countPendingCoinRequestsForAdmin(adminId);
        long pendingWithdrawals = walletService.countPendingWithdrawalsForAdmin(adminId);
        long pendingFundRequests = adminFundRequestRepository
                .countByAdminUserIdAndStatus(adminId, FundStatus.PENDING);

        return DashboardSummaryResponse.builder()
                .totalPlayers(players.size())
                .recentPlayers(recentPlayers)
                .totalGames(totalGames)
                .pendingClaimsCount(pendingClaims)
                .recentGames(recentGames)
                .pendingCoinRequests(pendingCoinRequests)
                .pendingWithdrawals(pendingWithdrawals)
                .pendingFundRequests(pendingFundRequests)
                .balance(admin.getBalance())
                .build();
    }

    /**
     * Revenue summary for an agent (admin). Runs inside the agent's tenant DB, so
     * commission/prize transactions are scoped to this agent's account.
     */
    public Map<String, Object> revenueForAdmin(User admin) {
        Long adminId = admin.getId();

        List<Transaction> agentCommission = transactionRepository
                .findByUserIdAndType(adminId, TransactionType.AGENT_COMMISSION.name());
        List<Transaction> unclaimedPrize = transactionRepository
                .findByUserIdAndType(adminId, TransactionType.UNCLAIMED_PRIZE.name());

        BigDecimal totalCommission = sumAmounts(agentCommission).add(sumAmounts(unclaimedPrize));
        BigDecimal todayCommission = sumAmountsSince(agentCommission, LocalDate.now())
                .add(sumAmountsSince(unclaimedPrize, LocalDate.now()));

        return Map.of(
                "totalGames", gameService.countGamesForAdmin(adminId),
                "totalTransactions", transactionRepository.findByUserIdOrderByCreatedAtDesc(adminId).size(),
                "totalPlayers", playerService.getPlayersByAdmin(adminId).size(),
                "balance", admin.getBalance(),
                "totalCommission", totalCommission,
                "todayCommission", todayCommission
        );
    }

    private BigDecimal sumAmounts(List<Transaction> txns) {
        return txns.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumAmountsSince(List<Transaction> txns, LocalDate from) {
        return txns.stream()
                .filter(t -> t.getCreatedAt() != null
                        && !t.getCreatedAt().toLocalDate().isBefore(from))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Attach the actual commission credited to the agent for a game (0 if it was refunded/no-winner). */
    private GameResponse withCommissionEarned(GameResponse game) {
        BigDecimal commission = commissionForGame(game.id());
        return game.toBuilder().commissionEarned(commission).build();
    }

    private BigDecimal commissionForGame(Long gameId) {
        BigDecimal commission = sumAmounts(transactionRepository
                .findAllByReferenceIdAndType(gameId, TransactionType.AGENT_COMMISSION.name()));
        BigDecimal unclaimed = sumAmounts(transactionRepository
                .findAllByReferenceIdAndType(gameId, TransactionType.UNCLAIMED_PRIZE.name()));
        return commission.add(unclaimed);
    }
}
