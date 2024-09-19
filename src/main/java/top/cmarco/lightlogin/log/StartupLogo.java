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

package top.cmarco.lightlogin.log;

import top.cmarco.lightlogin.configuration.ConfigurationFiles;

public class StartupLogo {

    public static final String LOGO =
            ("\n" +
            "&6░▒▓█▓▒░      ░▒▓█▓▒░░▒▓██████▓▒░░▒▓█▓▒░░▒▓█▓▒░▒▓████████▓▒░▒▓█▓▒░      ░▒▓██████▓▒░ ░▒▓██████▓▒░░▒▓█▓▒░▒▓███████▓▒░  \n" +
            "&6░▒▓█▓▒░      ░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░  ░▒▓█▓▒░   ░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░ \n" +
            "&6░▒▓█▓▒░      ░▒▓█▓▒░▒▓█▓▒░      ░▒▓█▓▒░░▒▓█▓▒░  ░▒▓█▓▒░   ░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░      ░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░ \n" +
            "&6░▒▓█▓▒░      ░▒▓█▓▒░▒▓█▓▒▒▓███▓▒░▒▓████████▓▒░  ░▒▓█▓▒░   ░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒▒▓███▓▒░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░ \n" +
            "&6░▒▓█▓▒░      ░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░  ░▒▓█▓▒░   ░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░ \n" +
            "&6░▒▓█▓▒░      ░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░  ░▒▓█▓▒░   ░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░ \n" +
            "&6░▒▓████████▓▒░▒▓█▓▒░░▒▓██████▓▒░░▒▓█▓▒░░▒▓█▓▒░  ░▒▓█▓▒░   ░▒▓████████▓▒░▒▓██████▓▒░ ░▒▓██████▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░").replace("█", "&e█&6");

    public static final String[] LOGO_ARRAY = {LOGO};

    private static final String[] STRINGS_ENG = {
            "&e© CMarco 2024",
            "&7[ &a&l. . .&r &7] &eSetting up JVM security factors",
            "&7[ &a&l✔&r &7] &aCorrectly set up JVM security factors!",
            "&7[ &a&l. . .&r &7] &eSetting up void world feature",
            "&7[ &a&l✔&r &7] &aCorrectly set up void world feature!",
            "&7[ &a&l. . .&r &7] &eSetting up authentication logging",
            "&7[ &a&l✔&r &7] &aCorrectly set up void authentication logging!",
            "login_world",
            "WARNING! Error loading custom configuration ",
            "&7[ &a&l. . .&r &7] &eSetting up safe log filter",
            "&7[ &a&l✔&r &7] &aCorrectly set up safe log filter!",
            "&7[ &a&l. . .&r &7] &eSetting up authentication manager.",
            "&7[ &a&l✔&r &7] &aCorrectly set up authentication manager!",
            "&7[ &a&l. . .&r &7] &eSetting up kick manager.",
            "&7[ &a&l✔&r &7] &aCorrectly set up kick manager!",
            "&7[ &a&l. . .&r &7] &eSetting up custom listeners.",
            "&7[ &a&l✔&r &7] &aCorrectly set up custom listeners!",
            "&7[ &a&l. . .&r &7] &eSetting up configurations.",
            "language",
            "WARNING! Invalid configuration language chosen as ",
            "Will use config_english.yml until issue is solved!",
            "&7[ &a&l✔&r &7] &aLoaded config with language",
            "&7[ &a&l. . .&r &7] &eSetting up authentication Database.",
            "WARNING! Invalid database type from config file. Cannot load plugin.",
            "Shutting down server . . .",
            "&7[ &a&l✔&r &7] &aCorrectly set up Authentication Database &7(&a",
            "&7)&e!",
            "&7[ &a&l. . .&r &7] &eSetting up CAPTCHA",
            "&7[ &a&l✔&r &7] &aCorrectly set up CAPTCHA features!",
    };

