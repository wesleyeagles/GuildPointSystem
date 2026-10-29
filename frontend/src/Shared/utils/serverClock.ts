// Timers (auction countdowns, waiting-list durations, header countdowns) follow the server clock, not
// the viewer's: a few seconds of skew between machines would otherwise show different values to each
// member, and a PC with a wrong UTC clock (e.g. Windows still applying the abolished Brazilian DST)
// would be a whole hour off.

let serverOffsetMs = 0
let hasSample = false

/** HTTP `Date` only has second resolution: it only replaces an earlier sample when this far off. */
const COARSE_TOLERANCE_MS = 2000

/** Call with the `serverTime` of any fresh server response. */
export function trackServerTime(serverTime: string | null | undefined) {
  if (!serverTime) return
  const parsed = new Date(serverTime).getTime()
  if (Number.isNaN(parsed)) return
  serverOffsetMs = parsed - Date.now()
  hasSample = true
}

/** Call with the HTTP `Date` header of any API response: gives every member an offset right after login. */
export function trackServerDateHeader(dateHeader: string | null | undefined) {
  if (!dateHeader) return
  const parsed = new Date(dateHeader).getTime()
  if (Number.isNaN(parsed)) return
  // The header truncates to the second: on average the server was ~0.5s past it.
  const offset = parsed + 500 - Date.now()
  if (hasSample && Math.abs(offset - serverOffsetMs) < COARSE_TOLERANCE_MS) return
  serverOffsetMs = offset
  hasSample = true
}

export function serverNow() {
  return Date.now() + serverOffsetMs
}
