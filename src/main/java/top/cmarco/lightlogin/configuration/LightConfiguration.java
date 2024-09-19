/*
 * LightLogin - Optimised and Safe SpigotMC Software for Authentication
 *     Copyright © 2024  CMarco
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package top.cmarco.lightlogin.configuration;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.cmarco.lightlogin.LightLoginPlugin;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents the configuration for the LightLoginPlugin, providing convenient methods
 * to access various configuration parameters related to the database.
 */
public final class LightConfiguration {

    private final ConfigurationFiles chosenLanguage;

    // The LightLoginPlugin instance associated with this configuration.
    private final LightLoginPlugin plugin;

    // The FileConfiguration instance used to store and retrieve configuration settings.
    private FileConfiguration configuration = null;

    /**
     * Public constructor for the configuration of this plugin.
     *
     * @param chosenLanguage The chosen language at runtime.
     * @param plugin The plugin instance.
     */
    public LightConfiguration(@NotNull ConfigurationFiles chosenLanguage, @NotNull LightLoginPlugin plugin) {
        this.chosenLanguage = chosenLanguage;
        this.plugin = plugin;
    }

    public void loadConfig() {
        configuration = this.plugin.getLanguagesConfigMap().get(chosenLanguage);
    }

    public ConfigurationFiles getChosenLanguage() {
        return chosenLanguage;
    }

    public List<String> getPluginInfo() {
        return this.configuration.getStringList("plugin.info");
    }

    public List<String> getIncorrectCommandUsage() {
        return this.configuration.getStringList("messages.incorrect-command-usage");
    }

    @Nullable
    public String getDatabaseType() {
        return this.configuration.getString("database.type");
    }

    @Nullable
    public String getUsername() {
        return this.configuration.getString("database.username");
    }

    @Nullable
    public String getPassword() {
        return this.configuration.getString("database.password");
    }

    @Nullable
    public String getAddress() {
        return this.configuration.getString("database.address");
    }

    public int getPort() {
        return this.configuration.getInt("database.port", 3306);
    }

    @Nullable
    public String getDatabaseName() {
        return this.configuration.getString("database.db-name");
    }

    public boolean isCrashShutdown() {
        return this.configuration.getBoolean("crash-shutdown", false);
    }

    @NotNull
    public List<String> getAllowedCommands() {
        return this.configuration.getStringList("login.allowed-commands");
    }

    public int getKickAfterSeconds() {
        return this.configuration.getInt("login.kick-after-seconds", 120);
    }

    public int getLoginDelay() {
        return this.configuration.getInt("login.command-delay");
    }

    public int getMaxFailedAttempts() {
        return this.configuration.getInt("login.max-failed-attempts");
    }

    public List<String> getBruteforcePunishment() {
        return this.configuration.getStringList("login.bruteforce-punishment");
    }

    public String getLoginTookTooMuchTime() {
        return String.join("\n", this.configuration.getStringList("messages.login-took-too-much-time"));
    }

    public boolean isAutoLogin() {
        return this.configuration.getBoolean("login.auto-login");
    }

    public int getSessionExpire() {
        return this.configuration.getInt("login.session-expire");
    }

    public String getMessagePrefix() {
        return this.configuration.getString("messages.prefix");
    }

    public List<String> getPlayerOnlyCommandMessage() {
        return this.configuration.getStringList("messages.player-only-command");
    }

    public List<String> getMissingPermissionMessage() {
        return this.configuration.getStringList("messages.missing-permission");
    }

    public List<String> getUnregisterIncorrectUsage() {
        return this.configuration.getStringList("messages.unregister-incorrect-usage");
    }

    public List<String> getRegisterMessage() {
        return this.configuration.getStringList("messages.register-message");
    }

    public List<String> getRegisterIncorrectUsageMessage() {
        return this.configuration.getStringList("messages.register-incorrect-usage");
    }

    public List<String> getRegisterUnequalPasswordsMessage() {
        return this.configuration.getStringList("messages.register-unequal-passwords");
    }

