package org.geysermc.geyser.util;

public class UoaUtils {
    public static class UoaResult {
        private String realSourceIp;
        private int realSourcePort;

        public UoaResult() {
            // 默认构造函数，JNI 代码中使用 NewObject 调用
        }

        public String getRealSourceIp() {
            return realSourceIp;
        }

        public void setRealSourceIp(String realSourceIp) {
            this.realSourceIp = realSourceIp;
        }

        public int getRealSourcePort() {
            return realSourcePort;
        }

        public void setRealSourcePort(int realSourcePort) {
            this.realSourcePort = realSourcePort;
        }
        public String toString() {
            return "realSourceIp:" + realSourceIp + ", realSourcePort:" + realSourcePort;
        }
    }
}