    private static final String[] STRINGS_FILIPINO = {
            "&e© CMarco 2024",
            "&7[ &a&l. . .&r &7] &eInaayos ang mga salik ng seguridad ng JVM",
            "&7[ &a&l✔&r &7] &aMatagumpay na na-set up ang mga salik ng seguridad ng JVM!",
            "&7[ &a&l. . .&r &7] &eInaayos ang tampok na void world",
            "&7[ &a&l✔&r &7] &aMatagumpay na na-set up ang tampok na void world!",
            "&7[ &a&l. . .&r &7] &eInaayos ang authentication logging",
            "&7[ &a&l✔&r &7] &aMatagumpay na na-set up ang authentication logging!",
            "login_world",
            "BABALA! Error sa pag-load ng custom na configuration",
            "&7[ &a&l. . .&r &7] &eInaayos ang ligtas na log filter",
            "&7[ &a&l✔&r &7] &aMatagumpay na na-set up ang ligtas na log filter!",
            "&7[ &a&l. . .&r &7] &eInaayos ang authentication manager",
            "&7[ &a&l✔&r &7] &aMatagumpay na na-set up ang authentication manager!",
            "&7[ &a&l. . .&r &7] &eInaayos ang kick manager",
            "&7[ &a&l✔&r &7] &aMatagumpay na na-set up ang kick manager!",
            "&7[ &a&l. . .&r &7] &eInaayos ang mga custom listeners",
            "&7[ &a&l✔&r &7] &aMatagumpay na na-set up ang mga custom listeners!",
            "&7[ &a&l. . .&r &7] &eInaayos ang mga configuration",
            "language",
            "BABALA! Maling configuration language ang napili",
            "Gagamitin ang config_english.yml hangga't hindi ito naayos!",
            "&7[ &a&l✔&r &7] &aNai-load ang config na may wika",
            "&7[ &a&l. . .&r &7] &eInaayos ang Authentication Database",
            "BABALA! Maling uri ng database mula sa configuration file. Hindi ma-load ang plugin.",
            "Isinasara ang server . . .",
            "&7[ &a&l✔&r &7] &aMatagumpay na na-set up ang Authentication Database &7(&a",
            "&7)&e!",
            "&7[ &a&l. . .&r &7] &eInaayos ang CAPTCHA",
            "&7[ &a&l✔&r &7] &aMatagumpay na na-set up ang CAPTCHA features!",
    };

    private static final String[] STRINGS_FRENCH = {
            "&e© CMarco 2024",
            "&7[ &a&l. . .&r &7] &eConfiguration des facteurs de sécurité JVM",
            "&7[ &a&l✔&r &7] &aFacteurs de sécurité JVM configurés correctement!",
            "&7[ &a&l. . .&r &7] &eConfiguration de la fonctionnalité void world",
            "&7[ &a&l✔&r &7] &aFonctionnalité void world configurée correctement!",
            "&7[ &a&l. . .&r &7] &eConfiguration de la journalisation de l'authentification",
            "&7[ &a&l✔&r &7] &aJournalisation de l'authentification configurée correctement!",
            "login_world",
            "AVERTISSEMENT ! Erreur lors du chargement de la configuration personnalisée",
            "&7[ &a&l. . .&r &7] &eConfiguration du filtre de journalisation sécurisée",
            "&7[ &a&l✔&r &7] &aFiltre de journalisation sécurisée configuré correctement!",
            "&7[ &a&l. . .&r &7] &eConfiguration du gestionnaire d'authentification",
            "&7[ &a&l✔&r &7] &aGestionnaire d'authentification configuré correctement!",
            "&7[ &a&l. . .&r &7] &eConfiguration du gestionnaire d'exclusion",
            "&7[ &a&l✔&r &7] &aGestionnaire d'exclusion configuré correctement!",
            "&7[ &a&l. . .&r &7] &eConfiguration des écouteurs personnalisés",
            "&7[ &a&l✔&r &7] &aÉcouteurs personnalisés configurés correctement!",
            "&7[ &a&l. . .&r &7] &eConfiguration des configurations",
            "language",
            "AVERTISSEMENT ! Langue de configuration invalide choisie",
            "Utilisation de config_english.yml jusqu'à résolution du problème!",
            "&7[ &a&l✔&r &7] &aConfiguration avec langue chargée",
            "&7[ &a&l. . .&r &7] &eConfiguration de la base de données d'authentification",
            "AVERTISSEMENT ! Type de base de données invalide dans le fichier de configuration. Impossible de charger le plugin.",
            "Arrêt du serveur . . .",
            "&7[ &a&l✔&r &7] &aBase de données d'authentification configurée correctement &7(&a",
            "&7)&e!",
            "&7[ &a&l. . .&r &7] &eConfiguration de CAPTCHA",
            "&7[ &a&l✔&r &7] &aFonctionnalités CAPTCHA configurées correctement!",
    };

