import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Button, Progress, Space, Tag, Tabs, Table, message, Modal, Input, Select, Form, Empty, Skeleton, Descriptions,
} from 'antd'
import {
  CheckCircleOutlined, DashboardOutlined, RiseOutlined, RollbackOutlined,
  SettingOutlined, UserOutlined, AuditOutlined, HeartOutlined, ThunderboltOutlined,
  PlusOutlined, KeyOutlined, EditOutlined, AlertOutlined, RobotOutlined, FileTextOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import api from '../../services/api'
import { Popconfirm } from 'antd'
import { fetchAlertRules } from '../../services/alertApi'
import useAuthStore from '../../stores/useAuthStore'
import { getAdminTabs } from '../../config/roles'
import type { Role } from '../../config/roles'
import AlertRuleForm from './AlertRuleForm'
import {
  fetchDemoLoadStatus, setDemoLoadMode, type DemoLoadStatus,
} from '../../services/demoApi'

const WHITE = '#E7EDF3'
const GREEN = '#5FA777'

/** 通过 label 查找并点击对应的 Ant Design Tab */
const TabSwitchButton = ({ icon, label }: { icon: React.ReactNode; label: string }) => (
  <Button size="small" icon={icon} onClick={() => {
    const tabs = document.querySelectorAll('.ant-tabs-tab')
    tabs.forEach((t) => {
      if (t.textContent?.includes(label)) (t as HTMLElement).click()
    })
  }}>{label}</Button>
)

const MODE_CONFIG = {
  NORMAL: { label: '正常运行', color: '#5FA777' },
  SPIKE: { label: '负荷突增', color: '#D85C5C' },
  RECOVERY: { label: '恢复中', color: '#D7A447' },
} as const

const ROLE_LABELS: Record<string, string> = {
  DISPATCHER: '电力调度员',
  OPERATOR: '运维管理员',
  SYSTEM_ADMIN: '系统管理员',
}

/* ══════════════════════════════════════════════
 *  演示控制面板（所有角色可见）
 * ══════════════════════════════════════════════ */
const DemoPanel = () => {
  const navigate = useNavigate()
  const [status, setStatus] = useState<DemoLoadStatus | null>(null)
  const [available, setAvailable] = useState<boolean | null>(null)
  const [commanding, setCommanding] = useState(false)

  const refresh = useCallback(async () => {
    try { const next = await fetchDemoLoadStatus(); setStatus(next); setAvailable(true) }
    catch { setAvailable(false) }
  }, [])
  useEffect(() => { refresh(); const t = window.setInterval(refresh, 1000); return () => window.clearInterval(t) }, [refresh])

  const runCommand = async (cmd: 'normal' | 'spike' | 'recover') => {
    setCommanding(true)
    try {
      const next = await setDemoLoadMode(cmd); setStatus(next)
      message.success({ normal: '已复位', spike: '突增场景已启动', recover: '恢复场景已启动' }[cmd])
    } catch { message.error('命令执行失败') }
    finally { setCommanding(false) }
  }

  const mode = status ? MODE_CONFIG[status.mode] : MODE_CONFIG.NORMAL
  const progress = useMemo(() => {
    if (!status || status.redThreshold <= 0) return 0
    return Math.min(100, status.currentLoad / status.redThreshold * 100)
  }, [status])

  return (
    <div className="demo-console">
      <div className="demo-console__header">
        <div>
          <div className="section-label">负荷异常演示模式</div>
          <div className="section-value">实时告警全链路</div>
        </div>
        {available === false ? <Tag color="default">演示接口未启用</Tag> : <Tag color={mode.color}>{mode.label}</Tag>}
      </div>
      {available !== false && status && (
        <>
          <div className="demo-metrics">
            <div><span>当前负荷</span><strong>{status.currentLoad.toFixed(1)} <small>MW</small></strong></div>
            <div><span>场景目标</span><strong>{status.targetLoad.toFixed(0)} <small>MW</small></strong></div>
            <div><span>正常基线</span><strong>{status.normalTargetLoad.toFixed(0)} <small>MW</small></strong></div>
          </div>
          <Progress percent={progress} showInfo={false} strokeColor={mode.color} trailColor="#1A1A1A" strokeLinecap="butt" />
          <div className="threshold-grid">
            <div style={{ borderColor: '#D7A447' }}><span>黄色阈值</span><strong>{status.yellowThreshold.toFixed(0)} MW</strong></div>
            <div style={{ borderColor: '#C9823D' }}><span>橙色阈值</span><strong>{status.orangeThreshold.toFixed(0)} MW</strong></div>
            <div style={{ borderColor: '#D85C5C' }}><span>红色阈值</span><strong>{status.redThreshold.toFixed(0)} MW</strong></div>
          </div>
          <Space wrap size={8} className="demo-actions">
            <Button icon={<CheckCircleOutlined />} loading={commanding} onClick={() => runCommand('normal')}>正常运行</Button>
            <Button type="primary" danger icon={<RiseOutlined />} loading={commanding} disabled={status.mode === 'SPIKE'} onClick={() => runCommand('spike')}>模拟负荷突增</Button>
            <Button icon={<RollbackOutlined />} loading={commanding} disabled={status.mode === 'NORMAL'} onClick={() => runCommand('recover')}>恢复正常</Button>
            <Button icon={<DashboardOutlined />} onClick={() => navigate('/dashboard')}>查看实时曲线</Button>
          </Space>
        </>
      )}
    </div>
  )
}

/* ══════════════════════════════════════════════
 *  用户管理面板（SYSTEM_ADMIN）
 * ══════════════════════════════════════════════ */
const UsersPanel = () => {
  const [users, setUsers] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [resetOpen, setResetOpen] = useState<number | null>(null)
  const [form] = Form.useForm()
  const [pwdForm] = Form.useForm()

  const refresh = useCallback(async () => {
    setLoading(true)
    try { setUsers(await api.get('/system/users') as any[]) }
    catch { message.error('用户列表加载失败') }
    finally { setLoading(false) }
  }, [])

  useEffect(() => { refresh() }, [refresh])

  const handleCreate = async (v: any) => {
    try { await api.post('/system/users', v); message.success('用户已创建'); setCreateOpen(false); form.resetFields(); refresh() }
    catch (e: any) { message.error(e?.response?.data?.message || e.message) }
  }

  if (loading) return <Skeleton active paragraph={{ rows: 6 }} />
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
        <h3 style={{ color: '#ccc', margin: 0 }}>系统用户</h3>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>新建用户</Button>
      </div>
      <Table
        dataSource={users}
        rowKey="id"
        size="small"
        pagination={false}
        columns={[
          { title: '用户名', dataIndex: 'username', key: 'username' },
          { title: '显示名', dataIndex: 'displayName', key: 'displayName' },
          { title: '角色', dataIndex: 'role', key: 'role', render: (r: string) => <Tag>{ROLE_LABELS[r] || r}</Tag> },
          { title: '邮箱', dataIndex: 'email', key: 'email' },
          {
            title: '操作', key: 'actions', render: (_: any, r: any) => (
              <Space size="small">
                <Button size="small" icon={<KeyOutlined />} onClick={() => { pwdForm.resetFields(); setResetOpen(r.id) }}>重置密码</Button>
              </Space>
            ),
          },
        ]}
      />

      <Modal title="新建用户" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={() => form.submit()} okText="创建">
        <Form form={form} layout="vertical" onFinish={handleCreate}>
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, min: 6 }]}><Input.Password /></Form.Item>
          <Form.Item name="displayName" label="显示名"><Input /></Form.Item>
          <Form.Item name="role" label="角色" initialValue="DISPATCHER">
            <Select options={Object.entries(ROLE_LABELS).map(([k, v]) => ({ value: k, label: v }))} />
          </Form.Item>
          <Form.Item name="email" label="邮箱"><Input /></Form.Item>
        </Form>
      </Modal>

      <Modal title="重置密码" open={resetOpen !== null} onCancel={() => setResetOpen(null)} onOk={() => pwdForm.submit()} okText="确认">
        <Form form={pwdForm} layout="vertical" onFinish={async (v: any) => {
          try { await api.post(`/system/users/${resetOpen}/reset-password`, { password: v.password }); message.success('已重置'); setResetOpen(null) }
          catch (e: any) { message.error(e.message) }
        }}>
          <Form.Item name="password" label="新密码" rules={[{ required: true, min: 6 }]}><Input.Password /></Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

