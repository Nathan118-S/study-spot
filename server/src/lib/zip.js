// Tiny ZIP reader (stored and deflate entries) for Google Calendar's export,
// which is a .zip of .ics files. No dependencies.
import zlib from 'node:zlib';

const MAX_ENTRIES = 100;
const MAX_TOTAL = 40 * 1024 * 1024;

/** Returns [{ name, data: Buffer }] for the files in a zip archive. */
export function readZip(buf) {
  // End of central directory: the last 22+ bytes, signature 0x06054b50.
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65557); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('That file isn’t a valid .zip');
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  if (count > MAX_ENTRIES) throw new Error('That .zip has too many files');
  const files = [];
  let total = 0;
  for (let n = 0; n < count; n++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error('That .zip is damaged');
    const method = buf.readUInt16LE(p + 10);
    const compSize = buf.readUInt32LE(p + 20);
    const size = buf.readUInt32LE(p + 24);
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    const local = buf.readUInt32LE(p + 42);
    const name = buf.slice(p + 46, p + 46 + nameLen).toString('utf8');
    p += 46 + nameLen + extraLen + commentLen;
    if (name.endsWith('/')) continue;
    total += size;
    if (total > MAX_TOTAL) throw new Error('That .zip is too large');
    if (buf.readUInt32LE(local) !== 0x04034b50) throw new Error('That .zip is damaged');
    const start = local + 30 + buf.readUInt16LE(local + 26) + buf.readUInt16LE(local + 28);
    const raw = buf.slice(start, start + compSize);
    let data;
    if (method === 0) data = raw;
    else if (method === 8) data = zlib.inflateRawSync(raw, { maxOutputLength: Math.max(size, 1) + 1024 });
    else continue;
    files.push({ name, data });
  }
  return files;
}
