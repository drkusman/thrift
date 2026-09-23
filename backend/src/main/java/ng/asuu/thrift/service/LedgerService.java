package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.LedgerEntry;
import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.LedgerEntry.LedgerSource;
import ng.asuu.thrift.domain.LedgerEntry.TransCat;
import ng.asuu.thrift.repo.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public LedgerEntry post(Long memberId, long amount, LocalDate date, String description, String transType,
                             TransCat transCat, DrCr drCr, Long loanId, LedgerSource source, Long createdBy) {
        LedgerEntry e = new LedgerEntry();
        e.setTransCode(source.name() + "-" + UUID.randomUUID());
        e.setMemberId(memberId);
        e.setAmount(amount);
        e.setDate(date);
        e.setDescription(description);
        e.setTransType(transType);
        e.setTransCat(transCat);
        e.setDrCrStatus(drCr);
        e.setLoanId(loanId);
        e.setSource(source);
        e.setCreatedBy(createdBy);
        return ledgerEntryRepository.save(e);
    }

    public List<LedgerEntry> history(Long memberId) {
        return ledgerEntryRepository.findByMemberIdOrderByDateDesc(memberId);
    }

    public List<LedgerEntry> forLoan(Long loanId) {
        return ledgerEntryRepository.findByLoanIdOrderByDateAsc(loanId);
    }

    /** Net savings balance: sum of CR SAVINGS postings minus DR SAVINGS postings. */
    public long savingsBalance(Long memberId) {
        long balance = 0;
        for (LedgerEntry e : ledgerEntryRepository.findByMemberIdOrderByDateDesc(memberId)) {
            if (e.getTransCat() != TransCat.SAVINGS) continue;
            balance += e.getDrCrStatus() == DrCr.CR ? e.getAmount() : -e.getAmount();
        }
        return balance;
    }
}
