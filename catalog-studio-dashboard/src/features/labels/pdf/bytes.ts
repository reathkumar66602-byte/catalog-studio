export function coercePdfBytes(data: ArrayBuffer | Uint8Array) {
  return data instanceof ArrayBuffer ? new Uint8Array(data) : new Uint8Array(data);
}
