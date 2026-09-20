import { FlipkartLogo } from "./logos";
import { MeeshoToolLink, MergeToolLink } from "./LabelToolUi";
import { LabelCropPage } from "./LabelCropPage";

export function FlipkartCropPage() {
  return (
    <LabelCropPage
      marketplace="flipkart"
      title="Quick Flipkart Shipping Label Crop Tool"
      description="Automatically detect Flipkart label size, drop the invoice portion, and prepare labels for a thermal printer or an A4 sheet. SKU-ready cropped labels download in one click."
      logo={<FlipkartLogo />}
      extraLinks={
        <>
          <MeeshoToolLink />
          <MergeToolLink />
        </>
      }
      defaultPrinter="a4"
    />
  );
}