/* ══════════════════════════════════════════════
 *  操作日志面板（SYSTEM_ADMIN）
 * ══════════════════════════════════════════════ */
const LogsPanel = () => {
  const [logs, setLogs] = useState<any>({ records: [], total: 0 })
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    setLoading(true)
    try { setLogs(await api.get('/system/logs', { params: { page, size: 20 } }) as any) }
    catch { message.error('日志加载失败') }
    finally { setLoading(false) }
  }, [page])
  useEffect(() => { refresh() }, [refresh])

  return (
    <Table
      dataSource={logs.records || []}
      rowKey="id"
      size="small"
      loading={loading}
      pagination={{ current: page, pageSize: 20, total: logs.total || 0, onChange: setPage, showSizeChanger: false }}
      columns={[
        { title: '时间', dataIndex: 'createdAt', width: 160, render: (v: string) => v ? dayjs(v).format('MM-DD HH:mm:ss') : '-' },
        { title: '用户', dataIndex: 'username', width: 100 },
        { title: '模块', dataIndex: 'module', width: 100 },
        { title: '操作', dataIndex: 'action', width: 100 },
        { title: '结果', dataIndex: 'result', width: 80, render: (v: string) => <Tag color={v === 'SUCCESS' ? 'green' : 'red'}>{v}</Tag> },
        { title: '详情', dataIndex: 'detail', ellipsis: true },
      ]}
    />
  )
}

