// Minimal WebAuthn helpers: base64url, CBOR decode, authData parsing, COSE->JWK.
// Used by the passkeyRegisterFinish and passkeyLoginFinish backend functions.

export function b64uEncode(bytes) {
  const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let bin = "";
  for (let i = 0; i < arr.length; i++) bin += String.fromCharCode(arr[i]);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function b64uDecode(str) {
  const s = String(str).replace(/-/g, "+").replace(/_/g, "/");
  const pad = s.length % 4 ? 4 - (s.length % 4) : 0;
  const bin = atob(s + "=".repeat(pad));
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export function concatBytes(a, b) {
  const out = new Uint8Array(a.length + b.length);
  out.set(a, 0);
  out.set(b, a.length);
  return out;
}

export function bufsEqual(a, b) {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}

function readUint(data, offset, length) {
  let val = 0;
  for (let i = 0; i < length; i++) val = val * 256 + data[offset + i];
  return val;
}

// Decodes CBOR sufficient for WebAuthn attestation objects and COSE public keys.
// Maps are returned as Map (supports integer keys); byte strings as Uint8Array.
export function decodeCbor(input) {
  const data = input instanceof Uint8Array ? input : new Uint8Array(input);
  let pos = 0;

  function decode() {
    const first = data[pos++];
    const major = first >> 5;
    const ai = first & 0x1f;
    let len;
    if (ai < 24) len = ai;
    else if (ai === 24) len = data[pos++];
    else if (ai === 25) { len = readUint(data, pos, 2); pos += 2; }
    else if (ai === 26) { len = readUint(data, pos, 4); pos += 4; }
    else if (ai === 27) { len = readUint(data, pos, 8); pos += 8; }
    else throw new Error("Unsupported CBOR additional info: " + ai);

    switch (major) {
      case 0: return len;
      case 1: return -1 - len;
      case 2: { const out = data.slice(pos, pos + len); pos += len; return out; }
      case 3: { const out = new TextDecoder().decode(data.slice(pos, pos + len)); pos += len; return out; }
      case 4: { const arr = []; for (let i = 0; i < len; i++) arr.push(decode()); return arr; }
      case 5: { const m = new Map(); for (let i = 0; i < len; i++) { const k = decode(); const v = decode(); m.set(k, v); } return m; }
      case 6: { decode(); return decode(); }
      case 7:
        if (ai === 20) return false;
        if (ai === 21) return true;
        if (ai === 22) return null;
        if (ai === 23) return undefined;
        throw new Error("Unsupported CBOR simple/float");
    }
  }

  return decode();
}

export function parseAuthData(authData) {
  const data = authData instanceof Uint8Array ? authData : new Uint8Array(authData);
  if (data.length < 37) throw new Error("authData too short");
  const rpIdHash = data.slice(0, 32);
  const flags = data[32];
  const signCount = (data[33] << 24) | (data[34] << 16) | (data[35] << 8) | data[36];
  let offset = 37;
  let attested = null;
  if (flags & 0x40) {
    const aaguid = data.slice(offset, offset + 16); offset += 16;
    const credLen = (data[offset] << 8) | data[offset + 1]; offset += 2;
    const credId = data.slice(offset, offset + credLen); offset += credLen;
    const credPubKey = data.slice(offset);
    attested = { aaguid, credId, credPubKey };
  }
  return { rpIdHash, flags, signCount, attested };
}

// Convert a COSE-encoded public key (decoded Map) into a JWK plus import/verify params.
export function coseToJwk(cose) {
  const kty = cose.get(1);
  const alg = cose.get(3);
  if (kty === 2) {
    const crv = cose.get(-1);
    const crvName = crv === 1 ? "P-256" : crv === 2 ? "P-384" : crv === 3 ? "P-521" : "P-256";
    const x = cose.get(-2);
    const y = cose.get(-3);
    return {
      jwk: { kty: "EC", crv: crvName, x: b64uEncode(x), y: b64uEncode(y), ext: true },
      alg,
      importAlg: { name: "ECDSA", namedCurve: crvName },
      verifyAlg: { name: "ECDSA", hash: "SHA-256" },
    };
  }
  if (kty === 3) {
    const n = cose.get(-1);
    const e = cose.get(-2);
    return {
      jwk: { kty: "RSA", n: b64uEncode(n), e: b64uEncode(e), ext: true },
      alg,
      importAlg: { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      verifyAlg: { name: "RSASSA-PKCS1-v1_5" },
    };
  }
  throw new Error("Unsupported COSE key type: " + kty);
}