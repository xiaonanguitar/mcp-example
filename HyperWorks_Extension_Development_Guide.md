# HyperWorks 2024 插件开发完整指南

> 基于 Altair HyperWorks 2024 官方 SDK 扩展插件示例 (Extension_Demo)

---

## 目录

1. [概述](#1-概述)
2. [插件目录结构](#2-插件目录结构)
3. [extension.xml — 插件注册清单](#3-extensionxml--插件注册清单)
4. [global-init.tcl — 全局初始化脚本](#4-global-inittcl--全局初始化脚本)
5. [客户端配置（HM / HV / HG）](#5-客户端配置hm--hv--hg)
6. [Ribbon 功能区配置](#6-ribbon-功能区配置)
7. [File Menu 文件菜单配置](#7-file-menu-文件菜单配置)
8. [Toolbar 工具栏配置](#8-toolbar-工具栏配置)
9. [Context 交互式上下文（向导式工作流）](#9-context-交互式上下文向导式工作流)
10. [Workflow Help 工作流帮助](#10-workflow-help-工作流帮助)
11. [HWC 命令行接口](#11-hwc-命令行接口)
12. [图标与资源](#12-图标与资源)
13. [部署与加载插件](#13-部署与加载插件)
14. [最佳实践与常见陷阱](#14-最佳实践与常见陷阱)

---

## 1. 概述

HyperWorks Desktop 提供了一套完整的插件 (Extension) 框架，允许开发者将自定义功能、菜单、工具栏和向导式工作流无缝集成到 HyperMesh、HyperView 和 HyperGraph 等客户端中。

插件通过 TCL/Tk 脚本语言驱动，结合 XML 声明式 UI 配置，可以添加：

| 能力 | 说明 |
|------|------|
| **Ribbon 页面和分组** | 在功能区加入自定义标签页、分组和按钮 |
| **File Menu 入口** | 在文件菜单中添加自定义菜单项 |
| **Toolbar 工具栏** | 在左侧或右侧添加垂直工具栏 |
| **Context 上下文** | 创建交互式向导流程（含实体选择器、微对话框） |
| **HWC 命令** | 通过命令行接口调用各客户端 API |
| **跨客户端共享** | 全局脚本在所有客户端中可用 |

---

## 2. 插件目录结构

```
Extension_Demo/
├── extension.xml                   # ★ 核心：插件注册清单
├── global-init.tcl                 # 全局初始化脚本（所有客户端共享）
├── nul                             # (可忽略)
├── images/                         # 图标资源目录
│   ├── save_to_file_ribbon.png
│   ├── save_to_clipboard_ribbon.png
│   ├── toolbarRunStrip-32.png
│   ├── ribbonRenameEntityStrip-80.png
│   └── ...（更多图标文件）
├── documentation/                  # 插件文档（HTML）
│   ├── ExtensionDemo.htm
│   └── ExtensionDemo.files/
├── hm/                             # ★ HyperMesh 客户端配置
│   ├── hm-init.tcl                 #   HM 初始化脚本
│   ├── hm-ribbon.xml               #   HM Ribbon 布局
│   ├── hm-filemenu.xml             #   HM 文件菜单
│   ├── xsim-copilot.tcl            #   HM 额外脚本
│   ├── toolbars/
│   │   └── toolbar.xml             #   HM 工具栏
│   └── contexts/                   #   HM 交互式上下文
│       ├── solidcentroid.xml       #     实体质心上下文 (UI 定义)
│       ├── solidcentroid.tcl       #     实体质心上下文 (逻辑实现)
│       ├── renameentity.xml
│       ├── renameentity.tcl
│       ├── copytranslate.xml
│       ├── copytranslate.tcl
│       ├── copyrotate.xml
│       ├── copyrotate.tcl
│       ├── coord3tocoord1.xml
│       ├── coord3tocoord1.tcl
│       └── workflowhelp.xml        #     工作流帮助文本
├── hv/                             # ★ HyperView 客户端配置
│   ├── hv-init.tcl
│   ├── hv-ribbon.xml
│   ├── hv-filemenu.xml
│   ├── HV_Animate_Demo_Session.mvw #   演示会话文件
│   └── toolbars/
│       └── toolbar.xml
└── hg/                             # ★ HyperGraph 客户端配置
    ├── hg-init.tcl
    ├── hg-ribbon.xml
    ├── hg-filemenu.xml
    ├── HG_Fit_Demo_Session.mvw     #   演示会话文件
    └── toolbars/
        └── toolbar.xml
```

---

## 3. extension.xml — 插件注册清单

这是插件的"身份证"。HyperWorks Desktop 启动时扫描此文件以发现并加载插件。

### 3.1 顶层元数据

```xml
<section name="Extension">
    <!-- 插件名称（内部标识） -->
    <entry name="name"              value="Extension Demo" />

    <!-- 显示名称（界面中显示） -->
    <entry name="displayName"       value="Extension Demo" />

    <!-- 资源目录（图标等） -->
    <entry name="resources"         value="images" />

    <!-- 最低产品版本要求 -->
    <entry name="minProductVersion" value="2022.2" />

    <!-- 插件版本 -->
    <entry name="version"           value="1.0" />

    <!-- 作者 -->
    <entry name="author"            value="Altair" />

    <!-- 描述 -->
    <entry name="description"       value="HyperWorks extension demo..." />

    <!-- 支持的客户端类型 -->
    <entry name="supportedClient"   value="HyperWorksDesktop" />

    <!-- 帮助文档路径 -->
    <entry name="documentation"     value="documentation/ExtensionDemo.htm" />

    <!-- ★ 全局初始化 TCL 脚本 -->
    <entry name="tclscript"         value="global-init.tcl" />
```

### 3.2 各字段详解

| 字段 | 必需 | 说明 |
|------|------|------|
| `name` | 是 | 内部唯一标识符，避免与系统和其他插件冲突 |
| `displayName` | 是 | 界面中显示的友好名称 |
| `resources` | 否 | 图标资源的相对路径。Ribbon/Filemenu 中的 `image` 属性相对于此目录 |
| `minProductVersion` | 否 | 最低支持的 HW 版本，如 `2022.2`。不兼容的版本不会加载该插件 |
| `version` | 否 | 插件自身版本号 |
| `author` | 否 | 作者信息 |
| `description` | 否 | 插件描述文本 |
| `supportedClient` | 是 | 固定为 `HyperWorksDesktop` |
| `documentation` | 否 | 帮助文档 HTML 文件的相对路径 |
| `tclscript` | 是 | 全局初始化脚本的**相对路径**（相对于 `extension.xml` 所在目录）。在所有客户端加载前执行 |

### 3.3 客户端配置 (Profile Section)

每个客户端通过一个 `<section name="profile">` 定义：

```xml
<section name="profile" value="HyperMesh">
    <entry name="ribbonxml"  value="hm/hm-ribbon.xml" />
    <entry name="tclscript"  value="hm/hm-init.tcl" />
    <entry name="toolbars"   value="hm/toolbars" />
    <entry name="contexts"   value="hm/contexts" />
    <entry name="filemenu"   value="hm/hm-filemenu.xml"/>
</section>
```

| 字段 | 说明 |
|------|------|
| `ribbonxml` | Ribbon 功能区定义 XML（对该客户端可见） |
| `tclscript` | 该客户端专用的初始化 TCL 脚本 |
| `toolbars` | 工具栏 XML 所在的**目录路径**（该目录下所有 `.xml` 均被加载） |
| `contexts` | 交互式上下文（context）XML 和 TCL 所在的**目录路径** |
| `filemenu` | 文件菜单自定义入口定义 XML |

支持的 `value` 值：
- `HyperMesh` — HyperMesh 有限元前处理
- `HyperView` — HyperView 后处理/动画
- `HyperGraph` — HyperGraph 2D/3D 绘图

---

## 4. global-init.tcl — 全局初始化脚本

全局脚本在**所有客户端启动前**执行一次。适合放置跨客户端共享的工具函数。

### 4.1 命名空间约定

```tcl
namespace eval ::ExtensionDemoGlobal {
    global env
    variable workDir [file normalize [[::hwp::GetSession] GetSystemVariable CURRENTWORKINGDIR]]
}
```

**建议**：为避免与系统和其他插件冲突，所有全局过程和变量应放在一个以插件名命名的命名空间中。

### 4.2 关键 API 调用

```tcl
# 获取当前工作目录
set workDir [[::hwp::GetSession] GetSystemVariable CURRENTWORKINGDIR]

# 截屏到文件
[::hwp::GetSession] CaptureScreen PNG "$imageFileWithPath"

# 截屏到剪贴板
[::hwp::GetSession] CaptureScreen CLIPBOARD dummyFileName.png
```

### 4.3 完整的 global-init.tcl 示例

```tcl
namespace eval ::ExtensionDemoGlobal {
    global env
    variable workDir [file normalize [[::hwp::GetSession] GetSystemVariable CURRENTWORKINGDIR]]
}

# 截屏到 PNG 文件
proc ::ExtensionDemoGlobal::CaptureToPNGFile {} {
    variable workDir
    set types {{{PNG Files} {.png}}}
    set imageFile "screenshot.png"
    set imageFileWithPath [tk_getSaveFile -title "Save Screenshot" \
        -initialdir $workDir -initialfile $imageFile -filetypes $types]
    [::hwp::GetSession] CaptureScreen PNG "$imageFileWithPath"
    if {[file exists $imageFileWithPath]} {
        tk_messageBox -message "PNG saved in:\n${imageFileWithPath}" -type ok
    }
}

# 截屏到剪贴板
proc ::ExtensionDemoGlobal::CaptureToClipboard {} {
    [::hwp::GetSession] CaptureScreen CLIPBOARD dummyFileName.png
    tk_messageBox -message "Screenshot saved in Clipboard!" -type ok
}
```

---

## 5. 客户端配置（HM / HV / HG）

### 5.1 HyperMesh (hm-init.tcl)

```tcl
namespace eval ::ExtensionDemoHM {
    variable scriptdir [file dirname [info script]]
}

# 将多个 Solid 拆分到独立 Component
proc ::ExtensionDemoHM::Solids2Comps {} {
    *createentitypanel comps 1 "Select component with multiple solids"
    set compId [hm_info lastselectedentity comps]
    *createmark solids 1 "by comp id" $compId
    set solidList [hm_getmark solids 1]
    *clearmark solids 1

    ::ExtensionDemoHM::Graphics 0    ;# 关闭图形刷新以加速

    foreach solidId $solidList {
        *createentity comps name="component_$solidId"
        *createmark solids 1 $solidId
        *movemark solids 1 "component_$solidId"
        *clearmark solids 1
    }

    ::ExtensionDemoHM::Graphics 1    ;# 恢复图形刷新
}

# 批量操作优化：暂停/恢复图形刷新和消息
proc ::ExtensionDemoHM::Graphics { switch } {
    if { $switch == 0 } {
        *setoption block_redraw=1
        *setoption block_messages=1
        *setoption block_error_messages=1
        *setoption command_file_state=0
        hm_blockbrowserupdate 1
    } else {
        *setoption block_redraw=0
        *setoption block_messages=0
        *setoption block_error_messages=0
        *setoption command_file_state=1
        hm_blockbrowserupdate 0
    }
}

# 加载额外的脚本文件
source [file join $::ExtensionDemoHM::scriptdir "xsim-copilot.tcl"]
```

### 5.2 HyperView (hv-init.tcl)

HyperView 特有的 API 模式：

```tcl
namespace eval ::ExtensionDemoHV {
    variable fileVar [info script]
    variable dirVar [file dirname $fileVar]
}

# 加载演示会话
proc ::ExtensionDemoHV::loadHVAnimationDemoSession {} {
    variable dirVar
    hwc open session [file normalize [file join $dirVar HV_Animate_Demo_Session.mvw]] replace
}

# 高分辨率截屏（使用 Object-Oriented API）
proc ::ExtensionDemoHV::CaptureHVWindowToJPEGFileFixedNamResolution {} {
    set imageWidth 3200
    set imageHeight 1800
    set type PNG
    set t [expr rand()][clock milliseconds]
    hwi GetSessionHandle sesh$t              ;# 获取会话句柄
    sesh$t GetActiveClientHandle client$t     ;# 获取活跃客户端
    client$t GetModelHandle model$t [client$t GetActiveModel]
    # ... 生成唯一文件名 ...
    sesh$t CaptureActiveWindow ${type} "$imageFileWithPath" pixels $imageWidth $imageHeight
    sesh$t ReleaseHandle                     ;# 释放句柄
}
```

**注意**：HyperView/HyperGraph 使用 `hwi` (HyperWorks Interface) 获取对象句柄，用完后必须调用 `ReleaseHandle` 释放。

### 5.3 HyperGraph (hg-init.tcl)

```tcl
namespace eval ::ExtensionDemoHG {
    variable fileVar [info script]
    variable dirVar [file dirname $fileVar]
}

# 自动适配所有绘图窗口
proc ::ExtensionDemoHG::autoFitAllPlots {{mode all}} {
    set ui [expr rand()][clock seconds]
    hwi OpenStack
    hwi GetSessionHandle ses$ui
    set projectHandle [ses$ui GetProjectHandle pro$ui]
    set numpages [$projectHandle GetNumberOfPages]

    for {set i 1} {$i <= $numpages} {incr i} {
        set pageHandle [$projectHandle GetPageHandle page$ui $i]
        set numwin [$pageHandle GetNumberOfWindows]
        for {set j 1} {$j <= $numwin} {incr j} {
            set windowHandle [$pageHandle GetWindowHandle window$ui $j]
            if {[$windowHandle GetClientType] == "Plot"} {
                set clientHandle [$windowHandle GetClientHandle client$ui]
                $clientHandle Recalculate
                if {$mode == "all"} {
                    $clientHandle Autoscale true true
                } elseif {$mode == "x"} {
                    $clientHandle Autoscale true false
                } elseif {$mode == "y"} {
                    $clientHandle Autoscale false true
                }
                $clientHandle ReleaseHandle
            }
            $windowHandle ReleaseHandle
        }
        $pageHandle ReleaseHandle
    }
    hwi CloseStack
}
```

---

## 6. Ribbon 功能区配置

Ribbon 是 HyperWorks Desktop 主要的 UI 交互区。通过 XML 定义 action（动作）和 page（标签页）。

### 6.1 整体结构

```xml
<root>
    <!-- 1. 动作列表：定义所有可用的按钮/菜单项 -->
    <actionlist>
        <action tag="..." text="..." tooltip="..." image="..." command="..." />
        <!-- ... -->
    </actionlist>

    <!-- 2. 标签页：组织动作到 Ribbon 页面中 -->
    <page tag="..." text="页面标题" visible="条件表达式">
        <group tag="..." text="分组标题">
            <!-- 引用 actionlist 中定义的动作 -->
            <action actiontag="..." />
            <!-- 或定义子组 -->
            <actiongroup tag="..." text="..." />
            <!-- 或定义次级 Ribbon（下拉展开） -->
            <secondaryribbon tag="..." text="..." />
        </group>

        <!-- 条件渲染 -->
        <if eval="表达式">
            <group tag="..." text="条件分组">
                <action actiontag="..." />
            </group>
        </if>
    </page>

    <!-- 3. 菜单页：将动作组织为下拉菜单 -->
    <page tag="..." text="Custom Menu">
        <menu tag="...">
            <action actiontag="..." />
            <actiongroup tag="..." text="子菜单">
                <action actiontag="..." />
            </actiongroup>
        </menu>
    </page>
</root>
```

### 6.2 Action 定义详解

```xml
<action
    tag="Ext_Ribbon_HM_RenameEntity"           <!-- 唯一标识，用于 actiontag 引用 -->
    text="Rename"                              <!-- 按钮显示文本 -->
    tooltip="Rename Entities"                  <!-- 悬停提示 -->
    image="ribbonRenameEntityStrip-80.png"     <!-- 图标文件名（相对于 resources 目录） -->
    command="scontext: RenameEntityCtx"        <!-- ★ 命令：scontext / tcl / hwc -->
/>
```

#### 命令类型 (command 属性)

| 前缀 | 格式 | 说明 | 示例 |
|------|------|------|------|
| `scontext:` | `scontext: ContextTag` | 启动一个交互式上下文 | `scontext: RenameEntityCtx` |
| `tcl:` | `tcl: tcl_procedure` | 执行 TCL 过程 | `tcl: ::ExtensionDemoHM::Solids2Comps` |
| `tcl:` (内联) | `tcl: 内联命令` | 执行单行 TCL 命令 | `tcl: *createnode 0 0 20 0 0 0` |
| (无前缀) | `hwc command` | 通过 HWC 调用 | `hwc xy plot view range=all fitaxis=all` |

```xml
<!-- 示例：各种命令类型 -->
<action tag="Ext_HM_Ctx"     command="scontext: SolidCentroidCtx" />
<action tag="Ext_HM_Proc"    command="tcl: ::ExtensionDemoHM::Solids2Comps" />
<action tag="Ext_HM_Inline"  command="tcl: *createnode 0 0 20 0 0 0" />
<action tag="Ext_HM_Dialog"  command="tcl: tk_messageBox -title &quot;Test&quot; -message &quot;Hello&quot;" />
<action tag="Ext_HG_Fit"     command="tcl: hwc xy plot view range=all fitaxis=all" />
```

#### 特殊属性

| 属性 | 说明 | 示例 |
|------|------|------|
| `pickmask` | 配合列表按钮使用 | `pickmask="ribbonSatelliteListMask-80.png"` |
| `assatellite="True"` | 标记为卫星按钮（列表按钮的子项） | `<action ... assatellite="True"/>` |

### 6.3 Page 标签页

```xml
<page tag="Ext_Ribbon_HM_Page1" text="Custom Tools"
      visible="expr: $HMPROFILE in {OptiStruct, Nastran, NVH}">
```

`visible` 属性支持 TCL 表达式，可基于环境变量或 Profile 动态控制可见性。

- `$HMPROFILE` — 当前 HyperMesh 求解器 Profile
- `$CUSTOM_BETA` — 自定义变量（在 init 脚本中设置）
- `expr: 1` — 始终可见
- `expr: 0` — 始终隐藏

### 6.4 Group 分组

```xml
<group tag="Ext_Ribbon_HM_Group1" text="General">
    <action actiontag="Ext_Ribbon_HM_RenameEntity"/>
</group>
```

### 6.5 ActionGroup (按钮组/下拉菜单)

```xml
<!-- 下拉菜单按钮组 -->
<actiongroup tag="Ext_Ribbon_HM_AGmenu" text="Surface Mesh"
             type="menu"
             dropdownsmallicons="true"
             defaultaction="Ext_Ribbon_HM_Solids2Comps"
             image="ribbonUncombineStrip-80.png">
    <action actiontag="Ext_Ribbon_HM_Solids2Comps"/>
    <action actiontag="Ext_Ribbon_HM_SolidCentroid"/>
</actiongroup>
```

### 6.6 SecondaryRibbon (次级 Ribbon)

```xml
<secondaryribbon tag="Ext_Ribbon_HM_Transform" text="Copy Transform"
                 defaultaction="Ext_Ribbon_HM_Translate"
                 tooltip="Copy Translate/Rotate"
                 image="ribbonCopyStrip-80.png">
    <action actiontag="Ext_Ribbon_HM_Translate"/>
    <action actiontag="Ext_Ribbon_HM_Rotate"/>
</secondaryribbon>
```

### 6.7 条件渲染

```xml
<if eval="expr: $CUSTOM_BETA == 1">
    <group tag="Ext_Ribbon_HM_GroupBeta" text="Beta Features">
        <action actiontag="Ext_Ribbon_HM_Script_Beta1"/>
    </group>
</if>
```

### 6.8 菜单页 (Menu Page)

菜单页将动作组织为下拉菜单（而非 Ribbon 按钮）：

```xml
<page tag="Ext_Ribbon_HM_Page2" text="Custom Menu">
    <menu tag="Ext_Ribbon_HM_MyMenu">
        <action actiontag="Ext_Ribbon_HM_RenameEntity"/>
        <actiongroup tag="Ext_Ribbon_HM_Submenu2" text="Geometry">
            <action actiontag="Ext_Ribbon_HM_Script3"/>
            <action actiontag="Ext_Ribbon_HM_Script4"/>
        </actiongroup>
    </menu>
</page>
```

---

## 7. File Menu 文件菜单配置

文件菜单配置允许在 Application Menu（左上角的大按钮）中添加自定义入口。

```xml
<root>
    <!-- 带子项的按钮 -->
    <button tag="Ext_Filemenu_HM1" text="Custom Entry 1"
            tooltip="Custom file menu entry 1"
            image="fileHelpStrip-24.png">
        <item tag="Ext_Filemenu_HM1_A" text="Custom Option A"
              hint="Custom file menu sub-entry A"
              command="tcl: tk_messageBox -title &quot;Option A&quot; -message &quot;Option A is working.&quot;"/>
        <item tag="Ext_Filemenu_HM1_B" text="Custom Option B"
              hint="Custom file menu sub-entry B"
              command="tcl: tk_messageBox -title &quot;Option B&quot; -message &quot;Option B is working.&quot;"/>
    </button>

    <!-- 直接执行的按钮 -->
    <button tag="Ext_Filemenu_HM2" text="Custom Entry 2"
            tooltip="Custom file menu entry 2"
            image="fileOpenStrip-24.png"
            command="tcl: tk_messageBox -title &quot;Custom Entry 2&quot; -message &quot;Placeholder.&quot;">
    </button>
</root>
```

| 属性 | 说明 |
|------|------|
| `text` | 菜单项显示文字 |
| `tooltip` | 悬停提示 |
| `hint` | 菜单右侧的快捷键提示文字 |
| `image` | 图标（通常为 24x24 像素） |
| `command` | 点击触发的命令（与 Ribbon 相同格式） |

---

## 8. Toolbar 工具栏配置

工具栏在客户端窗口的左侧或右侧显示垂直排列的按钮。

```xml
<root>
    <actionlist>
        <action tag="Ext_Toolbar_HM_Screenshot1"
                tooltip="Save image to clipboard"
                image="save_to_clipboard_toolbar.png"
                command="tcl: ::ExtensionDemoHM::ToClipboardHM"/>

        <action tag="Ext_Toolbar_HM_Screenshot2"
                tooltip="Save image to file"
                image="save_to_file_toolbar.png"
                command="tcl: ::ExtensionDemoHM::ToFileHM"/>
    </actionlist>

    <toolbar tag="Ext_Toolbar_HM" location="left">
        <item actiontag="Ext_Toolbar_HM_Screenshot1"/>
        <item actiontag="Ext_Toolbar_HM_Screenshot2"/>
    </toolbar>
</root>
```

| 属性 | 说明 |
|------|------|
| `location` | 工具栏位置：`left`（左侧）或 `right`（右侧） |
| `tag` | 唯一标识符 |
| 图标尺寸 | 工具栏图标通常为 32x32 像素 |

---

## 9. Context 交互式上下文（向导式工作流）

Context 是 HyperWorks 插件框架中最强大的机制。它创建一个交互式向导流程，通过 Guide Bar（引导栏）和 Micro Dialog（微对话框）引导用户逐步完成操作。

### 9.1 架构

一个 Context 由两个文件组成：

| 文件 | 内容 |
|------|------|
| `*.xml` | UI 布局定义（引导栏控件、微对话框布局） |
| `*.tcl` | 逻辑实现（事件处理、业务逻辑、注册） |

### 9.2 XML UI 布局 — 简单示例

**renameentity.xml** — 一个典型的简单 Context：

```xml
<root>
    <context tag="RenameEntityCtx">                          <!-- ★ 上下文标识 -->
        <guidebar tag="gb">                                  <!-- 引导栏 -->
            <!-- 实体选择器 -->
            <item tag="sel" type="entityselector"
                  selectionname="compSelector"
                  entitytypes="Components Properties Materials"
                  defaultentity="Components"
                  hmselectionmode="append"/>

            <!-- 执行按钮 -->
            <item tag="rename_label"
                  image="toolbarActionOKStrip-16.png"
                  label="Rename"
                  command="hwctx proceed"/>
        </guidebar>

        <!-- 微对话框（在实体选中后弹出） -->
        <microdialog tag="prefix_md">
            <layout tag="prefix_md_layout" columns="2">
                <item tag="prefix_md_label" type="label" label="Prefix" />
                <item tag="prefix_md_entry" type="entry" optionname="entityprefix" />
            </layout>
        </microdialog>
    </context>
</root>
```

#### Guide Bar 元素类型

| type | 说明 | 常用属性 |
|------|------|----------|
| `entityselector` | 实体选择器 | `selectionname`（TCL 中引用名）, `entitytypes`（可选实体类型）, `defaultentity`, `hmselectionmode`（append/single）, `syncwithbrowser`, `visible` |
| 普通项 | 按钮/标签 | `label`, `image`, `command`, `visible` |
| 下拉菜单 | 选项弹出 | 嵌套 `<popup>` > `<options>` 结构 |

#### Entity Selector 属性

| 属性 | 说明 |
|------|------|
| `entitytypes` | 允许选择的实体类型，空格分隔。如 `"Components Properties Materials"` |
| `defaultentity` | 默认实体类型 |
| `hmselectionmode` | `"append"` — 多选；不设置则为单选 |
| `selectionname` | TCL 中通过 `ctx::selection` 引用的选择器名称 |
| `selectmode` | `"single"` — 单选 |
| `syncwithbrowser` | `"true"` — 同步浏览器选择 |
| `showreset` | `"1"` — 显示重置按钮 |
| `embed` | `"0"` — 独立弹出选择面板 |
| `visible` | `"true"` / `"false"` — 动态显隐 |

### 9.3 XML UI 布局 — 复杂示例

**copytranslate.xml** — 带下拉选项、动态选择器和微对话框：

```xml
<context tag="CopyTranslateCtx">
    <guidebar tag="gb">
        <!-- 选项下拉菜单 -->
        <item tag="options" image="fdmTextToolbarDropdownStrip-16.png" indicator="hide">
            <popup tag="pp">
                <options tag="op">
                    <item tag="elemdup" label="Element Duplication"
                          type="combobox" default="Original"
                          values="Current Original" optionname="elemdup"/>
                </options>
            </popup>
        </item>

        <!-- 方向选择器 -->
        <item tag="trans_dir_chooser" label="X-axis">
            <menu tag="menu1">
                <item label="X-axis"   tag="Xaxis"     command="hwctx settrans {X-axis}" />
                <item label="Y-axis"   tag="Yaxis"     command="hwctx settrans {Y-axis}" />
                <item label="Z-axis"   tag="Zaxis"     command="hwctx settrans {Z-axis}" />
                <item label="Vector"   tag="Vector"    command="hwctx settrans {Vector}" />
                <item label="Direction" tag="Direction" command="hwctx settrans {Direction}" />
            </menu>
        </item>

        <!-- 元素选择器 -->
        <item tag="elemsel" type="entityselector" selectionname="ElemSel"
              entitytypes="Elements" hmselectionmode="append"/>

        <!-- 动态显隐的选择器 -->
        <item tag="vecsel" type="entityselector" selectionname="VecSel"
              entitytypes="Vector" visible="false"/>
        <item tag="dirsel" type="entityselector" selectionname="Direction"
              entitytypes="Direction" visible="false"/>

        <!-- 操作按钮 -->
        <item tag="next"   image="toolbarActionApplyStrip-16.png" command="hwctx proceed" />
        <item tag="ok"     image="toolbarActionOKStrip-16.png"    command="hwctx ok"/>
        <item tag="cancel" image="toolbarActionCancelStrip-16.png" command="hwctx cancel"/>
    </guidebar>

    <!-- 微对话框 — 显示高级参数 -->
    <microdialog tag="trans_options_microd">
        <layout tag="hl" columns="auto">
            <item tag="syssel"  type="entityselector" selectionname="SystemSel"
                  entitytypes="Systems" selectmode="single" showreset="1" embed="0"/>
            <item tag="nodesel" type="entityselector" selectionname="BaseNode"
                  entitytypes="Nodes" hmselectionmode="append" embed="0"/>
            <item tag="realitemlabel" type="label" label="Magnitude:" value="Real"/>
            <item tag="transmag" label="Translation Magnitude:"
                  type="entry" inputtype="real" default="10.0"
                  state="normal" optionname="transmag" />
            <item tag="intitemlabel" type="label" label="Number:" value="Integer"/>
            <item tag="transnum" label="Number of translations"
                  type="entry" inputtype="int" default="5"
                  state="normal" optionname="transnum" />
        </layout>
    </microdialog>
</context>
```

### 9.4 TCL 逻辑实现

所有 Context 类继承自 `::hm::context::HMScriptableBase`。

#### 最小 Context 模板

```tcl
itcl::class ::demo::SolidCentroidCtx {
    inherit ::hm::context::HMScriptableBase

    constructor {args} {}

    public method proceed {args}
    public method ok {args}
}

itcl::body ::demo::SolidCentroidCtx::proceed {args} {
    # 获取用户选择的实体 ID 列表
    set solidIdList [ctx::selection ids "SolidSel"]

    # 执行业务逻辑
    ::ExtensionDemoHM::NodeatCentroid $solidIdList

    # 清除选择
    ctx::selection clear "SolidSel"
}

itcl::body ::demo::SolidCentroidCtx::ok {} {
    proceed
    ctx exit              ;# 退出上下文
}

# ★ 注册 Context（必需！）
ctx::manager register hm SolidCentroidCtx "::demo::SolidCentroidCtx"
```

#### 带参数的 Context

```tcl
itcl::class ::demo::RenameEntityCtx {
    inherit ::hm::context::HMScriptableBase

    constructor {args} {}
    public method proceed {args}
    protected method OnSelectionChange {args}   ;# 选择变化回调
}

itcl::body ::demo::RenameEntityCtx::constructor {args} {
    ctx SetOption entityprefix "rev1_"           ;# 设置选项默认值
    eval itk_initialize $args
}

itcl::body ::demo::RenameEntityCtx::proceed {args} {
    set prefix [ctx GetOption entityprefix]      ;# 获取选项值
    set entityType [ctx::selection get compSelector -type]
    set count [ctx::selection count compSelector]

    ctx StartRecordHistory "Renamed $count entities"  ;# 开始 Undo 记录

    foreach id [ctx::selection ids compSelector] {
        set oldName [hm_getvalue $entityType id=$id dataname=name]
        set newName ${prefix}$oldName
        *setvalue $entityType id=$id name=$newName
    }

    ctx EndRecordHistory "Renamed $count entities"    ;# 结束 Undo 记录
    ctx::selection clear compSelector
}

# 选择变化时自动弹出微对话框
itcl::body ::demo::RenameEntityCtx::OnSelectionChange {args} {
    if {[dict getifexists {*}$args count]} {
        ctx::ui set prefix_md_label -label "Prefix"
        ctx::ui post prefix_md          ;# 弹出微对话框
    } else {
        ctx::ui unpost prefix_md        ;# 隐藏微对话框
    }
}

# 注册
ctx::manager register hm RenameEntityCtx "::demo::RenameEntityCtx"
```

### 9.5 Context 核心 API 速查

```tcl
# === 选项管理 ===
ctx SetOption key value          # 设置选项
ctx GetOption key                # 获取选项值

# === 选择管理 ===
ctx::selection ids "SelName"             # 获取选中的实体 ID 列表
ctx::selection count "SelName"           # 获取选中数量
ctx::selection get "SelName" -type       # 获取选中实体类型
ctx::selection clear "SelName"           # 清除选择
ctx GetNamedHMSelection "SelName"        # 获取 HM 选择器对象

# === UI 管理 ===
ctx::ui set item_tag -label "text"       # 设置 UI 控件属性
ctx::ui set item_tag -visible true       # 设置可见性
ctx::ui post microdialog_tag             # 弹出微对话框
ctx::ui unpost microdialog_tag           # 隐藏微对话框

# === 上下文控制 ===
ctx exit                                 # 退出上下文
ctx::manager exit                        # 退出上下文管理器
hwctx proceed                            # 触发 proceed 方法
hwctx ok                                 # 触发 ok 方法（通常执行 proceed + exit）
hwctx cancel                             # 触发 cancel 方法

# === Undo 支持 ===
ctx StartRecordHistory "描述"            # 开始记录可撤销操作
ctx EndRecordHistory "描述"              # 结束记录可撤销操作

# === 工作流帮助 ===
ctx WorkflowHelp "key"                   # 切换到指定工作流步骤提示
ctx ShowNamedCursor cursorElements       # 切换鼠标光标样式
```

### 9.6 Context 注册

```tcl
ctx::manager register <客户端> <ContextTag> "<类名>"
```

- 客户端：`hm` (HyperMesh)、`hv` (HyperView)、`hg` (HyperGraph)
- ContextTag：与 XML 中 `<context tag="...">` 一致
- 类名：完整的 [incr Tcl] 类名

---

## 10. Workflow Help 工作流帮助

工作流帮助在界面底部状态栏显示当前操作步骤的提示文字。

```xml
<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE task PUBLIC "-//OASIS//DTD DITA Task//EN" "task.dtd">

<task id="task_u4g_njs_vr" profileId="HmGeneralWfh">
    <topic id="RenameEntityCtx">
        <shortmessage>
            <ph id="wh_RenameEntity_sm">Rename Components</ph>
        </shortmessage>
        <longmessage>
            <ph id="wh_RenameEntity_lm">
                Renames selected entities by prefixing with a given string.
            </ph>
        </longmessage>
    </topic>

    <topic id="CopyTranslateCtx">
        <shortmessage>
            <ph id="wh_Translate_sm">Translate select elements</ph>
        </shortmessage>
        <longmessage>
            <ph id="wh_Translate_lm">
                Translate a selection of elements multiple times along a global axis...
            </ph>
        </longmessage>
        <!-- 子步骤 -->
        <subTopic id="Source">
            <shortmessage>
                <ph id="wh_TranslateCtx_sm_source">Select elements to translate</ph>
            </shortmessage>
        </subTopic>
        <subTopic id="Target">
            <shortmessage>
                <ph id="wh_TranslateCtx_sm_destination">Choose the magnitude...</ph>
            </shortmessage>
        </subTopic>
    </topic>
</task>
```

在 TCL 代码中通过 `ctx WorkflowHelp` 切换子步骤：

```tcl
# 进入"选择源元素"步骤时
ctx WorkflowHelp "Source"

# 选完元素，进入"确定目标"步骤时
ctx WorkflowHelp "Target"
```

---

## 11. HWC 命令行接口

HWC (HyperWorks Command) 是 HyperWorks Desktop 的命令行接口，可以直接在 TCL 中调用。

### 11.1 打开会话

```tcl
hwc open session <文件路径.mvw> replace
```

### 11.2 HyperGraph 绘图命令

```tcl
# 自动适配所有绘图 — X 和 Y 轴
hwc xy plot view range=all fitaxis=all

# 仅适用 X 轴
hwc xy plot view range=all fitaxis=x

# 仅适用 Y 轴
hwc xy plot view range=all fitaxis=y
```

### 11.3 在 Ribbon/Toolbar Action 中使用 HWC

```xml
<action tag="Ext_Ribbon_HG_Autofit_All_Plots_HWC"
        text="Fit All"
        tooltip="Autofit All Plots X and Y"
        image="ribbonSearchStrip-80.png"
        command="tcl: hwc xy plot view range=all fitaxis=all"/>
```

---

## 12. 图标与资源

### 12.1 资源路径

`extension.xml` 中的 `resources` 字段指定图标搜索的基础路径：

```xml
<entry name="resources" value="images" />
```

所有 Ribbon、Filemenu 和 Toolbar XML 中的 `image` 属性相对于此目录。

### 12.2 推荐图标尺寸

| 使用场景 | 推荐尺寸 | 示例文件 |
|----------|----------|----------|
| Ribbon 按钮（含文本） | 32×32 px | `ribbonRenameEntityStrip-80.png` |
| Ribbon 大按钮 | 80×80 px | `ribbonTransStrip-64.png` |
| Ribbon 超大按钮 | 120×120 px | — |
| 工具栏按钮 | 32×32 px | `save_to_file_toolbar.png` |
| File Menu 图标 | 24×24 px | `fileHelpStrip-24.png` |
| Guide Bar 按钮 | 16×16 px | `toolbarActionApplyStrip-16.png` |

### 12.3 图标命名约定

官方示例中的命名模式：
- `ribbon<Name>Strip-<size>.png` — Ribbon 图标
- `toolbar<Name>Strip-<size>.png` — 工具栏图标
- `file<Name>Strip-<size>.png` — 文件菜单图标

---

## 13. 部署与加载插件

### 13.1 插件搜索路径

HyperWorks Desktop 在以下位置搜索插件（扩展名为 `.xml` 的 `extension.xml` 所在目录）：

| 平台 | 路径 |
|------|------|
| Windows | `%HW_INSTALL_DIR%/hwdesktop/extensions/` |
| Windows | `%APPDATA%/Altair/HyperWorks/extensions/` |
| Windows | `%USERPROFILE%/Documents/Altair/HyperWorks/extensions/` |
| Linux | `$ALTAIR_HOME/hwdesktop/extensions/` |

具体路径可通过环境变量控制：

```tcl
# 在 TCL 中获取插件路径
set extPath [[::hwp::GetSession] GetSystemVariable EXTENSIONPATH]
```

### 13.2 部署步骤

```
1. 将插件文件夹复制到 extensions 目录
   例：C:\Users\<用户名>\AppData\Roaming\Altair\HyperWorks\extensions\MyExtension\

2. 确保 extension.xml 在插件文件夹根目录

3. 重启 HyperWorks Desktop
   → 启动时自动扫描并加载 extensions 目录下的所有插件

4. 验证：在 HyperMesh/HyperView/HyperGraph 中检查
   → Ribbon 是否出现自定义标签页
   → File Menu 是否出现自定义入口
   → 左侧工具栏是否出现自定义按钮
```

### 13.3 调试技巧

```tcl
# 在 HyperMesh 命令行中手动加载插件
source /path/to/your/extension/global-init.tcl

# 查看已加载的上下文
puts [ctx::manager list hm]

# 手动执行某个 TCL 过程测试
::YourNamespace::YourProcedure

# 查看日志
# HyperWorks 启动日志中会显示插件加载信息
```

---

## 14. 最佳实践与常见陷阱

### 14.1 命名空间

✅ **始终使用命名空间**，避免污染全局作用域：

```tcl
namespace eval ::MyCompany::MyExtension {}
proc ::MyCompany::MyExtension::DoSomething {} { ... }
```

### 14.2 图形性能优化

在批量操作时暂停图形刷新以提升性能：

```tcl
proc ::ExtensionDemoHM::Graphics { switch } {
    if { $switch == 0 } {
        *setoption block_redraw=1
        *setoption block_messages=1
        *setoption block_error_messages=1
        *setoption command_file_state=0
        hm_blockbrowserupdate 1
    } else {
        *setoption block_redraw=0
        *setoption block_messages=0
        *setoption block_error_messages=0
        *setoption command_file_state=1
        hm_blockbrowserupdate 0
    }
}
```

**使用方式**：
```tcl
Graphics 0      ;# 操作前关闭刷新
# ... 批量操作 ...
Graphics 1      ;# 操作后恢复刷新
```

### 14.3 Undo/Redo 支持

```tcl
ctx StartRecordHistory "操作描述"
# ... 执行核心操作 ...
ctx EndRecordHistory "操作描述"
```

### 14.4 资源释放（HyperView/HyperGraph）

使用 `hwi` 获取的句柄必须释放：

```tcl
hwi GetSessionHandle sesh$t
sesh$t GetActiveClientHandle client$t
# ... 使用句柄 ...
sesh$t ReleaseHandle     ;# ★ 不释放会导致内存泄漏
```

### 14.5 XML 转义

在 XML 的 `command` 属性中使用 TCL 时，注意转义：

| 字符 | 转义 |
|------|------|
| `"` | `&quot;` |
| `&` | `&amp;` |
| `<` | `&lt;` |
| `>` | `&gt;` |

```xml
<!-- 正确 -->
command="tcl: tk_messageBox -title &quot;Test&quot; -message &quot;Hello&quot;"

<!-- 错误 — 会导致 XML 解析失败 -->
command="tcl: tk_messageBox -title "Test" -message "Hello""
```

### 14.6 Context XML 与 TCL 的对应关系

确保 XML 中的 `tag` 与 TCL 中 `ctx::selection` 引用的 `selectionname` 一致：

```xml
<!-- XML -->
<item tag="elemsel" type="entityselector" selectionname="ElemSel" ... />
```
```tcl
# TCL — 必须匹配 selectionname
set elems [ctx::selection ids "ElemSel"]  ;# ✓ 正确
set elems [ctx::selection ids "elemsel"]  ;# ✗ 错误 — 那是 tag 不是 selectionname
```

### 14.7 TCL 脚本重新加载

修改 TCL 脚本后，无需重启 HyperWorks Desktop：

```tcl
# 在命令行中重新 source
source /path/to/modified/script.tcl

# Context 需要重新注册
ctx::manager register hm ContextTag "::demo::ContextClass"
```

### 14.8 版本兼容性

```xml
<!-- extension.xml 中指定最低要求版本 -->
<entry name="minProductVersion" value="2022.2" />
```

---

## 附录：快速参考

### A. 文件清单

| 文件 | 功能 |
|------|------|
| `extension.xml` | 插件注册（必须） |
| `global-init.tcl` | 全局初始化脚本 |
| `<client>/<client>-init.tcl` | 客户端初始化脚本 |
| `<client>/<client>-ribbon.xml` | Ribbon 功能区 UI |
| `<client>/<client>-filemenu.xml` | 文件菜单 UI |
| `<client>/toolbars/toolbar.xml` | 工具栏 UI |
| `<client>/contexts/*.xml` | 交互式上下文 UI 定义 |
| `<client>/contexts/*.tcl` | 交互式上下文逻辑实现 |
| `<client>/contexts/workflowhelp.xml` | 工作流帮助文本 |
| `images/` | 图标资源 |

### B. 命令类型速记

| command 值 | 含义 |
|------------|------|
| `scontext: <Tag>` | 启动一个 Context |
| `tcl: <proc>` | 调用 TCL 过程 |
| `tcl: <inline>` | 执行单行 TCL |
| `tcl: hwc <cmd>` | 通过 TCL 调用 HWC |

### C. 关键 TCL API

| API | 用途 |
|-----|------|
| `[[::hwp::GetSession] GetSystemVariable NAME]` | 获取系统变量 |
| `[::hwp::GetSession] CaptureScreen FORMAT path` | 截屏 |
| `ctx::selection ids "name"` | 获取选中实体 ID |
| `ctx::selection count "name"` | 获取选中数量 |
| `ctx SetOption key value` | 设置上下文选项 |
| `ctx GetOption key` | 获取上下文选项 |
| `ctx::ui post tag` | 弹出微对话框 |
| `ctx::ui unpost tag` | 隐藏微对话框 |
| `ctx::ui set tag -prop val` | 设置 UI 控件属性 |
| `ctx StartRecordHistory "desc"` | 开始撤销记录 |
| `ctx EndRecordHistory "desc"` | 结束撤销记录 |
| `ctx::manager register client Tag Class` | 注册上下文 |
| `hwi OpenStack` / `hwi CloseStack` | HV/HG API 调用栈 |
| `hwi GetSessionHandle name` | 获取会话句柄 |

---

> 本文档基于 Altair HyperWorks 2024 SDK `Extension_Demo` 示例深度分析编写，
> 涵盖插件开发的完整流程：目录结构、XML 配置、TCL 脚本、Context 交互、HWC 命令等。
>
> 如需更详细的信息，请参考 `documentation/ExtensionDemo.htm` 和官方 HyperWorks API 文档。
