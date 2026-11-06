import type { TextSpan } from './search'

interface Piece {
  text: string
  marked: boolean
}

export function textPieces(text: string, spans: TextSpan[]): Piece[] {
  const ranges = spans
    .map((span) => ({
      start: Math.max(0, span.start),
      end: Math.min(text.length, span.end),
    }))
    .filter((span) => span.start < span.end)
    .sort((left, right) => left.start - right.start || left.end - right.end)

  const merged: { start: number; end: number }[] = []
  for (const span of ranges) {
    const last = merged[merged.length - 1]
    if (last && span.start <= last.end) {
      last.end = Math.max(last.end, span.end)
    } else {
      merged.push({ start: span.start, end: span.end })
    }
  }

  const pieces: Piece[] = []
  let cursor = 0
  for (const span of merged) {
    if (span.start > cursor) {
      pieces.push({ text: text.slice(cursor, span.start), marked: false })
    }
    pieces.push({ text: text.slice(span.start, span.end), marked: true })
    cursor = span.end
  }
  if (cursor < text.length) {
    pieces.push({ text: text.slice(cursor), marked: false })
  }
  return pieces
}

export function MarkedText({ text, spans }: { text: string; spans: TextSpan[] }) {
  return textPieces(text, spans).map((piece, index) =>
    piece.marked ? <mark key={index}>{piece.text}</mark> : <span key={index}>{piece.text}</span>,
  )
}
