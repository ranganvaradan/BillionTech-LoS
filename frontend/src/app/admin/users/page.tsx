'use client';

import { useState } from 'react';
import {
  Users,
  Plus,
  Search,
  Edit2,
  Trash2,
  Shield,
  CheckCircle,
  XCircle,
  Lock,
  Eye,
  EyeOff,
} from 'lucide-react';

type User = {
  id: string;
  username: string;
  fullName: string;
  email: string;
  role: string;
  status: 'ACTIVE' | 'LOCKED' | 'INACTIVE';
  lastLogin: string;
  createdAt: string;
  loginAttempts: number;
  twoFactorEnabled: boolean;
};

const MOCK_USERS: User[] = [
  { id: '1', username: 'admin', fullName: 'System Administrator', email: 'admin@billiontech.in', role: 'ADMIN', status: 'ACTIVE', lastLogin: '2026-04-13T10:00:00Z', createdAt: '2026-01-01T00:00:00Z', loginAttempts: 0, twoFactorEnabled: true },
  { id: '2', username: 'rahul.mehta', fullName: 'Rahul Mehta', email: 'rahul.mehta@billiontech.in', role: 'CREDIT_MANAGER', status: 'ACTIVE', lastLogin: '2026-04-13T09:30:00Z', createdAt: '2026-02-15T00:00:00Z', loginAttempts: 0, twoFactorEnabled: true },
  { id: '3', username: 'priya.reddy', fullName: 'Priya Reddy', email: 'priya.reddy@billiontech.in', role: 'CREDIT_OFFICER', status: 'ACTIVE', lastLogin: '2026-04-12T16:45:00Z', createdAt: '2026-03-01T00:00:00Z', loginAttempts: 0, twoFactorEnabled: false },
  { id: '4', username: 'amit.singh', fullName: 'Amit Singh', email: 'amit.singh@billiontech.in', role: 'CREDIT_OFFICER', status: 'ACTIVE', lastLogin: '2026-04-13T08:15:00Z', createdAt: '2026-03-10T00:00:00Z', loginAttempts: 0, twoFactorEnabled: true },
  { id: '5', username: 'deepak.verma', fullName: 'Deepak Verma', email: 'deepak.verma@billiontech.in', role: 'OPERATIONS', status: 'LOCKED', lastLogin: '2026-04-10T14:00:00Z', createdAt: '2026-03-15T00:00:00Z', loginAttempts: 5, twoFactorEnabled: false },
  { id: '6', username: 'neha.gupta', fullName: 'Neha Gupta', email: 'neha.gupta@billiontech.in', role: 'VIEWER', status: 'ACTIVE', lastLogin: '2026-04-11T11:30:00Z', createdAt: '2026-04-01T00:00:00Z', loginAttempts: 0, twoFactorEnabled: false },
  { id: '7', username: 'ravi.kumar', fullName: 'Ravi Kumar', email: 'ravi.kumar@billiontech.in', role: 'CREDIT_OFFICER', status: 'INACTIVE', lastLogin: '2026-03-28T10:00:00Z', createdAt: '2026-02-01T00:00:00Z', loginAttempts: 0, twoFactorEnabled: false },
];

const ROLES = ['ADMIN', 'CREDIT_MANAGER', 'CREDIT_OFFICER', 'OPERATIONS', 'VIEWER'];

const ROLE_COLORS: Record<string, string> = {
  ADMIN: 'bg-red-100 text-red-700',
  CREDIT_MANAGER: 'bg-purple-100 text-purple-700',
  CREDIT_OFFICER: 'bg-blue-100 text-blue-700',
  OPERATIONS: 'bg-green-100 text-green-700',
  VIEWER: 'bg-gray-100 text-gray-700',
};

