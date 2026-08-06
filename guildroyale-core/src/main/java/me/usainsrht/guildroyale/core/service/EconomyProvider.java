package me.usainsrht.guildroyale.core.service;

import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Optional VaultUnlocked economy wrapper. When the economy API is not present
 * all operations succeed silently (cost = free).
 *
 * <p>
 * Obtain an instance via {@link #load(Logger, String)}.
 */
public final class EconomyProvider {

    private final Economy economy; // null if unavailable
    private final String pluginName;

    private EconomyProvider(Economy economy, String pluginName) {
        this.economy = economy;
        this.pluginName = pluginName;
    }

    /**
     * Attempts to hook into VaultUnlocked. Returns an instance with
     * {@code economy == null} if VaultUnlocked or its economy provider is
     * unavailable.
     */
    public static EconomyProvider load(Logger logger, String pluginName) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null
                && Bukkit.getPluginManager().getPlugin("VaultUnlocked") == null) {
            logger.info("VaultUnlocked/Vault not found — economy features disabled.");
            return new EconomyProvider(null, pluginName);
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.warning("No VaultUnlocked economy provider is registered.");
            return new EconomyProvider(null, pluginName);
        }
        logger.info("VaultUnlocked economy hooked: " + rsp.getProvider().getName());
        return new EconomyProvider(rsp.getProvider(), pluginName);
    }

    public boolean isAvailable() {
        return economy != null;
    }

    /**
     * Returns {@code true} if the account has at least {@code amount}, or economy
     * is absent.
     */
    public boolean has(UUID accountId, double amount) {
        if (economy == null || amount <= 0)
            return true;
        return economy.has(pluginName, accountId, BigDecimal.valueOf(amount));
    }

    /**
     * Withdraws {@code amount} from the account.
     * Returns {@code true} on success or if economy is absent / amount is zero.
     */
    public boolean withdraw(UUID accountId, double amount) {
        if (economy == null || amount <= 0)
            return true;
        EconomyResponse resp = economy.withdraw(pluginName, accountId, BigDecimal.valueOf(amount));
        return resp.transactionSuccess();
    }

    /**
     * Deposits {@code amount} into the account.
     * Returns {@code true} on success or if economy is absent / amount is zero.
     */
    public boolean deposit(UUID accountId, double amount) {
        if (economy == null || amount <= 0)
            return true;
        EconomyResponse resp = economy.deposit(pluginName, accountId, BigDecimal.valueOf(amount));
        return resp.transactionSuccess();
    }

    /** Returns the account balance, or {@code 0} if economy is absent. */
    public double getBalance(UUID accountId) {
        if (economy == null)
            return 0;
        return economy.balance(pluginName, accountId).doubleValue();
    }

    /**
     * Creates a non-player guild bank account keyed by the guild UUID.
     * No-op when economy is absent.
     */
    public boolean createGuildAccount(UUID guildId, String name) {
        if (economy == null)
            return true;
        if (economy.hasAccount(guildId))
            return true;
        return economy.createAccount(guildId, name, false);
    }

    /** Returns a formatted string for the given amount (e.g. "$500.00"). */
    public String format(double amount) {
        if (economy == null)
            return String.valueOf(amount);
        return economy.format(pluginName, BigDecimal.valueOf(amount));
    }
}
