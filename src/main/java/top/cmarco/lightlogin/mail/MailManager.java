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

package top.cmarco.lightlogin.mail;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.configuration.LightConfiguration;
import top.cmarco.lightlogin.database.LightLoginColumn;
import top.cmarco.lightlogin.database.PluginDatabase;
import top.cmarco.lightlogin.encrypt.Argon2Utilities;
import top.cmarco.lightlogin.encrypt.PasswordGenerator;

import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class MailManager {

    private final Properties properties;
    private final LightConfiguration config;
    private final LightLoginPlugin plugin;
    // -------
    private Session session = null;

    private Properties generateProperties() {
        Properties prop = new Properties();
        prop.put("mail.smtp.auth", true);
        prop.put("mail.smtp.starttls.enable", String.valueOf(this.config.getEmailUseTLS()));
        prop.put("mail.smtp.host", this.config.getEmailSMTP());
        prop.put("mail.smtp.port", this.config.getEmailPort());
        prop.put("mail.smtp.ssl.trust", this.config.getEmailSMTP());
        return prop;
    }

    public MailManager(@NotNull final LightLoginPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getLightConfiguration();
        this.properties = this.generateProperties();
    }

    public void startSession() {
        if (session != null) {
            return;
        }

        this.session = Session.getInstance(this.properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.getEmailAccount(), config.getEmailPassword());
            }
        });
    }

    public CompletableFuture<Boolean> updatePasswordAndSendEmail(UUID uuid, String username, String emailDestination) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        final String newPassword = PasswordGenerator.generateRandomPassword(this.config.getRecoveryPasswordMinLength());

        PluginDatabase db = this.plugin.getDatabase();

        final byte[] salt = Argon2Utilities.generateSaltByte(0x10);
        final String basedPassword = Argon2Utilities.encryptArgon2(newPassword, salt);

        db.updateRow(uuid.toString(), LightLoginColumn.PASSWORD, basedPassword).whenCompleteAsync((v, t) -> {

            if (t != null) {
                plugin.getLogger().warning("Something went wrong when updating password from email request.");
                plugin.getLogger().warning(t.getLocalizedMessage());
                result.complete(false);
                return;
            }

            db.updateRow(uuid.toString(), LightLoginColumn.SALT, basedPassword).whenCompleteAsync((v2, t2) -> {

                if (t2 != null) {
                    plugin.getLogger().warning("Something went wrong when updating salt from email request.");
                    plugin.getLogger().warning(t2.getLocalizedMessage());
                    result.complete(false);
                    return;
                }

                Message message = new MimeMessage(session);
                try {
                    message.setFrom(new InternetAddress(this.config.getEmailAccount()));
                    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailDestination));
                    message.setSubject(this.config.getEmailSubject());


                    List<String> mailContent = this.config.getEmailTextContent()
                            .stream()
                            .map(s -> s.replace("{PASSWORD}", newPassword)
                                    .replace("{PLAYER}", username)
                            ).collect(Collectors.toList());


                    StringBuilder splitNewlineBuilder = new StringBuilder();
                    mailContent.forEach(s -> splitNewlineBuilder.append(s).append('\n'));

                    MimeBodyPart mimeBodyPart = new MimeBodyPart();
                    mimeBodyPart.setContent(splitNewlineBuilder.toString(), "text/html; charset=utf-8");

                    Multipart multipart = new MimeMultipart();
                    multipart.addBodyPart(mimeBodyPart);

                    message.setContent(multipart);

                    Transport.send(message);
                    plugin.getAuthLogs().add("Sent new recovery password to email " + emailDestination);
                    result.complete(true);
                } catch (final MessagingException exception) {
                    plugin.sendConsoleColoured("&7[&c&lX&r&7] &cSomething went wrong while sending email!");
                    plugin.getLogger().warning(exception.getLocalizedMessage());
                    result.complete(false);
                }

            });

        });

        return result;
    }
}
