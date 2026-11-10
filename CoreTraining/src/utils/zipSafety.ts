/**
 * Checks an Office (ZIP) file's central directory before a browser renderer
 * unpacks it. docx-preview has no limits of its own, so a small upload that
 * expands to gigabytes would freeze or crash the learner's tab.
 *
 * Only the directory is read; nothing is decompressed. Declared sizes can lie,
 * so this stops accidental and naive oversized files, not a crafted archive.
 */
export type ZipLimits = {
  maxEntries: number;
  maxTotalBytes: number;
  maxEntryBytes: number;
  /** Uncompressed / compressed, applied to entries larger than 1 MiB. */
  maxCompressionRatio: number;
};

export const DOCUMENT_ZIP_LIMITS: ZipLimits = {
  maxEntries: 5_000,
  maxTotalBytes: 300 * 1024 * 1024,
  maxEntryBytes: 100 * 1024 * 1024,
  maxCompressionRatio: 1_000,
};

export type ZipSafetyReason =
  | "not-zip"
  | "zip64"
  | "too-many-entries"
  | "entry-too-large"
  | "total-too-large"
  | "compression-ratio";

export class ZipSafetyError extends Error {
  readonly reason: ZipSafetyReason;

  constructor(reason: ZipSafetyReason) {
    super(`ZIP safety check failed: ${reason}`);
    this.name = "ZipSafetyError";
    this.reason = reason;
  }
}

const EOCD = 0x06054b50;
const CENTRAL_ENTRY = 0x02014b50;
const EOCD_SIZE = 22;
const ENTRY_SIZE = 46;
const MAX_COMMENT = 0xffff;
const RATIO_FLOOR = 1024 * 1024;

export function assertZipWithinLimits(
  data: ArrayBuffer,
  limits: ZipLimits = DOCUMENT_ZIP_LIMITS,
): void {
  const view = new DataView(data);
  const length = view.byteLength;
  let eocd = -1;
  for (
    let at = length - EOCD_SIZE;
    at >= Math.max(0, length - EOCD_SIZE - MAX_COMMENT);
    at--
  ) {
    if (view.getUint32(at, true) === EOCD) {
      eocd = at;
      break;
    }
  }
  if (eocd < 0) throw new ZipSafetyError("not-zip");

  const entries = view.getUint16(eocd + 10, true);
  const directorySize = view.getUint32(eocd + 12, true);
  const directoryOffset = view.getUint32(eocd + 16, true);
  if (
    entries === 0xffff ||
    directorySize === 0xffffffff ||
    directoryOffset === 0xffffffff
  )
    throw new ZipSafetyError("zip64");
  if (entries > limits.maxEntries)
    throw new ZipSafetyError("too-many-entries");
  if (directoryOffset + directorySize > eocd)
    throw new ZipSafetyError("not-zip");

  let at = directoryOffset;
  let total = 0;
  for (let index = 0; index < entries; index++) {
    if (at + ENTRY_SIZE > eocd || view.getUint32(at, true) !== CENTRAL_ENTRY)
      throw new ZipSafetyError("not-zip");
    const compressed = view.getUint32(at + 20, true);
    const uncompressed = view.getUint32(at + 24, true);
    if (compressed === 0xffffffff || uncompressed === 0xffffffff)
      throw new ZipSafetyError("zip64");
    if (uncompressed > limits.maxEntryBytes)
      throw new ZipSafetyError("entry-too-large");
    total += uncompressed;
    if (total > limits.maxTotalBytes)
      throw new ZipSafetyError("total-too-large");
    if (
      uncompressed > RATIO_FLOOR &&
      uncompressed > Math.max(1, compressed) * limits.maxCompressionRatio
    )
      throw new ZipSafetyError("compression-ratio");
    at +=
      ENTRY_SIZE +
      view.getUint16(at + 28, true) +
      view.getUint16(at + 30, true) +
      view.getUint16(at + 32, true);
  }
}
