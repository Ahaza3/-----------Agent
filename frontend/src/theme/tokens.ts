/**
 * Ant Design Brutalist CRT Terminal 主题 Token
 * Tactical Telemetry 范式 — 零圆角 / 危险红 / 磷光白 / 无渐变无阴影
 */
import type { ThemeConfig } from 'antd'

const themeToken: ThemeConfig = {
  token: {
    // ---- 品牌色系：危险红为唯一强调色 ----
    colorPrimary: '#FF2A2A',
    colorSuccess: '#4AF626',
    colorWarning: '#FF2A2A',
    colorError: '#FF2A2A',
    colorInfo: '#AAAAAA',

    // ---- 基底：失活 CRT 黑底 + 磷光白字 ----
    colorBgBase: '#0A0A0A',
    colorTextBase: '#EAEAEA',

    // ---- 零圆角：绝对直角工业感 ----
    borderRadius: 0,
    borderRadiusSM: 0,
    borderRadiusLG: 0,
    borderRadiusXS: 0,
    borderRadiusOuter: 0,

    // ---- 字号层级 ----
    fontSize: 13,
    fontSizeHeading1: 28,
    fontSizeHeading2: 20,
    fontSizeHeading3: 16,
    fontFamily:
      "'Inter', 'SF Pro Display', 'Helvetica Neue', -apple-system, sans-serif",
    fontFamilyCode:
      "'JetBrains Mono', 'IBM Plex Mono', 'SF Mono', 'Cascadia Code', 'Consolas', monospace",

    // ---- 衍生色 ----
    colorBgContainer: '#0E0E0E',
    colorBgElevated: '#111111',
    colorBgLayout: '#0A0A0A',
    colorBorder: '#2A2A2A',
    colorBorderSecondary: '#1F1F1F',
    colorText: '#EAEAEA',
    colorTextSecondary: '#888888',
    colorTextTertiary: '#666666',
    colorLink: '#FF2A2A',
    colorLinkHover: '#FF5555',

    // ---- 无阴影 ----
    boxShadow: 'none',
    boxShadowSecondary: 'none',
    boxShadowTertiary: 'none',

    // ---- 间距 / 控件高度 ----
    controlHeight: 34,
    lineHeight: 1.4,
  },
  components: {
    // ---- 布局 ----
    Layout: {
      bodyBg: '#0A0A0A',
      headerBg: '#0C0C0C',
      headerColor: '#EAEAEA',
      headerHeight: 52,
      siderBg: '#0C0C0C',
      triggerBg: '#0C0C0C',
      triggerColor: '#888888',
    },
    // ---- 菜单：终端式 ----
    Menu: {
      darkItemBg: '#0C0C0C',
      darkItemColor: '#888888',
      darkItemHoverBg: '#1A1A1A',
      darkItemHoverColor: '#EAEAEA',
      darkItemSelectedBg: '#1A1A1A',
      darkItemSelectedColor: '#FF2A2A',
      darkSubMenuItemBg: '#0C0C0C',
      darkGroupTitleColor: '#666666',
      itemBorderRadius: 0,
      itemMarginInline: 0,
      iconSize: 16,
    },
    // ---- 卡片：可见边框 ----
    Card: {
      colorBgContainer: '#0E0E0E',
      colorBorderSecondary: '#2A2A2A',
      headerBg: '#0C0C0C',
      headerFontSize: 13,
      paddingLG: 16,
    },
    // ---- 表格：密集数据 ----
    Table: {
      headerBg: '#0C0C0C',
      headerColor: '#AAAAAA',
      headerSplitColor: '#2A2A2A',
      rowHoverBg: '#111111',
      borderColor: '#1F1F1F',
      cellPaddingBlock: 8,
      cellPaddingInline: 12,
    },
    // ---- 按钮：直角工业风 ----
    Button: {
      primaryShadow: 'none',
      dangerShadow: 'none',
      defaultBg: '#0E0E0E',
      defaultBorderColor: '#2A2A2A',
      defaultColor: '#AAAAAA',
      defaultHoverBg: '#111111',
      defaultHoverBorderColor: '#FF2A2A',
      defaultHoverColor: '#FF2A2A',
      primaryColor: '#0A0A0A',
    },
    // ---- 输入框 ----
    Input: {
      colorBgContainer: '#0A0A0A',
      colorBorder: '#2A2A2A',
      hoverBorderColor: '#FF2A2A',
      activeBorderColor: '#FF2A2A',
      activeShadow: 'none',
    },
    // ---- Select 下拉 ----
    Select: {
      colorBgContainer: '#0A0A0A',
      colorBgElevated: '#111111',
      optionSelectedBg: '#1A1A1A',
      optionActiveBg: '#111111',
    },
    // ---- Tag ----
    Tag: {
      defaultBg: '#0E0E0E',
      defaultColor: '#888888',
    },
    // ---- Tooltip ----
    Tooltip: {
      colorBgSpotlight: '#111111',
      colorTextLightSolid: '#EAEAEA',
    },
    // ---- Statistic ----
    Statistic: {
      titleFontSize: 12,
      contentFontSize: 24,
    },
    // ---- Modal ----
    Modal: {
      contentBg: '#0E0E0E',
      headerBg: '#0E0E0E',
    },
    // ---- Segmented ----
    Segmented: {
      itemColor: '#888888',
      itemHoverColor: '#EAEAEA',
      itemSelectedBg: '#1A1A1A',
      itemSelectedColor: '#FF2A2A',
      itemActiveBg: '#111111',
      trackBg: '#0A0A0A',
    },
    // ---- DatePicker ----
    DatePicker: {
      cellHoverBg: '#111111',
      cellActiveWithRangeBg: '#1A1A1A',
      cellRangeBorderColor: '#FF2A2A',
    },
    // ---- Pagination ----
    Pagination: {
      itemBg: '#0E0E0E',
      itemActiveBg: '#1A1A1A',
    },
  },
}

export default themeToken
