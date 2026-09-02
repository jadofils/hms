#!/usr/bin/env python3
"""
Reverse proxy sitting between ngrok and the real HMS server. Forwards everything
EXCEPT /graphql and /graphiql, which it rejects outright with a 404 before the request
ever reaches the app -- this is what actually gets tunneled publicly via ngrok (see
docs/ngrok-deployment-guide.md), not the app's own port directly, so the block is
guaranteed regardless of which ngrok plan/feature set is available. GraphQL resolvers
aren't covered by AuthorizationAspect at all (see CLAUDE.md) -- every mutation is
reachable with zero authorization -- which is fine on a LAN of trusted machines and not
fine the moment this is reachable from the whole internet.

Standard-library only (http.server + urllib) -- nothing to install.

Configurable via environment variables (defaults match this project's own ports) so
this doesn't need editing if the app or the proxy itself ever needs to run on a
different port:
  HMS_APP_PORT    -- port the real app (Tomcat) is listening on. Default: 8080.
  HMS_PROXY_PORT  -- port this proxy itself listens on (the one ngrok should tunnel).
                     Default: 8081.
"""
import http.server
import socketserver
import urllib.request
import urllib.error
import os
import sys

APP_PORT = int(os.environ.get("HMS_APP_PORT", "8080"))
LISTEN_PORT = int(os.environ.get("HMS_PROXY_PORT", "8081"))
TARGET = f"http://localhost:{APP_PORT}"
BLOCKED_PREFIXES = ("/graphql", "/graphiql")

HOP_BY_HOP = {"connection", "keep-alive", "transfer-encoding", "upgrade",
              "proxy-authenticate", "proxy-authorization", "te", "trailers"}


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    """urllib auto-follows 3xx responses by default -- confirmed live: '/' (a real 302
    to Swagger UI's actual asset path) was coming back as a silent 200 with the final
    page's body, which would break the browser's own relative-asset resolution (it
    would still think it's at '/', not the redirected path). Returning None here makes
    urllib raise the 3xx as an HTTPError instead of following it, which the existing
    HTTPError branch below already relays as-is, Location header included -- letting
    the browser follow the redirect itself, correctly, through this same proxy."""
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


REDIRECT_SAFE_OPENER = urllib.request.build_opener(NoRedirectHandler)


class ProxyHandler(http.server.BaseHTTPRequestHandler):
    # HTTP/1.0, deliberately: BaseHTTPRequestHandler's HTTP/1.1 keep-alive handling
    # needs the client to be told the connection is closing (or Content-Length to line
    # up exactly with no proxying quirks) or the client hangs waiting for more bytes
    # that never come -- confirmed live (curl hung on a request the proxy's own log
    # showed had already succeeded). HTTP/1.0 closes the connection after every
    # response, sidestepping the whole issue -- irrelevant performance cost for a
    # manual-testing proxy like this one.
    protocol_version = "HTTP/1.0"

    def _blocked(self):
        path_only = self.path.split("?", 1)[0]
        return any(path_only == p or path_only.startswith(p + "/") for p in BLOCKED_PREFIXES)

    def _proxy(self):
        if self._blocked():
            body = (b'{"status":404,"error":"Not Found",'
                     b'"message":"This path is not available through the public tunnel."}')
            self.send_response(404)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        url = TARGET + self.path
        length = self.headers.get("Content-Length")
        data = self.rfile.read(int(length)) if length else None

        req = urllib.request.Request(url, data=data, method=self.command)
        for k, v in self.headers.items():
            if k.lower() not in ("host", "content-length"):
                req.add_header(k, v)

        try:
            resp = REDIRECT_SAFE_OPENER.open(req, timeout=30)
            status, headers, payload = resp.status, resp.getheaders(), resp.read()
        except urllib.error.HTTPError as e:
            status, headers, payload = e.code, e.headers.items(), e.read()
        except Exception as e:
            body = str(e).encode()
            self.send_response(502)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        self.send_response(status)
        for k, v in headers:
            if k.lower() not in HOP_BY_HOP:
                self.send_header(k, v)
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self): self._proxy()
    def do_POST(self): self._proxy()
    def do_PUT(self): self._proxy()
    def do_PATCH(self): self._proxy()
    def do_DELETE(self): self._proxy()
    def do_OPTIONS(self): self._proxy()
    def do_HEAD(self): self._proxy()

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))


if __name__ == "__main__":
    class ThreadingServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
        daemon_threads = True

    server = ThreadingServer(("0.0.0.0", LISTEN_PORT), ProxyHandler)
    print(f"Blocking proxy on :{LISTEN_PORT} -> {TARGET}  (blocking {BLOCKED_PREFIXES})")
    server.serve_forever()
