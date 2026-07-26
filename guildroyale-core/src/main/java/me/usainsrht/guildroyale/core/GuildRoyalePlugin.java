package me.usainsrht.guildroyale.core;

import me.usainsrht.guildroyale.api.service.GuildService;
import me.usainsrht.guildroyale.api.service.LeaderboardService;
import me.usainsrht.guildroyale.api.service.MemberService;
import me.usainsrht.guildroyale.api.service.RoleService;
import me.usainsrht.guildroyale.api.storage.GuildRepository;
import me.usainsrht.guildroyale.core.config.ConfigManager;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.config.MessagesManager;
import me.usainsrht.guildroyale.core.dialog.DialogManager;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.core.integration.GuildRoyalePlaceholderExpansion;
import me.usainsrht.guildroyale.core.listener.GuildEventListener;
import me.usainsrht.guildroyale.core.listener.GuiListener;
import me.usainsrht.guildroyale.core.listener.PlayerQuitListener;
import me.usainsrht.guildroyale.core.logging.GuildLogWriter;
import me.usainsrht.guildroyale.core.scheduler.FoliaScheduler;
import me.usainsrht.guildroyale.core.service.*;
import me.usainsrht.guildroyale.core.storage.StorageFactory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * GuildRoyale main plugin class.
 */
public final class GuildRoyalePlugin extends JavaPlugin {

    private static GuildRoyalePlugin instance;

    private ConfigManager configManager;
    private MessagesManager messagesManager;
    private GuiConfig guiConfig;
    private FoliaScheduler scheduler;
    private GuildRepository repository;
    private GuildLogWriter logWriter;

    private GuildServiceImpl guildService;
    private MemberServiceImpl memberService;
    private RoleServiceImpl roleService;
    private LeaderboardServiceImpl leaderboardService;

    private GuiManager guiManager;
    private DialogManager dialogManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        configManager = new ConfigManager(this);
        messagesManager = new MessagesManager(this);
        guiConfig = new GuiConfig(this);

        scheduler = new FoliaScheduler(this);
        logWriter = new GuildLogWriter(this);

        repository = StorageFactory.create(this, configManager, scheduler);
        repository.init().whenComplete((v, ex) -> {
            if (ex != null) {
                getSLF4JLogger().error("Failed to initialise storage backend", ex);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            getSLF4JLogger().info("Storage backend initialised: {}", configManager.getStorageType());
        });

        EconomyProvider economy = EconomyProvider.load(getLogger(), getName());

        guildService = new GuildServiceImpl(repository, configManager, economy, scheduler);
        memberService = new MemberServiceImpl(repository, configManager);
        roleService = new RoleServiceImpl(repository);
        leaderboardService = new LeaderboardServiceImpl(repository, configManager, scheduler);
        leaderboardService.startRefreshTask();

        guiManager = new GuiManager();
        dialogManager = new DialogManager();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new GuiListener(guiManager), this);
        pm.registerEvents(new GuildEventListener(logWriter), this);
        pm.registerEvents(new PlayerQuitListener(guiManager), this);

        if (pm.getPlugin("PlaceholderAPI") != null) {
            new GuildRoyalePlaceholderExpansion(this, guildService, leaderboardService).register();
            getSLF4JLogger().info("PlaceholderAPI integration enabled.");
        }

        getSLF4JLogger().info("GuildRoyale enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (guiManager != null) guiManager.clear();
        if (memberService != null) memberService.shutdown();
        if (repository != null) repository.shutdown();
        if (logWriter != null) logWriter.close();
        instance = null;
        getSLF4JLogger().info("GuildRoyale disabled.");
    }

    public static GuildRoyalePlugin getInstance() { return instance; }

    public ConfigManager getConfigManager() { return configManager; }
    public MessagesManager getMessages() { return messagesManager; }
    public GuiConfig getGuiConfig() { return guiConfig; }
    public FoliaScheduler getScheduler() { return scheduler; }
    public GuildRepository getRepository() { return repository; }

    public GuildService getGuildService() { return guildService; }
    public MemberService getMemberService() { return memberService; }
    public RoleService getRoleService() { return roleService; }
    public LeaderboardService getLeaderboardService() { return leaderboardService; }

    public GuiManager getGuiManager() { return guiManager; }
    public DialogManager getDialogManager() { return dialogManager; }
}
