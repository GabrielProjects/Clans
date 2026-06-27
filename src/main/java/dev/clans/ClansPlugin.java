package dev.clans;

import dev.clans.command.ClanCommand;
import dev.clans.config.ConfigManager;
import dev.clans.config.Messages;
import dev.clans.database.DatabaseManager;
import dev.clans.database.repository.ClaimRepository;
import dev.clans.database.repository.ClanRepository;
import dev.clans.database.repository.InviteRepository;
import dev.clans.database.repository.MemberRepository;
import dev.clans.integration.ClansPlaceholderExpansion;
import dev.clans.integration.WorldGuardIntegration;
import dev.clans.listener.PlayerJoinListener;
import dev.clans.listener.PlayerQuitListener;
import dev.clans.service.ClaimService;
import dev.clans.service.ClanService;
import dev.clans.service.HomeService;
import dev.clans.service.InviteService;
import dev.clans.service.PermissionService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClansPlugin extends JavaPlugin {

    private static ClansPlugin instance;

    private ConfigManager configManager;
    private Messages messages;
    private DatabaseManager databaseManager;
    private WorldGuardIntegration worldGuardIntegration;

    private ClanRepository clanRepository;
    private MemberRepository memberRepository;
    private InviteRepository inviteRepository;
    private ClaimRepository claimRepository;

    private PermissionService permissionService;
    private ClanService clanService;
    private InviteService inviteService;
    private ClaimService claimService;
    private HomeService homeService;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.messages = new Messages(this);

        if (!initDatabase()) {
            getLogger().severe("Impossibile connettersi al database. Disabilitazione plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (!initWorldGuard()) {
            getLogger().severe("WorldGuard non trovato. Disabilitazione plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        initRepositories();
        initServices();
        registerCommands();
        registerListeners();
        registerPlaceholderApi();

        getLogger().info("Clans abilitato con successo.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("Clans disabilitato.");
    }

    private boolean initDatabase() {
        try {
            this.databaseManager = new DatabaseManager(this, configManager);
            databaseManager.init();
            return true;
        } catch (Exception e) {
            getLogger().severe("Errore database: " + e.getMessage());
            return false;
        }
    }

    private boolean initWorldGuard() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            return false;
        }
        this.worldGuardIntegration = new WorldGuardIntegration(this);
        return true;
    }

    private void initRepositories() {
        this.clanRepository = new ClanRepository(databaseManager);
        this.memberRepository = new MemberRepository(databaseManager);
        this.inviteRepository = new InviteRepository(databaseManager);
        this.claimRepository = new ClaimRepository(databaseManager);
    }

    private void initServices() {
        this.permissionService = new PermissionService(configManager);
        this.clanService = new ClanService(this, clanRepository, memberRepository, claimRepository, worldGuardIntegration);
        this.inviteService = new InviteService(this, inviteRepository, memberRepository, clanRepository, permissionService, worldGuardIntegration);
        this.claimService = new ClaimService(this, claimRepository, clanRepository, memberRepository, worldGuardIntegration, configManager, permissionService);
        this.homeService = new HomeService(this, clanRepository, claimRepository, configManager, permissionService);
    }

    private void registerCommands() {
        ClanCommand clanCommand = new ClanCommand(this);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(inviteService, homeService), this);
    }

    private void registerPlaceholderApi() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ClansPlaceholderExpansion(this).register();
            getLogger().info("PlaceholderAPI hook registrato.");
        }
    }

    public static ClansPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public ClanService getClanService() {
        return clanService;
    }

    public InviteService getInviteService() {
        return inviteService;
    }

    public ClaimService getClaimService() {
        return claimService;
    }

    public HomeService getHomeService() {
        return homeService;
    }

    public PermissionService getPermissionService() {
        return permissionService;
    }

    public MemberRepository getMemberRepository() {
        return memberRepository;
    }

    public ClanRepository getClanRepository() {
        return clanRepository;
    }

    public ClaimRepository getClaimRepository() {
        return claimRepository;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
