// 记得改 package 名称
package com.yourname.meowauth;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

public class MeowAuth {

    private final JavaPlugin plugin;
    private final String authServerUrl;
    private String verifiedIP = null;
    private int ipMismatchCount = 0;

    // ------- 配置项开始 -------

    private static final long IP_CHECK_INTERVAL = 5 * 60 * 20; // IP 检查间隔, 默认 5 分钟一次（以 20 tick 每秒为单位）

    private static final boolean CLEAR_MISMATCH_COUNT = false; // 是否在 IP 验证成功时清除当前的 IP 验证失败次数

    private static final int MAX_MISMATCH_COUNT = 5; // IP 验证失败次数阈值

    // 获取本机 IP 的接口, 自带两个稳定的国内外服务, 可以用来判断服务端是否存在代理
    private static final String[] IP_CHECK_SERVICES = {
        "https://api.ip.sb/ip",
        "https://4.ipw.cn"
    };

    // ------- 配置项结束 -------

    /**
     * 构造函数
     * @param plugin 插件实例
     * @param authServerUrl 验证服务器地址
     */
    public MeowAuth(JavaPlugin plugin, String authServerUrl) {
        this.plugin = plugin;
        this.authServerUrl = authServerUrl.endsWith("/") ? authServerUrl.substring(0, authServerUrl.length() - 1) : authServerUrl;
    }

    /**
     * 启动反盗版验证
     */
    public void startVerification() {
        // 异步执行初始验证
        new BukkitRunnable() {
            @Override
            public void run() {
                boolean result = verifyServer();
                if (result) {
                    // 验证成功, 开始定期检查
                    startIpCheckTask();
                } else {
                    // 验证失败
                    plugin.getServer().shutdown();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * 向验证服务器发送请求，获取验证 IP
     * @return boolean 验证结果
     */
    private boolean verifyServer() {
        try {
            // 获取服务器端口号
            int port = plugin.getServer().getPort();

            // 构造请求 URL
            String url = authServerUrl + "?port=" + port;

            // 发送 HTTPS 请求
            HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // 读取响应
            int responseCode = connection.getResponseCode();
            BufferedReader reader;
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            // 读取响应内容
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            JSONObject json = new JSONObject(response.toString());

            // 判断逻辑
            if (responseCode == 200) {
                // 验证成功
                verifiedIP = json.getString("requestIP");
                return true;
            } else {
                // 验证失败
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 启动定时 IP 检查任务
     */
    private void startIpCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!checkIpMatch()) {
                    // IP 不匹配
                    ipMismatchCount++;
                    if (ipMismatchCount >= MAX_MISMATCH_COUNT) {
                        // 达到阈值, 重新请求联网验证
                        boolean result = verifyServer();
                        if (!result) {
                            // 验证失败
                            plugin.getServer().shutdown();
                            cancel();
                        } else {
                            // 验证成功, 清除失败次数
                            ipMismatchCount = 0;
                        }
                    }
                } else if (CLEAR_MISMATCH_COUNT) {
                    // IP 匹配, 清除失败次数
                    ipMismatchCount = 0;
                }
            }
        }.runTaskTimerAsynchronously(plugin, IP_CHECK_INTERVAL, IP_CHECK_INTERVAL);
    }

    /**
     * 检查本机 IP 是否与启动时验证的 IP 匹配
     */
    private boolean checkIpMatch() {
        try {
            boolean unmatched = true;
            for (String service : IP_CHECK_SERVICES) {
                String localIP = queryLocalIp(service);
                if (localIP != null && !localIP.equals(verifiedIP)) {
                    // 服务器 IP 能变的也是神人了
                    unmatched = false;
                }
            }
            return unmatched;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 查询本机 IP
     */
    private String queryLocalIp(String serviceUrl) {
        try {
            HttpsURLConnection connection = (HttpsURLConnection) new URL(serviceUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String ip = reader.readLine().trim();
                reader.close();
                return ip;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}