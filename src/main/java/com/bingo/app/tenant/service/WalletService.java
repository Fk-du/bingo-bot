package com.bingo.app.tenant.service;

import com.bingo.app.master.entity.AgentFundRequest;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.FundStatus;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.AgentFundRequestRepository;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.entity.CoinRequest;
import com.bingo.app.tenant.entity.Player;
import com.bingo.app.tenant.entity.Transaction;
import com.bingo.app.tenant.entity.Withdrawal;
import com.bingo.app.tenant.enums.RequestStatus;
import com.bingo.app.tenant.enums.TransactionStatus;
import com.bingo.app.tenant.enums.TransactionType;
import com.bingo.app.tenant.repository.CoinRequestRepository;
import com.bingo.app.tenant.repository.PlayerRepository;
import com.bingo.app.tenant.repository.TransactionRepository;
import com.bingo.app.tenant.repository.WithdrawalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;
    private final TransactionRepository transactionRepository;
    private final CoinRequestRepository coinRequestRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final AgentFundRequestRepository agentFundRequestRepository;

    // =========================================================
    // PLAYER METHODS
    // =========================================================

    @Transactional
    public CoinRequest buyPoints(Long playerId, BigDecimal amount, String screenshotUrl) {
        playerService.findByUserId(playerId);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be positive");
        }

        CoinRequest request = CoinRequest.builder()
                .userId(playerId)
                .amount(amount)
                .screenshotUrl(screenshotUrl)
                .status(RequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        CoinRequest saved = coinRequestRepository.save(request);

        createTransaction(playerId, TransactionType.TOP_UP, amount,
                TransactionStatus.PENDING, saved.getId(), "Points purchase requested");

        log.info("Buy points request created for player {}: amount={}", playerId, amount);
        return saved;
    }

    @Transactional
    public Withdrawal createWithdrawRequest(Long playerId, BigDecimal amount, String payoutDetails) {
        Player player = playerService.findByUserId(playerId);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be positive");
        }

        if (player.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        playerService.freezeBalance(playerId, amount);

        Withdrawal withdrawal = Withdrawal.builder()
                .userId(playerId)
                .amount(amount)
                .payoutMethod("BANK_TRANSFER")
                .payoutDetails(payoutDetails)
                .status(RequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        Withdrawal saved = withdrawalRepository.save(withdrawal);

        createTransaction(playerId, TransactionType.WITHDRAWAL, amount,
                TransactionStatus.PENDING, saved.getId(), "Withdrawal request created");

        log.info("Withdrawal request created for player {}: amount={}", playerId, amount);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Transaction> getHistory(Long playerId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(playerId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long playerId) {
        return playerService.getBalance(playerId);
    }

    // =========================================================
    // AGENT METHODS
    // =========================================================

    @Transactional
    public void fundPlayer(Long agentId, Long playerId, BigDecimal amount) {
        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found"));

        if (agent.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only agents can fund players");
        }

        if (agent.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient agent balance");
        }

        Player player = playerRepository.findByUserId(playerId)
                .orElseThrow(() -> new RuntimeException("Player not found"));

        if (!player.getAgentId().equals(agentId)) {
            throw new RuntimeException("Player does not belong to this agent");
        }

        agent.setBalance(agent.getBalance().subtract(amount));
        userRepository.save(agent);

        playerService.addBalance(playerId, amount);

        createTransaction(agentId, TransactionType.FUND_AGENT_TO_PLAYER, amount,
                TransactionStatus.COMPLETED, null, "Funded player " + playerId);
        createTransaction(playerId, TransactionType.DEPOSIT, amount,
                TransactionStatus.COMPLETED, null, "Received from agent " + agentId);

        log.info("Agent {} funded player {} with amount {}", agentId, playerId, amount);
    }

    @Transactional(readOnly = true)
    public List<Withdrawal> getPendingWithdrawsForAdminPlayers(Long agentId) {
        List<Player> players = playerRepository.findByAgentId(agentId);
        List<Long> playerIds = players.stream().map(Player::getUserId).toList();

        return withdrawalRepository.findByUserIdInAndStatus(playerIds, RequestStatus.PENDING);
    }

    @Transactional
    public void approveWithdrawal(Long withdrawalId, Long approverId) {
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new RuntimeException("Withdrawal not found"));

        if (withdrawal.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Withdrawal already processed");
        }

        withdrawal.setStatus(RequestStatus.APPROVED);
        withdrawal.setProcessedBy(approverId);
        withdrawal.setProcessedAt(LocalDateTime.now());
        withdrawalRepository.save(withdrawal);

        playerService.unfreezeBalance(withdrawal.getUserId(), withdrawal.getAmount());

        Transaction transaction = transactionRepository.findByReferenceIdAndType(
                        withdrawalId, TransactionType.WITHDRAWAL.name())
                .orElse(null);

        if (transaction != null) {
            transaction.setStatus(TransactionStatus.COMPLETED);
            transactionRepository.save(transaction);
        }

        log.info("Withdrawal {} approved by {}", withdrawalId, approverId);
    }

    @Transactional
    public void rejectWithdrawal(Long withdrawalId, Long approverId, String reason) {
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new RuntimeException("Withdrawal not found"));

        if (withdrawal.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Withdrawal already processed");
        }

        withdrawal.setStatus(RequestStatus.REJECTED);
        withdrawal.setProcessedBy(approverId);
        withdrawal.setProcessedAt(LocalDateTime.now());
        withdrawal.setRejectionReason(reason);
        withdrawalRepository.save(withdrawal);

        playerService.returnFrozenBalance(withdrawal.getUserId(), withdrawal.getAmount());

        Transaction transaction = transactionRepository.findByReferenceIdAndType(
                        withdrawalId, TransactionType.WITHDRAWAL.name())
                .orElse(null);

        if (transaction != null) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
        }

        log.info("Withdrawal {} rejected by {}: {}", withdrawalId, approverId, reason);
    }

    // =========================================================
    // SUPER ADMIN METHODS
    // =========================================================

    @Transactional
    public void fundAgent(Long superAdminId, Long agentId, BigDecimal amount) {
        User superAdmin = userRepository.findById(superAdminId)
                .orElseThrow(() -> new RuntimeException("Super admin not found"));

        if (superAdmin.getRole() != Role.SUPER_ADMIN) {
            throw new RuntimeException("Only super admin can fund agents");
        }
//
//        if (superAdmin.getBalance().compareTo(amount) < 0) {
//            throw new RuntimeException("Insufficient platform balance");
//        }

        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found"));

        if (agent.getRole() != Role.ADMIN) {
            throw new RuntimeException("User is not an agent");
        }

//        superAdmin.setBalance(superAdmin.getBalance().subtract(amount));
//        userRepository.save(superAdmin);

        agent.setBalance(agent.getBalance().add(amount));
        userRepository.save(agent);

        createTransaction(superAdminId, TransactionType.FUND_SUPER_ADMIN_TO_AGENT, amount,
                TransactionStatus.COMPLETED, null, "Funded agent " + agentId);
        createTransaction(agentId, TransactionType.DEPOSIT, amount,
                TransactionStatus.COMPLETED, null, "Received from super admin");

        log.info("Super admin {} funded agent {} with amount {}", superAdminId, agentId, amount);
    }

    @Transactional
    public void approveCoinRequest(Long requestId, Long approverId) {
        CoinRequest request = coinRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Coin request not found"));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));

        if (approver.getRole() != Role.SUPER_ADMIN || approver.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient balance to approve request");
        }

        approver.setBalance(approver.getBalance().subtract(request.getAmount()));
        userRepository.save(approver);

        playerService.addBalance(request.getUserId(), request.getAmount());

        request.setStatus(RequestStatus.APPROVED);
        request.setApprovedBy(approverId);
        request.setApprovedAt(LocalDateTime.now());
        coinRequestRepository.save(request);

        Transaction transaction = transactionRepository.findByReferenceIdAndType(
                        requestId, TransactionType.TOP_UP.name())
                .orElse(null);

        if (transaction != null) {
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setDescription("Approved by " + approverId);
            transactionRepository.save(transaction);
        }

        createTransaction(approverId, TransactionType.FUND_AGENT_TO_PLAYER, request.getAmount(),
                TransactionStatus.COMPLETED, requestId, "Funded player " + request.getUserId());

        log.info("Coin request {} approved by {} — deducted from approver balance", requestId, approverId);
    }

    @Transactional
    public void rejectCoinRequest(Long requestId, Long approverId, String reason) {
        CoinRequest request = coinRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Coin request not found"));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        request.setStatus(RequestStatus.REJECTED);
        request.setApprovedBy(approverId);
        request.setApprovedAt(LocalDateTime.now());
        request.setRejectionReason(reason);
        coinRequestRepository.save(request);

        Transaction transaction = transactionRepository.findByReferenceIdAndType(
                        requestId, TransactionType.TOP_UP.name())
                .orElse(null);

        if (transaction != null) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
        }

        log.info("Coin request {} rejected by {}: {}", requestId, approverId, reason);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<CoinRequest> getPendingCoinRequestsForAgent(Long agentId) {
        List<Player> players = playerRepository.findByAgentId(agentId);
        List<Long> playerIds = players.stream().map(Player::getUserId).toList();

        return coinRequestRepository.findByUserIdInAndStatus(playerIds, RequestStatus.PENDING);
    }

    // =========================================================
    // AGENT FUND REQUEST METHODS (Admin → Super Admin)
    // =========================================================

    @Transactional
    public AgentFundRequest requestAgentFund(Long agentId, BigDecimal amount, String screenshotUrl) {
        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found"));

        if (agent.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only agents can request funds");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be positive");
        }

        AgentFundRequest request = AgentFundRequest.builder()
                .agentId(agentId)
                .amount(amount)
                .screenshotUrl(screenshotUrl)
                .status(FundStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        AgentFundRequest saved = agentFundRequestRepository.save(request);

        log.info("Agent {} requested fund of amount {} from super admin", agentId, amount);
        return saved;
    }

    @Transactional
    public void approveAgentFundRequest(Long requestId, Long superAdminId) {
        User superAdmin = userRepository.findById(superAdminId)
                .orElseThrow(() -> new RuntimeException("Super admin not found"));

        if (superAdmin.getRole() != Role.SUPER_ADMIN) {
            throw new RuntimeException("Only super admin can approve agent fund requests");
        }

        AgentFundRequest request = agentFundRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Fund request not found"));

        if (request.getStatus() != FundStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

//        if (superAdmin.getBalance().compareTo(request.getAmount()) < 0) {
//            throw new RuntimeException("Insufficient platform balance");
//        }

//        superAdmin.setBalance(superAdmin.getBalance().subtract(request.getAmount()));
//        userRepository.save(superAdmin);

        User agent = userRepository.findById(request.getAgentId())
                .orElseThrow(() -> new RuntimeException("Agent not found"));

        agent.setBalance(agent.getBalance().add(request.getAmount()));
        userRepository.save(agent);

        request.setStatus(FundStatus.APPROVED);
        request.setApprovedBy(superAdminId);
        request.setApprovedAt(LocalDateTime.now());
        agentFundRequestRepository.save(request);

        createTransaction(superAdminId, TransactionType.FUND_SUPER_ADMIN_TO_AGENT, request.getAmount(),
                TransactionStatus.COMPLETED, requestId, "Approved fund request for agent " + request.getAgentId());
        createTransaction(request.getAgentId(), TransactionType.DEPOSIT, request.getAmount(),
                TransactionStatus.COMPLETED, requestId, "Fund request approved by super admin");

        log.info("Agent fund request {} approved by super admin {} — agent {} funded with {}",
                requestId, superAdminId, request.getAgentId(), request.getAmount());
    }

    @Transactional
    public void rejectAgentFundRequest(Long requestId, Long superAdminId, String reason) {
        AgentFundRequest request = agentFundRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Fund request not found"));

        if (request.getStatus() != FundStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        request.setStatus(FundStatus.REJECTED);
        request.setApprovedBy(superAdminId);
        request.setApprovedAt(LocalDateTime.now());
        request.setRejectionReason(reason);
        agentFundRequestRepository.save(request);

        log.info("Agent fund request {} rejected by super admin {}: {}", requestId, superAdminId, reason);
    }

    @Transactional(readOnly = true)
    public List<AgentFundRequest> getPendingAgentFundRequests() {
        return agentFundRequestRepository.findByStatusOrderByCreatedAtDesc(FundStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<AgentFundRequest> getAgentFundRequestsByAgent(Long agentId) {
        return agentFundRequestRepository.findByAgentIdOrderByCreatedAtDesc(agentId);
    }

    // =========================================================
    // GAME RELATED METHODS
    // =========================================================

    @Transactional
    public void deductBet(Long playerId, BigDecimal amount, Long gameId) {
        playerService.deductBalance(playerId, amount);

        createTransaction(playerId, TransactionType.BET, amount,
                TransactionStatus.COMPLETED, gameId, "Bet placed for game " + gameId);
    }

    @Transactional
    public void creditWinnings(Long playerId, BigDecimal amount, Long gameId) {
        playerService.addBalance(playerId, amount);

        createTransaction(playerId, TransactionType.WIN, amount,
                TransactionStatus.COMPLETED, gameId, "Won from game " + gameId);
    }

    @Transactional
    public void deductPlatformFee(Long agentId, BigDecimal amount, Long gameId) {
        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found"));

        if (agent.getBalance().compareTo(amount) < 0) {
            log.warn("Agent {} has insufficient balance for platform fee {}", agentId, amount);
            return;
        }

        agent.setBalance(agent.getBalance().subtract(amount));
        userRepository.save(agent);

        createTransaction(agentId, TransactionType.PLATFORM_FEE, amount,
                TransactionStatus.COMPLETED, gameId, "Platform fee for game " + gameId);
    }

    // =========================================================
    // PRIVATE HELPER METHODS
    // =========================================================

    private void createTransaction(Long userId, TransactionType type, BigDecimal amount,
                                   TransactionStatus status, Long referenceId, String description) {
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .type(type.name())
                .amount(amount)
                .status(status)
                .referenceId(referenceId)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);
    }
}