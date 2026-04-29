interface ScoreGaugeProps {
  score: number | null | undefined;
  label: string;
  size?: 'sm' | 'md' | 'lg';
}

function scoreColor(score: number): string {
  if (score >= 75) return 'text-green-600';
  if (score >= 50) return 'text-yellow-600';
  if (score >= 25) return 'text-orange-600';
  return 'text-red-600';
}

function scoreBg(score: number): string {
  if (score >= 75) return 'bg-green-100';
  if (score >= 50) return 'bg-yellow-100';
  if (score >= 25) return 'bg-orange-100';
  return 'bg-red-100';
}

export default function ScoreGauge({ score, label, size = 'md' }: ScoreGaugeProps) {
  const val = score ?? 0;
  const sizes = { sm: 'w-20 h-20', md: 'w-28 h-28', lg: 'w-36 h-36' };
  const textSizes = { sm: 'text-lg', md: 'text-2xl', lg: 'text-3xl' };
  const labelSizes = { sm: 'text-xs', md: 'text-xs', lg: 'text-sm' };

  return (
    <div className="flex flex-col items-center gap-2">
      <div className={`${sizes[size]} rounded-full ${scoreBg(val)} flex items-center justify-center`}>
        <span className={`${textSizes[size]} font-bold ${scoreColor(val)}`}>
          {score != null ? Math.round(val) : '-'}
        </span>
      </div>
      <span className={`${labelSizes[size]} font-medium text-gray-600 text-center`}>{label}</span>
    </div>
  );
}
