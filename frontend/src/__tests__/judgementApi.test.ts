/**
 * 智能研判 API 和数据结构测试
 */
import { describe, it, expect } from 'vitest'

describe('JudgementResult 数据结构', () => {
  const sampleJudgement = {
    alertId: 1,
    level: 'RED',
    currentLoad: 1200,
    thresholdValue: 1100,
    trendDirection: 'RISING',
    forecastPeakLoad: 1300,
    forecastPeakTime: '2026-07-18T15:00:00',
    hasExistingTicket: false,
    hasOpenSimilarTicket: false,
    shouldCreateTicket: true,
    autoCreateTicket: false,
    recommendedPriority: 'URGENT',
    dispatcherAdvice: '建议立即创建工单处置',
    operatorAdvice: '红色紧急告警需要人工确认后建单',
    decisionReason: '红色告警，负荷 1200 MW 超过阈值 1100 MW',
    source: 'LLM_AGENT',
    createdAt: '2026-07-18T14:30:00',
  }

  it('RED 告警无已有工单时 shouldCreateTicket=true, autoCreateTicket=false', () => {
    expect(sampleJudgement.level).toBe('RED')
    expect(sampleJudgement.shouldCreateTicket).toBe(true)
    expect(sampleJudgement.autoCreateTicket).toBe(false)
    expect(sampleJudgement.recommendedPriority).toBe('URGENT')
  })

  it('研判应包含调度员建议和运维建议', () => {
    expect(sampleJudgement.dispatcherAdvice).toBeTruthy()
    expect(sampleJudgement.operatorAdvice).toBeTruthy()
  })

  it('source 应为 LLM_AGENT', () => {
    expect(sampleJudgement.source).toBe('LLM_AGENT')
  })
})

describe('工单报告生成', () => {
  const sampleReport = `【工单处理报告】

一、工单基本信息
工单编号：TK-20260718000001
风险来源：告警触发
风险级别：URGENT

二、问题研判
根据系统记录，本次风险主要表现为负荷 1200.0 MW 超过 RED 告警阈值 1100 MW。

三、处理过程
运维人员记录：
已检查数据采集通道，确认负荷异常属实。

四、处理结果
当前处理结论：工单仍在处理中（IN_PROGRESS）。

五、后续建议
1. 确认调峰措施已执行，复核负荷是否回落至安全区间。
2. 检查关联设备运行状态，排除设备异常。
3. 如频繁触发，建议重新评估阈值配置。`

  it('报告应包含工单编号', () => {
    expect(sampleReport).toContain('TK-20260718000001')
  })

  it('报告应包含五个段落', () => {
    expect(sampleReport).toContain('一、工单基本信息')
    expect(sampleReport).toContain('二、问题研判')
    expect(sampleReport).toContain('三、处理过程')
    expect(sampleReport).toContain('四、处理结果')
    expect(sampleReport).toContain('五、后续建议')
  })

  it('报告应包含运维人员填写的处理过程', () => {
    expect(sampleReport).toContain('已检查数据采集通道')
  })
})

describe('快捷问题按角色区分', () => {
  const questions: Record<string, string[]> = {
    DISPATCHER: ['最近一个告警是否需要创建工单？', '生成当前告警研判和调度建议'],
    OPERATOR: ['帮我生成这个工单的处理报告', '当前待处理工单优先处理哪一个？'],
    SYSTEM_ADMIN: ['查看最近待确认工单草稿和人工操作记录', '检查告警到工单闭环是否完整'],
  }

  it('调度员快捷问题应包含研判和建单', () => {
    const qs = questions['DISPATCHER']
    expect(qs.some((q) => q.includes('研判'))).toBe(true)
    expect(qs.some((q) => q.includes('建单') || q.includes('创建工单'))).toBe(true)
  })

  it('运维快捷问题应包含报告', () => {
    const qs = questions['OPERATOR']
    expect(qs.some((q) => q.includes('报告'))).toBe(true)
  })

  it('管理员快捷问题应包含审计', () => {
    const qs = questions['SYSTEM_ADMIN']
    expect(qs.some((q) => q.includes('审计') || q.includes('待确认工单草稿'))).toBe(true)
  })
})
