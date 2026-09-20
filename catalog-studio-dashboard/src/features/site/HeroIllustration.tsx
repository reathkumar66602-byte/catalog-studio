export function HeroIllustration() {
  return (
    <svg viewBox="0 0 520 420" className="mx-auto h-auto w-full max-w-xl" aria-hidden="true">
      <defs>
        <linearGradient id="cs-orbit" x1="0" x2="1" y1="0" y2="1">
          <stop offset="0%" stopColor="#38bdf8" stopOpacity="0.9" />
          <stop offset="100%" stopColor="#14b8a6" stopOpacity="0.4" />
        </linearGradient>
      </defs>
      <ellipse cx="270" cy="210" rx="168" ry="168" fill="none" stroke="url(#cs-orbit)" strokeWidth="1.4" opacity="0.7" />
      <ellipse cx="270" cy="210" rx="124" ry="124" fill="none" stroke="#38bdf8" strokeWidth="1" opacity="0.35" />
      <ellipse cx="270" cy="210" rx="82" ry="82" fill="none" stroke="#f472b6" strokeWidth="1" opacity="0.35" />
      <circle cx="402" cy="92" r="22" fill="#fff" />
      <text x="402" y="98" textAnchor="middle" fontSize="20" fontWeight="700" fill="#047BD6">
        F
      </text>
      <circle cx="430" cy="210" r="20" fill="#f43397" />
      <text x="430" y="216" textAnchor="middle" fontSize="14" fontWeight="700" fill="#fff">
        m
      </text>
      <circle cx="118" cy="128" r="18" fill="#2563eb" />
      <text x="118" y="134" textAnchor="middle" fontSize="14" fontWeight="700" fill="#fff">
        %
      </text>
      <circle cx="148" cy="292" r="18" fill="#0f766e" />
      <rect x="140" y="284" width="16" height="16" rx="2" fill="none" stroke="#ecfdf5" strokeWidth="1.6" />
      <circle cx="330" cy="338" r="16" fill="#fbbf24" />
      <path d="M323 338h14M330 331v14" stroke="#7c2d12" strokeWidth="1.8" />
      <rect x="188" y="168" width="168" height="118" rx="18" fill="#1e3a5f" />
      <circle cx="272" cy="148" r="36" fill="#f8d7b0" />
      <path d="M246 148c8-22 44-22 52 0" fill="#1e293b" />
      <rect x="214" y="196" width="116" height="92" rx="16" fill="#2563eb" />
      <rect x="232" y="228" width="80" height="52" rx="6" fill="#0f172a" />
      <rect x="240" y="236" width="64" height="36" rx="3" fill="#38bdf8" opacity="0.85" />
      <path d="M214 278c18 28 80 28 98 0" fill="#1d4ed8" />
      <rect x="248" y="280" width="18" height="36" rx="4" fill="#1e3a5f" />
      <rect x="278" y="280" width="18" height="36" rx="4" fill="#1e3a5f" />
      <rect x="244" y="312" width="26" height="10" rx="3" fill="#0f172a" />
      <rect x="274" y="312" width="26" height="10" rx="3" fill="#0f172a" />
    </svg>
  );
}
