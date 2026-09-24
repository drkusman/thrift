package ng.asuu.thrift.domain;

/** PULSED is a genuine legacy status carried over verbatim from the old system's own `runningstatus`
 *  column ("Pulses") - it appears on a real minority of historical loans (roughly 5%) distinctly from
 *  "Running" and "Complete", so it's kept as its own state rather than folded into either. */
public enum LoanStatus {
    PENDING, APPROVED, REJECTED, DISBURSED, RUNNING, PULSED, COMPLETED, DEFAULTED
}
