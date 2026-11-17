"""Tiny little-endian NBT reader/writer (Bedrock level.dat) - just enough to switch experiments on for tests."""
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(13)


class Tag:
    def __init__(self, t, v, sub=None):
        self.t, self.v, self.sub = t, v, sub

    def __repr__(self):
        return f"Tag({self.t},{self.v!r})"


def _rd(b, o, t):
    if t == BYTE: return Tag(t, struct.unpack_from("<b", b, o)[0]), o + 1
    if t == SHORT: return Tag(t, struct.unpack_from("<h", b, o)[0]), o + 2
    if t == INT: return Tag(t, struct.unpack_from("<i", b, o)[0]), o + 4
    if t == LONG: return Tag(t, struct.unpack_from("<q", b, o)[0]), o + 8
    if t == FLOAT: return Tag(t, struct.unpack_from("<f", b, o)[0]), o + 4
    if t == DOUBLE: return Tag(t, struct.unpack_from("<d", b, o)[0]), o + 8
    if t == BYTES:
        n = struct.unpack_from("<i", b, o)[0]; return Tag(t, bytes(b[o + 4:o + 4 + n])), o + 4 + n
    if t == STRING:
        n = struct.unpack_from("<H", b, o)[0]; return Tag(t, bytes(b[o + 2:o + 2 + n])), o + 2 + n
    if t == LIST:
        sub = b[o]; n = struct.unpack_from("<i", b, o + 1)[0]; o += 5; out = []
        for _ in range(n):
            x, o = _rd(b, o, sub); out.append(x)
        return Tag(t, out, sub), o
    if t == COMPOUND:
        d = {}
        while True:
            tt = b[o]; o += 1
            if tt == END: break
            n = struct.unpack_from("<H", b, o)[0]; name = bytes(b[o + 2:o + 2 + n]).decode("utf8"); o += 2 + n
            d[name], o = _rd(b, o, tt)
        return Tag(t, d), o
    if t == INTS:
        n = struct.unpack_from("<i", b, o)[0]; return Tag(t, list(struct.unpack_from(f"<{n}i", b, o + 4))), o + 4 + 4 * n
    if t == LONGS:
        n = struct.unpack_from("<i", b, o)[0]; return Tag(t, list(struct.unpack_from(f"<{n}q", b, o + 4))), o + 4 + 8 * n
    raise ValueError(t)


def _wr(x, out):
    t, v = x.t, x.v
    if t == BYTE: out += struct.pack("<b", v)
    elif t == SHORT: out += struct.pack("<h", v)
    elif t == INT: out += struct.pack("<i", v)
    elif t == LONG: out += struct.pack("<q", v)
    elif t == FLOAT: out += struct.pack("<f", v)
    elif t == DOUBLE: out += struct.pack("<d", v)
    elif t == BYTES: out += struct.pack("<i", len(v)) + v
    elif t == STRING: out += struct.pack("<H", len(v)) + v
    elif t == LIST:
        out += bytes([x.sub if v else (x.sub or 0)]) + struct.pack("<i", len(v))
        for e in v: _wr(e, out)
    elif t == COMPOUND:
        for k, e in v.items():
            kb = k.encode("utf8"); out += bytes([e.t]) + struct.pack("<H", len(kb)) + kb; _wr(e, out)
        out += b"\0"
    elif t == INTS: out += struct.pack("<i", len(v)) + struct.pack(f"<{len(v)}i", *v)
    elif t == LONGS: out += struct.pack("<i", len(v)) + struct.pack(f"<{len(v)}q", *v)


def load_level(path):
    raw = open(path, "rb").read()
    ver, n = struct.unpack_from("<ii", raw, 0)
    b = raw[8:8 + n]
    assert b[0] == COMPOUND
    ln = struct.unpack_from("<H", b, 1)[0]
    root, _ = _rd(b, 3 + ln, COMPOUND)
    return ver, root


def save_level(path, ver, root):
    out = bytearray(b"\x0a\0\0"); _wr(root, out)
    open(path, "wb").write(struct.pack("<ii", ver, len(out)) + bytes(out))


def byte(v): return Tag(BYTE, v)
