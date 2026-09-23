package ng.asuu.thrift.domain;

/** AT_SOURCE ("AS"): interest deducted from disbursement, full requested amount still repaid.
 *  BUILT_IN ("BI"): full amount disbursed, interest added on top of the repayment total. */
public enum InterestMethod {
    AT_SOURCE, BUILT_IN
}