    private static final String[] STRINGS_HEBREW = {
            "&e© CMarco 2024",
            "&7[ &a&l. . .&r &7] &eהגדרת גורמי האבטחה של JVM",
            "&7[ &a&l✔&r &7] &aהגדרת גורמי האבטחה של JVM בהצלחה!",
            "&7[ &a&l. . .&r &7] &eהגדרת תכונת עולם ריק",
            "&7[ &a&l✔&r &7] &aהגדרת תכונת עולם ריק בהצלחה!",
            "&7[ &a&l. . .&r &7] &eהגדרת יומני אימות",
            "&7[ &a&l✔&r &7] &aהגדרת יומני אימות בהצלחה!",
            "login_world",
            "אזהרה! שגיאה בטעינת תצורה מותאמת אישית",
            "&7[ &a&l. . .&r &7] &eהגדרת מסנן יומן מאובטח",
            "&7[ &a&l✔&r &7] &aהגדרת מסנן יומן מאובטח בהצלחה!",
            "&7[ &a&l. . .&r &7] &eהגדרת מנהל אימות",
            "&7[ &a&l✔&r &7] &aהגדרת מנהל אימות בהצלחה!",
            "&7[ &a&l. . .&r &7] &eהגדרת מנהל ההרחקות",
            "&7[ &a&l✔&r &7] &aהגדרת מנהל ההרחקות בהצלחה!",
            "&7[ &a&l. . .&r &7] &eהגדרת מאזינים מותאמים אישית",
            "&7[ &a&l✔&r &7] &aהגדרת מאזינים מותאמים אישית בהצלחה!",
            "&7[ &a&l. . .&r &7] &eהגדרת תצורות",
            "language",
            "אזהרה! שפת תצורה לא תקינה נבחרה",
            "השתמש בקובץ config_english.yml עד לפתרון הבעיה!",
            "&7[ &a&l✔&r &7] &aטעינת תצורה עם שפה",
            "&7[ &a&l. . .&r &7] &eהגדרת מאגר אימות",
            "אזהרה! סוג מאגר הנתונים בקובץ התצורה לא תקין. לא ניתן לטעון את התוסף.",
            "כיבוי השרת . . .",
            "&7[ &a&l✔&r &7] &aמאגר אימות הוגדר בהצלחה &7(&a",
            "&7)&e!",
            "&7[ &a&l. . .&r &7] &eהגדרת CAPTCHA",
            "&7[ &a&l✔&r &7] &aהגדרת תכונות CAPTCHA בהצלחה!",
    };

    private static final String[] STRINGS_ITALIAN = {
            "&e© CMarco 2024",
            "&7[ &a&l. . .&r &7] &eImpostazione dei fattori di sicurezza JVM",
            "&7[ &a&l✔&r &7] &aFattori di sicurezza JVM impostati correttamente!",
            "&7[ &a&l. . .&r &7] &eImpostazione della funzionalità void world",
            "&7[ &a&l✔&r &7] &aFunzionalità void world impostata correttamente!",
            "&7[ &a&l. . .&r &7] &eImpostazione del logging di autenticazione",
            "&7[ &a&l✔&r &7] &aLogging di autenticazione impostato correttamente!",
            "login_world",
            "ATTENZIONE! Errore nel caricamento della configurazione personalizzata",
            "&7[ &a&l. . .&r &7] &eImpostazione del filtro log sicuro",
            "&7[ &a&l✔&r &7] &aFiltro log sicuro impostato correttamente!",
            "&7[ &a&l. . .&r &7] &eImpostazione del gestore di autenticazione",
            "&7[ &a&l✔&r &7] &aGestore di autenticazione impostato correttamente!",
            "&7[ &a&l. . .&r &7] &eImpostazione del gestore dei kick",
            "&7[ &a&l✔&r &7] &aGestore dei kick impostato correttamente!",
            "&7[ &a&l. . .&r &7] &eImpostazione degli ascoltatori personalizzati",
            "&7[ &a&l✔&r &7] &aAscoltatori personalizzati impostati correttamente!",
            "&7[ &a&l. . .&r &7] &eImpostazione delle configurazioni",
            "language",
            "ATTENZIONE! Lingua di configurazione non valida scelta",
            "Verrà utilizzato config_english.yml fino a quando il problema non sarà risolto!",
            "&7[ &a&l✔&r &7] &aConfigurazione con lingua caricata",
            "&7[ &a&l. . .&r &7] &eImpostazione del database di autenticazione",
            "ATTENZIONE! Tipo di database non valido dal file di configurazione. Impossibile caricare il plugin.",
            "Spegnimento del server . . .",
            "&7[ &a&l✔&r &7] &aDatabase di autenticazione impostato correttamente &7(&a",
            "&7)&e!",
            "&7[ &a&l. . .&r &7] &eImpostazione CAPTCHA",
            "&7[ &a&l✔&r &7] &aFunzionalità CAPTCHA impostate correttamente!",
    };

