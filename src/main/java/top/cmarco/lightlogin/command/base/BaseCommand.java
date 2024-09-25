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

package top.cmarco.lightlogin.command.base;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.command.LightLoginCommand;

import java.util.Locale;
import java.util.stream.Collectors;

public final class BaseCommand extends LightLoginCommand {
    public BaseCommand(@NotNull LightLoginPlugin plugin) {
        super(plugin, "lightlogin.admin.info", "lightlogin", true);
    }

    public static void sendCommandHelpEng(@NotNull final CommandSender sender) {
        final String s1 = colorMessage("&6&l★&r&eLightLogin&6&l★&r&f Help Page:");
        final String s2 = colorMessage("&7Set your account's email.");
        final String s3 = colorMessage("  ● &7/&eemail &7<&6email&7>");
        final String s4 = colorMessage("&7Request to change your current password.");
        final String s5 = colorMessage("  ● &7/&echangepassword &7<&6oldPassword&7> &7<&6newPassword&7>");
        final String s6 = colorMessage("&7Reset your password via email.");
        final String s7 = colorMessage("  ● &7/&eresetpassword");
        final String s8 = colorMessage("&7Unauthenticate an online player.");
        final String s9 = colorMessage("  ● &7/&eunlogin &7<&6username&7>");
        final String s10 = colorMessage("&7Unregister a player");
        final String s11 = colorMessage("  ● &7/&eunregister &7<&6username&7>");
        final String s12 = colorMessage("&7Authenticate into the server.");
        final String s13 = colorMessage("  ● &7/&elogin &7<&6password&7>");
        final String s14 = colorMessage("&7Register into this server.");
        final String s15 = colorMessage("  ● &7/&eregister &7<&6password&7> &7<&6passwordConfirm&7>");
        final String s16 = colorMessage("&7Assign temp-password through console.");
        final String s17 = colorMessage("  ● &7/&etemppassword &7<&6username&7> &7<&6password&7> &7<&6security-key&7>");
        final String s18 = colorMessage("&7Read user login data.");
        final String s19 = colorMessage("  ● &7/&elogin-data &7<&6username&7>");

        final String[] englishMessages = {s1,s2,s3,s4,s5,s6,s7,s8,s9,s10,s11,s12,s13,s14,s15,s16,s17, s18,s19};
        sender.sendMessage(englishMessages);
    }

    public static void sendCommandHelpFilipino(@NotNull final CommandSender sender) {
        final String s1 = colorMessage("&6&l★&r&eLightLogin&6&l★&r&f Pahina ng Tulong:");
        final String s2 = colorMessage("&7Itakda ang email ng iyong account.");
        final String s3 = colorMessage("  ● &7/&eemail &7<&6email&7>");
        final String s4 = colorMessage("&7Humiling ng pagbabago ng iyong kasalukuyang password.");
        final String s5 = colorMessage("  ● &7/&echangepassword &7<&6lumangPassword&7> &7<&6bagongPassword&7>");
        final String s6 = colorMessage("&7I-reset ang iyong password sa pamamagitan ng email.");
        final String s7 = colorMessage("  ● &7/&eresetpassword");
        final String s8 = colorMessage("&7Huwag paganahin ang pag-authenticate ng isang online player.");
        final String s9 = colorMessage("  ● &7/&eunlogin &7<&6username&7>");
        final String s10 = colorMessage("&7Tanggalin ang rehistro ng isang player");
        final String s11 = colorMessage("  ● &7/&eunregister &7<&6username&7>");
        final String s12 = colorMessage("&7Mag-authenticate sa server.");
        final String s13 = colorMessage("  ● &7/&elogin &7<&6password&7>");
        final String s14 = colorMessage("&7Magrehistro sa server na ito.");
        final String s15 = colorMessage("  ● &7/&eregister &7<&6password&7> &7<&6kumpirmahinPassword&7>");
        final String s16 = colorMessage("&7Mag-assign ng pansamantalang password sa pamamagitan ng console.");
        final String s17 = colorMessage("  ● &7/&etemppassword &7<&6username&7> &7<&6password&7> &7<&6security-key&7>");
        final String s18 = colorMessage("&7Basahin ang login data ng user.");
        final String s19 = colorMessage("  ● &7/&elogin-data &7<&6username&7>");

        final String[] filipinoMessages = {s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19};
        sender.sendMessage(filipinoMessages);
    }

