interface MetricCardProps {
  label: string;
  value: string;
  subtext?: string;
  color?: 'blue' | 'green' | 'red' | 'yellow' | 'gray';
}

const colorMap = {
  blue: 'bg-blue-50 border-blue-200',
  green: 'bg-green-50 border-green-200',
  red: 'bg-red-50 border-red-200',
  yellow: 'bg-yellow-50 border-yellow-200',
  gray: 'bg-gray-50 border-gray-200',
};

export default function MetricCard({ label, value, subtext, color = 'gray' }: MetricCardProps) {
  return (
    <div className={`rounded-xl border p-4 ${colorMap[color]}`}>
      <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">{label}</p>
      <p className="mt-1 text-xl font-semibold text-gray-900">{value}</p>
      {subtext && <p className="mt-0.5 text-xs text-gray-500">{subtext}</p>}
    </div>
  );
}
