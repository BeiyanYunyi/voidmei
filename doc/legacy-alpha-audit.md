# 旧独立 HUD 透明度属性核对

2026-09-12，基于当前仓库 Java 源码。范围为“飞行信息”“地平仪”“舵面值”“起落襟翼”四类面板。

`ConfigLoader.GroupConfig.alpha` 从面板 `:alpha` 读取，保存时写回同名属性。但在这四类窗口的实际绘制路径中，未发现对这个属性的读取。它不能直接等同于 KMP 的 `backgroundAlpha`、`contentAlpha` 或整个原生窗口的不透明度。

源码链路：

- [ConfigLoader.java](../src/prog/config/ConfigLoader.java)：`GroupConfig.alpha`、`:alpha` 的读取与保存。
- [FlightInfoOverlay.java](../src/ui/overlay/FlightInfoOverlay.java) → [FieldOverlay.java](../src/ui/base/FieldOverlay.java) → [DraggableOverlay.java](../src/ui/base/DraggableOverlay.java)：飞行窗口使用透明面板和独立文本颜色。
- [AttitudeOverlay.java](../src/ui/overlay/AttitudeOverlay.java)、[ControlSurfacesOverlay.java](../src/ui/overlay/ControlSurfacesOverlay.java)、[GearFlapsOverlay.java](../src/ui/overlay/GearFlapsOverlay.java)：均沿用 `DraggableOverlay`，没有读取面板 `alpha`。
- [OverlayStyleHelper.java](../src/ui/util/OverlayStyleHelper.java)：原生每像素透明和预览背景分别处理，没有使用 `GroupConfig.alpha`。
- [PropertyBinder.java](../src/prog/util/PropertyBinder.java) 及设置行渲染器可以反射读写面板属性；这属于设置界面的读写，不构成上述窗口的绘制消费者。

另有 [BaseOverlay.java](../src/ui/overlay/BaseOverlay.java) 使用自己的 `alpha` 字段绘制背景。上述四类窗口不继承此类，不能用这条路径证明面板 `:alpha` 已生效。结论也不扩展到全部 Java 窗口或未检查的其他版本。

KMP 已有独立背景、内容和边框透明度。导入器现在将四类面板明确保存的 `:alpha` 列入未迁移报告，提示用户在分区设置中分别调整；不修改任何透明度，也不阻塞文件中其他已支持设置。仅含该属性的文件可以预览原因，应用按钮保持禁用。

本轮清点比上一轮增加四条报告，是补全原先静默忽略的属性，不表示已有功能被移除。报告标识可能是旧 `target` 或面板属性名，不能直接作为缺失功能数量。