    public static void sendCommandHelpFrench(@NotNull final CommandSender sender) {
        final String s1 = colorMessage("&6&l★&r&eLightLogin&6&l★&r&f Page d'aide:");
        final String s2 = colorMessage("&7Définissez l'email de votre compte.");
        final String s3 = colorMessage("  ● &7/&eemail &7<&6email&7>");
        final String s4 = colorMessage("&7Demander à changer votre mot de passe actuel.");
        final String s5 = colorMessage("  ● &7/&echangepassword &7<&6ancienMotDePasse&7> &7<&6nouveauMotDePasse&7>");
        final String s6 = colorMessage("&7Réinitialisez votre mot de passe par email.");
        final String s7 = colorMessage("  ● &7/&eresetpassword");
        final String s8 = colorMessage("&7Déconnecter un joueur en ligne.");
        final String s9 = colorMessage("  ● &7/&eunlogin &7<&6nomDUtilisateur&7>");
        final String s10 = colorMessage("&7Désinscrire un joueur");
        final String s11 = colorMessage("  ● &7/&eunregister &7<&6nomDUtilisateur&7>");
        final String s12 = colorMessage("&7Authentifiez-vous sur le serveur.");
        final String s13 = colorMessage("  ● &7/&elogin &7<&6motDePasse&7>");
        final String s14 = colorMessage("&7Inscrivez-vous sur ce serveur.");
        final String s15 = colorMessage("  ● &7/&eregister &7<&6motDePasse&7> &7<&6confirmationMotDePasse&7>");
        final String s16 = colorMessage("&7Attribuez un mot de passe temporaire via la console.");
        final String s17 = colorMessage("  ● &7/&etemppassword &7<&6nomDUtilisateur&7> &7<&6motDePasse&7> &7<&6cléSécurité&7>");
        final String s18 = colorMessage("&7Lire les données de connexion de l'utilisateur.");
        final String s19 = colorMessage("  ● &7/&elogin-data &7<&6nomDUtilisateur&7>");

        final String[] frenchMessages = {s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19};
        sender.sendMessage(frenchMessages);
    }

    public static void sendCommandHelpHebrew(@NotNull final CommandSender sender) {
        final String s1 = colorMessage("&6&l★&r&eLightLogin&6&l★&r&f עמוד עזרה:");
        final String s2 = colorMessage("&7הגדר את כתובת האימייל של החשבון שלך.");
        final String s3 = colorMessage("  ● &7/&eemail &7<&6אימייל&7>");
        final String s4 = colorMessage("&7בקש לשנות את הסיסמה הנוכחית שלך.");
        final String s5 = colorMessage("  ● &7/&echangepassword &7<&6סיסמהישנה&7> &7<&6סיסמהחדשה&7>");
        final String s6 = colorMessage("&7אפס את הסיסמה שלך באמצעות אימייל.");
        final String s7 = colorMessage("  ● &7/&eresetpassword");
        final String s8 = colorMessage("&7נתק שחקן מחובר.");
        final String s9 = colorMessage("  ● &7/&eunlogin &7<&6שם משתמש&7>");
        final String s10 = colorMessage("&7הסר רישום שחקן.");
        final String s11 = colorMessage("  ● &7/&eunregister &7<&6שם משתמש&7>");
        final String s12 = colorMessage("&7התחבר לשרת.");
        final String s13 = colorMessage("  ● &7/&elogin &7<&6סיסמה&7>");
        final String s14 = colorMessage("&7הירשם לשרת זה.");
        final String s15 = colorMessage("  ● &7/&eregister &7<&6סיסמה&7> &7<&6אישורסיסמה&7>");
        final String s16 = colorMessage("&7הקצה סיסמה זמנית דרך הקונסול.");
        final String s17 = colorMessage("  ● &7/&etemppassword &7<&6שם משתמש&7> &7<&6סיסמה&7> &7<&6מפתח אבטחה&7>");
        final String s18 = colorMessage("&7קרא את נתוני ההתחברות של המשתמש.");
        final String s19 = colorMessage("  ● &7/&elogin-data &7<&6שם משתמש&7>");

        final String[] hebrewMessages = {s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19};
        sender.sendMessage(hebrewMessages);
    }

