/**
 * View-only helpers to present arithmetic expressions with BODMAS-style parentheses
 * and optional operand value substitution. Does not change evaluation logic.
 */

export type FormulaOperandValue = {
  parameter?: string
  source?: string
  valueUsed?: string | null
}

type Tok =
  | { kind: 'num'; value: string }
  | { kind: 'id'; value: string }
  | { kind: 'op'; value: '+' | '-' | '*' | '/' }
  | { kind: 'lp' }
  | { kind: 'rp' }

type Ast =
  | { kind: 'num'; value: string }
  | { kind: 'id'; value: string }
  | { kind: 'bin'; op: '+' | '-' | '*' | '/'; left: Ast; right: Ast }

function tokenize(expression: string): Tok[] | null {
  const src = (expression ?? '').trim()
  if (!src) return null
  const out: Tok[] = []
  let i = 0
  while (i < src.length) {
    const ch = src[i]!
    if (/\s/.test(ch)) {
      i++
      continue
    }
    if (ch === '(') {
      out.push({ kind: 'lp' })
      i++
      continue
    }
    if (ch === ')') {
      out.push({ kind: 'rp' })
      i++
      continue
    }
    if (ch === '+' || ch === '-' || ch === '*' || ch === '/') {
      out.push({ kind: 'op', value: ch })
      i++
      continue
    }
    if (/[0-9.]/.test(ch)) {
      let j = i + 1
      while (j < src.length && /[0-9.]/.test(src[j]!)) j++
      out.push({ kind: 'num', value: src.slice(i, j) })
      i = j
      continue
    }
    if (/[A-Za-z_]/.test(ch)) {
      let j = i + 1
      while (j < src.length && /[A-Za-z0-9_]/.test(src[j]!)) j++
      out.push({ kind: 'id', value: src.slice(i, j) })
      i = j
      continue
    }
    return null
  }
  return out
}

function parseAst(tokens: Tok[]): Ast | null {
  let pos = 0
  const peek = () => tokens[pos]
  const take = () => tokens[pos++]

  function parseExpr(): Ast | null {
    let left = parseTerm()
    if (!left) return null
    while (true) {
      const t = peek()
      if (t?.kind !== 'op' || (t.value !== '+' && t.value !== '-')) break
      const opTok = take() as { kind: 'op'; value: '+' | '-' }
      const right = parseTerm()
      if (!right) return null
      left = { kind: 'bin', op: opTok.value, left, right }
    }
    return left
  }

  function parseTerm(): Ast | null {
    let left = parseFactor()
    if (!left) return null
    while (true) {
      const t = peek()
      if (t?.kind !== 'op' || (t.value !== '*' && t.value !== '/')) break
      const opTok = take() as { kind: 'op'; value: '*' | '/' }
      const right = parseFactor()
      if (!right) return null
      left = { kind: 'bin', op: opTok.value, left, right }
    }
    return left
  }

  function parseFactor(): Ast | null {
    const t = peek()
    if (!t) return null
    if (t.kind === 'lp') {
      take()
      const inner = parseExpr()
      if (!inner || peek()?.kind !== 'rp') return null
      take()
      return inner
    }
    if (t.kind === 'num') {
      take()
      return { kind: 'num', value: t.value }
    }
    if (t.kind === 'id') {
      take()
      return { kind: 'id', value: t.value }
    }
    // unary minus as 0 - x
    if (t.kind === 'op' && t.value === '-') {
      take()
      const right = parseFactor()
      if (!right) return null
      return { kind: 'bin', op: '-', left: { kind: 'num', value: '0' }, right }
    }
    return null
  }

  const ast = parseExpr()
  if (!ast || pos !== tokens.length) return null
  return ast
}

function precedence(op: '+' | '-' | '*' | '/'): number {
  return op === '+' || op === '-' ? 1 : 2
}

function printAst(node: Ast, parentPrec = 0): string {
  if (node.kind === 'num' || node.kind === 'id') return node.value
  const prec = precedence(node.op)
  const left = printAst(node.left, prec)
  const right = printAst(node.right, prec + (node.op === '-' || node.op === '/' ? 1 : 0))
  const body = `${left} ${node.op} ${right}`
  return prec < parentPrec ? `(${body})` : body
}

function printAstFullyParenthesized(node: Ast): string {
  if (node.kind === 'num' || node.kind === 'id') return node.value
  return `(${printAstFullyParenthesized(node.left)} ${node.op} ${printAstFullyParenthesized(node.right)})`
}

