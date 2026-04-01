package uk.greenparty.routines;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import uk.greenparty.GreenPartyPlugin;

import java.util.*;
import java.util.logging.Logger;

/**
 * RoutineScheduler — Autonomously triggers scripted NPC routines on a periodic timer.
 *
 * <p>Every {@code auto-routine-interval-ticks} ticks (default 36 000 = 30 in-game minutes),
 * the scheduler performs a weighted random draw over the configured routines and attempts to
 * start the winner via {@link RoutineManager#startRoutine(String, int)}.
 *
 * <p>A "no-routine" slot is added to the pool so that the scheduler idles on some cycles,
 * keeping the world feeling alive without being relentless.  The no-routine slot weight
 * equals the sum of all routine weights, giving each individual routine a roughly equal
 * share of activity with idle gaps filling the rest.
 *
 * <p>Config keys (all under root level):
 * <pre>
 *   auto-routine-enabled: true
 *   auto-routine-interval-ticks: 36000
 *   auto-routine-weights:
 *     council_session: 10
 *     site_inspection_tree_farm: 8
 *     heated_debate: 5
 * </pre>
 *
 * <p>Approximate trigger probabilities with default weights (total pool = 23 routine + 23 idle = 46):
 * <ul>
 *   <li>{@code council_session}          — ~21.7 % (weight 10 / 46)</li>
 *   <li>{@code site_inspection_tree_farm}— ~17.4 % (weight  8 / 46)</li>
 *   <li>{@code heated_debate}            — ~10.9 % (weight  5 / 46)</li>
 *   <li>No routine fires               — ~50.0 % (idle slot = 23 / 46)</li>
 * </ul>
 *
 * <p>If the chosen routine is still on cooldown or its NPCs are busy, the scheduler
 * simply skips this interval — no retry.  The cooldown is enforced inside
 * {@link RoutineManager#startRoutine}.
 *
 * <p>Thread safety: all scheduling and callbacks run on the main server thread.
 *
 * @since 1.5.0
 */
public class RoutineScheduler {

    // ─── Config Defaults ──────────────────────────────────────────────────────

    private static final boolean DEFAULT_ENABLED        = true;
    private static final long    DEFAULT_INTERVAL_TICKS = 36_000L; // 30 in-game minutes

