# mcloader-injector

[English](README.md) | 简体中文

本项目提供一个Java Agent，将 Minecraft 的各种 modloader 的 installer 的 HTTP(S) 下载地址按参数中的前缀转发表改写。

目前支持

- [x] Forge
- [x] Neoforge
- [x] Fabric

## 使用

一个 `-Dmcloader.injector.rule.xxx` 参数指定一条规则，后缀名称可以任意指定：

```sh
java \
  '-Dmcloader.injector.rule.forge=https://maven.minecraftforge.net/=>https://bmclapi2.bangbang93.com/maven/' \
  '-Dmcloader.injector.rule.fabric=https://maven.fabricmc.net/=>https://bmclapi2.bangbang93.com/maven/' \
  -javaagent:/path/mcloader-injector-agent.jar \
  -jar installer.jar --installServer
```

安装器命令和选项仍使用各自原有用法，示例中的 `--installServer` 不是 Fabric 的选项。

也可以一次传整个表：

```sh
TABLE='https://origin.example/a/=>https://mirror.example/b/|https://other.example/=>https://mirror.example/other/'
java "-Dmcloader.injector.table=$TABLE" -javaagent:/path/mcloader-injector-agent.jar -jar installer.jar
# 或 agent 参数
java "-javaagent:/path/mcloader-injector-agent.jar=$TABLE" -jar installer.jar
```

- 会优先合并 `mcloader.injector.table`、`mcloader.injector.rule` 和 `mcloader.injector.rule.*`；没有这些属性时才使用 agent 参数。
- 对于 Table，每个值用 `from=>to` 表示规则，多个规则用 `|` 分隔；整个参数加引号。

## 构建

构建需要 JDK 17 或更新版本；生成的 agent 和 bridge 使用 Java 8 字节码/API。

```sh
./gradlew build
```

产物：`agent/build/libs/mcloader-injector-agent.jar`。bridge 和隔离后的 ASM 已包含其中，不需额外 classpath。

## 日志

日志只写 stderr，未引入额外日志库：

```sh
java -Dmcloader.injector.log=debug \
  '-Dmcloader.injector.rule.forge=https://maven.minecraftforge.net/=>https://bmclapi2.bangbang93.com/maven/' \
  -javaagent:/path/mcloader-injector-agent.jar -jar installer.jar --installServer
```

| 值 | 内容 |
| --- | --- |
| `error`（默认） | 初始化和字节码转换失败 |
| `info` | 加上初始化完成和规则数量 |
| `debug` | 再加上已改写的类/调用点数、实际 URL 改写 |

- URL 日志只显示协议、host、port 和 path，隐藏 userinfo、query、fragment；路径本身不做隐私识别。
- 不记录未匹配请求或恒等规则；关闭 debug 时不拼接调试消息。

## 原理

替换应用字节码里的 `URL.openConnection()`、`URL.openConnection(Proxy)` 和 `URL.openStream()` 调用。保留原 Proxy 参数、标准连接类型与异常。
