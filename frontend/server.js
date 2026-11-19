// Node.js server for the frontend.
// - Serves the built React app from dist/ (run `npm run build` first).
// - Forwards /api/* to the Java backend (HospitalServer, http://localhost:8080 by default).
// - Sends index.html for any other page so React Router links and refreshes work.
//
// Start: npm start           (http://localhost:3000)
// Options (environment variables): PORT, API_URL

import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.dirname(fileURLToPath(import.meta.url))
const DIST = path.join(ROOT, 'dist')
const PORT = Number(process.env.PORT) || 3000
const API = new URL(process.env.API_URL || 'http://localhost:8080')

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
}

if (!fs.existsSync(path.join(DIST, 'index.html'))) {
  console.error('No build found. Run "npm run build" first, then "npm start".')
  process.exit(1)
}

// ---------- /api → Java backend ----------

function proxy(req, res) {
  const upstream = http.request(
    {
      protocol: API.protocol,
      hostname: API.hostname,
      port: API.port,
      method: req.method,
      path: req.url,
      headers: { ...req.headers, host: API.host },
    },
    (up) => {
      res.writeHead(up.statusCode, up.headers)
      up.pipe(res)
    },
  )
  upstream.on('error', () => {
    if (res.headersSent) return res.destroy()
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
    res.end(JSON.stringify({ error: 'The backend is not running.' }))
  })
  req.pipe(upstream)
}

// ---------- static files ----------

function sendFile(res, file, cache) {
  const type = TYPES[path.extname(file).toLowerCase()] || 'application/octet-stream'
  res.writeHead(200, { 'Content-Type': type, 'Cache-Control': cache })
  fs.createReadStream(file).pipe(res)
}

function serveStatic(req, res) {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405, { Allow: 'GET, HEAD' })
    return res.end()
  }
  let pathname
  try {
    pathname = decodeURIComponent(new URL(req.url, 'http://x').pathname)
  } catch {
    res.writeHead(400)
    return res.end()
  }
  // Resolve inside dist/ only (no "../" escapes).
  const file = path.join(DIST, path.normalize(pathname))
  if (file.startsWith(DIST + path.sep) && fs.existsSync(file) && fs.statSync(file).isFile()) {
    // Vite puts a content hash in asset names, so those can be cached for a long time.
    const cache = pathname.startsWith('/assets/') ? 'public, max-age=31536000, immutable' : 'no-cache'
    return sendFile(res, file, cache)
  }
  // A missing file with an extension is a real 404; anything else is a React Router page.
  if (path.extname(pathname)) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
    return res.end('Not found')
  }
  sendFile(res, path.join(DIST, 'index.html'), 'no-cache')
}

http
  .createServer((req, res) => {
    if (req.url === '/api' || req.url.startsWith('/api/')) return proxy(req, res)
    serveStatic(req, res)
  })
  .listen(PORT, () => {
    console.log(`Haven Hospital frontend on http://localhost:${PORT} (API → ${API.origin})`)
  })
