package org.geysermc.geyser.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class PortToFD {

    /**
     * 根据端口号查询进程的文件描述符信息，并返回 FD 列表。
     *
     * @param port 目标端口号
     * @return 包含文件描述符 (FD) 的整型数组，或者空数组如果未找到匹配结果
     */
    public static List<Integer> getFDsByPort(int port) {
        String command = "lsof -i :" + port; // 使用 lsof 命令查询端口
        List<Integer> fdList = new ArrayList<>();
        try {
            // 执行命令
            Process process = Runtime.getRuntime().exec(command);

            // 读取命令输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                // 检查是否是有效行（忽略标题行）
                if (line.startsWith("COMMAND")) {
                    continue;
                }

                // 解析有效的行
                String[] parts = line.split("\\s+");
                if (parts.length > 8) { // 确保行结构正确
                    System.out.println("parts -> " + parts);
                    String fdString = parts[3]; // 获取 FD 字段
                    if (fdString.endsWith("u")) { // 只解析正常的 FD
                        try {
                            int fd = Integer.parseInt(fdString.replace("u", "")); // 去掉 "u" 并转换为整数
                            fdList.add(fd);
                        } catch (NumberFormatException e) {
                            // 如果转换失败，忽略该条记录
                        }
                    }
                }
            }

            reader.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        // 将 List 转换为 int[]
        return fdList;
    }
}
