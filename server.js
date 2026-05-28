const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;
const API_PORT = 8080;
const FRONTEND_DIR = path.join(__dirname, 'frontend');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js':   'application/javascript; charset=utf-8',
  '.css':  'text/css; charset=utf-8',
  '.json': 'application/json',
  '.png':  'image/png',
  '.jpg':  'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif':  'image/gif',
  '.svg':  'image/svg+xml',
  '.ico':  'image/x-icon',
};

function serveFile(res, filePath) {
  const ext = path.extname(filePath);
  fs.readFile(filePath, (err, data) => {
    if (err) {
      // SPA fallback: servir index.html para rutas que no son archivos
      fs.readFile(path.join(FRONTEND_DIR, 'index.html'), (err2, data2) => {
        if (err2) { res.writeHead(404); res.end('Not found'); return; }
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(data2);
      });
      return;
    }
    const headers = { 'Content-Type': MIME[ext] || 'application/octet-stream' };
    if (ext === '.css' || ext === '.js' || ext === '.html') {
      headers['Cache-Control'] = 'no-cache, no-store, must-revalidate';
      headers['Pragma'] = 'no-cache';
      headers['Expires'] = '0';
    }
    res.writeHead(200, headers);
    res.end(data);
  });
}

function proxyApi(req, res) {
  // Extract token from query string and set as Authorization header
  const url = new URL(req.url, 'http://localhost');
  const queryToken = url.searchParams.get('token');
  const headers = Object.assign({}, req.headers, { host: 'localhost:' + API_PORT });
  if (queryToken) {
    headers['authorization'] = 'Bearer ' + queryToken;
    console.log('[PROXY] Token extraído, longitud: ' + queryToken.length + ', primeros 20 chars: ' + queryToken.substring(0, 20));
  } else {
    console.log('[PROXY] NO SE ENCONTRÓ TOKEN en query string');
  }

  const options = {
    hostname: 'localhost',
    port: API_PORT,
    path: req.url,
    method: req.method,
    headers: headers,
  };

  const proxyReq = http.request(options, (proxyRes) => {
    const resHeaders = Object.assign({}, proxyRes.headers);
    resHeaders['access-control-allow-origin'] = '*';
    res.writeHead(proxyRes.statusCode, resHeaders);
    proxyRes.pipe(res);
  });

  proxyReq.on('error', (e) => {
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ error: 'Backend no disponible (' + e.message + ')' }));
  });

  req.pipe(proxyReq);
}

const server = http.createServer((req, res) => {
  // CORS para peticiones directas (si alguien accede desde otro origen)
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.url.startsWith('/api')) {
    proxyApi(req, res);
    return;
  }

  // Servir archivos estáticos
  const cleanPath = decodeURIComponent(req.url.split('?')[0]);
  let filePath = cleanPath === '/'
    ? path.join(FRONTEND_DIR, 'index.html')
    : path.join(FRONTEND_DIR, cleanPath);

  // Seguridad: evitar salir del directorio frontend
  if (!filePath.startsWith(FRONTEND_DIR)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }

  serveFile(res, filePath);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('');
  console.log('  Servidor iniciado!');
  console.log('  ─────────────────────────────');
  console.log('  Local:    http://localhost:' + PORT);
  console.log('  Red:      http://10.206.119.217:' + PORT);
  console.log('  ─────────────────────────────');
  console.log('  Backend:  http://localhost:' + API_PORT + '/api');
  console.log('  (Asegúrate de que el backend Java esté corriendo)');
  console.log('');
});
