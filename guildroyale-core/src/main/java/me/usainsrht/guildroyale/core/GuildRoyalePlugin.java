package me.usainsrht.guildroyale.core;

import me.usainsrht.guildroyale.api.service.GuildService;
import me.usainsrht.guildroyale.api.service.LeaderboardService;
import me.usainsrht.guildroyale.api.service.MemberService;
import me.usainsrht.guildroyale.api.service.RoleService;
import me.usainsrht.guildroyale.api.storage.GuildRepository;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.usainsrht.guildroyale.core.command.GuildAdminCommandRegistrar;
import me.usainsrht.guildroyale.core.command.GuildCommandRegistrar;
import me.usainsrht.guildroyale.core.config.CommandConfig;
import me.usainsrht.guildroyale.core.config.ConfigManager;
import me.usainsrht.guildroyale.core.config.GuiConfig;
import me.usainsrht.guildroyale.core.config.MessagesManager;
import me.usainsrht.guildroyale.core.dialog.DialogManager;
import me.usainsrht.guildroyale.core.event.EventDispatcher;
import me.usainsrht.guildroyale.core.gui.GuiManager;
import me.usainsrht.guildroyale.core.integration.GuildRoyalePlaceholderExpansion;
import me.usainsrht.guildroyale.core.integration.MiniPlaceholdersHook;
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
@SuppressWarnings("UnstableApiUsage")
public final class GuildRoyalePlugin extends JavaPlugin {

    private static GuildRoyalePlugin instance;

    private ConfigManager configManager;
    private MessagesManager messagesManager;
    private GuiConfig guiConfig;
    private FoliaScheduler scheduler;
    private GuildRepository repository;
    private me.usainsrht.guildroyale.api.storage.MissionRepository missionRepository;
    private GuildLogWriter logWriter;

    private GuildServiceImpl guildService;
    private MemberServiceImpl memberService;
    private RoleServiceImpl roleService;
    private LeaderboardServiceImpl leaderboardService;
    private TagServiceImpl tagService;
    private MissionServiceImpl missionService;

    private GuiManager guiManager;
    private DialogManager dialogManager;
    private me.usainsrht.guildroyale.core.gui.GuildStorageManager guildStorageManager;

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
        missionRepository = StorageFactory.createMissionRepository(this, configManager, scheduler, repository);
        try {
            repository.init().join();
            missionRepository.init().join();
            getSLF4JLogger().info("Storage backend initialised: {}", configManager.getStorageType());
        } catch (Exception ex) {
            getSLF4JLogger().error("Failed to initialise storage backend", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        CommandConfig cmdCfg = CommandConfig.load(
                getDataFolder().toPath(),
                getClassLoader());
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            GuildCommandRegistrar.register(event.registrar(), cmdCfg);
            GuildAdminCommandRegistrar.register(event.registrar(), cmdCfg);
        });

        EconomyProvider economy = EconomyProvider.load(getLogger(), getName());
        EventDispatcher events = new EventDispatcher(scheduler);

        guildService = new GuildServiceImpl(repository, configManager, economy, scheduler, events);
        memberService = new MemberServiceImpl(repository, configManager, events);
        roleService = new RoleServiceImpl(repository, configManager);
        leaderboardService = new LeaderboardServiceImpl(repository, configManager, scheduler);
        leaderboardService.startRefreshTask();
        tagService = new TagServiceImpl(messagesManager);
        missionService = new MissionServiceImpl(this, repository, missionRepository, configManager, economy, scheduler, events);
        missionService.init();

        getServer().getServicesManager().register(
                me.usainsrht.guildroyale.api.service.MissionService.class,
                missionService,
                this,
                org.bukkit.plugin.ServicePriority.Normal
        );

        guiManager = new GuiManager();
        dialogManager = new DialogManager();
        guildStorageManager = new me.usainsrht.guildroyale.core.gui.GuildStorageManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new GuiListener(guiManager), this);
        pm.registerEvents(new GuildEventListener(logWriter), this);
        pm.registerEvents(new PlayerQuitListener(guiManager), this);
        pm.registerEvents(new me.usainsrht.guildroyale.core.listener.GuildDamageListener(guildService), this);
        pm.registerEvents(new me.usainsrht.guildroyale.core.listener.MissionListener(this, missionService, guildService), this);

        if (pm.getPlugin("PlaceholderAPI") != null) {
            new GuildRoyalePlaceholderExpansion(this, guildService, leaderboardService).register();
            getSLF4JLogger().info("PlaceholderAPI integration enabled.");
        }
        MiniPlaceholdersHook.register(this, guildService, leaderboardService);

        getSLF4JLogger().info("GuildRoyale enabled successfully.");
    }

    @Override
    public void onDisable() {
        MiniPlaceholdersHook.unregister();
        if (missionService != null) missionService.shutdown();
        if (missionRepository != null) missionRepository.shutdown();
        if (guildStorageManager != null) guildStorageManager.clear();
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
    public me.usainsrht.guildroyale.api.storage.MissionRepository getMissionRepository() { return missionRepository; }

    public GuildService getGuildService() { return guildService; }
    public MemberService getMemberService() { return memberService; }
    public RoleService getRoleService() { return roleService; }
    public LeaderboardService getLeaderboardService() { return leaderboardService; }
    public me.usainsrht.guildroyale.api.service.MissionService getMissionService() { return missionService; }

    public me.usainsrht.guildroyale.core.service.TagService getTagService() { return tagService; }

    public GuiManager getGuiManager() { return guiManager; }
    public DialogManager getDialogManager() { return dialogManager; }
    public me.usainsrht.guildroyale.core.gui.GuildStorageManager getGuildStorageManager() { return guildStorageManager; }
}
