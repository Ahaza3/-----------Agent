/**
 * Ant Design 暗色大屏风格主题 Token
 * 统一视觉基调 — 科技蓝 + 深色底 + 发光点缀
 */
import type { ThemeConfig } from 'antd'

const themeToken: ThemeConfig = {
  token: {
    // ---- 品牌色系 ----
    colorPrimary: '#4f8cff',
    colorSuccess: '#34d399',
    colorWarning: '#fbbf24',
    colorError: '#d54747',
    colorInfo: '#60a5fa',

    // ---- 基底 ----
    colorBgBase: '#0a0e27',
    colorTextBase: '#e0e6f0',

    // ---- 圆角 / 字号 ----
    borderRadius: 4,
    fontSize: 14,
    fontSizeHeading1: 28,
    fontSizeHeading2: 22,
    fontSizeHeading3: 18,

    // ---- 衍生色 ----
    colorBgContainer: '#0f1440',
    colorBgElevated: '#141a4a',
    colorBgLayout: '#0a0e27',
    colorBorder: '#1e2a5a',
    colorBorderSecondary: '#162050',
    colorText: '#e0e6f0',
    colorTextSecondary: '#8892a4',
    colorTextTertiary: '#5c6680',
    colorLink: '#4f8cff',
    colorLinkHover: '#7aabff',

    // ---- 间距 / 控件高度 ----
    controlHeight: 36,
    lineHeight: 1.5715,
  },
  components: {
    // ---- 布局 ----
    Layout: {
      bodyBg: '#0a0e27',
      headerBg: '#0c1035',
      headerColor: '#e0e6f0',
      headerHeight: 56,
      siderBg: '#0c1035',
      triggerBg: '#0c1035',
      triggerColor: '#8892a4',
    },
    // ---- 菜单 ----
    Menu: {
      darkItemBg: '#0c1035',
      darkItemColor: '#8892a4',
      darkItemHoverBg: '#151b4a',
      darkItemHoverColor: '#e0e6f0',
      darkItemSelectedBg: '#1a2a6e',
      darkItemSelectedColor: '#4f8cff',
      darkSubMenuItemBg: '#0c1035',
      darkGroupTitleColor: '#5c6680',
      itemBorderRadius: 6,
      itemMarginInline: 8,
      iconSize: 18,
    },
    // ---- 卡片 ----
    Card: {
      colorBgContainer: '#0f1440',
      colorBorderSecondary: '#162050',
      headerBg: 'transparent',
      headerFontSize: 16,
      paddingLG: 20,
    },
    // ---- 表格 ----
    Table: {
      headerBg: '#0c1035',
      headerColor: '#8892a4',
      rowHoverBg: '#141a4a',
      borderColor: '#162050',
      cellPaddingBlock: 10,
      cellPaddingInline: 16,
    },
    // ---- 按钮 ----
    Button: {
      primaryShadow: '0 2px 8px rgba(79, 140, 255, 0.3)',
      defaultBg: '#141a4a',
      defaultBorderColor: '#1e2a5a',
      defaultColor: '#e0e6f0',
      defaultHoverBg: '#1a2260',
      defaultHoverBorderColor: '#2e3a6e',
      defaultHoverColor: '#ffffff',
    },
    // ---- 输入框 ----
    Input: {
      colorBgContainer: '#0a0e27',
      colorBorder: '#1e2a5a',
      hoverBorderColor: '#4f8cff',
      activeBorderColor: '#145ada',
    },
    // ---- Select 下拉 ----
    Select: {
      colorBgContainer: '#0a0e27',
      colorBgElevated: '#141a4a',
      optionSelectedBg: '#1a2a6e',
      optionActiveBg: '#151b4a',
    },
    // ---- Tag ----
    Tag: {
      defaultBg: '#141a4a',
      defaultColor: '#8892a4',
    },
    // ---- Tooltip ----
    Tooltip: {
      colorBgSpotlight: '#141a4a',
      colorTextLightSolid: '#e0e6f0',
    },
    // ---- Statistic ----
    Statistic: {
      titleFontSize: 14,
      contentFontSize: 24,
    },
    // ---- 滚动条拟态 ----
    Modal: {
      contentBg: '#0f1440',
      headerBg: '#0f1440',
    },
  },
}

export default themeToken
