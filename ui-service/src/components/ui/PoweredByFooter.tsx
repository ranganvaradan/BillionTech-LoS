import { BrandLogo } from '@/components/BrandLogo'

type Props = {
  className?: string
}

export function PoweredByFooter({ className = '' }: Props) {
  return (
    <footer className={['bt-powered-by-footer', className].filter(Boolean).join(' ')}>
      <div className="flex flex-wrap items-center justify-center gap-1.5">
        <span>Powered by</span>
        <BrandLogo variant="billiontech" tone="dark" className="text-xs" />
      </div>
    </footer>
  )
}