    public List<String> getRegisterSuccessMessage() {
        return this.configuration.getStringList("messages.register-success");
    }

    public List<String> getAlreadyRegisteredMessage() {
        return this.configuration.getStringList("messages.already-registered");
    }

    public List<String> getUnregisteredMessage() {
        return this.configuration.getStringList("messages.unregistered");
    }

    public List<String> getUnregisteredSuccess() {
        return this.configuration.getStringList("messages.unregistered-success");
    }


    public List<String> getUnsafePassword() {
        return this.configuration.getStringList("messages.unsafe-password");
    }

    public List<String> getRegisterError() {
        return this.configuration.getStringList("messages.register-error");
    }

    public List<String> getLoginIncorrectUsage() {
        return this.configuration.getStringList("messages.login-incorrect-usage");
    }

    public List<String> getUnregisterNotFound() {
        return this.configuration.getStringList("messages.unregister-not-found");
    }

    public List<String> getLoginAlreadyAuthenticated() {
        return this.configuration.getStringList("messages.login-already-authenticated");
    }

    public List<String> getLoginError() {
        return this.configuration.getStringList("messages.login-error");
    }

    public List<String> getLoginUnregistered() {
        return this.configuration.getStringList("messages.login-unregistered");
    }

    public List<String> getLoginWrongPassword() {
        return this.configuration.getStringList("messages.login-wrong-password");
    }

    public List<String> getLoginSuccess() {
        return this.configuration.getStringList("messages.login-success");
    }

    public List<String> getLoginAuto() {
        return this.configuration.getStringList("messages.login-auto");
    }

    public List<String> getLoginMessages() {
        return this.configuration.getStringList("messages.login-messages");
    }

    public boolean isSafePasswordForceEnabled() {
        return this.configuration.getBoolean("safe-passwords.force-safe.enabled", true);
    }

    public int getSafePasswordMinLength() {
        return this.configuration.getInt("safe-passwords.force-safe.min-length", 8);
    }

    public int getSafePasswordMinUppercase() {
        return this.configuration.getInt("safe-passwords.force-safe.min-uppercase", 1);
    }

    public int getSafePasswordMinNumbers() {
        return this.configuration.getInt("safe-passwords.force-safe.min-numbers", 2);
    }

    public int getSafePasswordMinSpecial() {
        return this.configuration.getInt("safe-passwords.force-safe.min-special", 1);
    }

    public List<Character> getSafePasswordAllowedSpecial() {
        return this.configuration.getCharacterList("safe-passwords.force-safe.allowed-special");
    }

    public String getMessagePlayersSameIp() {
        return this.configuration.getString("messages.players-same-ip");
    }

    public int getSafePasswordMaxLength() {
        return this.configuration.getInt("safe-passwords.force-safe.max-length", 32);
    }

    public List<String> getCommandTooFast() {
        return this.configuration.getStringList("messages.command-too-fast");
    }

    public List<String> getChangepasswordUnregistered() {
        return this.configuration.getStringList("messages.changepassword-unregistered");
    }

    public List<String> getChangepasswordWrongOldPassword() {
        return this.configuration.getStringList("messages.changepassword-wrong-oldpassword");
    }

    public List<String> getChangePasswordUpdated() {
        return this.configuration.getStringList("messages.changepassword-changed");
    }

    public List<String> getPlayerNotOnline() {
        return this.configuration.getStringList("messages.player-not-online");
    }

    public int getPlayersSameIp() {
        return this.configuration.getInt("safety.players-same-ip", 2);
    }

    public boolean isVoidWorldEnabled() {
        return this.configuration.getBoolean("void-world.enabled", true);
    }

    public String getVoidWorldMode() {
        return this.configuration.getString("void-world.mode", "NORMAL");
    }

    public List<String> getPasswordInChat() {
        return this.configuration.getStringList("messages.password-in-chat");
    }

