/**
 * 结构化告警规则表单 — 将 JSON config 转换为可填写的表单字段
 * 实时显示计算后的各等级阈值
 */
import { useState, useEffect, useMemo } from 'react'
import { Form, Input, InputNumber, Switch, Alert, Descriptions, Typography } from 'antd'
import { ALERT_LEVEL_CONFIG } from '../../types/alert'
import type { AlertLevel } from '../../types/alert'

interface RuleConfig {
  threshold: number
  yellowRatio: number
  orangeRatio: number
  redRatio: number
  coolingTime: number
}

interface Props {
  form: any
  editing: any | null // 正在编辑的规则或 null（新建）
}

const AlertRuleForm = ({ form, editing }: Props) => {
  const [parseError, setParseError] = useState(false)

  // 解析现有规则的 config JSON
  const parsedConfig = useMemo<RuleConfig | null>(() => {
    if (!editing?.config) return null
    try {
      const cfg = typeof editing.config === 'string' ? JSON.parse(editing.config) : editing.config
      if (!cfg || typeof cfg.threshold !== 'number') {
        setParseError(true)
        return null
      }
      setParseError(false)
      return {
        threshold: cfg.threshold || 1100,
        yellowRatio: cfg.yellowRatio ?? 0.9,
        orangeRatio: cfg.orangeRatio ?? 1.0,
        redRatio: cfg.redRatio ?? 1.1,
        coolingTime: cfg.coolingTime ?? 3600,
      }
    } catch {
      setParseError(true)
      return null
    }
  }, [editing])

  // 初始化表单
  useEffect(() => {
    if (editing) {
      form.setFieldsValue({
        name: editing.name || '',
        type: editing.type || 'THRESHOLD',
        isActive: editing.isActive === 1,
        threshold: parsedConfig?.threshold ?? 1100,
        yellowRatio: parsedConfig?.yellowRatio ?? 0.9,
        orangeRatio: parsedConfig?.orangeRatio ?? 1.0,
        redRatio: parsedConfig?.redRatio ?? 1.1,
        coolingTime: parsedConfig?.coolingTime ?? 3600,
      })
    } else {
      form.resetFields()
      form.setFieldsValue({
        type: 'THRESHOLD',
        isActive: true,
        threshold: 1100,
        yellowRatio: 0.9,
        orangeRatio: 1.0,
        redRatio: 1.1,
        coolingTime: 3600,
      })
      setParseError(false)
    }
  }, [editing, form, parsedConfig])

  // 监听表单变化实时计算
  const threshold = Form.useWatch('threshold', form) ?? 0
  const yellowRatio = Form.useWatch('yellowRatio', form) ?? 0
  const orangeRatio = Form.useWatch('orangeRatio', form) ?? 0
  const redRatio = Form.useWatch('redRatio', form) ?? 0

  const computedLevels = useMemo(() => {
    return [
      { level: 'YELLOW' as AlertLevel, value: threshold * yellowRatio },
      { level: 'ORANGE' as AlertLevel, value: threshold * orangeRatio },
      { level: 'RED' as AlertLevel, value: threshold * redRatio },
    ]
  }, [threshold, yellowRatio, orangeRatio, redRatio])

  return (
    <div>
      {parseError && (
        <Alert
          type="warning"
          showIcon
          message="规则配置 JSON 解析失败"
          description="原 config 字段无法解析，将以默认值填充表单。保存后将覆盖旧配置。"
          style={{ marginBottom: 16 }}
        />
      )}

      <Form.Item name="name" label="规则名称" rules={[{ required: true, message: '请输入规则名称' }]}>
        <Input placeholder="如：主变压器负荷告警" />
      </Form.Item>

      <Form.Item name="type" label="规则类型" initialValue="THRESHOLD">
        <Input disabled />
      </Form.Item>

      <Form.Item name="isActive" label="启用" valuePropName="checked">
        <Switch checkedChildren="启用" unCheckedChildren="禁用" />
      </Form.Item>

      <Form.Item
        name="threshold"
        label="基准安全阈值 (MW)"
        rules={[
          { required: true, message: '请输入基准阈值' },
          { type: 'number', min: 1, message: '阈值必须大于 0' },
        ]}
      >
        <InputNumber style={{ width: '100%' }} min={1} step={10} addonAfter="MW" />
      </Form.Item>

      <Form.Item
        name="yellowRatio"
        label="黄色告警比例"
        rules={[
          { required: true },
          { type: 'number', min: 0, max: 2, message: '比例应在 0~2 之间' },
        ]}
      >
        <InputNumber style={{ width: '100%' }} min={0} max={2} step={0.01} />
      </Form.Item>

      <Form.Item
        name="orangeRatio"
        label="橙色告警比例"
        rules={[
          { required: true },
          { type: 'number', min: 0, max: 2, message: '比例应在 0~2 之间' },
        ]}
      >
        <InputNumber style={{ width: '100%' }} min={0} max={2} step={0.01} />
      </Form.Item>

      <Form.Item
        name="redRatio"
        label="红色告警比例"
        rules={[
          { required: true },
          { type: 'number', min: 0, max: 2, message: '比例应在 0~2 之间' },
        ]}
      >
        <InputNumber style={{ width: '100%' }} min={0} max={2} step={0.01} />
      </Form.Item>

      <Form.Item
        name="coolingTime"
        label="冷却时间 (秒)"
        rules={[
          { required: true },
          { type: 'number', min: 0, message: '冷却时间不能为负数' },
        ]}
      >
        <InputNumber style={{ width: '100%' }} min={0} step={60} addonAfter="秒" />
      </Form.Item>

      {/* 实时阈值计算预览 */}
      {threshold > 0 && (
        <div style={{
          border: '1px solid #2A2A2A',
          padding: 12,
          marginTop: 8,
          background: '#0c0c0c',
        }}>
          <Typography.Text type="secondary" style={{ fontSize: 11, marginBottom: 8, display: 'block' }}>
            实时阈值计算预览
          </Typography.Text>
          <Descriptions size="small" column={3} colon={false}>
            {computedLevels.map((item) => {
              const cfg = ALERT_LEVEL_CONFIG[item.level]
              return (
                <Descriptions.Item key={item.level} label={
                  <span style={{ color: cfg?.color || '#888' }}>{cfg?.label || item.level}</span>
                }>
                  <span style={{ color: cfg?.color || '#ccc', fontWeight: 600 }}>
                    {item.value > 0 ? `${item.value.toFixed(0)} MW` : '--'}
                  </span>
                </Descriptions.Item>
              )
            })}
          </Descriptions>
        </div>
      )}
    </div>
  )
}

export default AlertRuleForm
