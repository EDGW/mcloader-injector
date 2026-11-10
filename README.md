# mcloader-injector

English | [简体中文](README-zh.md)

This project provides a Java agent that rewrites HTTP(S) download URLs used by Minecraft modloader installers according to a prefix forwarding table supplied through parameters.

Currently supported:

- [x] Forge
- [x] NeoForge
- [x] Fabric

## Usage

Each `-Dmcloader.injector.rule.xxx` parameter specifies one rule. The suffix can be any name:

```sh
java \
  '-Dmcloader.injector.rule.forge=https://maven.minecraftforge.net/=>https://bmclapi2.bangbang93.com/maven/' \
  '-Dmcloader.injector.rule.fabric=https://maven.fabricmc.net/=>https://bmclapi2.bangbang93.com/maven/' \
  -javaagent:/path/mcloader-injector-agent.jar \
  -jar installer.jar --installServer
```

Use the installer's own commands and options. The `--installServer` option shown above is not a Fabric installer option.

Alternatively, supply the entire table:

```sh
TABLE='https://origin.example/a/=>https://mirror.example/b/|https://other.example/=>https://mirror.example/other/'
java "-Dmcloader.injector.table=$TABLE" -javaagent:/path/mcloader-injector-agent.jar -jar installer.jar
# Or use agent arguments
java "-javaagent:/path/mcloader-injector-agent.jar=$TABLE" -jar installer.jar
```

- The agent first merges `mcloader.injector.table`, `mcloader.injector.rule`, and `mcloader.injector.rule.*`. Agent arguments are used only when none of these properties are present.
- In a table, each rule is written as `from=>to`. Separate multiple rules with `|` and quote the entire argument.

## Build

Building requires JDK 17 or newer. The generated agent and bridge target Java 8 bytecode and APIs.

```sh
./gradlew build
```

Output: `agent/build/libs/mcloader-injector-agent.jar`. The bridge and relocated ASM are bundled; no extra classpath is required.

## Logging

Logs are written exclusively to stderr. No additional logging library is used:

```sh
java -Dmcloader.injector.log=debug \
  '-Dmcloader.injector.rule.forge=https://maven.minecraftforge.net/=>https://bmclapi2.bangbang93.com/maven/' \
  -javaagent:/path/mcloader-injector-agent.jar -jar installer.jar --installServer
```

| Value | Output |
| --- | --- |
| `error` (default) | Initialization and bytecode transformation failures |
| `info` | Also reports successful initialization and the number of rules |
| `debug` | Also reports rewritten classes/call-site counts and actual URL rewrites |

- URL logs include only the scheme, host, port, and path. User information, query strings, and fragments are omitted. Paths themselves are not inspected for sensitive information.
- Unmatched requests and identity mappings are not logged. Debug messages are not constructed when debug logging is disabled.

## How it works

The agent replaces calls to `URL.openConnection()`, `URL.openConnection(Proxy)`, and `URL.openStream()` in application bytecode. Original proxy arguments, standard connection types, and exceptions are preserved.