    public List<String> getEmailUpdateHelp() {
        return this.configuration.getStringList("messages.email-update-help");
    }

    public List<String> getEmailInvalidFormat() {
        return this.configuration.getStringList("messages.email-invalid-format");
    }

    public List<String> getEmailUpdated() {
        return this.configuration.getStringList("messages.email-updated");
    }

    public List<String> getEmailSomethingWrong() {
        return this.configuration.getStringList("messages.email-something-wrong");
    }

    public String getEmailSMTP() {
        return this.configuration.getString("email.email-smtp");
    }

    public int getEmailPort() {
        return this.configuration.getInt("email.email-port");
    }

    public boolean getEmailUseTLS() {
        return this.configuration.getBoolean("email.use-tls", true);
    }

    public String getEmailAccount() {
        return this.configuration.getString("email.email-account");
    }

    public String getEmailPassword() {
        return this.configuration.getString("email.email-password");
    }

    public String getEmailSenderName() {
        return this.configuration.getString("email.email-sender-name");
    }

    public int getRecoveryPasswordMinLength() {
        return this.configuration.getInt("email.recovery-password-min-length");
    }

    public String getEmailSubject() {
        return this.configuration.getString("email.email-subject");
    }

    public List<String> getEmailTextContent() {
        return this.configuration.getStringList("email.email-text-content");
    }

    public List<String> getPasswordResetHelp() {
        return this.configuration.getStringList("messages.password-reset-help");
    }

    public List<String> getPasswordResetHelpAdmins() {
        return this.configuration.getStringList("messages.password-reset-help-admins");
    }

    public List<String> getPasswordResetMissingEmail() {
        return this.configuration.getStringList("messages.password-reset-missing-email");
    }

    public List<String> getPasswordResetNotEnabled() {
        return this.configuration.getStringList("messages.password-reset-not-enabled");
    }

    public List<String> getPasswordResetFailed() {
        return this.configuration.getStringList("messages.password-reset-failed");
    }

    public List<String> getPasswordResetSuccess() {
        return this.configuration.getStringList("messages.password-reset-success");
    }

    public List<String> getPasswordResetTooEarly() {
        return this.configuration.getStringList("messages.password-reset-too-early");
    }

    public boolean isSoundsEnabled() {
        return this.configuration.getBoolean("sounds.enabled", true);
    }

    public String getSuccessfulLoginSound() {
        return this.configuration.getString("sounds.successful-login");
    }

    public String getWrongPasswordSound() {
        return this.configuration.getString("sounds.wrong-password");
    }

    public String getActionBarTickSound() {
        return this.configuration.getString("sounds.action-bar-tick");
    }

    public int getRecoveryMinDelay() {
        return this.configuration.getInt("email.recovery-min-delay", 90);
    }

    public boolean isEmailEnabled() {
        return this.configuration.getBoolean("email.enabled", false);
    }

    public boolean isLoginBlindness() {
        return this.configuration.getBoolean("login-blindness.enabled", false);
    }

    public boolean isLoginAnimationEnabled() {
        return this.configuration.getBoolean("login-animation.enabled", false);
    }

    public String getLoginAnimationDisplayType() {
        return this.configuration.getString("login-animation.display-type", "ACTION_BAR");
    }

    public String getLoginAnimationFormat() {
        return this.configuration.getString("login-animation.format", "&7[{BAR}&7] &e Login Time Left &c{TIME}");
    }

    public int getLoginAnimationBarLength() {
        return this.configuration.getInt("login-animation.bar-length", 24);
    }

    public String getLoginAnimationBarCharacterPassed() {
        return this.configuration.getString("login-animation.bar-character-passed", "|");
    }

    public String getLoginAnimationBarCharacterNotPassed() {
        return this.configuration.getString("login-animation.bar-character-not-passed", "|");
    }

    public String getLoginAnimationBarTimePassedColour() {
        return this.configuration.getString("login-animation.bar-time-passed-colour", "&c");
    }

