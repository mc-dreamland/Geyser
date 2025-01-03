package org.geysermc.geyser.util;

import io.netty.channel.Channel;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.UnixChannel;
import org.cloudburstmc.netty.channel.raknet.RakServerChannel;

import java.lang.reflect.Field;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
import java.net.InetAddress;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.HashMap;

public class UdpRealIp {
    /*
     * BIO 模式下获取真实客户端IP
     */
    public static String getRealIp(DatagramSocket socket, String clientAddress, int clientPort) {

        try {
            // 获取服务器端信息
            InetAddress serverAddress = socket.getLocalAddress();
            int serverPort = socket.getLocalPort();

            // 获取 DatagramSocket 的私有 'impl' 字段，该字段是 DatagramSocketImpl 的一个实例
            Field implField = DatagramSocket.class.getDeclaredField("impl");
            implField.setAccessible(true);
            DatagramSocketImpl impl = (DatagramSocketImpl)implField.get(socket);
            // 打印实现类以确认实际使用的类
            System.out.println("Actual implementation class: " + impl.getClass());

            // 尝试从父类获取 'fd' 字段
            Field fdField = null;
            Class<?> clazz = impl.getClass();
            while (clazz != null) {
                try {
                    fdField = clazz.getDeclaredField("fd");
                    fdField.setAccessible(true);
                    break;
                } catch (NoSuchFieldException e) {
                    // 如果当前类没有该字段，则继续在父类中查找
                    clazz = clazz.getSuperclass();
                }
            }

            if (fdField != null) {
                FileDescriptor fdes = (FileDescriptor) fdField.get(impl);
                System.out.println("FileDescriptor: " + fdes);

                // 获取 FileDescriptor 的私有 'fd' 字段的值
                Field fdValueField = FileDescriptor.class.getDeclaredField("fd");
                fdValueField.setAccessible(true);
                int fd = fdValueField.getInt(fdes);

                Uoa uoa = new Uoa();
                UoaUtils.UoaResult result = uoa.getsockoptNative(fd,clientAddress,clientPort,serverAddress.getHostAddress(),serverPort);
                System.out.println("Received packet from IP: " + result);
                if (result != null) {
                    return result.getRealSourceIp();
                }
                return null;
            } else {
                throw new RuntimeException("Could not find 'fd' field in class hierarchy");
            }
        } catch (Exception e) {
            throw new RuntimeException("get RealIp failure", e);
        }
    }


    /**
     *
     * NIO 场景下获取真实客户端IP
     */
    public static String getRealIp(DatagramChannel channel, String clientAddress, int clientPort) {

        try {
            // 获取服务器端信息
            DatagramSocket socket = channel.socket();
            InetAddress serverAddress = socket.getLocalAddress();
            int serverPort = socket.getLocalPort();

            Field fdField = channel.getClass().getDeclaredField("fd");
            fdField.setAccessible(true);
            FileDescriptor fdes = (FileDescriptor) fdField.get(channel);

            // 通过反射获取 FileDescriptor 的私有 'fd' 字段
            Field fdValueField = FileDescriptor.class.getDeclaredField("fd");
            fdValueField.setAccessible(true);
            int fd = fdValueField.getInt(fdes);

            Uoa uoa = new Uoa();
            UoaUtils.UoaResult result = uoa.getsockoptNative(fd,clientAddress,clientPort,serverAddress.getHostAddress(),serverPort);
            System.out.println("Received packet from IP: " + result);
            if (result != null) {
                return result.getRealSourceIp();
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     *
     * NIO 场景下获取真实客户端IP
     */

    public static HashMap<String, String> IP_PORT_WITH_RealIP = new HashMap<>();
    public static void getRealIp(Channel nettyChannel, String clientAddress, int clientPort) {
        String key = clientAddress + ":" + clientPort;
        if (IP_PORT_WITH_RealIP.containsKey(key)) {
            return;
        }
        try {
            int fd = getFDFromRakServerChannel(nettyChannel);
            // 使用 JNI 获取真实 IP
            Uoa uoa = new Uoa();
            UoaUtils.UoaResult result = uoa.getsockoptNative(fd, clientAddress, clientPort, "7.33.128.43", 19133);

            if (result != null) {
                result.getRealSourceIp();
                IP_PORT_WITH_RealIP.put(key, result.getRealSourceIp());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getFDFromRakServerChannel(Channel rakServerChannel) throws Exception {
        Field channelField = RakServerChannel.class.getSuperclass().getDeclaredField("channel");
        channelField.setAccessible(true);
        Object channel = channelField.get(rakServerChannel);

        UnixChannel epollDatagramChannel = (UnixChannel) channel;

        int i = epollDatagramChannel.fd().intValue();
        System.out.println("get fd: " + i);
        return i;
    }
}