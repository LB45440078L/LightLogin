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

package top.cmarco.lightlogin.network;

import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class NetworkUtilities {

    private NetworkUtilities() {
        throw new RuntimeException("Cannot instantiate utility class.");
    }

    public static long convertInetSocketAddressToLong(@NotNull final InetSocketAddress socketAddress) {
        final InetAddress inetAddress = socketAddress.getAddress();
        final byte[] ipAddressBytes = inetAddress.getAddress();

        long ipv4Value = 0;
        for (final byte b : ipAddressBytes) {
            ipv4Value = (ipv4Value << 8) | (b & 0xFFL);
        }

        return ipv4Value;
    }

    public static String convertLongToInetSocketAddress(final long ipv4Value) {
        StringBuilder ipAddress = new StringBuilder();
        for (int i = 3; i >= 0; i--) {
            final long octet = (ipv4Value >> (i * 8)) & 0xFF;
            ipAddress.append(octet);
            if (i > 0) {
                ipAddress.append('.');
            }
        }
        return ipAddress.toString();
    }
    
}
