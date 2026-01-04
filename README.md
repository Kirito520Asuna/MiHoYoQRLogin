# 米游社二维码登录工具 - 项目文档

这是一个用于实现米游社APP二维码登录功能的Java工具类，具有反风控机制，能够获取登录Cookie。



## 项目结构

- **主程序**: [MiHoYoQRLogin.java](file://MiHoYoQRLogin\QRLogin\src\main\java\com\cloud_guest\MiHoYoQRLogin.java) - 核心二维码登录实现
- **配置文件**: [config.json](file://MiHoYoQRLogin\QRLogin\src\main\resources\config.json) - 可配置反风控参数
- **依赖管理**: Maven项目，使用[pom.xml](file://MiHoYoQRLogin\pom.xml)
- **构建工具**: Dockerfile用于容器化部署

## 功能特性

- **二维码生成**: 通过米游社API生成登录二维码
- **状态轮询**: 持续轮询二维码扫描和确认状态
- **Cookie获取**: 成功登录后获取并保存Cookie
- **反风控机制**: 模拟真实APP请求头，降低被封风险
- **配置化**: 支持外部配置反风控参数

### 2. 新建配置文件 config.json
与.jar同一文件夹
```json
{
  "appVersion": null,
  "clientType": null,
  "salt": null,
  "deviceModel": null,
  "deviceName": null
}
```
### 3. 运行
#### 1.windows exe 直接运行
前往 [release](https://github.com/Kirito520Asuna/MiHoYoQRLogin/releases) 下载 带windows的zip包解压运行.exe文件即可
#### 2.java
```shell
java -jar xxxx.jar
```
#### 3.部署docker
```shell
docker pull ghcr.io/kirito520asuna/mihoyoqrlogin:latest
docker run -d -p 8081:8081 -v /path/to/config.json:/app/config.json --name mihoyoqrlogin ghcr.io/kirito520asuna/mihoyoqrlogin:latest
```
```shell
# 在 docker-compose.yml 文件所在目录执行
docker-compose up -d
```

```yml
version: '3.8'

services:
  mihoyoqrlogin:
    image: ghcr.io/kirito520asuna/mihoyoqrlogin:latest
    container_name: mihoyoqrlogin
    ports:
      - "8080:8080"
    volumes:
      - /path/to/config.json:/app/config.json
      - /path/to/miyoushe_qr.png:/app/miyoushe_qr.png
      - /path/to/cookie.json:/app/cookie.json
      - /path/to/:/app/
    networks:
      - mihoyoqrlogin-network
    restart: unless-stopped
networks:
  mihoyoqrlogin-network:
    driver: bridge

```

## 技术栈

- **HTTP客户端**: OkHttp3
- **JSON处理**: Gson
- **二维码生成**: ZXing
- **工具库**: Hutool
- **日志**: SLF4J
- **构建**: Maven + Spring Boot

## 核心参数

| 参数                                                                                                             | 默认值 | 说明 |
|----------------------------------------------------------------------------------------------------------------|--------|------|
| [APP_VERSION](file:///./QRLogin/src/main/java/com/cloud_guest/MiHoYoQRLogin.java#L41-L41)                      | 2.70.1 | 米游社版本号 |
| [CLIENT_TYPE](file:///./QRLogin/src/main/java/com/cloud_guest/MiHoYoQRLogin.java#L42-L42)                      | 5 | 客户端类型(5=Android APP) |
| [SALT](file:///./QRLogin/src/main/java/com/cloud_guest/MiHoYoQRLogin.java#L43-L43)         | xV8v4Qu54lUKrEYFZkJhB8cuOh9Asafs | 米游社DS算法盐值 |
| [DEVICE_MODEL](file:///./QRLogin/src/main/java/com/cloud_guest/MiHoYoQRLogin.java#L44-L44) | Pixel 7 | 设备型号 |
| [DEVICE_NAME](file:///./QRLogin/src/main/java/com/cloud_guest/MiHoYoQRLogin.java#L45-L45)  | Google Pixel 7 | 设备名称 |

## API接口

- **二维码获取**: `https://hk4e-sdk.mihoyo.com/hk4e_cn/combo/panda/qrcode/fetch`
- **二维码状态查询**: `https://hk4e-sdk.mihoyo.com/hk4e_cn/combo/panda/qrcode/query`

## 使用方法

1. 运行程序后会生成二维码图片 `miyoushe_qr.png`
2. 使用米游社APP扫描二维码
3. 在APP中确认登录
4. 程序获取到Cookie后自动保存到 `cookie.json`

## 配置文件

项目根目录下的 [config.json]() 可配置反风控参数：

```json
{
  "appVersion": "2.70.1",
  "clientType": "5", 
  "salt": "xV8v4Qu54lUKrEYFZkJhB8cuOh9Asafs",
  "deviceModel": "Pixel 7",
  "deviceName": "Google Pixel 7"
}
```


## 构建与部署

### Maven构建
```bash
mvn clean package
```


### Docker部署
```dockerfile
FROM openjdk:8u342-jre
VOLUME /tmp
COPY *.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
```


## 注意事项

- 该工具模拟真实APP请求，需要保持与米游社APP一致的请求参数
- 如遇到风控，建议更换网络环境或调整反风控参数
- 程序会自动处理DS算法生成和请求头构建
- 二维码有效期有限，超时后需重新生成

## 依赖库

- `okhttp:4.12.0` - HTTP请求处理
- `gson:2.10.1` - JSON序列化/反序列化
- `zxing:3.5.3` - 二维码生成和解析
- `hutool:5.8.18` - 通用工具库
- `lombok:1.18.26` - 简化代码
- `slf4j:1.7.0` - 日志记录