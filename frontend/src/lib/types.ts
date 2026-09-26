export type MemberOption = { id: number; regno: string; fullName: string };

export type Member = {
  id: number;
  regno: string;
  fullName: string;
  phone: string | null;
  email: string | null;
  sex: string | null;
  deptCode: string | null;
  factCode: string | null;
  payPoint: string | null;
  bankId: number | null;
  accountNo: string | null;
  status: "ACTIVE" | "WITHDRAWN" | "RETIRED" | "DECEASED" | "INACTIVE";
  monthlySavingsAmount: number;
  role: "MEMBER" | "FIN_SEC" | "ADMIN";
  mustChangePassword: boolean;
  lastSeenAt: string | null;
};

export type Bank = { id: number; bankCode: string; name: string; sortCode: string | null };

export type LoanType = {
  id: number;
  code: string;
  name: string;
  interestRate: number;
  interestMethod: "AT_SOURCE" | "BUILT_IN";
  maxDurationMonths: number;
  minAmount: number | null;
  maxAmount: number | null;
  maxConcurrentActive: number | null;
};

export type Loan = {
  id: number;
  loanCode: string | null;
  memberId: number;
  loanTypeId: number;
  requestedAmount: number;
  reason: string | null;
  durationMonths: number | null;
  interestAmount: number | null;
  disbursedAmount: number | null;
  totalRepayable: number | null;
  monthlyRepaymentAmount: number | null;
  balance: number | null;
  status: "PENDING" | "APPROVED" | "REJECTED" | "DISBURSED" | "RUNNING" | "PULSED" | "COMPLETED" | "DEFAULTED";
  appliedAt: string | null;
  decisionNote: string | null;
  guarantorOneId: number | null;
  guarantorTwoId: number | null;
  guarantorOneStatus: "PENDING" | "ACCEPTED" | "REJECTED";
  guarantorTwoStatus: "PENDING" | "ACCEPTED" | "REJECTED";
};

export type GuaranteeRequest = {
  loanId: number;
  loanCode: string | null;
  applicantMemberId: number;
  applicantName: string;
  applicantRegno: string;
  requestedAmount: number;
  reason: string | null;
  appliedAt: string | null;
  mySlot: 1 | 2;
  myStatus: "PENDING" | "ACCEPTED" | "REJECTED";
  otherGuarantorStatus: "PENDING" | "ACCEPTED" | "REJECTED";
};

export type ScheduleRow = {
  id: number;
  loanId: number;
  installmentNo: number;
  dueDate: string;
  amountDue: number;
  amountPaid: number;
  status: "PENDING" | "PAID" | "PARTIAL" | "OVERDUE";
};

export type LedgerEntry = {
  id: number;
  transCode: string | null;
  memberId: number;
  amount: number;
  date: string;
  description: string | null;
  transType: string | null;
  transCat: "SAVINGS" | "LOAN" | "INTEREST" | "FEES" | "OTHER";
  drCrStatus: "DR" | "CR";
  loanId: number | null;
  source: string;
};

export type SavingsRequest = {
  id: number;
  memberId: number;
  requestedAmount: number;
  status: "PENDING" | "APPROVED" | "REJECTED";
  requestedAt: string;
};

export type IosPayoutRequest = {
  id: number;
  memberId: number;
  requestedAmount: number;
  status: "PENDING" | "PAID" | "REJECTED";
  requestedAt: string;
};

export type AdminSummary = {
  totalMonthlySavings: number;
  totalLoanPayments: number;
  totalMonthlyDeductions: number;
  totalSavings: number;
  totalLoanBalance: number;
  totalEquity: number;
};

export type MonthPoint = { month: string; amount: number };
export type FiscalYearTrend = { label: string; points: MonthPoint[] };

export type TypeAmount = { type: string; amount: number };
export type FiscalYearBreakdown = { label: string; slices: TypeAmount[] };

export type Balance = {
  monthlySavings: number;
  loanPayments: number;
  monthlyDeductions: number;
  totalSavings: number;
  loanBalance: number;
  equity: number;
};
