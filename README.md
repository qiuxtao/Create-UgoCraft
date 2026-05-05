# Create-UgoCraft

一个基于 [Create (机械动力)](https://github.com/Creators-of-Create/Create) 的 Minecraft 结构移动附属模组，适用于 **Forge (1.20.1)** 和 **NeoForge (1.21.1)**。

## 简介

Create-UgoCraft 为 Minecraft 添加了简单易用的**结构滑动**和**结构旋转**功能。通过放置核心方块和标记器，你可以让任意结构沿直线滑动或绕轴旋转——一切都借助 Create 的 Contraption 引擎实现丝滑的运动效果。

## 方块一览

| 方块 | 功能 |
|------|------|
| **滑动方块** | 接收红石信号后，将连接的结构沿指定方向直线滑动 |
| **转轴方块** | 接收红石信号后，将连接的结构绕轴持续旋转 |
| **标记器（+）** | 标记通电时结构的目标位置 |
| **标记器（-）** | 标记断电时结构的目标位置 |

## 使用方法

### 滑动结构

1. 放置**滑动方块**，正面朝向你要移动的结构
2. 在结构上放置**标记器（+）**和**标记器（-）**，分别标记通电/断电时的停靠位置
3. 给滑动方块供电，结构就会自动滑动到对应标记器的位置

### 旋转结构

1. 放置**转轴方块**，正面朝向你要旋转的结构
2. 给转轴方块供电，结构会持续旋转
3. 断电后，结构会自动回正到最近的 90° 整数角度并停止

## 配置

模组提供两个配置文件，位于游戏的 `config/` 目录下：

### 速度配置 (`create_ugocraft-common.toml`)

使用与 Create 一致的 RPM 体系：

```toml
[speed]
# 滑动方块速度（RPM），默认 96
slideSpeedRPM = 96
# 转轴方块速度（RPM），默认 16
rotationSpeedRPM = 16
```

### 方块黑名单 (`create_ugocraft-blocks.json`)

配置哪些方块不会被结构捕获（如基岩、泥土等）。

## 依赖与支持版本

### Minecraft 1.21.1 (NeoForge)
- **NeoForge** 21.1.228+
- **Create** 6.0.10-280+ (NeoForge 版本)

### Minecraft 1.20.1 (Forge)
- **Forge** 47.1.3+
- **Create** 0.5.1f+（Forge 版本）

## 构建

```bash
./gradlew build
```

构建产物位于 `build/libs/` 目录下。

## 许可证

MIT License
