package uk.greenparty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import uk.greenparty.commands.*;
import uk.greenparty.listeners.*;
import uk.greenparty.managers.*;
import uk.greenparty.managers.NPCScheduleManager;
import uk.greenparty.routines.LocationRegistry;
import uk.greenparty.routines.RoutineCommand;
import uk.greenparty.routines.RoutineManager;
import uk.greenparty.routines.RoutineScheduler;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GreenPartyPlugin — The most environmentally responsible Minecraft plugin ever made.
 * Powered entirely by renewable energy. (It's not. It runs on Java.)
 *
 * Version 1.4.5 — Bugfix: Register all 3 custom item crafting recipes (Manifesto, Badge, Spoon).
 *   - Custom Structures (StructureManager — 4 themed landmark buildings)
 *   - Leaderboard System (LeaderboardManager — top 10, 5 categories, monthly rewards)
 *   - Cosmetics System (CosmeticsManager — armor dye, particle trails, chat titles)
 *   - Environmental Effects (EnvironmentEffects — ambient particles, sounds, aurora, boss bar)
 *   - Monthly Rewards & Progression (ProgressionManager — cycles, seasonal bonuses)
 *
 * Phase 3 (v1.3.0):
 *   - Quest chains, NPC schedules, debates, heckler commentary
 *
 * Phase 2 (v1.2.0):
 *   - Environmental Violations, Recycling, Motions, Announcements, Kidnap Lock
 *
 * Phase 1 (v1.1.0):
 *   - Eco-Score, Green Credits, Custom Items, Achievements
 */
public class GreenPartyPlugin extends JavaPlugin {

    private static GreenPartyPlugin instance;

    // ─── Phase 1 Managers ─────────────────────────────────────────────────────
    private DimensionManager dimensionManager;
    private QuestManager questManager;
    private NpcManager npcManager;
    private EcoScoreManager ecoScoreManager;
    private GreenCreditManager greenCreditManager;
    private CustomItemManager customItemManager;
    private AchievementManager achievementManager;

    // ─── Phase 2 Managers ─────────────────────────────────────────────────────
    private ViolationManager violationManager;
    private RecyclingManager recyclingManager;
    private MotionManager motionManager;
    private AnnouncementManager announcementManager;

    // ─── Phase 3 Managers ─────────────────────────────────────────────────────
    private QuestChainManager questChainManager;
    private NPCScheduleManager npcScheduleManager;
    private DebateManager debateManager;
    private HecklerManager hecklerManager;

    // ─── Phase 5 Managers ─────────────────────────────────────────────────────
    private LocationRegistry locationRegistry;
    private RoutineManager routineManager;
    private RoutineScheduler routineScheduler;

    // ─── Phase 4 Managers ─────────────────────────────────────────────────────
    private StructureManager structureManager;
    private LeaderboardManager leaderboardManager;
    private CosmeticsManager cosmeticsManager;
    private EnvironmentEffects environmentEffects;
    private ProgressionManager progressionManager;

    // ─── Kidnap Persistence ───────────────────────────────────────────────────
    private final Set<UUID> kidnappedPlayers = new HashSet<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private File kidnappedFile;

    private static final String[] STARTUP_SLOGANS = {
        "Loading... (carbon neutral, obviously)",
        "Initialising the Verdant Utopia... please wait while we count the trees",
        "Starting up... powered by 100% renewable server energy (citation needed)",
        "Booting the Green Party dimension... please recycle this startup message",
        "Warming up... but not the planet. Never the planet.",
        "v1.2.0: Now with violations, recycling bins, motions, and announcements you can't turn off.",
        "v1.3.0: Councillors now argue with each other on a schedule. Quest chains filed. NPCs have opinions.",
        "v1.3.0: Added quest chains, NPC schedules, debates, heckler commentary, and 847 new agenda items.",
        "v1.3.0: The council has MOVED. Literally. They walk now. This took 6 meetings to approve.",
        "v1.4.0: FINAL PHASE. Structures, leaderboards, cosmetics, aurora borealis, and monthly prizes.",
        "v1.4.0: The Council Chamber has been built. It took longer than expected. (2 ticks. Very fast.)",
        "v1.4.0: The Verdant Utopia is now Complete. The council is filing this as a net-zero achievement.",
        "v1.4.5: Crafting recipes for Manifesto, Badge, and Spoon are now actually registered. Apologies.",
        "v1.4.5: The council filed 3 emergency motions to fix the crafting table. All passed unanimously.",
    };

    @Override
    public void onEnable() {
        instance = this;

        int sloganIndex = (int)(Math.random() * STARTUP_SLOGANS.length);
        getLogger().info("╔══════════════════════════════════════════════════════╗");
        getLogger().info("║      GREEN PARTY DIMENSION - The Verdant Utopia      ║");
        getLogger().info("║  Version " + getDescription().getVersion() + " - For Planet, People & Placards  ║");
        getLogger().info("╚══════════════════════════════════════════════════════╝");
        getLogger().info(STARTUP_SLOGANS[sloganIndex]);

        saveDefaultConfig();

        // ─── Phase 1 managers ─────────────────────────────────────────────────
        this.achievementManager  = new AchievementManager(this);
        this.ecoScoreManager     = new EcoScoreManager(this);
        this.greenCreditManager  = new GreenCreditManager(this);
        this.customItemManager   = new CustomItemManager(this);

        // ─── Core managers ────────────────────────────────────────────────────
        this.dimensionManager = new DimensionManager(this);
        this.questManager     = new QuestManager(this);
        this.npcManager       = new NpcManager(this);

        // ─── Phase 2 managers ─────────────────────────────────────────────────
        this.violationManager    = new ViolationManager(this);
        this.recyclingManager    = new RecyclingManager(this);
        this.motionManager       = new MotionManager(this);
        this.announcementManager = new AnnouncementManager(this);

        // ─── Phase 3 managers ─────────────────────────────────────────────────
        this.questChainManager  = new QuestChainManager(this);
        this.hecklerManager     = new HecklerManager(this);
        this.debateManager      = new DebateManager(this);
        this.npcScheduleManager = new NPCScheduleManager(this);

        // ─── Phase 5 managers ─────────────────────────────────────────────────
        // LocationRegistry is created early so RoutineManager can hold a reference.
        // Locations are populated later (after structures are built) via the scheduler.
        this.locationRegistry = new LocationRegistry();
        this.locationRegistry.setLogger(getLogger());

        this.routineManager   = new RoutineManager(this, locationRegistry);
        this.routineScheduler = new RoutineScheduler(this);

        // ─── Phase 4 managers ─────────────────────────────────────────────────
        this.cosmeticsManager    = new CosmeticsManager(this);
        this.leaderboardManager  = new LeaderboardManager(this);
        this.structureManager    = new StructureManager(this);
        this.environmentEffects  = new EnvironmentEffects(this);
        this.progressionManager  = new ProgressionManager(this);

        // ─── Kidnap persistence ───────────────────────────────────────────────
        loadKidnappedPlayers();

        // ─── Listeners ────────────────────────────────────────────────────────
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WorldListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PortalListener(this), this);

        // ─── Commands ─────────────────────────────────────────────────────────
        getCommand("greenparty").setExecutor(new GreenPartyCommand(this));
        getCommand("greenportal").setExecutor(new GreenPortalCommand(this));
        getCommand("greenreset").setExecutor(new GreenResetCommand(this));
        getCommand("greencredit").setExecutor(new GreenCreditCommand(this));
        getCommand("achievements").setExecutor(new AchievementsCommand(this));
        getCommand("violations").setExecutor(new ViolationsCommand(this));
        getCommand("motion").setExecutor(new MotionCommand(this));

        // Phase 3 commands
        QuestCommand qc = new QuestCommand(this);
        getCommand("quest").setExecutor(qc);
        getCommand("quest").setTabCompleter(qc);

        NpcCommand nc = new NpcCommand(this);
        getCommand("npc").setExecutor(nc);
        getCommand("npc").setTabCompleter(nc);

        DebateCommand dc = new DebateCommand(this);
        getCommand("debate").setExecutor(dc);
        getCommand("debate").setTabCompleter(dc);

        // Tab completers
        ViolationsCommand vc = new ViolationsCommand(this);
        getCommand("violations").setTabCompleter(vc);
        MotionCommand mc = new MotionCommand(this);
        getCommand("motion").setTabCompleter(mc);

        // Phase 4 commands
        LeaderboardCommand lbc = new LeaderboardCommand(this);
        getCommand("leaderboard").setExecutor(lbc);
        getCommand("leaderboard").setTabCompleter(lbc);

        CosmeticsCommand cmc = new CosmeticsCommand(this);
        getCommand("cosmetics").setExecutor(cmc);
        getCommand("cosmetics").setTabCompleter(cmc);

        StructureCommand stc = new StructureCommand(this);
        getCommand("structure").setExecutor(stc);
        getCommand("structure").setTabCompleter(stc);

        ProgressCommand pgc = new ProgressCommand(this);
        getCommand("progress").setExecutor(pgc);
        getCommand("progress").setTabCompleter(pgc);

        // Phase 5: Location registry command (admin debug tool)
        LocationCommand lcc = new LocationCommand(this);
        getCommand("location").setExecutor(lcc);
        getCommand("location").setTabCompleter(lcc);

        // Phase 5: Routine commands
        RoutineCommand rc = new RoutineCommand(this);
        getCommand("routine").setExecutor(rc);
        getCommand("routine").setTabCompleter(rc);
        // /npc routine info is routed through existing NpcCommand with sub-delegation
        // The NpcCommand passes "routine" sub-commands to RoutineCommand
        nc.setRoutineCommand(rc);

        // /customitem give has been folded into /greenparty give (see GreenPartyCommand)
        // CustomItemCommand is retained for backwards compatibility but no longer registered

        // ─── Init dimension ───────────────────────────────────────────────────
        dimensionManager.initialiseDimension();

        // ─── Phase 4 init (after dimension is ready) ──────────────────────────
        Bukkit.getScheduler().runTaskLater(this, () -> {
            org.bukkit.World verdantWorld = dimensionManager.getVerdantWorld();
            if (verdantWorld != null) {
                structureManager.initialiseStructures(verdantWorld);

                // Register structure locations and light show after build tasks complete.
                // Structures are built at ticks +30/+60/+90/+120 from dimensionManager init,
                // so we wait 180 ticks (9 s) for all 4 builds to finish.
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    registerLightShowLocations(verdantWorld);
                    registerAllRoutineLocations(verdantWorld);

                    // Phase 5: start autonomous routine scheduling once locations are ready
                    routineScheduler.startAutoScheduling();
                }, 180L);
            }
            environmentEffects.startAll();
        }, 10L);

        getLogger().info("The Verdant Utopia is open! Remember: reduce, reuse, recycle.");
        getLogger().info("Phase 1: EcoScore | GreenCredits | Items | Achievements");
        getLogger().info("Phase 2: Violations | Recycling | Motions | Announcements | Kidnap Lock");
        getLogger().info("Phase 3: Quest Chains | NPC Schedules | Debates | Heckler | Expanded Dialogue");
        getLogger().info("Phase 4: Structures | Leaderboards | Cosmetics | Effects | Monthly Progression");
        getLogger().info("Phase 5: Scripted Routines | Council Sessions | Site Inspections | Choreographed Debates");
        getLogger().info("(This plugin has filed 94 environmental impact assessments for itself.)");
    }

    @Override
    public void onDisable() {
        getLogger().info("Green Party Dimension shutting down...");
        getLogger().info("The Verdant Utopia weeps. The composter bins are overflowing.");

        // Phase 1 saves
        if (ecoScoreManager != null)    ecoScoreManager.saveAll();
        if (greenCreditManager != null) greenCreditManager.saveAll();
        if (achievementManager != null) achievementManager.saveAll();

        // Phase 2 saves
        if (violationManager != null)  violationManager.saveAll();
        saveKidnappedPlayers();

        // Phase 3 saves
        if (questChainManager != null) questChainManager.saveData();

        // Phase 4 saves
        if (cosmeticsManager != null)   cosmeticsManager.saveData();
        if (leaderboardManager != null) leaderboardManager.saveData();
        if (structureManager != null)   structureManager.saveData();
        if (progressionManager != null) progressionManager.saveData();

        // Cancel tasks
        if (motionManager != null)       motionManager.cancelAll();
        if (announcementManager != null) announcementManager.cancelAll();
        if (violationManager != null)    violationManager.cancelTasks();
        if (debateManager != null)       debateManager.cancelAll();
        if (npcScheduleManager != null)  npcScheduleManager.cancelAll();
        if (routineScheduler != null)    routineScheduler.stopAutoScheduling();
        if (routineManager != null)      routineManager.cancelAll();
        if (environmentEffects != null)  environmentEffects.stopAll();
        if (cosmeticsManager != null)    cosmeticsManager.cancelAll();

        if (npcManager != null) npcManager.cleanup();

        getLogger().info("Please remember to reduce, reuse, recycle before your next server session.");
        instance = null;
    }

    // ─── Kidnap Persistence ───────────────────────────────────────────────────

    private void loadKidnappedPlayers() {
        kidnappedFile = new File(getDataFolder(), "playerdata/kidnapped.json");
        kidnappedFile.getParentFile().mkdirs();

        if (!kidnappedFile.exists()) return;

        try (Reader r = new FileReader(kidnappedFile)) {
            Type t = new TypeToken<List<String>>() {}.getType();
            List<String> raw = gson.fromJson(r, t);
            if (raw != null) {
                for (String s : raw) {
                    try {
                        kidnappedPlayers.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (IOException e) {
            getLogger().severe("[Kidnap] Failed to load kidnapped list: " + e.getMessage());
        }

        getLogger().info("[Kidnap] Loaded " + kidnappedPlayers.size() + " kidnapped player(s).");
    }

    public void saveKidnappedPlayers() {
        List<String> raw = kidnappedPlayers.stream()
            .map(UUID::toString)
            .collect(Collectors.toList());
        try (Writer w = new FileWriter(kidnappedFile)) {
            gson.toJson(raw, w);
        } catch (IOException e) {
            getLogger().severe("[Kidnap] Failed to save kidnapped list: " + e.getMessage());
        }
    }

    public boolean isKidnapped(UUID uuid) {
        return kidnappedPlayers.contains(uuid);
    }

    public void addKidnapped(UUID uuid) {
        kidnappedPlayers.add(uuid);
        saveKidnappedPlayers();
    }

    public void removeKidnapped(UUID uuid) {
        kidnappedPlayers.remove(uuid);
        saveKidnappedPlayers();
    }

    public Set<UUID> getKidnappedPlayers() {
        return Collections.unmodifiableSet(kidnappedPlayers);
    }

    // ─── Config Helpers ───────────────────────────────────────────────────────

    /**
     * Returns the list of kidnap target usernames from config (lower-cased for comparison).
     */
    public List<String> getKidnapTargets() {
        List<String> raw = getConfig().getStringList("kidnap-targets");
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        return raw.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toList());
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public static GreenPartyPlugin getInstance() { return instance; }

    public DimensionManager getDimensionManager()   { return dimensionManager; }
    public QuestManager getQuestManager()           { return questManager; }
    public NpcManager getNpcManager()               { return npcManager; }
    public EcoScoreManager getEcoScoreManager()     { return ecoScoreManager; }
    public GreenCreditManager getGreenCreditManager() { return greenCreditManager; }
    public CustomItemManager getCustomItemManager() { return customItemManager; }
    public AchievementManager getAchievementManager() { return achievementManager; }

    public ViolationManager getViolationManager()     { return violationManager; }
    public RecyclingManager getRecyclingManager()     { return recyclingManager; }
    public MotionManager getMotionManager()           { return motionManager; }
    public AnnouncementManager getAnnouncementManager() { return announcementManager; }

    public QuestChainManager getQuestChainManager()   { return questChainManager; }
    public NPCScheduleManager getNpcScheduleManager() { return npcScheduleManager; }
    public DebateManager getDebateManager()           { return debateManager; }
    public HecklerManager getHecklerManager()         { return hecklerManager; }

    public StructureManager getStructureManager()       { return structureManager; }
    public LeaderboardManager getLeaderboardManager()   { return leaderboardManager; }
    public CosmeticsManager getCosmeticsManager()       { return cosmeticsManager; }
    public EnvironmentEffects getEnvironmentEffects()   { return environmentEffects; }
    public ProgressionManager getProgressionManager()   { return progressionManager; }

    public RoutineManager getRoutineManager()           { return routineManager; }
    public RoutineScheduler getRoutineScheduler()       { return routineScheduler; }
    public LocationRegistry getLocationRegistry()       { return locationRegistry; }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void registerLightShowLocations(org.bukkit.World world) {
        for (StructureManager.StructureInfo info : structureManager.getAllStructures()) {
            if (info.built) {
                environmentEffects.addLightShowLocation(
                    new org.bukkit.Location(world, info.x, info.y + 1, info.z));
            }
        }
        getLogger().info("[Phase4] Light show locations registered for " +
            structureManager.getAllStructures().size() + " structures.");
    }

    /**
     * Register all named locations for the routine system.
     * Populates the LocationRegistry from StructureManager (structure sub-locations)
     * and RoutineManager (spawn-relative debate/audience positions).
     *
     * Called after the structure build tasks have had time to complete (~9 seconds).
     */
    private void registerAllRoutineLocations(org.bukkit.World world) {
        // 1. Structure internal locations (council seats, tree farm beds, etc.)
        structureManager.registerLocations(locationRegistry, world);

        // 2. Spawn-relative debate / audience podium locations
        routineManager.registerDebatePodiums(locationRegistry, world);

        // 3. Summary log
        int total = locationRegistry.size();
        getLogger().info("[Phase5] LocationRegistry ready — " + total
            + " named locations registered for routines.");
    }
}