    // Default weights as specified in the design document
    private static final Map<String, Integer> DEFAULT_WEIGHTS;
    static {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("council_session",           10);
        m.put("site_inspection_tree_farm",  8);
        m.put("heated_debate",              5);
        DEFAULT_WEIGHTS = Collections.unmodifiableMap(m);
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private final GreenPartyPlugin plugin;
    private final Logger log;
    private final Random random = new Random();

    /** The repeating task; null when not running. */
    private BukkitTask schedulerTask;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public RoutineScheduler(GreenPartyPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Start the repeating auto-scheduler.
     *
     * <p>Safe to call multiple times — calling while already running is a no-op.
     */
    public void startAutoScheduling() {
        if (schedulerTask != null && !schedulerTask.isCancelled()) {
            log.info("[RoutineScheduler] Already running — ignoring duplicate startAutoScheduling().");
            return;
        }

        if (!isEnabled()) {
            log.info("[RoutineScheduler] Auto-scheduling is disabled in config (auto-routine-enabled: false).");
            return;
        }

        long intervalTicks = getIntervalTicks();

        schedulerTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::rollAndFire,
            intervalTicks,   // initial delay = one full interval (don't fire immediately on startup)
            intervalTicks    // period
        );

        log.info(String.format(
            "[RoutineScheduler] Auto-scheduling started — checking every %,d ticks (~%.1f in-game minutes). " +
            "Routines in pool: %s.",
            intervalTicks,
            intervalTicks / 1200.0,   // 1200 ticks = 1 in-game minute
            buildWeightMap().keySet()
        ));
    }

    /**
     * Stop the repeating auto-scheduler.
     *
     * <p>Called from {@link uk.greenparty.GreenPartyPlugin#onDisable()} to cancel
     * any pending ticks cleanly.  Safe to call even if never started.
     */
    public void stopAutoScheduling() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
            log.info("[RoutineScheduler] Auto-scheduling stopped.");
        }
    }

    /** @return true if the scheduler task is currently running. */
    public boolean isRunning() {
        return schedulerTask != null && !schedulerTask.isCancelled();
    }

    // ─── Internal Logic ───────────────────────────────────────────────────────

    /**
     * Core tick callback — called every {@code intervalTicks} ticks.
     *
     * <ol>
     *   <li>Build weighted pool from config (routine weights + idle slot).</li>
     *   <li>Pick a winner by weighted random draw.</li>
     *   <li>Attempt to fire the chosen routine; log the outcome.</li>
     * </ol>
     */
    private void rollAndFire() {
        Map<String, Integer> weights = buildWeightMap();
        if (weights.isEmpty()) {
            log.warning("[RoutineScheduler] No routines configured in auto-routine-weights — nothing to trigger.");
            return;
        }

        // Build a flat entry list: [ (name, weight), ..., (null, idleWeight) ]
        // The idle slot has the same total weight as all routines combined,
        // so roughly half the intervals will produce no routine.
        int totalRoutineWeight = weights.values().stream().mapToInt(Integer::intValue).sum();

        List<String> pool   = new ArrayList<>();
        List<Integer> cumulativeWeights = new ArrayList<>();
        int cumulative = 0;

        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            if (entry.getValue() <= 0) continue;
            cumulative += entry.getValue();
            pool.add(entry.getKey());
            cumulativeWeights.add(cumulative);
        }

        // Idle slot
        cumulative += totalRoutineWeight;
        pool.add(null);   // null = "no routine this cycle"
        cumulativeWeights.add(cumulative);

        int roll = random.nextInt(cumulative) + 1;  // 1..cumulative inclusive
        String chosen = null;
        for (int i = 0; i < cumulativeWeights.size(); i++) {
            if (roll <= cumulativeWeights.get(i)) {
                chosen = pool.get(i);
                break;
            }
        }

        if (chosen == null) {
            // Idle cycle — intentionally quiet (debug-level only to avoid log spam)
            log.fine("[RoutineScheduler] Idle cycle — no routine this interval.");
            return;
        }

        log.info(String.format(
            "[RoutineScheduler] Auto-roll selected '%s' (roll=%d / %d). Attempting to start...",
            chosen, roll, cumulative
        ));

        RoutineManager rm = plugin.getRoutineManager();
        if (rm == null) {
            log.warning("[RoutineScheduler] RoutineManager is null — cannot trigger routine.");
            return;
        }

        // Check the routine is actually registered before trying
        if (rm.getRoutine(chosen) == null) {
            log.warning(String.format(
                "[RoutineScheduler] Routine '%s' is listed in auto-routine-weights but is NOT " +
                "registered in the RoutineManager. Check routines.yml.", chosen
            ));
            return;
        }

        boolean started = rm.startRoutine(chosen, -1);

        if (started) {
            log.info(String.format(
                "[RoutineScheduler] ✔ Routine '%s' auto-started successfully.", chosen
            ));
        } else {
            log.info(String.format(
                "[RoutineScheduler] ✘ Routine '%s' could not start this cycle " +
                "(cooldown active or NPCs busy). Will retry next interval.", chosen
            ));
        }
    }

    // ─── Config Readers ───────────────────────────────────────────────────────

    /** @return whether auto-routing is enabled in config. */
    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("auto-routine-enabled", DEFAULT_ENABLED);
    }

    /** @return interval in ticks between checks. */
    private long getIntervalTicks() {
        return plugin.getConfig().getLong("auto-routine-interval-ticks", DEFAULT_INTERVAL_TICKS);
    }

    /**
     * Build the weight map from config, falling back to {@link #DEFAULT_WEIGHTS} if
     * the {@code auto-routine-weights} section is absent.
     */
    private Map<String, Integer> buildWeightMap() {
        org.bukkit.configuration.ConfigurationSection section =
            plugin.getConfig().getConfigurationSection("auto-routine-weights");

        if (section == null) {
            // No config section — use defaults
            return new LinkedHashMap<>(DEFAULT_WEIGHTS);
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            int weight = section.getInt(key, 0);
            if (weight > 0) {
                result.put(key, weight);
            }
        }

        if (result.isEmpty()) {
            // Section exists but is empty — fall back to defaults
            return new LinkedHashMap<>(DEFAULT_WEIGHTS);
        }

        return result;
    }
}
