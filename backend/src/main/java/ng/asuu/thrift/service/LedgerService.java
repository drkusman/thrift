package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.LedgerEntry;
import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.LedgerEntry.LedgerSource;
import ng.asuu.thrift.domain.LedgerEntry.TransCat;
import ng.asuu.thrift.repo.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
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

    /** One still-unpaid IOS1 credit - the source ledger entry a payout request claims against, so each
     *  year's interest is applied for on its own rather than blended into one lump sum. */
    public record UnpaidIosCredit(Long ledgerEntryId, LocalDate date, String description, long amount) {}

    private static class OpenCredit {
        final Long ledgerEntryId;
        final LocalDate date;
        final String description;
        long remaining;
        OpenCredit(Long ledgerEntryId, LocalDate date, String description, long remaining) {
            this.ledgerEntryId = ledgerEntryId;
            this.date = date;
            this.description = description;
            this.remaining = remaining;
        }
    }

    /** Every IOS1 credit not yet fully reversed by an IOS2 debit, oldest first - each is its own
     *  independently-claimable item (see IosPayoutService), not rolled into one total.
     * <p>
     * Matches IOS2 debits against IOS1 credits FIFO, oldest open credit first, walked oldest-first
     * overall. A legacy member's ledger snapshot can start mid-stream, so an IOS2 debit can appear with
     * no matching IOS1 credit in view (its pairing credit predates the snapshot) - such a debit is
     * simply discarded once there's nothing open left to consume, rather than left to cancel out a
     * later, genuinely-unpaid credit that has nothing to do with it. */
    public List<UnpaidIosCredit> unpaidIosCredits(Long memberId) {
        List<LedgerEntry> oldestFirst = new ArrayList<>(ledgerEntryRepository.findByMemberIdOrderByDateDesc(memberId));
        Collections.reverse(oldestFirst);

        Deque<OpenCredit> open = new ArrayDeque<>();
        for (LedgerEntry e : oldestFirst) {
            if ("IOS1".equals(e.getTransType()) && e.getDrCrStatus() == DrCr.CR) {
                open.addLast(new OpenCredit(e.getId(), e.getDate(), e.getDescription(), e.getAmount()));
            } else if ("IOS2".equals(e.getTransType()) && e.getDrCrStatus() == DrCr.DR) {
                long toConsume = e.getAmount();
                while (toConsume > 0 && !open.isEmpty()) {
                    OpenCredit credit = open.peekFirst();
                    long consumed = Math.min(credit.remaining, toConsume);
                    credit.remaining -= consumed;
                    toConsume -= consumed;
                    if (credit.remaining <= 0) open.pollFirst();
                }
            }
        }
        return open.stream().map(c -> new UnpaidIosCredit(c.ledgerEntryId, c.date, c.description, c.remaining)).toList();
    }
}
