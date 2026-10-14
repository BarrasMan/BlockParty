package dev.blockparty;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Everything that touches Vault. Only loaded when Vault is installed, so the plugin
 * works without it (just without money). VaultUnlocked deprecates this classic API,
 * but it's the one Essentials registers the server's money with.
 */
@SuppressWarnings("deprecation")
final class Economia {

    private final Economy economy;

    private Economia(Economy economy) {
        this.economy = economy;
    }

    static Economia hook() {
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : new Economia(provider.getProvider());
    }

    boolean give(OfflinePlayer player, double amount) {
        return amount > 0 && economy.depositPlayer(player, amount).transactionSuccess();
    }

    String format(double amount) {
        return economy.format(amount);
    }
}