/* ══════════════════════════════════════════════
 *  系统健康面板（SYSTEM_ADMIN）
 * ══════════════════════════════════════════════ */
const HealthPanel = () => {
  const [health, setHealth] = useState<any>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api.get('/system/health').then(setHealth).catch(() => message.error('健康检查失败')).finally(() => setLoading(false))
  }, [])

  if (loading) return <Skeleton active paragraph={{ rows: 6 }} />
  if (!health) return <Empty description="无法获取健康状态" />

  const items = [
    { label: '运行状态', children: <Tag color="green">{health.status}</Tag> },
    { label: '启动时长', children: `${Math.floor(health.uptimeSeconds / 3600)}h ${Math.floor(health.uptimeSeconds % 3600 / 60)}m` },
    { label: 'MySQL', children: <Tag color={health.mysql === 'UP' ? 'green' : 'red'}>{health.mysql}</Tag> },
    { label: 'Redis', children: <Tag>{health.redis || 'N/A'}</Tag> },
    { label: 'Flask/LSTM', children: <Tag color={health.flask === 'UP' ? 'green' : 'red'}>{health.flask}</Tag> },
    { label: 'LLM', children: health.llm ? <Tag color={health.llm.configured ? 'green' : 'default'}>{health.llm.configured ? '已配置' : '未配置'}</Tag> : 'N/A' },
    { label: 'LLM 模型', children: health.llm?.model || 'N/A' },
    { label: '最近预测', children: health.lastPrediction && health.lastPrediction !== 'NONE' ? dayjs(health.lastPrediction).format('MM-DD HH:mm:ss') : 'N/A' },
    { label: '最近告警', children: health.lastAlert && health.lastAlert !== 'NONE' ? dayjs(health.lastAlert).format('MM-DD HH:mm:ss') : 'N/A' },
    { label: 'WebSocket', children: health.websocket?.endpoint || 'N/A' },
  ]

  return (
    <div>
      <Descriptions bordered size="small" column={2} items={items} labelStyle={{ color: '#888', background: '#0c0c0c' }} contentStyle={{ color: '#ccc', background: '#0e0e0e' }} />
      <Button style={{ marginTop: 12 }} icon={<HeartOutlined />} onClick={() => { setLoading(true); api.get('/system/health').then(setHealth).finally(() => setLoading(false)) }}>刷新健康状态</Button>
    </div>
  )
}

/* ══════════════════════════════════════════════
 *  告警规则管理面板（OPERATOR / SYSTEM_ADMIN）
 * ══════════════════════════════════════════════ */
