package com.bingo.app.master.service;

import com.bingo.app.infrastructure.persistence.TenantContext;
import com.bingo.app.infrastructure.persistence.TenantManagementService;
import com.bingo.app.master.dto.mapper.MasterMapper;
import com.bingo.app.master.dto.request.CreateAdminRequest;
import com.bingo.app.master.dto.request.CreatePlayerRequest;
import com.bingo.app.master.dto.response.AdminListItem;
import com.bingo.app.master.dto.response.AgentStatsResponse;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.master.entity.AdminWarning;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.AdminWarningRepository;
import com.bingo.app.master.repository.TenantRegistryRepository;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.dto.response.GameResponse;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.service.GameService;
import com.bingo.app.tenant.service.PlayerService;
import com.bingo.app.tenant.service.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final TenantManagementService tenantManagementService;
    private final PlayerService playerService;
    private final MasterMapper masterMapper;
    private final ObjectProvider<InviteService> inviteServiceProvider;
    private final NotificationService notificationService;
    private final AdminWarningRepository adminWarningRepository;
    private final TenantRegistryRepository tenantRegistryRepository;
    private final GameService gameService;
    private final WalletService walletService;

    public UserService(UserRepository userRepository,
                       TenantManagementService tenantManagementService,
                       PlayerService playerService,
                       MasterMapper masterMapper,
                       ObjectProvider<InviteService> inviteServiceProvider,
                       NotificationService notificationService,
                       AdminWarningRepository adminWarningRepository,
                       TenantRegistryRepository tenantRegistryRepository,
                       GameService gameService,
                       WalletService walletService) {
        this.userRepository = userRepository;
        this.tenantManagementService = tenantManagementService;
        this.playerService = playerService;
        this.masterMapper = masterMapper;
        this.inviteServiceProvider = inviteServiceProvider;
        this.notificationService = notificationService;
        this.adminWarningRepository = adminWarningRepository;
        this.tenantRegistryRepository = tenantRegistryRepository;
        this.gameService = gameService;
        this.walletService = walletService;
    }

    @Value("${app.super-admin.telegram-id}")
    private Long superAdminTelegramId;

    @Transactional
    public User findOrCreateUser(Long telegramId, String username, String firstName, String lastName) {
        return findOrCreateUser(telegramId, username, firstName, lastName, null);
    }

    public User findOrCreateUser(Long telegramId, String username, String firstName, String lastName, String startParam) {
        User existing = userRepository.findByTelegramId(telegramId).orElse(null);
        if (existing != null) {
            return existing;
        }

        // New user with invite code — register through InviteService (outside @Transactional to avoid poisoning)
        if (startParam != null && !startParam.isBlank()) {
            try {
                return inviteServiceProvider.getObject().registerWithInvite(telegramId, startParam);
            } catch (Exception e) {
                log.warn("Invite registration failed for code={}, falling back to default: {}", startParam, e.getMessage());
            }
        }

        return createNewUser(telegramId, username, firstName, lastName);
    }

    private User createNewUser(Long telegramId, String username, String firstName, String lastName) {
        Role role = telegramId.equals(superAdminTelegramId) ? Role.SUPER_ADMIN : Role.PLAYER;

        User user = User.builder()
                .telegramId(telegramId)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .active(true)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public User ensureSuperAdmin(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> {
                    User superAdmin = User.builder()
                            .telegramId(telegramId)
                            .username("superadmin")
                            .firstName("Super")
                            .lastName("Admin")
                            .role(Role.SUPER_ADMIN)
                            .active(true)
                            .build();
                    return userRepository.save(superAdmin);
                });
    }

    // NOT @Transactional — createTenant() runs CREATE DATABASE which cannot be in a transaction.
    public User createAdmin(CreateAdminRequest request) {
        User admin = User.builder()
                .telegramId(request.telegramId())
                .username(request.username())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.ADMIN)
                .parentId(request.creatorId())
                .adminApproved(false)
                .active(true)
                .build();

        User saved = userRepository.save(admin);
        tenantManagementService.createTenant(saved.getId());
        return saved;
    }

    @Transactional
    public AdminListItem approveAdmin(Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin not found: " + adminUserId));
        if (admin.getRole() != Role.ADMIN) {
            throw new RuntimeException("User is not an admin: " + adminUserId);
        }
        admin.setAdminApproved(true);
        admin.setActive(true);
        AdminListItem result = masterMapper.toAdminListItem(userRepository.save(admin));

        notificationService.notify(admin.getId(), "ADMIN_APPROVED",
                "Account approved",
                "Your admin account has been approved. You can now start managing your bingo room.",
                null, null,
                "\u2705 Account approved\nYou are now an active admin. Open the app to get started!");

        return result;
    }

    @Transactional
    public AdminListItem rejectAdmin(Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin not found: " + adminUserId));
        if (admin.getRole() != Role.ADMIN) {
            throw new RuntimeException("User is not an admin: " + adminUserId);
        }
        admin.setAdminApproved(false);
        admin.setActive(false);
        AdminListItem result = masterMapper.toAdminListItem(userRepository.save(admin));

        notificationService.notify(admin.getId(), "ADMIN_REJECTED",
                "Account rejected",
                "Your admin account request was rejected. Contact the platform owner for details.",
                null, null, null);

        return result;
    }

    @Transactional
    public AdminListItem suspendAdmin(Long adminUserId) {
        User admin = requireAdmin(adminUserId);
        admin.setActive(false);
        AdminListItem result = masterMapper.toAdminListItem(userRepository.save(admin));

        // Freeze the agent's operation: halt any open games in their tenant so
        // their players can no longer join, call, or claim. Players are separate
        // User rows (role PLAYER) and are not blocked by the admin's own 403, so
        // we must end the open games explicitly to stop them.
        String tenantId = TenantContext.tenantKeyForAdmin(adminUserId);
        TenantContext.setTenant(tenantId);
        try {
            List<Long> openGameIds = gameService.findOpenGamesForAdmin(adminUserId).stream()
                    .map(GameResponse::id)
                    .toList();
            for (Long gameId : openGameIds) {
                try {
                    gameService.endGameManually(gameId, adminUserId);
                    log.info("Suspended admin {} -> ended game {}", adminUserId, gameId);
                } catch (RuntimeException e) {
                    log.warn("Failed to end game {} on admin suspension: {}", gameId, e.getMessage());
                }
            }
        } finally {
            TenantContext.clear();
        }

        notificationService.notify(admin.getId(), "ADMIN_SUSPENDED",
                "Account suspended",
                "Your admin account has been suspended by the platform. All your active games were ended. Contact the owner for details.",
                null, null,
                "⏸️ Account suspended\nYour admin account was suspended and your active games were ended. Contact the platform owner.");

        // Notify the agent's players that their room is frozen.
        List<User> players = userRepository.findAllByAdminUserId(adminUserId);
        for (User player : players) {
            notificationService.notify(player.getId(), "ADMIN_SUSPENDED",
                    "Agent suspended",
                    "Your agent account has been suspended. All games are frozen until further notice.",
                    null, null,
                    "⏸️ Your agent account was suspended. Games are frozen until further notice.");
        }

        return result;
    }

    @Transactional
    public AdminListItem resumeAdmin(Long adminUserId) {
        User admin = requireAdmin(adminUserId);
        admin.setActive(true);
        AdminListItem result = masterMapper.toAdminListItem(userRepository.save(admin));

        notificationService.notify(admin.getId(), "ADMIN_RESUMED",
                "Account resumed",
                "Your admin account has been resumed. You can continue managing your bingo room.",
                null, null,
                "▶️ Account resumed\nYour admin account is active again.");

        return result;
    }

    @Transactional
    public AdminWarning warnAdmin(Long adminUserId, String reason, Long createdBy) {
        User admin = requireAdmin(adminUserId);
        AdminWarning warning = AdminWarning.builder()
                .adminUserId(adminUserId)
                .reason(reason)
                .createdBy(createdBy)
                .build();
        AdminWarning saved = adminWarningRepository.save(warning);

        notificationService.notify(admin.getId(), "ADMIN_WARNING",
                "Warning from platform",
                "You have received a warning: " + reason,
                null, null,
                "⚠️ Warning\n" + reason);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<AdminWarning> getWarningsForAdmin(Long adminUserId) {
        return adminWarningRepository.findByAdminUserIdOrderByCreatedAtDesc(adminUserId);
    }

    /**
     * Platform-level stats for a single agent, aggregated from that agent's tenant schema.
     */
    public AgentStatsResponse getAgentStats(Long adminUserId) {
        User admin = requireAdmin(adminUserId);
        String tenantId = TenantContext.tenantKeyForAdmin(adminUserId);
        TenantContext.setTenant(tenantId);
        try {
            long totalGames = gameService.getAllGamesForAdmin(adminUserId).size();
            long endedGames = gameService.getAllGamesForAdmin(adminUserId).stream()
                    .filter(g -> g.status() == GameStatus.ENDED)
                    .count();
            long totalPlayers = playerService.getPlayersByAdmin(adminUserId).size();
            long totalTransactions = walletService.getAllTransactions().size();
            BigDecimal totalCommission = walletService.getTotalCommissionInTenant();
            return new AgentStatsResponse(
                    totalGames, endedGames, totalPlayers, totalTransactions,
                    totalCommission, admin.getBalance());
        } finally {
            TenantContext.clear();
        }
    }

    private User requireAdmin(Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin not found: " + adminUserId));
        if (admin.getRole() != Role.ADMIN) {
            throw new RuntimeException("User is not an admin: " + adminUserId);
        }
        return admin;
    }

    public User createPlayer(CreatePlayerRequest request) {
        User player = User.builder()
                .telegramId(request.telegramId())
                .username(request.username())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.PLAYER)
                .adminUserId(request.adminUserId())
                .parentId(request.parentId())
                .active(true)
                .build();

        User saved = userRepository.save(player);

        String tenant = TenantContext.tenantKeyForAdmin(request.adminUserId());
        TenantContext.setTenant(tenant);
        try {
            playerService.createPlayer(saved.getId(), request.adminUserId(), request.parentId());
            log.info("Player record created in tenant DB for user: {}", saved.getId());
        } finally {
            TenantContext.clear();
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public User findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> getPlayersByAdmin(Long adminUserId) {
        return userRepository.findAllByAdminUserId(adminUserId).stream()
                .map(masterMapper::toUserProfile)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return masterMapper.toUserProfile(user);
    }

    @Transactional
    public void deductBalance(Long userId, BigDecimal amount) {
        int updated = userRepository.deductBalance(userId, amount);
        if (updated == 0) {
            throw new RuntimeException("Insufficient balance");
        }
    }

    @Transactional(readOnly = true)
    public List<AdminListItem> findAllByRole(Role role) {
        return userRepository.findAllByRole(role).stream()
                .map(masterMapper::toAdminListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminListItem> findAllByParentIdAndRole(Long parentId, Role role) {
        return userRepository.findAllByParentIdAndRole(parentId, role).stream()
                .map(masterMapper::toAdminListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminListItem> findAllByAdminUserId(Long adminUserId) {
        return userRepository.findAllByAdminUserId(adminUserId).stream()
                .map(masterMapper::toAdminListItem)
                .toList();
    }
}