    private static final String[] STRINGS_RUSSIAN = {
            "&e© CMarco 2024",
            "&7[ &a&l. . .&r &7] &eНастройка факторов безопасности JVM",
            "&7[ &a&l✔&r &7] &aФакторы безопасности JVM успешно настроены!",
            "&7[ &a&l. . .&r &7] &eНастройка функции пустого мира",
            "&7[ &a&l✔&r &7] &aФункция пустого мира успешно настроена!",
            "&7[ &a&l. . .&r &7] &eНастройка журналирования аутентификации",
            "&7[ &a&l✔&r &7] &aЖурналирование аутентификации успешно настроено!",
            "login_world",
            "ВНИМАНИЕ! Ошибка при загрузке пользовательской конфигурации",
            "&7[ &a&l. . .&r &7] &eНастройка безопасного фильтра журналов",
            "&7[ &a&l✔&r &7] &aБезопасный фильтр журналов успешно настроен!",
            "&7[ &a&l. . .&r &7] &eНастройка менеджера аутентификации",
            "&7[ &a&l✔&r &7] &aМенеджер аутентификации успешно настроен!",
            "&7[ &a&l. . .&r &7] &eНастройка менеджера исключений",
            "&7[ &a&l✔&r &7] &aМенеджер исключений успешно настроен!",
            "&7[ &a&l. . .&r &7] &eНастройка пользовательских слушателей",
            "&7[ &a&l✔&r &7] &aПользовательские слушатели успешно настроены!",
            "&7[ &a&l. . .&r &7] &eНастройка конфигураций",
            "language",
            "ВНИМАНИЕ! Выбран недопустимый язык конфигурации",
            "Будет использоваться config_english.yml до устранения проблемы!",
            "&7[ &a&l✔&r &7] &aКонфигурация с языком загружена",
            "&7[ &a&l. . .&r &7] &eНастройка базы данных аутентификации",
            "ВНИМАНИЕ! Недопустимый тип базы данных в файле конфигурации. Невозможно загрузить плагин.",
            "Выключение сервера . . .",
            "&7[ &a&l✔&r &7] &aБаза данных аутентификации успешно настроена &7(&a",
            "&7)&e!",
            "&7[ &a&l. . .&r &7] &eНастройка CAPTCHA",
            "&7[ &a&l✔&r &7] &aФункции CAPTCHA успешно настроены!",
    };

    private static final String[] STRINGS_SPANISH = {
            "&e© CMarco 2024",
            "&7[ &a&l. . .&r &7] &eConfigurando los factores de seguridad de JVM",
            "&7[ &a&l✔&r &7] &a¡Factores de seguridad de JVM configurados correctamente!",
            "&7[ &a&l. . .&r &7] &eConfigurando la función de mundo vacío",
            "&7[ &a&l✔&r &7] &a¡Función de mundo vacío configurada correctamente!",
            "&7[ &a&l. . .&r &7] &eConfigurando el registro de autenticación",
            "&7[ &a&l✔&r &7] &a¡Registro de autenticación configurado correctamente!",
            "login_world",
            "¡ADVERTENCIA! Error al cargar la configuración personalizada",
            "&7[ &a&l. . .&r &7] &eConfigurando el filtro de registro seguro",
            "&7[ &a&l✔&r &7] &a¡Filtro de registro seguro configurado correctamente!",
            "&7[ &a&l. . .&r &7] &eConfigurando el gestor de autenticación",
            "&7[ &a&l✔&r &7] &a¡Gestor de autenticación configurado correctamente!",
            "&7[ &a&l. . .&r &7] &eConfigurando el gestor de expulsiones",
            "&7[ &a&l✔&r &7] &a¡Gestor de expulsiones configurado correctamente!",
            "&7[ &a&l. . .&r &7] &eConfigurando los oyentes personalizados",
            "&7[ &a&l✔&r &7] &a¡Oyentes personalizados configurados correctamente!",
            "&7[ &a&l. . .&r &7] &eConfigurando las configuraciones",
            "language",
            "¡ADVERTENCIA! Se eligió un idioma de configuración no válido",
            "¡Se utilizará config_english.yml hasta que se resuelva el problema!",
            "&7[ &a&l✔&r &7] &aConfiguración con idioma cargado",
            "&7[ &a&l. . .&r &7] &eConfigurando la base de datos de autenticación",
            "¡ADVERTENCIA! Tipo de base de datos no válido en el archivo de configuración. No se puede cargar el complemento.",
            "Apagando el servidor . . .",
            "&7[ &a&l✔&r &7] &a¡Base de datos de autenticación configurada correctamente &7(&a",
            "&7)&e!",
            "&7[ &a&l. . .&r &7] &eConfigurando CAPTCHA",
            "&7[ &a&l✔&r &7] &a¡Funciones de CAPTCHA configuradas correctamente!",
    };


    public static String getLoadingString(int number, ConfigurationFiles configFile) {
        return switch (configFile) {
            case ENGLISH -> STRINGS_ENG[number];
            case SPANISH -> STRINGS_SPANISH[number];
            case FRENCH -> STRINGS_FRENCH[number];
            case HEBREW -> STRINGS_HEBREW[number];
            case ITALIAN -> STRINGS_ITALIAN[number];
            case RUSSIAN -> STRINGS_RUSSIAN[number];
            case FILIPINO -> STRINGS_FILIPINO[number];
            default -> null;
        };
    }

}