const RulesPanel = () => {
  const [rules, setRules] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [editOpen, setEditOpen] = useState(false)
  const [editing, setEditing] = useState<any>(null)
  const [form] = Form.useForm()

  const refresh = useCallback(async () => {
    setLoading(true)
    try { setRules(await fetchAlertRules()) }
    catch { message.error('规则加载失败') }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { refresh() }, [refresh])

  const openCreate = () => { setEditing(null); setEditOpen(true) }
  const openEdit = (r: any) => { setEditing(r); setEditOpen(true) }

  const handleSave = async (v: any) => {
    try {
      // 从表单字段构造 config JSON
      const config = JSON.stringify({
        threshold: v.threshold,
        yellowRatio: v.yellowRatio,
        orangeRatio: v.orangeRatio,
        redRatio: v.redRatio,
        coolingTime: v.coolingTime,
      })

      // 前端校验
      if (v.threshold <= 0) { message.error('基准阈值必须大于 0'); return }
      if (v.yellowRatio > v.orangeRatio || v.orangeRatio > v.redRatio) {
        message.error('告警比例必须满足：黄色 ≤ 橙色 ≤ 红色'); return
      }
      if (v.coolingTime < 0) { message.error('冷却时间不能为负数'); return }

      const payload = {
        name: v.name,
        type: v.type || 'THRESHOLD',
        config,
        isActive: v.isActive ? 1 : 0,
      }

      if (editing) {
        await api.put(`/alert/rules/${editing.id}`, payload)
      } else {
        await api.post('/alert/rules', payload)
      }
      message.success(editing ? '已更新' : '已创建'); setEditOpen(false); refresh()
    } catch (e: any) { message.error(e?.response?.data?.message || e.message) }
  }

  if (loading) return <Skeleton active paragraph={{ rows: 5 }} />
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
        <h3 style={{ color: '#ccc', margin: 0 }}>告警规则</h3>
        <Button icon={<PlusOutlined />} onClick={openCreate}>新建规则</Button>
      </div>
      <Table
        dataSource={rules} rowKey="id" size="small" pagination={false}
        columns={[
          { title: '名称', dataIndex: 'name', key: 'name' },
          { title: '类型', dataIndex: 'type', key: 'type', render: (v: string) => <Tag>{v}</Tag> },
          { title: '启用', dataIndex: 'isActive', key: 'isActive', render: (v: number) => <Tag color={v ? 'green' : 'default'}>{v ? '启用' : '禁用'}</Tag> },
          { title: '配置', dataIndex: 'config', key: 'config', ellipsis: true,
            render: (v: string) => {
              try { const c = JSON.parse(v); return `${c.threshold || 0}MW / Y${c.yellowRatio??0} O${c.orangeRatio??0} R${c.redRatio??0}` }
              catch { return <Tag color="red">配置异常</Tag> }
            }
          },
          {
            title: '操作', key: 'actions', render: (_: any, r: any) => (
              <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>编辑</Button>
            ),
          },
        ]}
      />
      <Modal title={editing ? '编辑规则' : '新建规则'} open={editOpen} onCancel={() => setEditOpen(false)} onOk={() => form.submit()} okText="保存" width={560}>
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <AlertRuleForm form={form} editing={editing} />
        </Form>
      </Modal>
    </div>
  )
}

/* ══════════════════════════════════════════════
 *  模型管理面板（OPERATOR / SYSTEM_ADMIN）
 * ══════════════════════════════════════════════ */
