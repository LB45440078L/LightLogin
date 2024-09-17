package top.cmarco.lightlogin.command.verify;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.cmarco.lightlogin.LightLoginPlugin;

import java.util.HashMap;
import java.util.UUID;

public final class VerificationManager {

    private boolean isCaptchaActive = true;
    private final LightLoginPlugin plugin;
    private final HashMap<UUID, VerificationData> verificationData = new HashMap<>();

    public VerificationManager(@NotNull LightLoginPlugin plugin) {
        this.plugin = plugin;
        this.isCaptchaActive = plugin.getLightConfiguration().getCaptchaEnabled();
    }

    public void addData(@NotNull Player player) {
        addData(player.getUniqueId());
    }

    public void addData(@NotNull UUID uuid) {
        this.verificationData.put(uuid, VerificationData.generateRandom());
    }

    @Nullable
    public VerificationData getData(@NotNull Player player) {
        return getData(player.getUniqueId());
    }

    @Nullable
    public VerificationData getData(@NotNull UUID uuid) {
        return this.verificationData.get(uuid);
    }

    public void verifyPlayer(@NotNull Player player) {
        verifyPlayer(player.getUniqueId());
    }

    public void verifyPlayer(@NotNull UUID uuid) {
        verificationData.remove(uuid);
    }

    public boolean isCaptchaActive() {
        return isCaptchaActive;
    }

    public void setCaptchaActive(boolean captchaActive) {
        isCaptchaActive = captchaActive;
    }
}
