import { useState } from 'react';
import { Layout, Menu, Button, Typography, Space } from 'antd';
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const { Header, Sider, Content } = Layout;

export default function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { email, logout } = useAuth();
  const [collapsed, setCollapsed] = useState(false);

  const onLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const navigation = [
    { key: '/open/apps', label: '我的应用', match: (path: string) => path.startsWith('/open') },
    {
      key: '/knowledge/bases',
      label: '知识库管理',
      match: (path: string) => path.startsWith('/knowledge'),
    },
    { key: '/chat', label: '智能问答', match: (path: string) => path.startsWith('/chat') },
    {
      key: '/video/canvas',
      label: '视频创作画布',
      match: (path: string) => path.startsWith('/video'),
    },
  ];
  const activeItem = navigation.find((item) => item.match(location.pathname)) ?? navigation[0];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        trigger={null}
        width={220}
        collapsedWidth={72}
        theme="light"
        style={{
          background: '#fff',
          borderInlineEnd: '1px solid #f0f0f0',
        }}
      >
        <div className="sider-brand">
          {collapsed ? (
            <img
              src="/brand/liganex-logo.png"
              alt="Liganex"
              className="sider-logo-mark"
            />
          ) : (
            <img
              src="/brand/liganex-lockup.png"
              alt="Liganex"
              className="sider-logo-lockup"
            />
          )}
        </div>
        <Menu
          theme="light"
          mode="inline"
          selectedKeys={[activeItem.key]}
          items={navigation.map(({ key, label }) => ({ key, label }))}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#fff',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            paddingInline: 24,
            boxShadow: '0 1px 4px rgba(0,21,41,0.08)',
          }}
        >
          <Space>
            <Button
              type="text"
              aria-label="切换菜单"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed((v) => !v)}
            />
            <Typography.Title level={4} style={{ margin: 0 }}>
              {activeItem.label}
            </Typography.Title>
          </Space>
          <Space>
            <span style={{ color: 'rgba(0,0,0,0.65)' }}>{email}</span>
            <Button onClick={onLogout}>退出</Button>
          </Space>
        </Header>
        <Content className="studio-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
