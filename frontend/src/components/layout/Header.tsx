'use client';

import { Bell, Search, User, LogOut } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

export default function Header() {
  const router = useRouter();
  const [showUserMenu, setShowUserMenu] = useState(false);

  const handleLogout = () => {
    if (typeof window !== 'undefined') {
      localStorage.removeItem('los_token');
      localStorage.removeItem('los_user');
    }
    router.push('/login');
  };

  return (
    <header className="h-14 bg-card-bg border-b border-border flex items-center justify-between px-6 shrink-0">
      {/* Search */}
      <div className="flex items-center gap-2 bg-slate-50 rounded-lg px-3 py-1.5 w-80">
        <Search size={16} className="text-slate-400" />
        <input
          type="text"
          placeholder="Search applications, customers..."
          className="bg-transparent text-sm outline-none w-full placeholder-slate-400"
        />
      </div>

      {/* Right side */}
      <div className="flex items-center gap-4">
        {/* Notifications */}
        <button className="relative p-2 text-slate-500 hover:text-slate-700 transition-colors">
          <Bell size={18} />
          <span className="absolute top-1 right-1 w-2 h-2 bg-danger rounded-full" />
        </button>

        {/* User menu */}
        <div className="relative">
          <button
            onClick={() => setShowUserMenu(!showUserMenu)}
            className="flex items-center gap-2 text-sm text-slate-700 hover:text-slate-900 transition-colors"
          >
            <div className="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center">
              <User size={16} className="text-primary" />
            </div>
            <span className="font-medium">Admin</span>
          </button>

          {showUserMenu && (
            <div className="absolute right-0 top-full mt-2 w-48 bg-white rounded-lg shadow-lg border border-border py-1 z-50">
              <button
                onClick={handleLogout}
                className="flex items-center gap-2 w-full px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 transition-colors"
              >
                <LogOut size={14} />
                Sign Out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