export default function UserManagementPage() {
  const [users, setUsers] = useState<User[]>(MOCK_USERS);
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newUser, setNewUser] = useState({ username: '', fullName: '', email: '', role: 'CREDIT_OFFICER', password: '' });
  const [showPassword, setShowPassword] = useState(false);

  const filteredUsers = users.filter((u) => {
    const matchSearch =
      !searchQuery ||
      u.fullName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      u.username.toLowerCase().includes(searchQuery.toLowerCase()) ||
      u.email.toLowerCase().includes(searchQuery.toLowerCase());
    const matchRole = !roleFilter || u.role === roleFilter;
    const matchStatus = !statusFilter || u.status === statusFilter;
    return matchSearch && matchRole && matchStatus;
  });

  const handleCreateUser = () => {
    const created: User = {
      id: String(users.length + 1),
      ...newUser,
      status: 'ACTIVE',
      lastLogin: 'Never',
      createdAt: new Date().toISOString(),
      loginAttempts: 0,
      twoFactorEnabled: false,
    };
    setUsers([...users, created]);
    setShowCreateModal(false);
    setNewUser({ username: '', fullName: '', email: '', role: 'CREDIT_OFFICER', password: '' });
  };

  const handleUnlock = (userId: string) => {
    setUsers(users.map((u) => (u.id === userId ? { ...u, status: 'ACTIVE' as const, loginAttempts: 0 } : u)));
  };

  const handleToggleStatus = (userId: string) => {
    setUsers(
      users.map((u) =>
        u.id === userId
          ? { ...u, status: u.status === 'ACTIVE' ? 'INACTIVE' as const : 'ACTIVE' as const }
          : u
      )
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">User Management</h1>
          <p className="text-sm text-gray-500 mt-1">
            {users.length} users &middot; {users.filter((u) => u.status === 'ACTIVE').length} active &middot;{' '}
            {users.filter((u) => u.status === 'LOCKED').length} locked
          </p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 flex items-center gap-2"
        >
          <Plus size={16} /> Create User
        </button>
      </div>

      {/* Filters */}
      <div className="flex gap-3 items-center">
        <div className="relative flex-1 max-w-md">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by name, username, or email..."
            className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          />
        </div>
        <select
          value={roleFilter}
          onChange={(e) => setRoleFilter(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
        >
          <option value="">All Roles</option>
          {ROLES.map((r) => (
            <option key={r} value={r}>{r.replace(/_/g, ' ')}</option>
          ))}
        </select>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
        >
          <option value="">All Status</option>
          <option value="ACTIVE">Active</option>
          <option value="LOCKED">Locked</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </div>

      {/* Users Table */}
      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        <table className="w-full">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">User</th>
              <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">Role</th>
              <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">Status</th>
              <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">2FA</th>
              <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">Last Login</th>
              <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 uppercase">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {filteredUsers.map((user) => (
              <tr key={user.id} className="hover:bg-gray-50">
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-xs font-bold">
                      {user.fullName.split(' ').map((n) => n[0]).join('')}
                    </div>
                    <div>
                      <p className="text-sm font-medium text-gray-900">{user.fullName}</p>
                      <p className="text-xs text-gray-500">{user.email}</p>
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${ROLE_COLORS[user.role] || 'bg-gray-100 text-gray-700'}`}>
                    {user.role.replace(/_/g, ' ')}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-1.5">
                    {user.status === 'ACTIVE' && <CheckCircle size={14} className="text-green-500" />}
                    {user.status === 'LOCKED' && <Lock size={14} className="text-red-500" />}
                    {user.status === 'INACTIVE' && <XCircle size={14} className="text-gray-400" />}
                    <span className={`text-xs font-medium ${
                      user.status === 'ACTIVE' ? 'text-green-700' : user.status === 'LOCKED' ? 'text-red-700' : 'text-gray-500'
                    }`}>
                      {user.status}
                    </span>
                  </div>
                  {user.status === 'LOCKED' && (
                    <p className="text-xs text-red-500 mt-0.5">{user.loginAttempts} failed attempts</p>
                  )}
                </td>
                <td className="px-4 py-3">
                  {user.twoFactorEnabled ? (
                    <span className="text-xs text-green-600 font-medium flex items-center gap-1">
                      <Shield size={12} /> Enabled
                    </span>
                  ) : (
                    <span className="text-xs text-gray-400">Disabled</span>
                  )}
                </td>
                <td className="px-4 py-3">
                  <p className="text-xs text-gray-600">
                    {user.lastLogin === 'Never'
                      ? 'Never'
                      : new Date(user.lastLogin).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}
                  </p>
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <button className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded" title="Edit">
                      <Edit2 size={14} />
                    </button>
                    {user.status === 'LOCKED' && (
                      <button
                        onClick={() => handleUnlock(user.id)}
                        className="px-2 py-1 text-xs bg-yellow-100 text-yellow-700 rounded hover:bg-yellow-200"
                      >
                        Unlock
                      </button>
                    )}
                    <button
                      onClick={() => handleToggleStatus(user.id)}
                      className={`px-2 py-1 text-xs rounded ${
                        user.status === 'ACTIVE'
                          ? 'bg-red-50 text-red-600 hover:bg-red-100'
                          : 'bg-green-50 text-green-600 hover:bg-green-100'
                      }`}
                    >
                      {user.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Create User Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md p-6 space-y-4">
            <h2 className="text-lg font-bold text-gray-900">Create New User</h2>
            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Full Name</label>
                <input
                  type="text"
                  value={newUser.fullName}
                  onChange={(e) => setNewUser({ ...newUser, fullName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  placeholder="Enter full name"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Username</label>
                <input
                  type="text"
                  value={newUser.username}
                  onChange={(e) => setNewUser({ ...newUser, username: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  placeholder="Enter username"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                <input
                  type="email"
                  value={newUser.email}
                  onChange={(e) => setNewUser({ ...newUser, email: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  placeholder="Enter email"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Role</label>
                <select
                  value={newUser.role}
                  onChange={(e) => setNewUser({ ...newUser, role: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                >
                  {ROLES.map((r) => (
                    <option key={r} value={r}>{r.replace(/_/g, ' ')}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
                <div className="relative">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={newUser.password}
                    onChange={(e) => setNewUser({ ...newUser, password: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm pr-10"
                    placeholder="Min 10 chars, uppercase, lowercase, digit, special"
                  />
                  <button
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400"
                  >
                    {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
                <p className="text-xs text-gray-400 mt-1">Must meet password policy (BR-14.6)</p>
              </div>
            </div>
            <div className="flex gap-3 pt-2">
              <button
                onClick={() => setShowCreateModal(false)}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={handleCreateUser}
                disabled={!newUser.username || !newUser.fullName || !newUser.email || !newUser.password}
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
              >
                Create User
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
