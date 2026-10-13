/**
 * Evidence links are free text stored by users and shown to other users (F9-09). Only absolute http(s) URLs
 * without credentials are rendered as links; anything else (javascript:, data:, file:, relative paths) is shown
 * as plain text by the caller.
 */
export function safeHttpUrl(value) {
  if (typeof value !== 'string') return null;
  const candidate = value.trim();
  if (!candidate || candidate.length > 500) return null;
  try {
    const url = new URL(candidate);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;
    if (url.username || url.password) return null;
    return url.href;
  } catch {
    return null;
  }
}

/** Splits a stored evidence field into the safe http(s) links it contains. */
export function safeHttpUrls(value) {
  if (typeof value !== 'string') return [];
  return value
    .split(/[\s,;]+/)
    .map(safeHttpUrl)
    .filter(Boolean);
}
