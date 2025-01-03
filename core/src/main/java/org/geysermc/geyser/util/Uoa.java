package org.geysermc.geyser.util;

public class Uoa {
    static {
        System.loadLibrary("Uoa");
    }

    public native UoaUtils.UoaResult getsockoptNative(int fd, String clientIp, int clientPort, String serverIp, int serverPort);
}