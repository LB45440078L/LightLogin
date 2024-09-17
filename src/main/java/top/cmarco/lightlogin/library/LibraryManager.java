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

package top.cmarco.lightlogin.library;

import com.alessiodp.libby.BukkitLibraryManager;
import com.alessiodp.libby.Library;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;

public final class LibraryManager {

    private final LightLoginPlugin plugin;

    private BukkitLibraryManager bukkitLibraryManager = null;
    private Library hikariCP = null, bcpkix = null, bcutils = null, bcprov = null, postgreSQL = null, jakartaMail = null, jakartaApi = null, angus = null;

    public LibraryManager(@NotNull final LightLoginPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadLibraries() {
        if (this.hikariCP != null || this.bcpkix != null || this.bcutils != null || this.bcprov != null || this.bukkitLibraryManager != null || postgreSQL != null || angus != null) {
            return;
        }

        this.bukkitLibraryManager = new BukkitLibraryManager(this.plugin);
        this.bukkitLibraryManager.addMavenCentral();
        this.hikariCP = Library.builder()
                .groupId("com{}zaxxer")
                .artifactId("HikariCP")
                .version("5.1.0")
                .relocate("com{}zaxxer{}hikari","top{}cmarco{}lightlogin{}libs{}com{}zaxxer{}hikari")
                .build();
        this.bcpkix = Library.builder()
                .groupId("org{}bouncycastle")
                .artifactId("bcpkix-jdk18on")
                .version("1.77")
                .relocate("org{}bouncycastle", "top{}cmarco{}lightlogin{}libs{}org{}bouncycastle")
                .build();
        this.bcutils = Library.builder()
                .groupId("org{}bouncycastle")
                .artifactId("bcutil-jdk18on")
                .version("1.77")
                .relocate("org{}bouncycastle", "top{}cmarco{}lightlogin{}libs{}org{}bouncycastle")
                .build();
        this.bcprov = Library.builder()
                .groupId("org{}bouncycastle")
                .artifactId("bcprov-jdk18on")
                .version("1.77")
                .relocate("org{}bouncycastle", "top{}cmarco{}lightlogin{}libs{}org{}bouncycastle")
                .build();
        this.postgreSQL = Library.builder()
                .groupId("org{}postgresql")
                .artifactId("postgresql")
                .version("42.7.4")
                .relocate("org{}postgresql", "top{}cmarco{}lightlogin{}libs{}org{}postgresql")
                .build();

        this.angus = Library.builder()
                .groupId("org{}eclipse{}angus")
                .artifactId("angus-mail")
                .version("2.0.3")
                .relocate("org{}eclipse{}angus", "top{}cmarco{}lightlogin{}libs{}org{}eclipse{}angus")
                .build();

        this.jakartaApi = Library.builder()
                .groupId("jakarta{}activation")
                .artifactId("jakarta{}activation-api")
                .version("2.1.3")
                .relocate("jakarta{}activation", "top{}cmarco{}lightlogin{}libs{}jakarta{}activation")
                .build();

        this.jakartaMail = Library.builder()
                .groupId("jakarta{}mail")
                .artifactId("jakarta{}mail-api")
                .version("2.1.3")
                .relocate("jakarta{}mail","top{}cmarco{}lightlogin{}libs{}jakarta{}mail")
                .build();

        this.bukkitLibraryManager.loadLibraries(this.bcpkix, this.bcprov, this.bcutils, this.hikariCP, this.postgreSQL, this.jakartaApi, this.jakartaMail, this.angus);
    }
}
