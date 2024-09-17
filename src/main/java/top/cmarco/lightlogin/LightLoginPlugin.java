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
package top.cmarco.lightlogin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import top.cmarco.lightlogin.command.CommandManager;
import top.cmarco.lightlogin.command.LightLoginCommand;
import top.cmarco.lightlogin.command.temppassword.TempPasswordManager;
import top.cmarco.lightlogin.command.verify.VerificationManager;
import top.cmarco.lightlogin.configuration.ConfigUtils;
import top.cmarco.lightlogin.configuration.ConfigurationFiles;
import top.cmarco.lightlogin.configuration.LightConfiguration;
import top.cmarco.lightlogin.data.*;
import top.cmarco.lightlogin.database.*;
import top.cmarco.lightlogin.library.LibraryManager;
import top.cmarco.lightlogin.listeners.ListenerManager;
import top.cmarco.lightlogin.log.AuthLogs;
import top.cmarco.lightlogin.log.SafetyFilter;
import top.cmarco.lightlogin.log.StartupLogo;
import top.cmarco.lightlogin.mail.MailManager;
import top.cmarco.lightlogin.world.WorldUtils;

import java.util.EnumMap;
import java.util.Objects;

public final class LightLoginPlugin extends JavaPlugin {

    private AbstractFilter safetyFilter = null;
    private LightConfiguration lightConfiguration = null;
    private PluginDatabase database = null;
    private AuthenticationManager authenticationManager = null;
    private AutoKickManager autoKickManager = null;
    private final EnumMap<ConfigurationFiles, FileConfiguration> languagesConfigMap = new EnumMap<>(ConfigurationFiles.class);
    private PlaintextPasswordManager plaintextPasswordManager = null;
    private TempPasswordManager tempPasswordManager = null;
    private boolean disabled = false;
    private World loginWorld;
    private AuthLogs authLogs = null;
    private VoidLoginManager voidLoginManager = null;
    private StartupLoginsManager startupLoginsManager = null;
    private MailManager mailManager = null;
    private LibraryManager libraryManager = null;
    private CommandManager commandManager = null;
    private ListenerManager listenerManager = null;
    private VerificationManager verificationManager = null;

    /* --------------------------------------------------------- */

    /**
     * Plugin startup logic.
     * Called on STARTUP phase.
     */
    @Override
    public void onEnable() {
        this.printLogoStartup();
        this.setupConfig();
        this.setupChatFilter();
        this.setupSecurity();
        this.loadLibraries();
        this.setupDatabase();
        this.loadLoginWorld(); // 1
        this.setVoidLoginManager(); // 2
        this.setupVerificationManager();
        this.setupAuthenticationManager();
        this.setupPasswordManager();
        this.setupTempPswManager();
        this.setupStartupLoginsManager();
        this.registerAllListeners();
        this.setupMailManager();
        this.setupCommands();
        this.setupKickManager();
        this.setAuthLogs();
    }

    /**
     * Plugin disable logic.
     */
    @Override
    public void onDisable() {
        if (this.database != null) {
            this.database.close();
        }

        if (this.authLogs != null) {
            this.authLogs.saveLogs();
        }
    }

    /* --------------------------------------------------------- */

    private void loadLibraries() {
        this.libraryManager = new LibraryManager(this);
        this.libraryManager.loadLibraries();
    }

    private void setupMailManager() {
        this.mailManager = new MailManager(this);

        if (!lightConfiguration.isEmailEnabled()) {
            return;
        }

        this.mailManager.startSession();
    }

    private void setupStartupLoginsManager() {
        this.startupLoginsManager = new StartupLoginsManager();
    }

    public void sendConsoleColoured(String text) {
        super.getServer().getConsoleSender().sendMessage(LightLoginCommand.colorMessage(text));
    }

    private void setupTempPswManager() {
        this.tempPasswordManager = new TempPasswordManager(this);
    }

    private void setupPasswordManager() {
        this.plaintextPasswordManager = new PlaintextPasswordManager();
    }

    private void printLogoStartup() {
        this.sendConsoleColoured(StartupLogo.LOGO_ARRAY[0]);
        this.sendConsoleColoured(StartupLogo.getLoadingString(0, ConfigurationFiles.ENGLISH));
    }

    private void setupSecurity() {
        this.sendConsoleColoured(StartupLogo.getLoadingString(1, lightConfiguration.getChosenLanguage()));
        this.sendConsoleColoured(StartupLogo.getLoadingString(2, lightConfiguration.getChosenLanguage()));
    }

    public void setVoidLoginManager() {
        if (!this.lightConfiguration.isVoidWorldEnabled()) {
            return;
        }

        this.sendConsoleColoured(StartupLogo.getLoadingString(3, lightConfiguration.getChosenLanguage()));
        this.voidLoginManager = new VoidLoginManager(this.loginWorld);
        this.sendConsoleColoured(StartupLogo.getLoadingString(4, lightConfiguration.getChosenLanguage()));
    }

    public void setupVerificationManager() {
        this.sendConsoleColoured(StartupLogo.getLoadingString(27, lightConfiguration.getChosenLanguage()));
        this.verificationManager = new VerificationManager(this);
        this.sendConsoleColoured(StartupLogo.getLoadingString(28, lightConfiguration.getChosenLanguage()));
    }

    private void setAuthLogs() {
        this.sendConsoleColoured(StartupLogo.getLoadingString(5, lightConfiguration.getChosenLanguage()));
        this.authLogs = new AuthLogs(this);
        this.sendConsoleColoured(StartupLogo.getLoadingString(6, lightConfiguration.getChosenLanguage()));
    }

