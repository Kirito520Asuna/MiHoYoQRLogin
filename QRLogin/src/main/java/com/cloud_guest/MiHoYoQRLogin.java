package com.cloud_guest;

import cn.hutool.core.io.FileUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 米游社二维码登录反风控工具类
 * 用于实现米游社APP的二维码登录功能，并包含反风控机制
 */
@Slf4j
public class MiHoYoQRLogin {
    // 创建OkHttpClient实例，设置连接超时和读取超时
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final Gson gson = new Gson();

    // 最新URL（2026年确认仍有效）
    private static String QR_FETCH = "https://hk4e-sdk.mihoyo.com/hk4e_cn/combo/panda/qrcode/fetch";
    private static String QR_QUERY = "https://hk4e-sdk.mihoyo.com/hk4e_cn/combo/panda/qrcode/query";
    private static  String TOKEN_GAME_COOKIE_API ="https://api-takumi.mihoyo.com/auth/api/getCookieAccountInfoByGameToken";
    // 反风控参数（可根据最新米游社APP抓包更新）
    private static String APP_VERSION = "2.70.1";     // 最新米游社版本
    private static String APP_ID = "1";     // 米游社版本
    private static String CLIENT_TYPE = "5";          // 5=Android APP
    private static String SALT = "xV8v4Qu54lUKrEYFZkJhB8cuOh9Asafs"; // 常用web/mobile盐
    private static String DEVICE_MODEL = "Pixel 7";   // 模拟常见Android机型
    private static String DEVICE_NAME = "Google Pixel 7";
    private static String MIYOUSHE_QR = "miyoushe_qr.png";
    private static String COOKIE_JSON = "cookie.json";
    private static String CONFIG_JSON = "config.json";

    //@Slf4j
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AppConfig {
        // 反风控参数
        private String appVersion = APP_VERSION;
        private String appId = APP_ID;
        private String clientType = CLIENT_TYPE;
        private String salt = SALT;
        private String deviceModel = DEVICE_MODEL;
        private String deviceName = DEVICE_NAME;
    }

    public static AppConfig appConfig = new AppConfig();

