package org.geysermc.geyser.util;

import java.net.DatagramSocket;
public class Uoa {
    static {
        try {
            // 加载 libUoa.so 文件
            System.loadLibrary("Uoa"); // 不需要带前缀 `lib` 和后缀 `.so`
            System.out.println("成功加载Uoa lib");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load native library libUoa.so", e);
        }
    }
    /**
     * 本地方法声明，通过指定的四元组获取当前报文的真实客户端Ip和Port
     * @param fd socket fd
     * @param clientIp 从报文获取的初始源Ip
     * @param clientPort 从报文获取的初始源Port
     * @param serverIp 从报文获取的初始目的Ip
     * @param serverPort 从报文获取的初始目的Port
     * @return
     */
    public native UoaUtils.UoaResult getsockoptNative(int fd, String clientIp, int clientPort, String serverIp, int serverPort);
}