/**
 * 告警规则表单 — 阈值计算与校验测试
 */
import { describe, it, expect } from 'vitest'

interface RuleConfig {
  threshold: number
  yellowRatio: number
  orangeRatio: number
  redRatio: number
  coolingTime: number
}

function computeLevels(config: RuleConfig) {
  return {
    yellow: config.threshold * config.yellowRatio,
    orange: config.threshold * config.orangeRatio,
    red: config.threshold * config.redRatio,
  }
}

function validateConfig(config: RuleConfig): string[] {
  const errors: string[] = []
  if (config.threshold <= 0) errors.push('基准阈值必须大于 0')
  if (config.yellowRatio > config.orangeRatio) errors.push('黄色比例不能大于橙色比例')
  if (config.orangeRatio > config.redRatio) errors.push('橙色比例不能大于红色比例')
  if (config.coolingTime < 0) errors.push('冷却时间不能为负数')
  return errors
}

function parseConfig(json: string): RuleConfig | null {
  try {
    const c = JSON.parse(json)
    return {
      threshold: c.threshold || 0,
      yellowRatio: c.yellowRatio ?? 0.9,
      orangeRatio: c.orangeRatio ?? 1.0,
      redRatio: c.redRatio ?? 1.1,
      coolingTime: c.coolingTime ?? 3600,
    }
  } catch {
    return null
  }
}

describe('阈值计算', () => {
  it('基准 1100 MW / Y=0.9 O=1.0 R=1.1 → Y=990 O=1100 R=1210', () => {
    const cfg: RuleConfig = { threshold: 1100, yellowRatio: 0.9, orangeRatio: 1.0, redRatio: 1.1, coolingTime: 3600 }
    const levels = computeLevels(cfg)
    expect(levels.yellow).toBeCloseTo(990, 0)
    expect(levels.orange).toBeCloseTo(1100, 0)
    expect(levels.red).toBeCloseTo(1210, 0)
  })

  it('基准 2000 MW / Y=0.85 O=0.95 R=1.15 → Y=1700 O=1900 R=2300', () => {
    const cfg: RuleConfig = { threshold: 2000, yellowRatio: 0.85, orangeRatio: 0.95, redRatio: 1.15, coolingTime: 1800 }
    const levels = computeLevels(cfg)
    expect(levels.yellow).toBeCloseTo(1700, 0)
    expect(levels.orange).toBeCloseTo(1900, 0)
    expect(levels.red).toBeCloseTo(2300, 0)
  })

  it('阈值 0 时各等级均为 0', () => {
    const cfg: RuleConfig = { threshold: 0, yellowRatio: 0.9, orangeRatio: 1.0, redRatio: 1.1, coolingTime: 3600 }
    const levels = computeLevels(cfg)
    expect(levels.yellow).toBe(0)
    expect(levels.orange).toBe(0)
    expect(levels.red).toBe(0)
  })
})

describe('校验规则', () => {
  it('合法配置不产生错误', () => {
    const cfg: RuleConfig = { threshold: 1100, yellowRatio: 0.9, orangeRatio: 1.0, redRatio: 1.1, coolingTime: 3600 }
    expect(validateConfig(cfg)).toHaveLength(0)
  })

  it('threshold <= 0 产生错误', () => {
    const cfg: RuleConfig = { threshold: 0, yellowRatio: 0.9, orangeRatio: 1.0, redRatio: 1.1, coolingTime: 3600 }
    const errors = validateConfig(cfg)
    expect(errors.length).toBeGreaterThan(0)
    expect(errors[0]).toContain('大于')
  })

  it('yellowRatio > orangeRatio 产生错误', () => {
    const cfg: RuleConfig = { threshold: 1100, yellowRatio: 1.1, orangeRatio: 1.0, redRatio: 1.2, coolingTime: 3600 }
    const errors = validateConfig(cfg)
    expect(errors.some((e) => e.includes('黄色'))).toBe(true)
  })

  it('orangeRatio > redRatio 产生错误', () => {
    const cfg: RuleConfig = { threshold: 1100, yellowRatio: 0.9, orangeRatio: 1.3, redRatio: 1.1, coolingTime: 3600 }
    const errors = validateConfig(cfg)
    expect(errors.some((e) => e.includes('橙色'))).toBe(true)
  })

  it('coolingTime < 0 产生错误', () => {
    const cfg: RuleConfig = { threshold: 1100, yellowRatio: 0.9, orangeRatio: 1.0, redRatio: 1.1, coolingTime: -1 }
    const errors = validateConfig(cfg)
    expect(errors.some((e) => e.includes('冷却'))).toBe(true)
  })

  it('相同比例可以接受（单阈值告警）', () => {
    const cfg: RuleConfig = { threshold: 1100, yellowRatio: 1.0, orangeRatio: 1.0, redRatio: 1.0, coolingTime: 3600 }
    const errors = validateConfig(cfg)
    expect(errors).toHaveLength(0)
  })
})

describe('JSON 解析容错', () => {
  it('合法 JSON 正确解析', () => {
    const cfg = parseConfig('{"threshold":1100,"yellowRatio":0.9,"orangeRatio":1.0,"redRatio":1.1,"coolingTime":3600}')
    expect(cfg).not.toBeNull()
    expect(cfg!.threshold).toBe(1100)
    expect(cfg!.yellowRatio).toBe(0.9)
  })

  it('非法 JSON 返回 null（不崩溃）', () => {
    expect(parseConfig('???')).toBeNull()
    expect(parseConfig('')).toBeNull()
  })

  it('缺失字段使用默认值', () => {
    const cfg = parseConfig('{"threshold":1000}')
    expect(cfg).not.toBeNull()
    expect(cfg!.yellowRatio).toBe(0.9)
    expect(cfg!.orangeRatio).toBe(1.0)
    expect(cfg!.redRatio).toBe(1.1)
    expect(cfg!.coolingTime).toBe(3600)
  })
})