    public static void init() {
        // 读取配置文件
        Map<String, String> configMap = null;
        try {
            configMap = readFromJson(CONFIG_JSON);
        } catch (Exception e) {
            log.info("未找到配置文件，使用默认配置");
        }
        if (configMap != null) {
            String appVersion = configMap.get("app_version");
            String clientType = configMap.get("client_type");
            String salt = configMap.get("salt");
            String deviceModel = configMap.get("device_model");
            String deviceName = configMap.get("device_name");


            if (appVersion != null) {
                appConfig.setAppVersion(appVersion);
            }
            if (clientType != null) {
                appConfig.setClientType(clientType);
            }
            if (salt != null) {
                appConfig.setSalt(salt);
            }
            if (deviceModel != null) {
                appConfig.setDeviceModel(deviceModel);
            }
            if (deviceName != null) {
                appConfig.setDeviceName(deviceName);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        init();
        String appId = appConfig.appId; // 米游社
        String deviceId = UUID.randomUUID().toString().replace("-", "").toUpperCase(); // 大写UUID

        String ticket = generateQRCode(appId, deviceId);
        if (ticket == null) return;

        while (true) {
            String cookie = pollQRStatus(appId, deviceId, ticket);
            if (cookie != null) {
                System.out.println("登录成功！获取到的Cookie：\n" + cookie);
                break;
            }
            Thread.sleep(5000); // 5秒轮询，减少风控触发
        }
    }

    public static Map<String, String> readFromJson(String filePath) {
        try (FileReader reader = new FileReader(filePath)) {
            // 定义 Map<String, String> 类型
            Type type = new TypeToken<Map<String, String>>() {
            }.getType();
            Map<String, String> cookieMap = gson.fromJson(reader, type);

            System.out.println("成功读取 JSON 文件：");
            cookieMap.forEach((k, v) -> System.out.println(k + " = " + v));

            return cookieMap;
        } catch (IOException e) {
            System.err.println("读取 JSON 文件失败：" + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 推荐方式：使用 Gson 把对象转为美化 JSON 并写入文件
     */
    private static void writeWithGson(Object obj, String filePath) {
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(obj, writer);
            System.out.println("JSON 已成功写入文件：" + filePath);
        } catch (IOException e) {
            System.err.println("写入文件失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    // 动态生成DS（米哈游标准算法）
    private static String getDS(String body) {
        long t = System.currentTimeMillis() / 1000;
        String r = String.valueOf((int) (Math.random() * 100000) + 100000); // 6位随机数
        String q = ""; // query为空
        String check = "salt=" + SALT + "&t=" + t + "&r=" + r + "&b=" + body + "&q=" + q;
        return t + "," + r + "," + md5(check);
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // 通用Header构建
    private static Headers getCommonHeaders(String deviceId, String body) {
        return new Headers.Builder()
                .add("User-Agent", "Mozilla/5.0 (Linux; Android 13; " + appConfig.deviceModel + " Build/TQ3A.230805.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.128 Mobile Safari/537.36 miHoYoBBS/" + appConfig.appVersion)
                .add("x-rpc-app_version", appConfig.appVersion)
                .add("x-rpc-client_type", appConfig.clientType)
                .add("x-rpc-device_id", deviceId)
                .add("x-rpc-device_model", appConfig.deviceModel)
                .add("x-rpc-device_name", appConfig.deviceName)
                .add("DS", getDS(body))
                .add("Referer", "https://app.mihoyo.com")
                .add("Origin", "https://app.mihoyo.com")
                .add("Content-Type", "application/json")
                .build();
    }

    @Nullable
    private static String pollQRStatus(String appId, String deviceId, String ticket) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("app_id", appId);
        params.put("ticket", ticket);
        params.put("device", deviceId);

        String jsonBody = gson.toJson(params);
        Request request = new Request.Builder()
                .url(QR_QUERY)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                .headers(getCommonHeaders(deviceId, jsonBody))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String respStr = response.body().string();
            System.out.println("轮询响应: " + respStr); // 调试用，便于看retcode

            Map<String, Object> resp = gson.fromJson(respStr, Map.class);
            int retcode = ((Double) resp.get("retcode")).intValue();
            String message = (String) resp.get("message");

            if (retcode != 0) {
                System.out.println("轮询失败: " + message);
                if (message.contains("风险") || message.contains("risk")) {
                    System.out.println("触发风控！建议换本地网络重试，或改用手动获取Cookie");
                }
                if (retcode == -311 || message.toLowerCase().contains("expired")) {
                    System.out.println("二维码已过期");
                    return null;
                }
                return null;
            }

            Map<String, Object> data = (Map<String, Object>) resp.get("data");
            String stat = (String) data.get("stat");

            switch (stat) {
                case "Init":
                    System.out.println("请用米游社APP扫描二维码");
                    break;
                case "Scanned":
                    System.out.println("已扫描，请在APP中确认登录");
                    break;
                case "Confirmed":
                    Map<String, Object> payload = (Map<String, Object>) data.get("payload");
                    String rawJson = (String) payload.get("raw");

                    Map<String, String> raw = gson.fromJson(rawJson, Map.class);
                    String uid = raw.get("uid");
                    String token = raw.get("token");
                    //getMultiToken(ticket, uid);
                    String cookieToken = getCookieAccountInfoByGameToken(token, uid);
                    String cookie = String.format("ltoken=%s;ltuid=%s;cookie_token=%s; account_id=%s;",token, uid, cookieToken,uid);
                    raw.put("cookie_token", cookieToken);
                    raw.put("cookie", cookie);
                    writeWithGson(raw, COOKIE_JSON);

                    FileUtil.del(MIYOUSHE_QR);
                    return cookie;
                default:
                    System.out.println("未知状态: " + stat);
            }
        }
        return null;
    }

    @Nullable
    private static String generateQRCode(String appId, String deviceId) throws IOException, WriterException {
        Map<String, String> params = new HashMap<>();
        params.put("app_id", appId);
        params.put("device", deviceId);

        String jsonBody = gson.toJson(params);
        Request request = new Request.Builder()
                .url(QR_FETCH)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                .headers(getCommonHeaders(deviceId, jsonBody))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("生成失败: HTTP " + response.code());
                return null;
            }

            String respStr = response.body().string();
            Map<String, Object> resp = gson.fromJson(respStr, Map.class);

            if (((Double) resp.get("retcode")).intValue() != 0) {
                System.out.println("生成失败: " + resp.get("message"));
                return null;
            }

            Map<String, Object> data = (Map<String, Object>) resp.get("data");
            String qrUrl = (String) data.get("url");

            // 提取ticket
            URL url = new URL(qrUrl);
            String query = url.getQuery();
            String ticket = null;
            for (String pair : query.split("&")) {
                if (pair.contains("ticket=")) {
                    ticket = pair.substring(pair.indexOf("ticket=") + 7);
                    break;
                }
            }

            if (ticket == null) return null;

            System.out.println("二维码URL: " + qrUrl);
            BitMatrix matrix = new MultiFormatWriter().encode(qrUrl, BarcodeFormat.QR_CODE, 300, 300);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ImageIO.write(image, MIYOUSHE_QR.substring(MIYOUSHE_QR.lastIndexOf(".") + 1), new File(MIYOUSHE_QR));
            System.out.println("二维码已保存，请用米游社APP扫码");
            // 小尺寸版用于控制台打印（推荐41x41，扫码率极高）
            // 控制台紧凑版：最小尺寸29（奇数，高纠错）
            int compactSize = 29;
            BitMatrix matrixSmall = new MultiFormatWriter().encode(qrUrl, BarcodeFormat.QR_CODE, compactSize, compactSize);
            printQRCode(matrixSmall);
            return ticket;
        }
    }

    public static String getCookieAccountInfoByGameToken(String gameToken, String account_id) throws IOException {
        String url = TOKEN_GAME_COOKIE_API;
        HttpUrl httpUrl = HttpUrl.parse(url).newBuilder()
                .addQueryParameter("game_token", gameToken)
                .addQueryParameter("account_id", account_id)
                .build();
        Request request = new Request.Builder()
                .url(httpUrl)
                .get()
                .addHeader("x-rpc-app_id", appConfig.appId)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败: " + response.code());
            }

            String respStr = response.body().string();
            JsonObject json = gson.fromJson(respStr, JsonObject.class);

            int retcode = json.get("retcode").getAsInt();
            if (retcode != 0) {
                String message = json.get("message").getAsString();
                throw new IOException("API错误: " + message);
            }

            JsonObject data = json.getAsJsonObject("data");
            String cookie_token = data.get("cookie_token").getAsString();
            String returnedUid = data.get("uid").getAsString();

            System.out.println("获取成功！stoken: " + cookie_token);
            System.out.println("对应UID: " + returnedUid);

            return cookie_token;
        }
    }
    // Java 8 兼容的字符串重复方法
    private static String repeat(String str, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    private static void printQRCode(BitMatrix matrix) {
        System.out.println("\n米游社扫码（高度砍半版）\n");

        int size = matrix.getWidth();

        // 上安静区（2行半块 ≈ 4模块）
        System.out.println(repeat("  ", size + 4));

        // 主体：每两行合并一行（高度直接砍半）
        for (int y = 0; y < size; y += 2) {
            StringBuilder line = new StringBuilder("  ");

            for (int x = 0; x < size; x++) {
                boolean top = !matrix.get(x, y); // 反转颜色
                boolean bottom = (y + 1 < size) && !matrix.get(x, y + 1);

                char c;
                if (top && bottom) c = '█';
                else if (top) c = '▀';
                else if (bottom) c = '▄';
                else c = ' ';

                line.append(c); // 只重复1次（紧凑不宽）
            }

            line.append("  ");
            System.out.println(line);
        }

        // 下安静区
        System.out.println(repeat("  ", size + 4));

        System.out.println("\n↑↑↑ 对准上方扫码（高度已砍半，一屏覆盖）");
        System.out.println("若显示乱码/扫不了，用 miyoushe_qr.png 图片扫！");
    }
}