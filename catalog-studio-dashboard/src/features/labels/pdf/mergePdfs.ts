import { PDFDocument } from "pdf-lib";
import { coercePdfBytes } from "./bytes";

export async function mergePdfs(files: Array<ArrayBuffer | Uint8Array>): Promise<Uint8Array> {
  if (files.length === 0) {
    throw new Error("Choose at least one PDF file");
  }
  const output = await PDFDocument.create();
  for (const bytes of files) {
    const source = await PDFDocument.load(coercePdfBytes(bytes), { ignoreEncryption: true });
    const copied = await output.copyPages(source, source.getPageIndices());
    copied.forEach((page) => output.addPage(page));
  }
  if (output.getPageCount() === 0) {
    throw new Error("The selected files did not contain any PDF pages");
  }
  return output.save();
}
