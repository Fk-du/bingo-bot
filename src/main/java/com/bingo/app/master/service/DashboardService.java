package com.bingo.app.master.service;

import com.bingo.app.master.dto.response.DashboardSummaryResponse;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.FundStatus;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.AdminFundRequestRepository;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.service.GameService;
import com.bingo.app.tenant.service.PlayerService;
import com.bingo.app.tenant.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final UserRepository userRepository;
    private final AdminFundRequestRepository adminFundRequestRepository;
    private final PlayerService playerService;
    private final GameService gameService;
    private final WalletService walletService;

    public DashboardSummaryResponse summaryForAdmin(User admin) {
        Long adminId = admin.getId();

        var players = playerService.getPlayersByAdmin(adminId);
        List<com.bingo.app.tenant.dto.response.PlayerResponse> recentPlayers =
                players.stream().limit(3).toList();

        var recentGames = gameService.getAllGamesForAdmin(adminId).stream()
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
}
