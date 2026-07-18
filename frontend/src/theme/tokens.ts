/**
 * Ant Design 运行工作台主题 Token
 * 专业调度界面：低干扰深色底、克制强调色、数据优先。
 */
import type { ThemeConfig } from 'antd'

const themeToken: ThemeConfig = {
  token: {
    colorPrimary: '#4F8FBA',
    colorSuccess: '#5FA777',
    colorWarning: '#D7A447',
    colorError: '#D85C5C',
    colorInfo: '#7FA7C7',

    colorBgBase: '#0B1117',
    colorTextBase: '#E7EDF3',

    borderRadius: 6,
    borderRadiusSM: 4,
    borderRadiusLG: 8,
    borderRadiusXS: 3,
    borderRadiusOuter: 6,

    fontSize: 13,
    fontSizeHeading1: 28,
    fontSizeHeading2: 20,
    fontSizeHeading3: 16,
    fontFamily:
      "'Satoshi', 'Inter', 'SF Pro Display', 'Helvetica Neue', -apple-system, sans-serif",
    fontFamilyCode:
      "'JetBrains Mono', 'IBM Plex Mono', 'SF Mono', 'Cascadia Code', 'Consolas', monospace",

    colorBgContainer: '#111A23',
    colorBgElevated: '#16212C',
    colorBgLayout: '#0B1117',
    colorBorder: '#253341',
    colorBorderSecondary: '#1C2935',
    colorText: '#E7EDF3',
    colorTextSecondary: '#9CAAB7',
    colorTextTertiary: '#6F7D8A',
    colorLink: '#7FA7C7',
    colorLinkHover: '#A8C4DD',

    boxShadow: '0 12px 32px rgba(0, 0, 0, 0.22)',
    boxShadowSecondary: '0 8px 22px rgba(0, 0, 0, 0.18)',
    boxShadowTertiary: '0 4px 14px rgba(0, 0, 0, 0.16)',

    controlHeight: 34,
    lineHeight: 1.4,
  },
  components: {
    Layout: {
      bodyBg: '#0B1117',
      headerBg: '#101923',
      headerColor: '#E7EDF3',
      headerHeight: 52,
      siderBg: '#0F1720',
      triggerBg: '#0F1720',
      triggerColor: '#9CAAB7',
    },
    Menu: {
      darkItemBg: '#0F1720',
      darkItemColor: '#9CAAB7',
      darkItemHoverBg: '#172331',
      darkItemHoverColor: '#E7EDF3',
      darkItemSelectedBg: '#1D3345',
      darkItemSelectedColor: '#DDEAF5',
      darkSubMenuItemBg: '#0F1720',
      darkGroupTitleColor: '#6F7D8A',
      itemBorderRadius: 6,
      itemMarginInline: 8,
      iconSize: 16,
    },
    Card: {
      colorBgContainer: '#111A23',
      colorBorderSecondary: '#253341',
      headerBg: '#111A23',
      headerFontSize: 13,
      paddingLG: 16,
    },
    Table: {
      headerBg: '#101923',
      headerColor: '#AEBAC6',
      headerSplitColor: '#253341',
      rowHoverBg: '#172331',
      borderColor: '#1C2935',
      cellPaddingBlock: 8,
      cellPaddingInline: 12,
    },
    Button: {
      primaryShadow: 'none',
      dangerShadow: 'none',
      defaultBg: '#111A23',
      defaultBorderColor: '#2A3949',
      defaultColor: '#C4CED8',
      defaultHoverBg: '#172331',
      defaultHoverBorderColor: '#4F8FBA',
      defaultHoverColor: '#E7EDF3',
      primaryColor: '#F4F8FB',
    },
    Input: {
      colorBgContainer: '#0E1620',
      colorBorder: '#2A3949',
      hoverBorderColor: '#4F8FBA',
      activeBorderColor: '#4F8FBA',
      activeShadow: 'none',
    },
    Select: {
      colorBgContainer: '#0E1620',
      colorBgElevated: '#16212C',
      optionSelectedBg: '#1D3345',
      optionActiveBg: '#172331',
    },
    Tag: {
      defaultBg: '#13202B',
      defaultColor: '#AEBAC6',
    },
    Tooltip: {
      colorBgSpotlight: '#16212C',
      colorTextLightSolid: '#E7EDF3',
    },
    Statistic: {
      titleFontSize: 12,
      contentFontSize: 24,
    },
    Modal: {
      contentBg: '#111A23',
      headerBg: '#111A23',
    },
    Segmented: {
      itemColor: '#9CAAB7',
      itemHoverColor: '#E7EDF3',
      itemSelectedBg: '#1D3345',
      itemSelectedColor: '#E7EDF3',
      itemActiveBg: '#172331',
      trackBg: '#0E1620',
    },
    DatePicker: {
      cellHoverBg: '#172331',
      cellActiveWithRangeBg: '#1D3345',
      cellRangeBorderColor: '#4F8FBA',
    },
    Pagination: {
      itemBg: '#111A23',
      itemActiveBg: '#1D3345',
    },
  },
}

export default themeToken
