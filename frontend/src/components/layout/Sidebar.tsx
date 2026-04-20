'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import {
  LayoutDashboard,
  FileText,
  Shield,
  Settings,
  CreditCard,
  GitBranch,
  Bell,
  BarChart3,
  LogOut,
  ChevronLeft,
  ChevronRight,
  Kanban,
  Users,
  Link2,
  UserCircle,
  Handshake,
  Key,
  Building2,
} from 'lucide-react';
import { useState } from 'react';

const NAV_ITEMS = [
  { href: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/applications', label: 'Applications', icon: FileText },
  { href: '/kanban', label: 'Pipeline', icon: Kanban },
  { href: '/transactions', label: 'Transactions', icon: CreditCard },
  { href: '/co-lending', label: 'Co-Lending', icon: Handshake },
  { href: '/collateral', label: 'Collateral', icon: Building2 },
  { href: '/notifications', label: 'Notifications', icon: Bell },
  { href: '/reports', label: 'Reports', icon: BarChart3 },
  { href: '/admin/users', label: 'User Management', icon: Users },
  { href: '/admin/aggregators', label: 'Aggregators', icon: Link2 },
  { href: '/admin/api-keys', label: 'API Keys', icon: Key },
  { href: '/portal', label: 'Customer Portal', icon: UserCircle },
];

export default function Sidebar() {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(false);

  return (
    <aside
      className={`flex flex-col bg-sidebar-bg text-sidebar-text transition-all duration-300 ${
        collapsed ? 'w-16' : 'w-60'
      }`}
    >
      {/* Logo */}
      <div className="flex items-center gap-3 px-4 py-5 border-b border-white/10">
        <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center text-white font-bold text-sm shrink-0">
          LOS
        </div>
        {!collapsed && (
          <span className="text-sm font-semibold tracking-wide whitespace-nowrap">
            BillionTech LOS
          </span>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 py-4 space-y-1 px-2">
        {NAV_ITEMS.map((item) => {
          const isActive = pathname.startsWith(item.href);
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors ${
                isActive
                  ? 'bg-primary text-white'
                  : 'text-slate-300 hover:bg-white/10 hover:text-white'
              }`}
              title={collapsed ? item.label : undefined}
            >
              <Icon size={18} className="shrink-0" />
              {!collapsed && <span>{item.label}</span>}
            </Link>
          );
        })}
      </nav>

      {/* Logout + Collapse */}
      <div className="border-t border-white/10">
        <button
          onClick={() => {
            localStorage.removeItem('los_token');
            localStorage.removeItem('los_refresh_token');
            localStorage.removeItem('los_user');
            window.location.href = '/login';
          }}
          className={`flex items-center gap-3 w-full px-3 py-2.5 text-sm text-slate-400 hover:bg-red-500/20 hover:text-red-300 transition-colors ${
            collapsed ? 'justify-center' : ''
          }`}
          title={collapsed ? 'Logout' : undefined}
        >
          <LogOut size={18} className="shrink-0" />
          {!collapsed && <span>Logout</span>}
        </button>
        <button
          onClick={() => setCollapsed(!collapsed)}
          className="flex items-center justify-center w-full py-3 text-slate-400 hover:text-white transition-colors"
        >
          {collapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
        </button>
      </div>
    </aside>
  );
}