const ModelPanel = () => {
  const [forecast, setForecast] = useState<any>(null)
  const [versions, setVersions] = useState<any[]>([])
  const [health, setHealth] = useState<any>(null)
  const [loading, setLoading] = useState(true)
  const [triggering, setTriggering] = useState(false)
  const [activating, setActivating] = useState<number | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    const [forecastResp, versionsResp, healthResp] = await Promise.allSettled([
      api.get('/predict/forecast'),
      api.get('/model/versions'),
      api.get('/system/health'),
    ])
    if (forecastResp.status === 'fulfilled') setForecast(forecastResp.value)
    if (versionsResp.status === 'fulfilled') setVersions((versionsResp.value as unknown as any[]) || [])
    if (healthResp.status === 'fulfilled') setHealth(healthResp.value)
    setLoading(false)
  }, [])
  useEffect(() => { refresh() }, [refresh])

  if (loading) return <Skeleton active paragraph={{ rows: 4 }} />
  const activeVersion = versions.find((item) => item.isActive === 1)

  const activateVersion = async (id: number) => {
    setActivating(id)
    try {
      await api.put(`/model/versions/${id}/activate`)
      message.success('模型版本已切换')
      await refresh()
    } catch (e: any) {
      message.error(e?.response?.data?.message || e.message || '模型版本切换失败')
    } finally {
      setActivating(null)
    }
  }
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
        <h3 style={{ color: '#ccc', margin: 0 }}>预测模型</h3>
        <Space>
          <Button icon={<HeartOutlined />} onClick={refresh}>刷新</Button>
          <Button type="primary" icon={<RobotOutlined />} loading={triggering} onClick={async () => {
            setTriggering(true)
            try { await api.get('/predict/forecast'); message.success('预测已触发'); refresh() }
            catch (e: any) { message.error(e.message) }
            finally { setTriggering(false) }
          }}>触发预测</Button>
        </Space>
      </div>
      <Descriptions bordered size="small" column={2}
        items={[
          { label: '当前版本', children: activeVersion ? `${activeVersion.modelName} ${activeVersion.version}` : '未登记' },
          { label: '推理服务', children: <Tag color={health?.flask === 'UP' ? 'green' : 'red'}>{health?.flask === 'UP' ? '正常' : '异常'}</Tag> },
          { label: '训练 MAPE', children: activeVersion?.mape != null ? `${Number(activeVersion.mape).toFixed(2)}%` : 'N/A' },
          { label: '训练 RMSE', children: activeVersion?.rmse != null ? `${Number(activeVersion.rmse).toFixed(2)} MW` : 'N/A' },
          { label: '预测起点', children: forecast?.forecastStartTime ? dayjs(forecast.forecastStartTime).format('MM-DD HH:mm') : 'N/A' },
          { label: '预测点数', children: forecast?.predictions?.length || 0 },
        ]}
        labelStyle={{ color: '#888', background: '#0c0c0c' }} contentStyle={{ color: '#ccc', background: '#0e0e0e' }}
      />
      <Table
        style={{ marginTop: 16 }}
        dataSource={versions}
        rowKey="id"
        size="small"
        pagination={false}
        locale={{ emptyText: <Empty description="暂无模型版本记录" /> }}
        columns={[
          { title: '模型', dataIndex: 'modelName', key: 'modelName' },
          { title: '版本', dataIndex: 'version', key: 'version' },
          { title: '状态', dataIndex: 'isActive', key: 'isActive', render: (v: number) => <Tag color={v === 1 ? 'green' : 'default'}>{v === 1 ? '当前使用' : '历史版本'}</Tag> },
          { title: 'MAPE', dataIndex: 'mape', key: 'mape', render: (v: number | null) => v == null ? 'N/A' : `${Number(v).toFixed(2)}%` },
          { title: 'RMSE', dataIndex: 'rmse', key: 'rmse', render: (v: number | null) => v == null ? 'N/A' : `${Number(v).toFixed(2)} MW` },
          { title: '训练时间', dataIndex: 'trainedAt', key: 'trainedAt', render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : 'N/A' },
          {
            title: '操作',
            key: 'actions',
            render: (_: any, row: any) => row.isActive === 1 ? <Tag color="green">已激活</Tag> : (
              <Popconfirm
                title="确认切换模型版本？"
                description="切换后该版本将标记为当前使用版本。"
                onConfirm={() => activateVersion(row.id)}
                okText="确认"
                cancelText="取消"
              >
                <Button size="small" icon={<RollbackOutlined />} loading={activating === row.id}>发布/回滚</Button>
              </Popconfirm>
            ),
          },
        ]}
      />
    </div>
  )
}

/* ══════════════════════════════════════════════
 *  运维概览面板（OPERATOR 首页）
 * ══════════════════════════════════════════════ */
