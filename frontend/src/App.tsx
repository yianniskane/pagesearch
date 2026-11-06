import { useEffect, useState, type FormEvent } from 'react'
import { MarkedText } from './MarkedText'
import { parseMode, searchPages, type SearchMode, type SearchPage } from './search'

const MODES: { value: SearchMode; label: string }[] = [
  { value: 'all', label: 'All keywords' },
  { value: 'any', label: 'Any keyword' },
  { value: 'phrase', label: 'Exact phrase' },
]

interface Request {
  query: string
  mode: SearchMode
  page: number
  matchCase: boolean
  regex: boolean
}

function flag(value: string | null): boolean {
  return value === 'true'
}

function requestFromUrl(): Request | null {
  const params = new URLSearchParams(window.location.search)
  const query = params.get('q') ?? ''
  if (query.trim() === '') {
    return null
  }
  const page = Number(params.get('page') ?? '1')
  const regex = flag(params.get('regex')) || params.get('mode') === 'regex'
  const mode = parseMode(params.get('mode'))
  return {
    query,
    mode: mode === 'regex' ? 'all' : mode,
    page: Number.isInteger(page) && page >= 1 ? page : 1,
    matchCase: flag(params.get('matchCase')),
    regex,
  }
}

function writeUrl(request: Request) {
  const params = new URLSearchParams({
    q: request.query,
    mode: request.mode,
    page: String(request.page),
    matchCase: String(request.matchCase),
    regex: String(request.regex),
  })
  const next = `/?${params}`
  if (`${window.location.pathname}${window.location.search}` !== next) {
    window.history.pushState(null, '', next)
  }
}

function summary(result: SearchPage): string {
  if (result.totalHits === 0) {
    return 'No pages'
  }
  const pages = result.totalHits === 1 ? '1 page' : `${result.totalHits} pages`
  if (result.totalMatches == null) {
    return pages
  }
  const matches = result.totalMatches === 1 ? '1 match' : `${result.totalMatches} matches`
  return `${matches} in ${pages}`
}

export default function App() {
  const initial = requestFromUrl()
  const [query, setQuery] = useState(initial?.query ?? '')
  const [mode, setMode] = useState<SearchMode>(initial?.mode ?? 'all')
  const [matchCase, setMatchCase] = useState(initial?.matchCase ?? false)
  const [regex, setRegex] = useState(initial?.regex ?? false)
  const [result, setResult] = useState<SearchPage | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [searching, setSearching] = useState(false)

  async function run(request: Request) {
    setQuery(request.query)
    setMode(request.mode)
    setMatchCase(request.matchCase)
    setRegex(request.regex)
    setSearching(true)
    setError(null)
    try {
      const page = await searchPages(request)
      setResult(page)
    } catch (caught) {
      setResult(null)
      setError(caught instanceof Error ? caught.message : 'Search failed')
    } finally {
      setSearching(false)
    }
  }

  useEffect(() => {
    const current = requestFromUrl()
    if (current) {
      void run(current)
    }
    const onPop = () => {
      const request = requestFromUrl()
      if (request) {
        void run(request)
      } else {
        setResult(null)
        setError(null)
      }
    }
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const request = { query: query.trim(), mode, page: 1, matchCase, regex }
    if (request.query === '') {
      setError('Enter a word to search for')
      setResult(null)
      return
    }
    writeUrl(request)
    void run(request)
  }

  function goTo(nextPage: number) {
    if (!result) {
      return
    }
    const request = { query: result.query, mode: result.mode, page: nextPage, matchCase, regex }
    writeUrl(request)
    void run(request)
  }

  function searchAs(nextMode: SearchMode) {
    if (!result) {
      return
    }
    const request = { query: result.query, mode: nextMode, page: 1, matchCase, regex }
    setMode(nextMode)
    writeUrl(request)
    void run(request)
  }

  const pageCount = result ? Math.ceil(result.totalHits / result.pageSize) : 0

  return (
    <main>
      <h1>Page search</h1>
      <form onSubmit={submit}>
        <label htmlFor="q">Words</label>
        <div className="query">
          <input
            id="q"
            name="q"
            type="text"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            autoComplete="off"
          />
          <button type="submit" disabled={searching}>
            {searching ? 'Searching…' : 'Search'}
          </button>
        </div>
        <fieldset disabled={regex}>
          <legend>Match</legend>
          {MODES.map((item) => (
            <label key={item.value}>
              <input
                type="radio"
                name="mode"
                value={item.value}
                checked={mode === item.value}
                onChange={() => setMode(item.value)}
              />
              {item.label}
            </label>
          ))}
        </fieldset>
        <fieldset className="options">
          <legend>Options</legend>
          <label>
            <input
              type="checkbox"
              checked={matchCase}
              onChange={(event) => setMatchCase(event.target.checked)}
            />
            Match case
          </label>
          <label>
            <input
              type="checkbox"
              checked={regex}
              onChange={(event) => setRegex(event.target.checked)}
            />
            Regular expression
          </label>
        </fieldset>
      </form>

      {error && <p className="message">{error}</p>}

      {result && !error && (
        <section aria-live="polite">
          <p className="summary">{summary(result)}</p>
          {result.totalHits === 0 && !regex && result.mode === 'all' && (
            <p className="message">
              No page contains every word.{' '}
              <button type="button" onClick={() => searchAs('any')}>
                Search for any of these words
              </button>
            </p>
          )}
          {result.totalHits === 0 && !regex && result.mode === 'phrase' && (
            <p className="message">
              That phrase was not found.{' '}
              <button type="button" onClick={() => searchAs('all')}>
                Search for all of these words
              </button>
            </p>
          )}
          {result.totalHits === 0 && regex && <p className="message">No match.</p>}
          {result.totalHits === 0 && !regex && result.mode === 'any' && (
            <p className="message">No page contains these words.</p>
          )}
          <ol className="hits">
            {result.hits.map((hit) => (
              <li key={hit.id}>
                <h2>
                  <MarkedText text={hit.title || hit.id} spans={hit.titleHighlights} />
                </h2>
                <p className="id">{hit.id}</p>
                {hit.matchCount != null && (
                  <p className="terms">
                    {hit.matchCount} {hit.matchCount === 1 ? 'match' : 'matches'}
                  </p>
                )}
                {result.mode === 'any' && !regex && hit.matchedTerms.length > 0 && (
                  <p className="terms">Found {hit.matchedTerms.join(', ')}</p>
                )}
                <p className="snippet">
                  <MarkedText text={hit.snippet} spans={hit.snippetHighlights} />
                </p>
              </li>
            ))}
          </ol>
          {pageCount > 1 && (
            <nav className="pager" aria-label="Results pages">
              <button type="button" disabled={result.page <= 1 || searching} onClick={() => goTo(result.page - 1)}>
                Previous
              </button>
              <span>
                Page {result.page} of {pageCount}
              </span>
              <button
                type="button"
                disabled={result.page >= pageCount || searching}
                onClick={() => goTo(result.page + 1)}
              >
                Next
              </button>
            </nav>
          )}
        </section>
      )}
    </main>
  )
}
