type BrandLogoProps = {
  /** Which wordmark to render. */
  variant?: 'billionloans' | 'billiontech'
  /** Text tone: 'light' for dark backgrounds (sidebar), 'dark' for light backgrounds. */
  tone?: 'light' | 'dark'
  /** Explicit height in px — preferred for sidebar sizing. */
  height?: number
  /**
   * Extra classes applied to the root element. Set the font size here (e.g. "text-xl");
   * the logo scales with the text so the lockup stays balanced.
   */
  className?: string
}

const BILLIONLOANS_LIGHT_BG = `${import.meta.env.BASE_URL}brand/BillionLoans_Logo_Final_noBG.png`
const BILLIONLOANS_DARK_BG = `${import.meta.env.BASE_URL}brand/BillionLoans_Logo_Final.png`
const BILLIONTECH_LOGO = `${import.meta.env.BASE_URL}brand/BillionTech_Logo_Final.png`

function logoHeightClass(className?: string): string {
  if (className?.includes('text-2xl')) return 'h-10'
  if (className?.includes('text-xs')) return 'h-3.5'
  if (className?.includes('text-lg')) return 'h-7'
  return 'h-8'
}

/**
 * Billionloans / BillionTech wordmark from bundled public brand assets.
 * Uses separate PNGs for light vs dark backgrounds (no CSS invert — that washed out the logo).
 */
export function BrandLogo({ variant = 'billiontech', tone = 'dark', height, className }: BrandLogoProps) {
  const alt = variant === 'billiontech' ? 'BillionTech' : 'Billionloans'
  const src =
    variant === 'billiontech'
      ? BILLIONTECH_LOGO
      : tone === 'light'
        ? BILLIONLOANS_DARK_BG
        : BILLIONLOANS_LIGHT_BG

  return (
    <span className={['inline-flex items-center leading-none', className].filter(Boolean).join(' ')}>
      <img
        src={src}
        alt={alt}
        className={height == null ? `${logoHeightClass(className)} w-auto max-w-[148px] object-contain object-left` : 'w-auto max-w-[148px] object-contain object-left'}
        style={height != null ? { height, width: 'auto' } : undefined}
      />
    </span>
  )
}
