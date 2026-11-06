// Mail sandbox for the isolated test stack: accepts SMTP on 1025, exposes received mail
// as JSON on 8025 (GET /messages, DELETE /messages). Never relays anything.
import net from 'node:net'
import http from 'node:http'

let messages = []
net.createServer((socket) => {
  let inData = false
  let buffer = ''
  let current = { from: '', to: [], raw: '' }
  socket.write('220 test-mail ESMTP\r\n')
  socket.on('error', () => {})
  socket.on('data', (chunk) => {
    buffer += chunk.toString('utf8')
    let index
    while ((index = buffer.indexOf('\r\n')) >= 0) {
      const line = buffer.slice(0, index)
      buffer = buffer.slice(index + 2)
      if (inData) {
        if (line === '.') {
          inData = false
          messages.push({ ...current, receivedAt: new Date().toISOString() })
          current = { from: '', to: [], raw: '' }
          socket.write('250 OK\r\n')
        } else current.raw += line + '\n'
        continue
      }
      const upper = line.toUpperCase()
      if (upper.startsWith('EHLO') || upper.startsWith('HELO')) socket.write('250 test-mail\r\n')
      else if (upper.startsWith('MAIL FROM')) { current.from = line.slice(10).trim(); socket.write('250 OK\r\n') }
      else if (upper.startsWith('RCPT TO')) { current.to.push(line.slice(8).trim().replace(/[<>]/g, '')); socket.write('250 OK\r\n') }
      else if (upper === 'DATA') { inData = true; socket.write('354 End with .\r\n') }
      else if (upper === 'QUIT') { socket.write('221 Bye\r\n'); socket.end() }
      else socket.write('250 OK\r\n')
    }
  })
}).listen(1025, '0.0.0.0')

http.createServer((req, res) => {
  if (req.method === 'DELETE') messages = []
  res.setHeader('Content-Type', 'application/json')
  res.end(JSON.stringify(messages))
}).listen(8025, '0.0.0.0')