const OperatorOverviewPanel = () => {
  const user = useAuthStore((s) => s.user)
  const [summary, setSummary] = useState<any>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const [ticketsResp, healthResp] = await Promise.allSettled([
        api.get('/tickets', { params: { page: 1, size: 50, assigneeUserId: user?.id } }),
        api.get('/system/health'),
      ])
      const tickets = ticketsResp.status === 'fulfilled' ? ticketsResp.value : {}
      const health = healthResp.status === 'fulfilled' ? healthResp.value : null
      const records = (tickets as any)?.records || []
      setSummary({
        total: (tickets as any)?.total || 0,
        assigned: records.filter((t: any) => t.assigneeUserId === user?.id && t.status !== 'RESOLVED' && t.status !== 'CLOSED').length,
        pending: records.filter((t: any) => t.status === 'PENDING').length,
        inProgress: records.filter((t: any) => t.status === 'IN_PROGRESS' && t.assigneeUserId === user?.id).length,
        resolved: records.filter((t: any) => t.status === 'RESOLVED').length,
        oldest: records.filter((t: any) => t.status !== 'CLOSED' && t.status !== 'CANCELLED').sort((a: any, b: any) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())[0],
        health,
      })
    } catch {} finally { setLoading(false) }
  }, [user?.id])

  useEffect(() => { refresh() }, [refresh])

  if (loading) return <Skeleton active paragraph={{ rows: 4 }} />

  return (
    <div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 8, marginBottom: 16 }}>
        <div style={{ border: '1px solid #2A2A2A', padding: '10px 12px', background: '#0c0c0c' }}>
          <div style={{ color: '#888', fontSize: 10 }}>分配给我</div>
          <div className="font-mono" style={{ color: WHITE, fontSize: 22, fontWeight: 700 }}>{summary?.assigned || 0}</div>
        </div>
        <div style={{ border: '1px solid #2A2A2A', padding: '10px 12px', background: '#0c0c0c' }}>
          <div style={{ color: '#888', fontSize: 10 }}>待认领</div>
          <div className="font-mono" style={{ color: '#FAAD14', fontSize: 22, fontWeight: 700 }}>{summary?.pending || 0}</div>
        </div>
        <div style={{ border: '1px solid #2A2A2A', padding: '10px 12px', background: '#0c0c0c' }}>
          <div style={{ color: '#888', fontSize: 10 }}>处理中</div>
          <div className="font-mono" style={{ color: '#177ddc', fontSize: 22, fontWeight: 700 }}>{summary?.inProgress || 0}</div>
        </div>
        <div style={{ border: '1px solid #2A2A2A', padding: '10px 12px', background: '#0c0c0c' }}>
          <div style={{ color: '#888', fontSize: 10 }}>今日已解决</div>
          <div className="font-mono" style={{ color: GREEN, fontSize: 22, fontWeight: 700 }}>{summary?.resolved || 0}</div>
        </div>
      </div>

      {summary?.oldest && (
        <div style={{ border: '1px solid #FAAD14', padding: '8px 12px', marginBottom: 16, background: 'rgba(250,173,20,0.08)' }}>
          <span style={{ color: '#FAAD14', fontSize: 11 }}>最久未处理: </span>
          <span style={{ color: '#ccc', fontSize: 12 }}>
            {summary.oldest.ticketNo} — {summary.oldest.summary || '无概要'} ({dayjs(summary.oldest.createdAt).format('MM-DD HH:mm')})
          </span>
        </div>
      )}

      {summary?.health && (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Tag color={summary.health.flask === 'UP' ? 'green' : 'red'}>Flask: {summary.health.flask === 'UP' ? '正常' : '异常'}</Tag>
          <Tag color={summary.health.mysql === 'UP' ? 'green' : 'red'}>MySQL: {summary.health.mysql === 'UP' ? '正常' : '异常'}</Tag>
          <Tag color="green">数据源: 模拟</Tag>
        </div>
      )}
    </div>
  )
}

/* ══════════════════════════════════════════════
 *  系统概览面板（SYSTEM_ADMIN 首页）
 * ══════════════════════════════════════════════ */
