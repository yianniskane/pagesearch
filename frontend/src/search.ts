export type SearchMode = 'all' | 'any' | 'phrase' | 'regex'

export interface TextSpan {
  start: number
  end: number
}

export interface SearchHit {
  id: string
  title: string
  titleHighlights: TextSpan[]
  path: string
  score: number
  matchedTerms: string[]
  snippet: string
  snippetHighlights: TextSpan[]
  matchCount: number | null
}

export interface SearchPage {
  query: string
  mode: SearchMode
  page: number
  pageSize: number
  totalHits: number
  totalMatches: number | null
  hits: SearchHit[]
}

export function parseMode(value: string | null): SearchMode {
  if (value === 'any' || value === 'phrase' || value === 'regex' || value === 'all') {
    return value
  }
  return 'all'
}

export interface SearchRequest {
  query: string
  mode: SearchMode
  page: number
  matchCase: boolean
  regex: boolean
}

export async function searchPages(request: SearchRequest): Promise<SearchPage> {
  const params = new URLSearchParams({
    q: request.query,
    mode: request.mode,
    page: String(request.page),
    matchCase: String(request.matchCase),
    regex: String(request.regex),
  })
  const response = await fetch(`/api/search?${params}`)
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null
    throw new Error(body?.message ?? `Search failed (${response.status})`)
  }
  return response.json() as Promise<SearchPage>
}