    public static void sendCommandHelpItalian(@NotNull final CommandSender sender) {
        final String s1 = colorMessage("&6&l★&r&eLightLogin&6&l★&r&f Pagina di aiuto:");
        final String s2 = colorMessage("&7Imposta l'email del tuo account.");
        final String s3 = colorMessage("  ● &7/&eemail &7<&6email&7>");
        final String s4 = colorMessage("&7Richiedi di cambiare la tua password attuale.");
        final String s5 = colorMessage("  ● &7/&echangepassword &7<&6vecchiaPassword&7> &7<&6nuovaPassword&7>");
        final String s6 = colorMessage("&7Reimposta la tua password via email.");
        final String s7 = colorMessage("  ● &7/&eresetpassword");
        final String s8 = colorMessage("&7Annulla l'autenticazione di un giocatore online.");
        final String s9 = colorMessage("  ● &7/&eunlogin &7<&6nomeUtente&7>");
        final String s10 = colorMessage("&7Annulla la registrazione di un giocatore");
        final String s11 = colorMessage("  ● &7/&eunregister &7<&6nomeUtente&7>");
        final String s12 = colorMessage("&7Autenticati nel server.");
        final String s13 = colorMessage("  ● &7/&elogin &7<&6password&7>");
        final String s14 = colorMessage("&7Registrati in questo server.");
        final String s15 = colorMessage("  ● &7/&eregister &7<&6password&7> &7<&6confermaPassword&7>");
        final String s16 = colorMessage("&7Assegna una password temporanea tramite la console.");
        final String s17 = colorMessage("  ● &7/&etemppassword &7<&6nomeUtente&7> &7<&6password&7> &7<&6chiaveDiSicurezza&7>");
        final String s18 = colorMessage("&7Leggi i dati di accesso dell'utente.");
        final String s19 = colorMessage("  ● &7/&elogin-data &7<&6nomeUtente&7>");

        final String[] italianMessages = {s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19};
        sender.sendMessage(italianMessages);
    }

    public static void sendCommandHelpRussian(@NotNull final CommandSender sender) {
        final String s1 = colorMessage("&6&l★&r&eLightLogin&6&l★&r&f Страница справки:");
        final String s2 = colorMessage("&7Установите электронную почту вашей учетной записи.");
        final String s3 = colorMessage("  ● &7/&eemail &7<&6email&7>");
        final String s4 = colorMessage("&7Запрос на изменение текущего пароля.");
        final String s5 = colorMessage("  ● &7/&echangepassword &7<&6старыйПароль&7> &7<&6новыйПароль&7>");
        final String s6 = colorMessage("&7Сбросьте пароль по электронной почте.");
        final String s7 = colorMessage("  ● &7/&eresetpassword");
        final String s8 = colorMessage("&7Разлогинить онлайн-игрока.");
        final String s9 = colorMessage("  ● &7/&eunlogin &7<&6имяПользователя&7>");
        final String s10 = colorMessage("&7Удалить регистрацию игрока");
        final String s11 = colorMessage("  ● &7/&eunregister &7<&6имяПользователя&7>");
        final String s12 = colorMessage("&7Авторизуйтесь на сервере.");
        final String s13 = colorMessage("  ● &7/&elogin &7<&6пароль&7>");
        final String s14 = colorMessage("&7Зарегистрируйтесь на этом сервере.");
        final String s15 = colorMessage("  ● &7/&eregister &7<&6пароль&7> &7<&6подтвердитеПароль&7>");
        final String s16 = colorMessage("&7Назначьте временный пароль через консоль.");
        final String s17 = colorMessage("  ● &7/&etemppassword &7<&6имяПользователя&7> &7<&6пароль&7> &7<&6ключБезопасности&7>");
        final String s18 = colorMessage("&7Чтение данных авторизации пользователя.");
        final String s19 = colorMessage("  ● &7/&elogin-data &7<&6имяПользователя&7>");

        final String[] russianMessages = {s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19};
        sender.sendMessage(russianMessages);
    }