    public String getLoginAnimationBarTimeNotPassedColour() {
        return this.configuration.getString("login-animation.bar-time-not-passed-colour", "&a");
    }

    public String getTempPasswordSecretKey() {
        return this.configuration.getString("temp-password.secret-key");
    }

    public int getTempPasswordTimeout() {
        return this.configuration.getInt("temp-password.password-timeout");
    }

    public List<String> getTempLoginWrongPasswordMessages() {
        return this.configuration.getStringList("messages.temp-login-wrong-password");
    }

    public List<String> getTempoLoginAddedPasswordMessages() {
        return this.configuration.getStringList("messages.added-temp-login-password");
    }

    public int getRegisterSameIp() {
        return this.configuration.getInt("safety.register-same-ip");
    }

    public List<String> getCannotRegisterTooManyIp() {
        return this.configuration.getStringList("messages.cannot-register-too-many-ip");
    }

    public List<String> getLoginDataNotFound() {
        return this.configuration.getStringList("messages.login-data-not-found");
    }

    public List<String> getLoginDataDisplay() {
        return this.configuration.getStringList("messages.login-data-display");
    }

    public boolean getCaptchaEnabled() {
        return this.configuration.getBoolean("captcha.enabled", true);
    }

    public List<String> getCaptchaMessage() {
        return this.configuration.getStringList("captcha.message");
    }

    public List<String> getCaptchaWrongAnswer() {
        return this.configuration.getStringList("captcha.wrong-answer");
    }

    public List<String> getCaptchaCorrectAnswer() {
        return this.configuration.getStringList("captcha.correct-answer");
    }

    public List<String> getCaptchaRegisterNotAllowed() {
        return this.configuration.getStringList("captcha.register-not-allowed");
    }

    public int getCaptchaMaxAttempts() {
        return this.configuration.getInt("captcha.max-attempts");
    }

    public List<String> getCaptchaPunishments() {
        return this.configuration.getStringList("captcha.punishments");
    }

    public String getLoginTeleportWorld() {
        return this.configuration.getString("teleport.login-teleport.world");
    }

    public double getLoginTeleportX() {
        return this.configuration.getDouble("teleport.login-teleport.x");
    }

    public double getLoginTeleportY() {
        return this.configuration.getDouble("teleport.login-teleport.y");
    }

    public double getLoginTeleportZ() {
        return this.configuration.getDouble("teleport.login-teleport.z");
    }

    public String getPostLoginTeleportWorld() {
        return this.configuration.getString("teleport.post-login-teleport.world");
    }

    public double getPostLoginTeleportX() {
        return this.configuration.getDouble("teleport.post-login-teleport.x");
    }

    public double getPostLoginTeleportY() {
        return this.configuration.getDouble("teleport.post-login-teleport.y");
    }

    public double getPostLoginTeleportZ() {
        return this.configuration.getDouble("teleport.post-login-teleport.z");
    }

    public boolean isLoginTeleportEnabled() {
        return this.configuration.getBoolean("teleport.enabled");
    }

    public boolean isTitleEnabled() {
        return this.configuration.getBoolean("title.enabled");
    }

    @Nullable
    public String getTitleText() {
        return this.configuration.getString("title.login-title-text");
    }

    @Nullable
    public String getSubtitleText() {
        return this.configuration.getString("title.login-subtitle-text");
    }

    @Nullable
    public String getRegisterTitleText() {
        return this.configuration.getString("title.register-title-text");
    }

    @Nullable
    public String getRegisterSubtitleText() {
        return this.configuration.getString("title.register-subtitle-text");
    }


    public int getTitleFadeIn() {
        return this.configuration.getInt("title.fade-in");
    }

    public int getTitleFadeOut() {
        return this.configuration.getInt("title.fade-out");
    }

    public int getTitleStay() {
        return this.configuration.getInt("title.stay");
    }

    public int getTitleRepeatDelay() {
        return this.configuration.getInt("title.repeat-delay");
    }
}
