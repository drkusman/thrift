type IconProps = { className?: string };

function Svg({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}
      strokeLinecap="round" strokeLinejoin="round" className={className ?? "w-5 h-5"}>
      {children}
    </svg>
  );
}

export const IconDashboard = (p: IconProps) => (
  <Svg {...p}><rect x="3" y="3" width="7" height="9" rx="1.5" /><rect x="14" y="3" width="7" height="5" rx="1.5" /><rect x="14" y="12" width="7" height="9" rx="1.5" /><rect x="3" y="16" width="7" height="5" rx="1.5" /></Svg>
);
export const IconLoan = (p: IconProps) => (
  <Svg {...p}><rect x="2" y="6" width="20" height="12" rx="2" /><circle cx="12" cy="12" r="3" /><path d="M6 12h.01M18 12h.01" /></Svg>
);
export const IconTransactions = (p: IconProps) => (
  <Svg {...p}><path d="M3 7h13l-3-3M21 17H8l3 3" /></Svg>
);
export const IconSettings = (p: IconProps) => (
  <Svg {...p}><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9c.13.36.36.68.66.92" /></Svg>
);
export const IconMembers = (p: IconProps) => (
  <Svg {...p}><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20c.9-3.6 3.6-5.5 6.5-5.5s5.6 1.9 6.5 5.5" /><circle cx="17.5" cy="9" r="2.7" /><path d="M15.5 14.2c2.4.3 4.3 2 5 5" /></Svg>
);
export const IconSavings = (p: IconProps) => (
  <Svg {...p}><path d="M19 8V6a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2v2M3 8h18v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8Z" /><path d="M16 13a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z" /></Svg>
);
export const IconUpload = (p: IconProps) => (
  <Svg {...p}><path d="M12 16V4M7 9l5-5 5 5" /><path d="M4 16v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3" /></Svg>
);
export const IconImport = (p: IconProps) => (
  <Svg {...p}><ellipse cx="12" cy="5" rx="8" ry="3" /><path d="M4 5v6c0 1.66 3.58 3 8 3s8-1.34 8-3V5" /><path d="M4 11v6c0 1.66 3.58 3 8 3s8-1.34 8-3v-6" /></Svg>
);
export const IconLogout = (p: IconProps) => (
  <Svg {...p}><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><path d="M16 17l5-5-5-5M21 12H9" /></Svg>
);
export const IconChevronLeft = (p: IconProps) => (
  <Svg {...p}><path d="M15 18l-6-6 6-6" /></Svg>
);
export const IconChevronRight = (p: IconProps) => (
  <Svg {...p}><path d="M9 18l6-6-6-6" /></Svg>
);
export const IconChevronDown = (p: IconProps) => (
  <Svg {...p}><path d="M6 9l6 6 6-6" /></Svg>
);
export const IconGuarantee = (p: IconProps) => (
  <Svg {...p}><path d="M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6l7-3Z" /><path d="M9 12l2 2 4-4" /></Svg>
);
export const IconRemittance = (p: IconProps) => (
  <Svg {...p}><rect x="4" y="2.5" width="16" height="19" rx="2" /><path d="M8 8h8M8 12h8M8 16h4" /></Svg>
);
export const IconEye = (p: IconProps) => (
  <Svg {...p}><path d="M1.5 12S5 5 12 5s10.5 7 10.5 7-3.5 7-10.5 7S1.5 12 1.5 12Z" /><circle cx="12" cy="12" r="3" /></Svg>
);
export const IconEyeOff = (p: IconProps) => (
  <Svg {...p}>
    <path d="M3 3l18 18" />
    <path d="M10.6 5.1A10.7 10.7 0 0 1 12 5c7 0 10.5 7 10.5 7a13.5 13.5 0 0 1-3.1 4.1M6.5 6.9C3.4 8.9 1.5 12 1.5 12S5 19 12 19a10.7 10.7 0 0 0 4.2-.86" />
    <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
  </Svg>
);
