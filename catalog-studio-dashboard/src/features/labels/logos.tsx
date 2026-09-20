import type { SVGProps } from "react";

function Svg(props: SVGProps<SVGSVGElement>) {
  return <svg viewBox="0 0 48 48" aria-hidden="true" {...props} />;
}

export function FlipkartLogo({ className = "h-12 w-12" }: { className?: string }) {
  return (
    <div className={`flex shrink-0 items-center justify-center overflow-hidden rounded-xl bg-[#ffe500] ${className}`} aria-hidden="true">
      <Svg className="h-[82%] w-[82%]">
        <path d="M16 18h16a3 3 0 0 1 3 3v18a4 4 0 0 1-4 4H17a4 4 0 0 1-4-4V21a3 3 0 0 1 3-3z" fill="#047BD6" />
        <path d="M18 18v-3a6 6 0 0 1 12 0v3" fill="none" stroke="#047BD6" strokeWidth="2.6" strokeLinecap="round" />
        <path d="M22 27c0-2.2 1.7-3.6 4.2-3.6 2.4 0 3.8 1.3 3.8 3.1 0 3.4-7.8 2.6-7.8 7.2h7.6" fill="none" stroke="#ffe500" strokeWidth="2.3" strokeLinecap="round" />
        <path d="M22 27v11" fill="none" stroke="#ffe500" strokeWidth="2.3" strokeLinecap="round" />
      </Svg>
    </div>
  );
}

export function MeeshoLogo({ className = "h-12 w-12" }: { className?: string }) {
  return (
    <div className={`flex shrink-0 items-center justify-center overflow-hidden rounded-xl bg-[#f43397] ${className}`} aria-hidden="true">
      <svg viewBox="0 0 48 48" className="h-[86%] w-[92%]">
        <text
          x="24"
          y="30"
          textAnchor="middle"
          fill="#fff"
          fontSize="11"
          fontWeight="700"
          fontFamily="system-ui,Segoe UI,sans-serif"
        >
          meesho
        </text>
      </svg>
    </div>
  );
}

export function MergePdfLogo({ className = "h-12 w-12" }: { className?: string }) {
  return (
    <div className={`flex shrink-0 flex-col items-center justify-center rounded-xl bg-zinc-900 text-white ${className}`} aria-hidden="true">
      <span className="text-[8px] font-bold uppercase leading-tight tracking-wide">Merge</span>
      <span className="text-[8px] font-bold uppercase leading-tight tracking-wide">PDF</span>
    </div>
  );
}
