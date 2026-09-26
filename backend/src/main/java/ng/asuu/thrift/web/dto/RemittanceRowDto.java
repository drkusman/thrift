package ng.asuu.thrift.web.dto;

/** One member's expected salary deduction for a remittance period: their standing monthly savings
 *  amount plus whatever loan repayment they currently owe, summed for payroll. */
public record RemittanceRowDto(String regno, String fullName, long monthlySavings, long loanRepayment, long total) {
}
