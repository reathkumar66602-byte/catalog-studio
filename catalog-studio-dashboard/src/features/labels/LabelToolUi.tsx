import { Link, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { FlipkartLogo, MeeshoLogo, MergePdfLogo } from "./logos";

export function ToolCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="mx-auto max-w-3xl rounded-3xl border border-slate-200 bg-white p-6 shadow-sm md:p-8 dark:border-slate-800 dark:bg-slate-900">
      <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">{title}</h1>
      {children}
    </div>
  );
}

export function FileRow({ logo, children }: { logo: ReactNode; children: ReactNode }) {
  return (
    <div className="mt-5 flex items-center gap-3 rounded-xl border border-slate-200 bg-white p-2 dark:border-slate-700 dark:bg-slate-950">
      {logo}
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}

const btn =
  "inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-800 hover:border-teal-600 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100";

function useToolPaths() {
  const { pathname } = useLocation();
  const publicMode = !pathname.startsWith("/tools/");
  return {
    flipkart: publicMode ? "/shipping-label-crop" : "/tools/labels/flipkart",
    meesho: publicMode ? "/meesho-shipping-label-crop" : "/tools/labels/meesho",
    merge: publicMode ? "/merge-pdf" : "/tools/labels/merge",
  };
}

export function FlipkartToolLink() {
  const paths = useToolPaths();
  return (
    <Link to={paths.flipkart} className={btn}>
      <FlipkartLogo className="h-8 w-8" />
      Flipkart Label Crop
    </Link>
  );
}

export function MeeshoToolLink() {
  const paths = useToolPaths();
  return (
    <Link to={paths.meesho} className={btn}>
      <MeeshoLogo className="h-8 w-8" />
      Meesho Label Crop
    </Link>
  );
}

export function MergeToolLink() {
  const paths = useToolPaths();
  return (
    <Link to={paths.merge} className={btn}>
      <MergePdfLogo className="h-8 w-8" />
      Merge PDF
    </Link>
  );
}