const OverviewPanel = () => {
  const [loading, setLoading] = useState(true)
  const [health, setHealth] = useState<any>(null)
  const [users, setUsers] = useState<any[]>([])
  const [forecast, setForecast] = useState<any>(null)
  const [logs, setLogs] = useState<any[]>([])
  const [error, setError] = useState('')

  const refresh = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [h, u, f, l] = await Promise.allSettled([
        api.get('/system/health'),
        api.get('/system/users'),
        api.get('/predict/forecast'),
        api.get('/system/logs', { params: { page: 1, size: 5, result: 'FAILURE' } }),
      ])
      setHealth(h.status === 'fulfilled' ? h.value : null)
      setUsers(u.status === 'fulfilled' ? (u.value as any) : [])
      setForecast(f.status === 'fulfilled' ? f.value : null)
      setLogs(l.status === 'fulfilled' ? (l.value as any)?.records || [] : [])
    } catch {
      setError('加载失败')
    } finally { setLoading(false) }
  }, [])

  useEffect(() => { refresh() }, [refresh])

  // 30s 静默刷新
  useEffect(() => {
    const t = setInterval(refresh, 30_000)
    return () => clearInterval(t)
  }, [refresh])

  if (loading && !health) return <Skeleton active paragraph={{ rows: 6 }} />

  const roleCounts = users.reduce((acc: Record<string, number>, u: any) => {
    acc[u.role] = (acc[u.role] || 0) + 1
    return acc
  }, {})

  const statusTag = (val: string | null, label: string) => {
    if (!val) return <Tag color="default">{label}: N/A</Tag>
    const up = val === 'UP'
    return <Tag color={up ? 'green' : 'red'}>{label}: {up ? '正常' : '异常'}</Tag>
  }

  return (
    <div>
      {error && (
        <div style={{ color: '#D85C5C', fontSize: 12, marginBottom: 8 }}>{error}</div>
      )}

      {/* 服务状态 */}
      <h4 style={{ color: '#888', fontSize: 11, marginBottom: 8 }}>服务状态</h4>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
        {health && (
          <>
            {statusTag(health.mysql, 'MySQL')}
            {statusTag(health.redis || 'UP', 'Redis')}
            {statusTag(health.flask, 'Flask')}
            {statusTag(health.llm?.configured ? 'UP' : null, 'LLM')}
            <Tag color="green">WebSocket: /ws/dashboard</Tag>
          </>
        )}
        {!health && <Tag color="default">服务状态不可用</Tag>}
      </div>

      {/* 用户统计 */}
      <h4 style={{ color: '#888', fontSize: 11, marginBottom: 8 }}>用户统计</h4>
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
        <div className="font-mono" style={{ color: WHITE, fontSize: 20 }}>{users.length} <small style={{ color: '#888', fontSize: 11 }}>启用用户</small></div>
        <span style={{ color: '#888' }}>调度员: {roleCounts['DISPATCHER'] || 0}</span>
        <span style={{ color: '#888' }}>运维: {roleCounts['OPERATOR'] || 0}</span>
        <span style={{ color: '#888' }}>管理员: {roleCounts['SYSTEM_ADMIN'] || 0}</span>
      </div>

      {/* 审计摘要 */}
      <h4 style={{ color: '#888', fontSize: 11, marginBottom: 8 }}>
        最近失败操作
        {logs.length > 0 && <TabSwitchButton icon={null} label="操作日志" />}
      </h4>
      <div style={{ marginBottom: 16, fontSize: 11, color: '#888' }}>
        {logs.length === 0 ? <span>近24小时无失败操作</span> : logs.map((l: any) => (
          <div key={l.id} style={{ marginBottom: 4 }}>
            <Tag color="red">{l.result}</Tag> {l.username} · {l.module} · {l.action}
            <span style={{ color: '#555', marginLeft: 8 }}>{l.createdAt ? dayjs(l.createdAt).format('MM-DD HH:mm') : ''}</span>
          </div>
        ))}
      </div>

      {/* 模型 */}
      <h4 style={{ color: '#888', fontSize: 11, marginBottom: 8 }}>当前模型</h4>
      <div style={{ marginBottom: 16 }}>
        {forecast ? (
          <span style={{ color: '#ccc', fontSize: 12 }}>{forecast.model || 'N/A'} · 最近预测: {forecast.forecastStartTime ? dayjs(forecast.forecastStartTime).format('MM-DD HH:mm') : 'N/A'}</span>
        ) : (
          <span style={{ color: '#666' }}>暂无预测数据</span>
        )}
      </div>

      {/* 快捷入口 — 通过模拟点击 tab 切换 */}
      <h4 style={{ color: '#888', fontSize: 11, marginBottom: 8 }}>快捷入口</h4>
      <Space wrap>
        <TabSwitchButton icon={<UserOutlined />} label="用户管理" />
        <TabSwitchButton icon={<AuditOutlined />} label="操作日志" />
        <TabSwitchButton icon={<HeartOutlined />} label="系统健康" />
        <TabSwitchButton icon={<AlertOutlined />} label="告警规则" />
        <TabSwitchButton icon={<RobotOutlined />} label="模型管理" />
      </Space>
    </div>
  )
}

