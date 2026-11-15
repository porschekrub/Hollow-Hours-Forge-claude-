#!/usr/bin/env python3
"""A fake AI server for Verity Pal's tests (no real AI key needed in CI).

- OpenAI-style chat (Verity JE's AI, provider OPENAI with this as its endpoint): the first question asks for Verity
  Pal's tool where_is("village"); once the tool answered, it says a line in Thai. Every request is kept in
  requests.jsonl so the workflow can check the prompt and the tools Verity's AI was given.
- Gemini generateContent: a speech request gets half a second of a tone back (Verity Pal's Thai voice), anything
  else gets "เวริตี้ หมู่บ้านอยู่ไหน" (her ears).
Usage: fake_ai.py PORT LOGFILE
"""
import base64, json, math, struct, sys, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 18787
LOG = sys.argv[2] if len(sys.argv) > 2 else "requests.jsonl"


def tone(rate=24000, secs=0.5):
    return b"".join(struct.pack("<h", int(8000 * math.sin(2 * math.pi * 440 * i / rate))) for i in range(int(rate * secs)))


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send(self, obj, code=200):
        b = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self):
        self.send({"object": "list", "data": [{"id": "fake-model", "object": "model"}], "models": [{"name": "models/fake"}]})

    def do_POST(self):
        n = int(self.headers.get("content-length", 0))
        raw = self.rfile.read(n)
        try:
            body = json.loads(raw)
        except Exception:
            body = {}
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(json.dumps({"t": time.time(), "path": self.path, "body": body}, ensure_ascii=False) + "\n")
        if "generateContent" in self.path:
            gen = body.get("generationConfig", {})
            if "AUDIO" in gen.get("responseModalities", []):
                data = base64.b64encode(tone()).decode()
                self.send({"candidates": [{"content": {"parts": [{"inlineData": {"mimeType": "audio/L16;codec=pcm;rate=24000", "data": data}}]}}]})
            else:
                self.send({"candidates": [{"content": {"parts": [{"text": "เวริตี้ หมู่บ้านอยู่ไหน"}]}}]})
            return
        if self.path.endswith("/chat/completions"):
            msgs = body.get("messages", [])
            answered = any(m.get("role") == "tool" for m in msgs[-3:])
            tools = [t.get("function", {}).get("name") for t in body.get("tools", [])]
            last = next((m for m in reversed(msgs) if m.get("role") == "user"), {})
            content = last.get("content")
            text = content if isinstance(content, str) else json.dumps(content, ensure_ascii=False)
            if "TEST" in text and not answered and "where_is" in tools:
                msg = {"role": "assistant", "content": None, "tool_calls": [
                    {"id": "call_1", "type": "function", "function": {"name": "where_is", "arguments": json.dumps({"place": "village"})}}]}
                finish = "tool_calls"
            else:
                msg = {"role": "assistant", "content": "[crazy] หมู่บ้านอยู่ทางนู้นไง หาเองไม่เป็นเหรอ"}
                finish = "stop"
            self.send({"id": "chatcmpl-fake", "object": "chat.completion", "created": int(time.time()), "model": body.get("model", "fake"),
                       "choices": [{"index": 0, "message": msg, "finish_reason": finish}],
                       "usage": {"prompt_tokens": 10, "completion_tokens": 10, "total_tokens": 20}})
            return
        self.send({"error": {"message": "unknown path " + self.path}}, 404)


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", PORT), H).serve_forever()