    public static void sendCommandHelpSpanish(@NotNull final CommandSender sender) {
        final String s1 = colorMessage("&6&l★&r&eLightLogin&6&l★&r&f Página de ayuda:");
        final String s2 = colorMessage("&7Establece el correo electrónico de tu cuenta.");
        final String s3 = colorMessage("  ● &7/&eemail &7<&6correo electrónico&7>");
        final String s4 = colorMessage("&7Solicita cambiar tu contraseña actual.");
        final String s5 = colorMessage("  ● &7/&echangepassword &7<&6contraseñaAntigua&7> &7<&6contraseñaNueva&7>");
        final String s6 = colorMessage("&7Restablece tu contraseña por correo electrónico.");
        final String s7 = colorMessage("  ● &7/&eresetpassword");
        final String s8 = colorMessage("&7Desautentica a un jugador en línea.");
        final String s9 = colorMessage("  ● &7/&eunlogin &7<&6nombreDeUsuario&7>");
        final String s10 = colorMessage("&7Anula el registro de un jugador");
        final String s11 = colorMessage("  ● &7/&eunregister &7<&6nombreDeUsuario&7>");
        final String s12 = colorMessage("&7Autentícate en el servidor.");
        final String s13 = colorMessage("  ● &7/&elogin &7<&6contraseña&7>");
        final String s14 = colorMessage("&7Regístrate en este servidor.");
        final String s15 = colorMessage("  ● &7/&eregister &7<&6contraseña&7> &7<&6confirmaciónContraseña&7>");
        final String s16 = colorMessage("&7Asigna una contraseña temporal a través de la consola.");
        final String s17 = colorMessage("  ● &7/&etemppassword &7<&6nombreDeUsuario&7> &7<&6contraseña&7> &7<&6claveDeSeguridad&7>");
        final String s18 = colorMessage("&7Leer los datos de inicio de sesión del usuario.");
        final String s19 = colorMessage("  ● &7/&elogin-data &7<&6nombreDeUsuario&7>");

        final String[] spanishMessages = {s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19};
        sender.sendMessage(spanishMessages);
    }

    public static void sendCommandHelpPor(@NotNull final CommandSender sender) {
        final String s1 = colorMessage("&6&l★&r&eLightLogin&6&l★&r&f Página de Ajuda:");
        final String s2 = colorMessage("&7Defina o e-mail da sua conta.");
        final String s3 = colorMessage("  ● &7/&eemail &7<&6email&7>");
        final String s4 = colorMessage("&7Solicite a alteração da sua senha atual.");
        final String s5 = colorMessage("  ● &7/&echangepassword &7<&6senhaAntiga&7> &7<&6novaSenha&7>");
        final String s6 = colorMessage("&7Redefina sua senha via e-mail.");
        final String s7 = colorMessage("  ● &7/&eresetpassword");
        final String s8 = colorMessage("&7Desautentique um jogador online.");
        final String s9 = colorMessage("  ● &7/&eunlogin &7<&6nomeDeUsuario&7>");
        final String s10 = colorMessage("&7Desregistre um jogador");
        final String s11 = colorMessage("  ● &7/&eunregister &7<&6nomeDeUsuario&7>");
        final String s12 = colorMessage("&7Autentique-se no servidor.");
        final String s13 = colorMessage("  ● &7/&elogin &7<&6senha&7>");
        final String s14 = colorMessage("&7Registre-se neste servidor.");
        final String s15 = colorMessage("  ● &7/&eregister &7<&6senha&7> &7<&6confirmarSenha&7>");
        final String s16 = colorMessage("&7Atribua uma senha temporária através do console.");
        final String s17 = colorMessage("  ● &7/&etemppassword &7<&6nomeDeUsuario&7> &7<&6senha&7> &7<&6chave-de-seguranca&7>");
        final String s18 = colorMessage("&7Leia os dados de login do usuário.");
        final String s19 = colorMessage("  ● &7/&elogin-data &7<&6nomeDeUsuario&7>");

        final String[] portugueseMessages = {s1,s2,s3,s4,s5,s6,s7,s8,s9,s10,s11,s12,s13,s14,s15,s16,s17, s18,s19};
        sender.sendMessage(portugueseMessages);
    }


    public void sendCommandHelps(CommandSender sender) {
        switch (plugin.getLightConfiguration().getChosenLanguage()) {
            case ENGLISH -> sendCommandHelpEng(sender);
            case SPANISH -> sendCommandHelpSpanish(sender);
            case FRENCH -> sendCommandHelpFrench(sender);
            case HEBREW -> sendCommandHelpHebrew(sender);
            case ITALIAN -> sendCommandHelpItalian(sender);
            case RUSSIAN -> sendCommandHelpRussian(sender);
            case FILIPINO -> sendCommandHelpFilipino(sender);
            case PORTUGUESE -> sendCommandHelpPor(sender);
            default -> {}
        }
    }


    @Override
    protected void commandLogic(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length == 0) {
            sendCommandHelps(sender);
            return;
        }

        if (args.length != 1) {
            sendColorPrefixMessages(sender, configuration.getIncorrectCommandUsage(), plugin);
            return;
        }

        if (args[0].toLowerCase(Locale.ROOT).equals("info")) {
            sendColorPrefixMessages(sender, configuration.getPluginInfo().stream().map(s ->
                    s.replaceAll("\\{VERSION}", plugin.getDescription().getVersion())).collect(Collectors.toList()), plugin);
        } else {
            sendColorPrefixMessages(sender, configuration.getIncorrectCommandUsage(), plugin);
        }

    }
}