/* ══════════════════════════════════════════════
 *  Admin 主页面
 * ══════════════════════════════════════════════ */
const Admin = () => {
  const role = useAuthStore((s) => s.user?.role) as Role | undefined

  const tabs = useMemo(() => {
    const adminTabs = getAdminTabs(role)
    const items: { key: string; label: React.ReactNode; children: React.ReactNode }[] = []

    // SYSTEM_ADMIN 首页增加概览
    if (role === 'SYSTEM_ADMIN') {
      items.push({ key: 'overview', label: <span><HeartOutlined />系统概览</span>, children: <OverviewPanel /> })
    }
    // OPERATOR 首页增加工单摘要
    if (role === 'OPERATOR') {
      items.push({ key: 'overview', label: <span><FileTextOutlined />运维概览</span>, children: <OperatorOverviewPanel /> })
    }

    items.push(...adminTabs.map((tab) => {
      switch (tab.key) {
        case 'users':  return { key: 'users',  label: <span><UserOutlined />用户管理</span>, children: <UsersPanel /> }
        case 'logs':   return { key: 'logs',   label: <span><AuditOutlined />操作日志</span>, children: <LogsPanel /> }
        case 'health': return { key: 'health', label: <span><HeartOutlined />系统健康</span>, children: <HealthPanel /> }
        case 'rules':  return { key: 'rules',  label: <span><AlertOutlined />告警规则</span>, children: <RulesPanel /> }
        case 'model':  return { key: 'model',  label: <span><RobotOutlined />模型管理</span>, children: <ModelPanel /> }
        case 'demo':   return { key: 'demo',   label: <span><ThunderboltOutlined />演示控制</span>, children: <DemoPanel /> }
        default: return null
      }
    }).filter(Boolean) as { key: string; label: React.ReactNode; children: React.ReactNode }[])
    return items
  }, [role])

  return (
    <div className="admin-root">
      <div className="admin-toolbar">
        <h1><SettingOutlined /> {role === 'OPERATOR' ? '运维管理' : '系统管理'} <small>{role ? ROLE_LABELS[role] : ''}</small></h1>
      </div>
      <Tabs defaultActiveKey={tabs[0]?.key} items={tabs} style={{ marginTop: 8 }} />
      <style>{`
        .admin-root { min-height: calc(100vh - 132px); }
        .admin-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
        .admin-toolbar h1 { margin: 0; color: #E7EDF3; font-size: 22px; font-weight: 750; }
        .admin-toolbar h1 > .anticon { color: #7FA7C7; margin-right: 8px; }
        .admin-toolbar h1 small { margin-left: 10px; color: #7D8A97; font-size: 12px; font-weight: 500; }
        .demo-console { border: 1px solid #1C2935; border-radius: 8px; margin-top: 14px; overflow: hidden; background: #111A23; }
        .demo-console__header { min-height: 72px; padding: 14px 16px; border-bottom: 1px solid #1C2935; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
        .section-label { color: #7D8A97; font-size: 11px; font-family: monospace; }
        .section-value { color: #E7EDF3; font-size: 18px; font-weight: 700; margin-top: 4px; }
        .demo-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); }
        .demo-metrics > div { padding: 20px 16px; border-right: 1px solid #1C2935; }
        .demo-metrics > div:last-child { border-right: 0; }
        .demo-metrics span, .threshold-grid span { display: block; color: #7D8A97; font-size: 11px; }
        .demo-metrics strong { display: block; color: #E7EDF3; font-size: 28px; margin-top: 5px; }
        .demo-metrics small { color: #6F7D8A; font-size: 11px; }
        .demo-console .ant-progress { display: block; padding: 0 16px; margin: 2px 0 18px; }
        .threshold-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; padding: 0 16px 16px; }
        .threshold-grid > div { border-left: 3px solid; border-radius: 6px; padding: 8px 10px; background: #0E1620; }
        .threshold-grid strong { display: block; color: #E7EDF3; font-size: 14px; margin-top: 3px; }
        .demo-actions { padding: 14px 16px; border-top: 1px solid #1C2935; width: 100%; }
        @media (max-width: 720px) { .demo-metrics, .threshold-grid { grid-template-columns: 1fr; } .demo-metrics > div { border-right: 0; border-bottom: 1px solid #1C2935; } }
      `}</style>
    </div>
  )
}

export default Admin
