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

package top.cmarco.lightlogin.command;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.command.base.BaseCommand;
import top.cmarco.lightlogin.command.changepassword.ChangePasswordCommand;
import top.cmarco.lightlogin.command.email.EmailCommand;
import top.cmarco.lightlogin.command.login.LoginCommand;
import top.cmarco.lightlogin.command.logindata.LoginDataCommand;
import top.cmarco.lightlogin.command.register.RegisterCommand;
import top.cmarco.lightlogin.command.resetpassword.ResetPasswordCommand;
import top.cmarco.lightlogin.command.temppassword.TempPasswordCommand;
import top.cmarco.lightlogin.command.unlogin.UnloginCommand;
import top.cmarco.lightlogin.command.unregister.UnregisterCommand;
import top.cmarco.lightlogin.command.verify.VerifyCommand;

import java.util.HashMap;
import java.util.Map;

public final class CommandManager {

    private final LightLoginPlugin plugin;
    private final Map<String, LightLoginCommand> registeredCommands = new HashMap<>();
    public CommandManager(@NotNull LightLoginPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        BaseCommand baseCommand = new BaseCommand(this.plugin);
        LoginCommand loginCommand = new LoginCommand(this.plugin);
        UnloginCommand unloginCommand = new UnloginCommand(this.plugin);
        RegisterCommand registerCommand = new RegisterCommand(this.plugin);
        UnregisterCommand unregisterCommand = new UnregisterCommand(this.plugin);
        ChangePasswordCommand changePasswordCommand = new ChangePasswordCommand(this.plugin);
        EmailCommand emailCommand = new EmailCommand(this.plugin);
        ResetPasswordCommand resetPasswordCommand = new ResetPasswordCommand(this.plugin);
        TempPasswordCommand tempPasswordCommand = new TempPasswordCommand(this.plugin);
        LoginDataCommand loginDataCommand = new LoginDataCommand(this.plugin);
        VerifyCommand verifyCommand = new VerifyCommand(this.plugin);

        this.registeredCommands.put(baseCommand.commandName, baseCommand);
        this.registeredCommands.put(loginCommand.commandName, loginCommand);
        this.registeredCommands.put(unloginCommand.commandName, unloginCommand);
        this.registeredCommands.put(registerCommand.commandName, registerCommand);
        this.registeredCommands.put(unregisterCommand.commandName, unregisterCommand);
        this.registeredCommands.put(changePasswordCommand.commandName, changePasswordCommand);
        this.registeredCommands.put(emailCommand.commandName, emailCommand);
        this.registeredCommands.put(resetPasswordCommand.commandName, resetPasswordCommand);
        this.registeredCommands.put(tempPasswordCommand.commandName, tempPasswordCommand);
        this.registeredCommands.put(loginDataCommand.commandName, loginDataCommand);
        this.registeredCommands.put(verifyCommand.commandName, verifyCommand);

        this.registeredCommands.values().forEach(LightLoginCommand::register);
        loginCommand.startClearTasks();
    }

    @Nullable
    public LightLoginCommand getRegisteredCommand(@NotNull String commandName) {
        return this.registeredCommands.get(commandName);
    }

}
