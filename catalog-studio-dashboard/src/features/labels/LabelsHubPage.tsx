import { Link } from "react-router-dom";
import { Calculator, Chrome } from "lucide-react";
import { FlipkartLogo, MeeshoLogo, MergePdfLogo } from "./logos";
import { useSite } from "../site/useSite";

const tools = [
  {
    to: "/tools/labels/flipkart",
    label: "Flipkart Label Crop",
    text: "Drop the invoice, crop thermal or A4 labels, and download in the browser.",
    logo: <FlipkartLogo className="h-12 w-12" />,
  },
  {
    to: "/tools/labels/meesho",
    label: "Meesho Label Crop",
    text: "Crop Meesho shipping PDFs for a label printer or A4 sheet.",
    logo: <MeeshoLogo className="h-12 w-12" />,
  },
  {
    to: "/tools/labels/merge",
    label: "Merge PDF",
    text: "Combine several cropped files into one print-ready PDF.",
    logo: <MergePdfLogo className="h-12 w-12" />,
  },
  {
    to: "/tools/meesho-calculator",
    label: "Profit Calculator",
    text: "Estimate GST, returns, RTO, and monthly profit before you list.",
    logo: (
      <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-teal-700 text-white">
        <Calculator size={22} />
      </div>
    ),
  },
  {
    to: "/extension",
    label: "Chrome Extension",
    text: "Pair Autofill, then fill GST, HSN, and packer fields on Meesho.",
    logo: (
      <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-sky-800 text-white">
        <Chrome size={22} />
      </div>
    ),
  },
];

export function LabelsHubPage() {
  const site = useSite();
  return (
    <div className="mx-auto max-w-5xl space-y-8">
      <div>
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-teal-700">Workspace tools</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-900">Label tools</h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-500 md:text-base">
          Crop Flipkart and Meesho shipping labels, merge PDFs, estimate profit, and open the Chrome extension. Processing
          stays in your browser
          {site.client ? ` — the same workflow ${site.client.storeName} uses.` : "."}
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        {tools.map((tool) => (
          <Link
            key={tool.to}
            to={tool.to}
            className="flex gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-teal-600 hover:shadow-md"
          >
            {tool.logo}
            <div>
              <h2 className="font-semibold text-slate-900">{tool.label}</h2>
              <p className="mt-1 text-sm text-slate-500">{tool.text}</p>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