    public void loadLoginWorld() {
        if (!this.lightConfiguration.isVoidWorldEnabled() || loginWorld != null) {
            return;
        }

        this.loginWorld = WorldUtils.findWorld(this.lightConfiguration);
    }

    private void saveAllConfigs() {
        for (final ConfigurationFiles file : ConfigurationFiles.values()) {
            final FileConfiguration fileConfiguration = ConfigUtils.createCustomConfig(file, this);
            this.languagesConfigMap.put(file, fileConfiguration);
        }
    }

    /**
     * Setup chat filter to prevent password leaks
     */
    private void setupChatFilter() {
        this.sendConsoleColoured(StartupLogo.getLoadingString(9, lightConfiguration.getChosenLanguage()));
        Logger rootLogger = (Logger) LogManager.getRootLogger();
        this.safetyFilter = new SafetyFilter();
        this.safetyFilter.start();
        rootLogger.addFilter(this.safetyFilter);
        this.sendConsoleColoured(StartupLogo.getLoadingString(10, lightConfiguration.getChosenLanguage()));
    }

    /**
     * Setup all the commands registered by this software,
     * registering them into the server instance.
     *
     */
    private void setupCommands() {
        this.commandManager = new CommandManager(this);
        this.commandManager.registerCommands();
    }

    /**
     * Setup the authentication manager instance
     */
    private void setupAuthenticationManager() {
        if (this.authenticationManager != null) {
            return;
        }

        this.sendConsoleColoured(StartupLogo.getLoadingString(11, lightConfiguration.getChosenLanguage()));
        this.authenticationManager = new BasicAuthenticationManager(this);
        this.authenticationManager.startLoginNotifyTask();
        this.authenticationManager.startRegisterNotifyTask();
        this.sendConsoleColoured(StartupLogo.getLoadingString(12, lightConfiguration.getChosenLanguage()));
    }

    private void setupKickManager() {
        if (this.autoKickManager != null) {
            return;
        }

        this.sendConsoleColoured(StartupLogo.getLoadingString(13, lightConfiguration.getChosenLanguage()));
        this.autoKickManager = new AutoKickManager(this);
        this.autoKickManager.startAutoKickTask();
        this.sendConsoleColoured(StartupLogo.getLoadingString(14, lightConfiguration.getChosenLanguage()));
    }

    /**
     * Method responsible for registering all of this software listeners.
     */
    private void registerAllListeners() {
        this.sendConsoleColoured(StartupLogo.getLoadingString(15, lightConfiguration.getChosenLanguage()));
        this.listenerManager = new ListenerManager(this);
        this.listenerManager.addAllListeners();
        this.listenerManager.registerAllAddedListeners();
        this.sendConsoleColoured(StartupLogo.getLoadingString(16, lightConfiguration.getChosenLanguage()));
    }

    private void setupConfig() {
        saveDefaultConfig();
        saveAllConfigs();
        ConfigurationFiles chosenLanguage = ConfigUtils.chosenLanguage(this);
        lightConfiguration = new LightConfiguration(chosenLanguage, this);
        lightConfiguration.loadConfig();
        this.sendConsoleColoured(StartupLogo.getLoadingString(17, lightConfiguration.getChosenLanguage()));
        this.sendConsoleColoured(StartupLogo.getLoadingString(21, lightConfiguration.getChosenLanguage()) + ' ' + chosenLanguage.name() + "!");
    }

    private void setupDatabase() {
        this.sendConsoleColoured(StartupLogo.getLoadingString(22, lightConfiguration.getChosenLanguage()));
        final DatabaseType databaseType = DatabaseType.fromName(
                Objects.requireNonNull(this.lightConfiguration.getDatabaseType()));

        if (databaseType == null) {
            this.disabled = true;
            getLogger().warning(StartupLogo.getLoadingString(23, lightConfiguration.getChosenLanguage()));
            if (lightConfiguration.isCrashShutdown()) {
                getLogger().warning(StartupLogo.getLoadingString(24, lightConfiguration.getChosenLanguage()));
                getServer().shutdown();
            }
            return;
        }

        switch (databaseType) {
            case SQLITE: {this.database = new SQLiteDatabase(this); break;}
            case MYSQL: {this.database = new MySqlDatabase(this); break;}
            case POSTGRESQL: {this.database = new PostgreSQLDatabase(this); break;}
            default: {this.database = null; break;}
        }

        assert this.database != null;

        this.database.loadDriverClass();
        this.database.connect();
        this.database.createTables();

        this.sendConsoleColoured(StartupLogo.getLoadingString(25, lightConfiguration.getChosenLanguage()) + databaseType.name() + StartupLogo.getLoadingString(26, lightConfiguration.getChosenLanguage()));
    }

    /* -------------------------------------------------------------------------- */

    public LightConfiguration getLightConfiguration() {
        return lightConfiguration;
    }

    public PluginDatabase getDatabase() {
        return database;
    }

    public AuthenticationManager getAuthenticationManager() {
        return authenticationManager;
    }

    public EnumMap<ConfigurationFiles, FileConfiguration> getLanguagesConfigMap() {
        return languagesConfigMap;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public AutoKickManager getAutoKickManager() {
        return autoKickManager;
    }

    public PlaintextPasswordManager getPlaintextPasswordManager() {
        return plaintextPasswordManager;
    }

    public AuthLogs getAuthLogs() {
        return authLogs;
    }

    public VoidLoginManager getVoidLoginManager() {
        return voidLoginManager;
    }

    public StartupLoginsManager getStartupLoginsManager() {
        return startupLoginsManager;
    }

    public TempPasswordManager getTempPasswordManager() {
        return tempPasswordManager;
    }

    public VerificationManager getVerificationManager() {
        return verificationManager;
    }
}