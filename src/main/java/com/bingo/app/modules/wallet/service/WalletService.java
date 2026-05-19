package com.bingo.app.modules.wallet.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.bingo.app.modules.user.enums.Role;
import com.bingo.app.modules.wallet.enums.TransactionStatus;
import com.bingo.app.modules.wallet.enums.TransactionType;
import com.bingo.app.exception.PlayerActionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import com.bingo.app.modules.wallet.entity.Transaction;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.wallet.repository.TransactionRepository;
import com.bingo.app.modules.user.repository.UserRepository;


@Service
@RequiredArgsConstructor
public class WalletService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional("tenantTransactionManager")
    public void transferPoints(Long fromUserId, Long toUserId, BigDecimal amount) {
        User fromUser = userRepository.findById(fromUserId).orElseThrow();
        User toUser = userRepository.findById(toUserId).orElseThrow();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PlayerActionException("Invalid amount", "Amount must be greater than zero.");
        }

        boolean directHierarchyTransfer =
                (fromUser.getRole() == Role.SUPER_ADMIN
                        && toUser.getRole() == Role.ADMIN
                        && fromUser.getId().equals(toUser.getParentId()))
                || (fromUser.getRole() == Role.ADMIN
                        && toUser.getRole() == Role.PLAYER
                        && fromUser.getId().equals(toUser.getParentId()));

        if (!directHierarchyTransfer) {
            throw new PlayerActionException(
                    "Ownership mismatch",
                    "You can only transfer points to direct children in your hierarchy."
            );
        }

        if (fromUser.getBalance().compareTo(amount) < 0) {
            throw new PlayerActionException("Insufficient balance", "You do not have enough points to transfer.");
        }

        fromUser.setBalance(fromUser.getBalance().subtract(amount));
        toUser.setBalance(toUser.getBalance().add(amount));

        userRepository.save(fromUser);
        userRepository.save(toUser);

        Transaction tx = Transaction.builder()
                .userId(toUserId)
                .type(toUser.getRole() == Role.ADMIN ? TransactionType.AGENT_FUND : TransactionType.PLAYER_FUND)
                .amount(amount)
                .status(TransactionStatus.APPROVED)
                .approvedBy(fromUserId)
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(tx);
    }

    @Transactional("tenantTransactionManager")
    public void creditSystem(Long userId, BigDecimal amount, TransactionType type) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setBalance(user.getBalance().add(amount));
        userRepository.save(user);

        Transaction tx = Transaction.builder()
                .userId(userId)
                .type(type)
                .amount(amount)
                .status(TransactionStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(tx);
    }

    @Transactional("tenantTransactionManager")
    public void creditWin(Long userId, BigDecimal amount) {

        User user = userRepository.findById(userId).orElseThrow();

        user.setBalance(user.getBalance().add(amount));
        userRepository.save(user);

        Transaction tx = Transaction.builder()
                .userId(userId)
                .type(TransactionType.WIN_REWARD)
                .amount(amount)
                .status(TransactionStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(tx);
    }

    public BigDecimal getBalance(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"))
                .getBalance();
    }

    @Transactional("tenantTransactionManager")
    public Transaction fundAgent(Long superAdminId, Long agentId, BigDecimal amount) {
        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new IllegalStateException("Agent not found"));

        if (agent.getRole() != Role.ADMIN || !superAdminId.equals(agent.getParentId())) {
            throw new PlayerActionException(
                    "Agent ownership mismatch",
                    "You can only fund agents created under your super admin account."
            );
        }

        return creditAndRecord(agent.getId(), amount, TransactionType.AGENT_FUND, superAdminId, null);
    }

    @Transactional("tenantTransactionManager")
    public Transaction fundPlayer(Long adminId, Long playerId, BigDecimal amount) {
        User player = userRepository.findById(playerId)
                .orElseThrow(() -> new IllegalStateException("Player not found"));

        if (player.getRole() != Role.PLAYER || !adminId.equals(player.getParentId())) {
            throw new PlayerActionException(
                    "Player ownership mismatch",
                    "You can only fund players registered under your admin account."
            );
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Admin not found"));

        if (admin.getBalance().compareTo(amount) < 0) {
            throw new PlayerActionException(
                    "Insufficient balance",
                    "You do not have enough coins to fund this player."
            );
        }

        admin.setBalance(admin.getBalance().subtract(amount));
        userRepository.save(admin);

        return creditAndRecord(player.getId(), amount, TransactionType.PLAYER_FUND, adminId, null);
    }

    @Transactional("tenantTransactionManager")
    public Transaction buyPoints(Long playerId, BigDecimal amount, String proofImageFileId) {
        return creditAndRecord(playerId, amount, TransactionType.POINT_PURCHASE, null, proofImageFileId);
    }

    @Transactional("tenantTransactionManager")
    public Transaction createWithdrawRequest(Long playerId, BigDecimal amount, String proofImageFileId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PlayerActionException("Invalid withdraw amount", "Withdraw amount must be greater than zero.");
        }

        User player = userRepository.findById(playerId)
                .orElseThrow(() -> new IllegalStateException("Player not found"));

        if (player.getBalance().compareTo(amount) < 0) {
            throw new PlayerActionException("Insufficient balance", "You do not have enough balance for this withdraw request.");
        }

        player.setBalance(player.getBalance().subtract(amount));
        player.setFrozenBalance(player.getFrozenBalance().add(amount));
        userRepository.save(player);

        Transaction tx = Transaction.builder()
                .userId(playerId)
                .type(TransactionType.WITHDRAW_REQUEST)
                .amount(amount)
                .status(TransactionStatus.PENDING)
                .proofImageFileId(proofImageFileId)
                .createdAt(LocalDateTime.now())
                .build();

        return transactionRepository.save(tx);
    }

    @Transactional("tenantTransactionManager")
    public Transaction chargeGameEntry(Long playerId, BigDecimal entryFee) {
        if (entryFee == null || entryFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new PlayerActionException("Invalid entry fee", "Entry fee must be zero or greater.");
        }

        User player = userRepository.findById(playerId)
                .orElseThrow(() -> new PlayerActionException("Player not found", "Player account could not be found."));

        if (player.getBalance().compareTo(entryFee) < 0) {
            throw new PlayerActionException("Insufficient balance", "You do not have enough balance to join this game.");
        }

        player.setBalance(player.getBalance().subtract(entryFee));
        userRepository.save(player);

        Transaction tx = Transaction.builder()
                .userId(playerId)
                .type(TransactionType.GAME_PURCHASE)
                .amount(entryFee)
                .status(TransactionStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .build();

        return transactionRepository.save(tx);
    }

    public List<Transaction> approvePendingWithdrawsForAdminPlayers(Long adminId) {
        List<User> players = userRepository.findByParentIdAndRole(adminId, Role.PLAYER);
        if (players.isEmpty()) {
            return List.of();
        }

        List<Long> playerIds = players.stream().map(User::getId).toList();

        List<Transaction> approved = transactionRepository.findByStatus(TransactionStatus.PENDING)
                .stream()
                .filter(tx -> tx.getType() == TransactionType.WITHDRAW_REQUEST)
                .filter(tx -> playerIds.contains(tx.getUserId()))
                .map(tx -> approveWithdraw(tx, adminId))
                .collect(Collectors.toList());

        return approved;
    }

    @Transactional("tenantTransactionManager")
    public Transaction approveWithdrawRequest(Long adminId, Long requestId) {
        Transaction tx = transactionRepository.findById(requestId)
                .orElseThrow(() -> new PlayerActionException("Request not found", "Withdraw request could not be found."));

        validateAdminCanReviewRequest(adminId, tx);

        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new PlayerActionException("Already processed", "This withdraw request is already processed.");
        }

        return approveWithdraw(tx, adminId);
    }

    @Transactional("tenantTransactionManager")
    public Transaction rejectWithdrawRequest(Long adminId, Long requestId) {
        Transaction tx = transactionRepository.findById(requestId)
                .orElseThrow(() -> new PlayerActionException("Request not found", "Withdraw request could not be found."));

        validateAdminCanReviewRequest(adminId, tx);

        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new PlayerActionException("Already processed", "This withdraw request is already processed.");
        }

        User player = userRepository.findById(tx.getUserId())
                .orElseThrow(() -> new PlayerActionException("Player not found", "Player account could not be found."));

        player.setFrozenBalance(player.getFrozenBalance().subtract(tx.getAmount()));
        player.setBalance(player.getBalance().add(tx.getAmount()));
        userRepository.save(player);

        tx.setStatus(TransactionStatus.REJECTED);
        tx.setApprovedBy(adminId);
        return transactionRepository.save(tx);
    }

    public List<Transaction> getHistory(Long userId) {
        return transactionRepository.findByUserId(userId);
    }

    public List<Transaction> getTransactionsForAdminPlayers(Long adminId) {
        List<User> players = userRepository.findByParentIdAndRole(adminId, Role.PLAYER);
        if (players.isEmpty()) {
            return List.of();
        }

        List<Long> playerIds = players.stream().map(User::getId).toList();
        return transactionRepository.findByUserIdIn(playerIds);
    }

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    @Transactional("tenantTransactionManager")
    public Transaction createAgentPointRequest(Long adminId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PlayerActionException("Invalid amount", "Amount must be greater than zero.");
        }

        Transaction tx = Transaction.builder()
                .userId(adminId)
                .type(TransactionType.POINT_PURCHASE)
                .amount(amount)
                .status(TransactionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        return transactionRepository.save(tx);
    }

    @Transactional("tenantTransactionManager")
    public Transaction approveWithdrawRequestBySuperAdmin(Long superAdminId, Long requestId) {
        Transaction tx = transactionRepository.findById(requestId)
                .orElseThrow(() -> new PlayerActionException("Request not found", "Withdraw request could not be found."));

        if (tx.getType() != TransactionType.WITHDRAW_REQUEST) {
            throw new PlayerActionException("Invalid request type", "Only withdraw requests can be reviewed here.");
        }

        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new PlayerActionException("Already processed", "This withdraw request is already processed.");
        }

        User player = userRepository.findById(tx.getUserId())
                .orElseThrow(() -> new PlayerActionException("Player not found", "Player for this request was not found."));

        if (player.getRole() != Role.PLAYER) {
            throw new PlayerActionException("Invalid request", "Only player withdrawal requests can be processed.");
        }

        User admin = userRepository.findById(player.getParentId())
                .orElseThrow(() -> new PlayerActionException("Admin not found", "Player's admin could not be found."));

        if (!superAdminId.equals(admin.getParentId())) {
            throw new PlayerActionException(
                    "Access denied",
                    "You can only process withdrawals for players under your agent hierarchy."
            );
        }

        return approveWithdraw(tx, superAdminId);
    }

    public List<Transaction> getPendingWithdrawsForAdminPlayers(Long adminId) {
        List<User> players = userRepository.findByParentIdAndRole(adminId, Role.PLAYER);
        if (players.isEmpty()) {
            return List.of();
        }
        List<Long> playerIds = players.stream().map(User::getId).toList();
        return transactionRepository.findByStatus(TransactionStatus.PENDING)
                .stream()
                .filter(tx -> tx.getType() == TransactionType.WITHDRAW_REQUEST)
                .filter(tx -> playerIds.contains(tx.getUserId()))
                .toList();
    }

    private Transaction creditAndRecord(Long userId, BigDecimal amount, TransactionType type, Long approvedBy, String proofImageFileId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PlayerActionException("Invalid amount", "Amount must be greater than zero.");
        }

        User user = userRepository.findById(userId).orElseThrow();

        user.setBalance(user.getBalance().add(amount));
        userRepository.save(user);

        Transaction tx = Transaction.builder()
                .userId(userId)
                .type(type)
                .amount(amount)
                .status(TransactionStatus.APPROVED)
                .approvedBy(approvedBy)
                .proofImageFileId(proofImageFileId)
                .createdAt(LocalDateTime.now())
                .build();

        return transactionRepository.save(tx);
    }


    private Transaction approveWithdraw(Transaction tx, Long approvedBy) {
        User player = userRepository.findById(tx.getUserId()).orElseThrow();
        if (player.getFrozenBalance().compareTo(tx.getAmount()) < 0) {
            tx.setStatus(TransactionStatus.REJECTED);
            tx.setApprovedBy(approvedBy);
            return transactionRepository.save(tx);
        }

        player.setFrozenBalance(player.getFrozenBalance().subtract(tx.getAmount()));
        userRepository.save(player);

        tx.setStatus(TransactionStatus.APPROVED);
        tx.setApprovedBy(approvedBy);
        return transactionRepository.save(tx);
    }

    private void validateAdminCanReviewRequest(Long adminId, Transaction tx) {
        if (tx.getType() != TransactionType.WITHDRAW_REQUEST) {
            throw new PlayerActionException("Invalid request type", "Only withdraw requests can be reviewed here.");
        }

        User player = userRepository.findById(tx.getUserId())
                .orElseThrow(() -> new PlayerActionException("Player not found", "Player for this request was not found."));

        if (player.getRole() != Role.PLAYER || !adminId.equals(player.getParentId())) {
            throw new PlayerActionException(
                    "Access denied",
                    "You can only review withdraw requests from players under your admin account."
            );
        }
    }
}
