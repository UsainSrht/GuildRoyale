package me.usainsrht.guildroyale.core.service;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Optional Vault economy wrapper. When Vault is not present all operations
 * succeed silently (cost = free).
 *
 * <p>Obtain an instance via {@link #load(Logger)}.
 */
public final class EconomyProvider {

    private final Economy economy; // null if Vault absent

    private EconomyProvider(Economy economy) {
        this.economy = economy;
    }

    /**
     * Attempts to hook into Vault. Returns an instance with {@code economy == null}
     * if Vault or its economy provider is not available.
     */
    public static EconomyProvider load(Logger logger) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logger.info("Vault not found — economy features disabled.");
            return new EconomyProvider(null);
        }
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.warning("Vault is installed but no economy provider is registered.");
            return new EconomyProvider(null);
        }
        logger.info("Vault economy hooked: " + rsp.getProvider().getName());
        return new EconomyProvider(rsp.getProvider());
    }

    public boolean isAvailable() { return economy != null; }

    /**
     * Returns {@code true} if the player has at least {@code amount}, or Vault is absent.
     */
    public boolean has(UUID playerId, double amount) {
        if (economy == null || amount <= 0) return true;
        var offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        return economy.has(offlinePlayer, amount);
    }

    /**
     * Withdraws {@code amount} from the player's balance.
     * Returns {@code true} on success or if Vault is absent / amount is zero.
     */
    public boolean withdraw(UUID playerId, double amount) {
        if (economy == null || amount <= 0) return true;
        var offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        EconomyResponse resp = economy.withdrawPlayer(offlinePlayer, amount);
        return resp.transactionSuccess();
    }

    /** Returns a formatted string for the given amount (e.g. "$500.00"). */
    public String format(double amount) {
        if (economy == null) return String.valueOf(amount);
        return economy.format(amount);
    }
}
