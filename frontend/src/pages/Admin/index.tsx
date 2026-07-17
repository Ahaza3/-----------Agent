import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Button, Progress, Space, Tag, Tabs, Table, message, Modal, Input, Select, Form, Empty, Skeleton, Descriptions, Switch,
} from 'antd'
import {
  CheckCircleOutlined, DashboardOutlined, RiseOutlined, RollbackOutlined,
  SettingOutlined, UserOutlined, AuditOutlined, HeartOutlined, ThunderboltOutlined,
  PlusOutlined, KeyOutlined, EditOutlined, AlertOutlined, RobotOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import api from '../../services/api'
import { fetchAlertRules } from '../../services/alertApi'
import useAuthStore from '../../stores/useAuthStore'
import {
  fetchDemoLoadStatus, setDemoLoadMode, type DemoLoadStatus,
} from '../../services/demoApi'

const MODE_CONFIG = {
  NORMAL: { label: '正常运行', color: '#4AF626' },
  SPIKE: { label: '负荷突增', color: '#FF2A2A' },
  RECOVERY: { label: '恢复中', color: '#FADB14' },
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
            <div style={{ borderColor: '#FADB14' }}><span>黄色阈值</span><strong>{status.yellowThreshold.toFixed(0)} MW</strong></div>
            <div style={{ borderColor: '#FA8C16' }}><span>橙色阈值</span><strong>{status.orangeThreshold.toFixed(0)} MW</strong></div>
            <div style={{ borderColor: '#FF2A2A' }}><span>红色阈值</span><strong>{status.redThreshold.toFixed(0)} MW</strong></div>
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

  const openCreate = () => { setEditing(null); form.resetFields(); setEditOpen(true) }
  const openEdit = (r: any) => { setEditing(r); form.setFieldsValue(r); setEditOpen(true) }

  const handleSave = async (v: any) => {
    try {
      if (editing) {
        await api.put(`/alert/rules/${editing.id}`, v)
      } else {
        await api.post('/alert/rules', v)
      }
      message.success(editing ? '已更新' : '已创建'); setEditOpen(false); refresh()
    } catch (e: any) { message.error(e?.response?.data?.message || e.message) }
  }

  if (loading) return <Skeleton active paragraph={{ rows: 5 }} />
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
        <h3 style={{ color: '#ccc', margin: 0 }}>告警规则</h3>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建规则</Button>
      </div>
      <Table
        dataSource={rules} rowKey="id" size="small" pagination={false}
        columns={[
          { title: '名称', dataIndex: 'name', key: 'name' },
          { title: '类型', dataIndex: 'type', key: 'type', render: (v: string) => <Tag>{v}</Tag> },
          { title: '启用', dataIndex: 'isActive', key: 'isActive', render: (v: number) => <Tag color={v ? 'green' : 'default'}>{v ? '启用' : '禁用'}</Tag> },
          { title: '配置', dataIndex: 'config', key: 'config', ellipsis: true },
          {
            title: '操作', key: 'actions', render: (_: any, r: any) => (
              <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>编辑</Button>
            ),
          },
        ]}
      />
      <Modal title={editing ? '编辑规则' : '新建规则'} open={editOpen} onCancel={() => setEditOpen(false)} onOk={() => form.submit()} okText="保存" width={560}>
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item name="name" label="规则名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="type" label="类型" initialValue="THRESHOLD"><Input /></Form.Item>
          <Form.Item name="isActive" label="启用" valuePropName="checked" initialValue={1}>
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
          <Form.Item name="config" label="配置 (JSON)" rules={[{ required: true }]}>
            <Input.TextArea rows={4} style={{ fontFamily: 'monospace' }} placeholder='{"threshold":1100,"yellowRatio":0.9,"orangeRatio":1.0,"redRatio":1.1,"coolingTime":3600}' />
          </Form.Item>
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
  const [loading, setLoading] = useState(true)
  const [triggering, setTriggering] = useState(false)

  const refresh = useCallback(async () => {
    setLoading(true)
    try { setForecast(await api.get('/predict/forecast')); }
    catch { /* Flask 可能未启动 */ }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { refresh() }, [refresh])

  if (loading) return <Skeleton active paragraph={{ rows: 4 }} />
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
      {forecast ? (
        <Descriptions bordered size="small" column={2}
          items={[
            { label: '模型名称', children: forecast.model || 'N/A' },
            { label: '预测起点', children: forecast.forecastStartTime ? dayjs(forecast.forecastStartTime).format('MM-DD HH:mm') : 'N/A' },
            { label: '预测点数', children: forecast.predictions?.length || 0 },
            { label: '预测范围', children: forecast.predictions ? `${Math.min(...forecast.predictions).toFixed(0)} - ${Math.max(...forecast.predictions).toFixed(0)} MW` : 'N/A' },
          ]}
          labelStyle={{ color: '#888', background: '#0c0c0c' }} contentStyle={{ color: '#ccc', background: '#0e0e0e' }}
        />
      ) : <Empty description="暂无预测数据，请确认 Flask 推理服务已启动" />}
    </div>
  )
}

/* ══════════════════════════════════════════════
 *  Admin 主页面
 * ══════════════════════════════════════════════ */
const Admin = () => {
  const role = useAuthStore((s) => s.user?.role)

  const tabs = useMemo(() => {
    const items = []
    if (role === 'SYSTEM_ADMIN') {
      items.push({ key: 'users', label: <span><UserOutlined />用户管理</span>, children: <UsersPanel /> })
      items.push({ key: 'logs', label: <span><AuditOutlined />操作日志</span>, children: <LogsPanel /> })
      items.push({ key: 'health', label: <span><HeartOutlined />系统健康</span>, children: <HealthPanel /> })
    }
    if (role === 'OPERATOR' || role === 'SYSTEM_ADMIN') {
      items.push({ key: 'rules', label: <span><AlertOutlined />告警规则</span>, children: <RulesPanel /> })
      items.push({ key: 'model', label: <span><RobotOutlined />模型管理</span>, children: <ModelPanel /> })
    }
    items.push({ key: 'demo', label: <span><ThunderboltOutlined />演示控制</span>, children: <DemoPanel /> })
    return items
  }, [role])

  return (
    <div className="admin-root">
      <div className="admin-toolbar">
        <h1><SettingOutlined /> 系统管理 <span>//</span> <small>{role ? ROLE_LABELS[role] : ''}</small></h1>
      </div>
      <hr className="brutalist" />
      <Tabs defaultActiveKey={tabs[0]?.key} items={tabs} style={{ marginTop: 8 }} />
      <style>{`
        .admin-root { min-height: calc(100vh - 132px); }
        .admin-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
        .admin-toolbar h1 { margin: 0; color: #EAEAEA; font-size: 16px; font-weight: 900; }
        .admin-toolbar h1 > .anticon { color: #FF2A2A; margin-right: 8px; }
        .admin-toolbar h1 span { color: #666; margin: 0 6px; font-size: 12px; }
        .admin-toolbar h1 small { color: #888; font-size: 11px; font-weight: 400; }
        .demo-console { border: 1px solid #2A2A2A; margin-top: 14px; }
        .demo-console__header { min-height: 72px; padding: 14px 16px; border-bottom: 1px solid #2A2A2A; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
        .section-label { color: #888; font-size: 11px; font-family: monospace; }
        .section-value { color: #EAEAEA; font-size: 18px; font-weight: 700; margin-top: 4px; }
        .demo-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); }
        .demo-metrics > div { padding: 20px 16px; border-right: 1px solid #2A2A2A; }
        .demo-metrics > div:last-child { border-right: 0; }
        .demo-metrics span, .threshold-grid span { display: block; color: #888; font-size: 11px; }
        .demo-metrics strong { display: block; color: #EAEAEA; font-size: 28px; margin-top: 5px; }
        .demo-metrics small { color: #777; font-size: 11px; }
        .demo-console .ant-progress { display: block; padding: 0 16px; margin: 2px 0 18px; }
        .threshold-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; padding: 0 16px 16px; }
        .threshold-grid > div { border-left: 3px solid; padding: 8px 10px; background: #0E0E0E; }
        .threshold-grid strong { display: block; color: #EAEAEA; font-size: 14px; margin-top: 3px; }
        .demo-actions { padding: 14px 16px; border-top: 1px solid #2A2A2A; width: 100%; }
        @media (max-width: 720px) { .demo-metrics, .threshold-grid { grid-template-columns: 1fr; } .demo-metrics > div { border-right: 0; border-bottom: 1px solid #2A2A2A; } }
      `}</style>
    </div>
  )
}

export default Admin