function substituteAst(node: Ast, values: Record<string, string>): Ast {
  if (node.kind === 'num') return node
  if (node.kind === 'id') {
    const v = values[node.value] ?? values[node.value.toUpperCase()]
    if (v != null && v !== '') return { kind: 'num', value: v }
    return node
  }
  return {
    kind: 'bin',
    op: node.op,
    left: substituteAst(node.left, values),
    right: substituteAst(node.right, values),
  }
}

function evalAst(node: Ast): number | null {
  if (node.kind === 'num') {
    const n = Number(node.value)
    return Number.isFinite(n) ? n : null
  }
  if (node.kind === 'id') return null
  const a = evalAst(node.left)
  const b = evalAst(node.right)
  if (a == null || b == null) return null
  switch (node.op) {
    case '+':
      return a + b
    case '-':
      return a - b
    case '*':
      return a * b
    case '/':
      return b === 0 ? null : a / b
  }
}

function collectEvalSteps(node: Ast, steps: string[]): number | null {
  if (node.kind === 'num') {
    const n = Number(node.value)
    return Number.isFinite(n) ? n : null
  }
  if (node.kind === 'id') return null
  const a = collectEvalSteps(node.left, steps)
  const b = collectEvalSteps(node.right, steps)
  if (a == null || b == null) return null
  let r: number | null = null
  switch (node.op) {
    case '+':
      r = a + b
      break
    case '-':
      r = a - b
      break
    case '*':
      r = a * b
      break
    case '/':
      r = b === 0 ? null : a / b
      break
  }
  if (r == null) return null
  const rounded = Math.round(r * 1e6) / 1e6
  steps.push(`(${formatNum(a)} ${node.op} ${formatNum(b)}) = ${formatNum(rounded)}`)
  return rounded
}

function formatNum(n: number): string {
  if (Number.isInteger(n)) return String(n)
  const s = n.toFixed(6).replace(/\.?0+$/, '')
  return s
}

export function operandValuesMap(operands?: FormulaOperandValue[] | null): Record<string, string> {
  const out: Record<string, string> = {}
  for (const op of operands ?? []) {
    const key = (op.parameter ?? '').trim()
    if (!key) continue
    if (op.valueUsed != null && String(op.valueUsed).trim() !== '') {
      out[key] = String(op.valueUsed).trim()
    }
  }
  return out
}

export type BodmasPreview = {
  /** Original expression text */
  original: string
  /** Expression with BODMAS-aware parentheses */
  parenthesized: string
  /** Fully parenthesized form (every binary step wrapped) */
  fullyParenthesized: string
  /** Expression with identifiers replaced by numeric values when available */
  withValues: string | null
  /** Step-by-step BODMAS evaluation lines when all values are numeric */
  steps: string[]
  /** Final numeric result when computable from values */
  computedResult: string | null
  parseOk: boolean
}

export function buildBodmasPreview(
  expression: string | undefined | null,
  operands?: FormulaOperandValue[] | null,
): BodmasPreview {
  const original = (expression ?? '').trim()
  const empty: BodmasPreview = {
    original: original || '—',
    parenthesized: original || '—',
    fullyParenthesized: original || '—',
    withValues: null,
    steps: [],
    computedResult: null,
    parseOk: false,
  }
  if (!original) return empty
  const tokens = tokenize(original)
  if (!tokens) return empty
  const ast = parseAst(tokens)
  if (!ast) return { ...empty, parseOk: false }

  const parenthesized = printAst(ast)
  const fullyParenthesized = printAstFullyParenthesized(ast)
  const values = operandValuesMap(operands)
  const hasValues = Object.keys(values).length > 0
  let withValues: string | null = null
  let steps: string[] = []
  let computedResult: string | null = null
  if (hasValues) {
    const sub = substituteAst(ast, values)
    withValues = printAstFullyParenthesized(sub)
    const stepBuf: string[] = []
    const result = collectEvalSteps(sub, stepBuf)
    steps = stepBuf
    if (result != null) computedResult = formatNum(result)
    else {
      const soft = evalAst(sub)
      if (soft != null) computedResult = formatNum(soft)
    }
  }

  return {
    original,
    parenthesized,
    fullyParenthesized,
    withValues,
    steps,
    computedResult,
    parseOk: true,
  }
}
