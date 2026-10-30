const J = (d, s = 200) => new Response(JSON.stringify(d), { status: s, headers: { "content-type": "application/json" } });
const hex = (b) => [...new Uint8Array(b)].map((x) => x.toString(16).padStart(2, "0")).join("");
const rnd = (n) => hex(crypto.getRandomValues(new Uint8Array(n)));
const hash = async (p, salt) => hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(salt + p)));

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    const p = url.pathname;
    const db = env.DB;
    try {
      if (req.method === "POST" && (p === "/register" || p === "/login")) {
        const { username, password } = await req.json();
        const u = (username || "").trim().toLowerCase();
        if (!/^[a-z0-9_]{3,20}$/.test(u) || (password || "").length < 6) return J({ error: "invalid" }, 400);
        if (p === "/register") {
          const salt = rnd(8), token = rnd(24);
          try {
            await db.prepare("INSERT INTO users VALUES (?,?,?,?)").bind(u, salt, await hash(password, salt), token).run();
          } catch {
            return J({ error: "taken" }, 409);
          }
          return J({ token, username: u });
        }
        const r = await db.prepare("SELECT * FROM users WHERE username=?").bind(u).first();
        if (!r || r.hash !== (await hash(password, r.salt))) return J({ error: "bad" }, 401);
        return J({ token: r.token, username: u });
      }

      const tk = (req.headers.get("authorization") || "").slice(7);
      const me = tk ? (await db.prepare("SELECT username FROM users WHERE token=?").bind(tk).first())?.username : null;
      if (!me) return J({ error: "auth" }, 401);

      if (p === "/chats") {
        const { results } = await db
          .prepare("SELECT * FROM messages WHERE id IN (SELECT MAX(id) FROM messages WHERE sender=?1 OR receiver=?1 GROUP BY CASE WHEN sender=?1 THEN receiver ELSE sender END) ORDER BY id DESC")
          .bind(me)
          .all();
        return J(results);
      }

      if (p === "/messages") {
        const w = (url.searchParams.get("with") || "").toLowerCase();
        const a = Number(url.searchParams.get("after")) || 0;
        const { results } = await db
          .prepare("SELECT * FROM messages WHERE id>? AND ((sender=? AND receiver=?) OR (sender=? AND receiver=?)) ORDER BY id LIMIT 200")
          .bind(a, me, w, w, me)
          .all();
        return J(results);
      }

      if (p === "/send" && req.method === "POST") {
        const b = await req.json();
        const to = (b.to || "").toLowerCase();
        if (!(await db.prepare("SELECT 1 FROM users WHERE username=?").bind(to).first())) return J({ error: "nouser" }, 404);
        if (!b.text && !b.file_key) return J({ error: "empty" }, 400);
        await db
          .prepare("INSERT INTO messages (sender,receiver,text,file_key,file_name,file_type,ts) VALUES (?,?,?,?,?,?,?)")
          .bind(me, to, b.text || null, b.file_key || null, b.file_name || null, b.file_type || null, Date.now())
          .run();
        return J({ ok: 1 });
      }

      if (p === "/upload" && req.method === "PUT") {
        const key = rnd(16);
        await env.FILES.put(key, req.body, { httpMetadata: { contentType: req.headers.get("content-type") || "application/octet-stream" } });
        return J({ key });
      }

      if (p.startsWith("/file/")) {
        const o = await env.FILES.get(p.slice(6));
        if (!o) return J({ error: "nf" }, 404);
        return new Response(o.body, { headers: { "content-type": o.httpMetadata?.contentType || "application/octet-stream" } });
      }

      return J({ error: "nf" }, 404);
    } catch (e) {
      return J({ error: "server" }, 500);
    }
  },
};
