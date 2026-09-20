import { MeeshoLogo } from "./logos";
import { FlipkartToolLink, MergeToolLink } from "./LabelToolUi";
import { LabelCropPage } from "./LabelCropPage";

export function MeeshoCropPage() {
  return (
    <LabelCropPage
      marketplace="meesho"
      title="Quick Meesho Shipping Label Crop Tool"
      description="Crop Meesho shipping labels from Sub_Order_Labels PDFs with automatic width and length. Choose a label printer or A4 printer, then download a print-ready file."
      logo={<MeeshoLogo />}
      extraLinks={
        <>
          <FlipkartToolLink />
          <MergeToolLink />
        </>
      }
    />
  );
}
