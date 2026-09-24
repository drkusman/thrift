package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.LoanType;

public record LoanTypeDto(Long id, String code, String name, double interestRate, String interestMethod,
                           int maxDurationMonths, Long minAmount, Long maxAmount, Integer maxConcurrentActive) {
    public static LoanTypeDto of(LoanType t) {
        return new LoanTypeDto(t.getId(), t.getCode(), t.getName(), t.getInterestRate(), t.getInterestMethod().name(),
                t.getMaxDurationMonths(), t.getMinAmount(), t.getMaxAmount(), t.getMaxConcurrentActive());
    }
}
