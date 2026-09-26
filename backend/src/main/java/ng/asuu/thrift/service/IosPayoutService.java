package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.IosPayoutRequest;
import ng.asuu.thrift.domain.IosPayoutRequest.RequestStatus;
import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.repo.IosPayoutRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * A member's payout request never carries a typed-in amount - it's always exactly their current unpaid
 * IOS balance, computed here and nowhere else. Applying is the only member action; everything after
 * that (paying them externally, then posting the matching IOS2 debit through the monthly contribution
 * upload) happens outside this service, in the admin's own workflow - markPaid() just records that it
 * happened.
 */
@Service
public class IosPayoutService {
    private final IosPayoutRequestRepository requestRepository;
    private final LedgerService ledgerService;

    public IosPayoutService(IosPayoutRequestRepository requestRepository, LedgerService ledgerService) {
        this.requestRepository = requestRepository;
        this.ledgerService = ledgerService;
    }

    /** Unpaid IOS still free to apply for: the ledger balance minus whatever's already tied up in a
     *  still-pending request, so the same naira can't be requested twice before the first request is
     *  decided. */
    public long availableToApply(Long memberId) {
        long unpaid = ledgerService.unpaidIos(memberId);
        long pending = requestRepository.findByMemberIdAndStatus(memberId, RequestStatus.PENDING)
                .stream().mapToLong(IosPayoutRequest::getRequestedAmount).sum();
        return Math.max(0, unpaid - pending);
    }

    public List<IosPayoutRequest> forMember(Long memberId) {
        return requestRepository.findByMemberIdOrderByRequestedAtDesc(memberId);
    }

    public List<IosPayoutRequest> pending() {
        return requestRepository.findByStatusOrderByRequestedAtAsc(RequestStatus.PENDING);
    }

    @Transactional
    public IosPayoutRequest apply(Long memberId) {
        long amount = availableToApply(memberId);
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No unpaid IOS available to apply for.");
        }
        IosPayoutRequest req = new IosPayoutRequest();
        req.setMemberId(memberId);
        req.setRequestedAmount(amount);
        return requestRepository.save(req);
    }

    @Transactional
    public IosPayoutRequest markPaid(Member admin, Long requestId) {
        IosPayoutRequest req = require(requestId);
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request already decided");
        }
        req.setStatus(RequestStatus.PAID);
        req.setDecidedBy(admin.getId());
        req.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
        return requestRepository.save(req);
    }

    @Transactional
    public IosPayoutRequest reject(Member admin, Long requestId) {
        IosPayoutRequest req = require(requestId);
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request already decided");
        }
        req.setStatus(RequestStatus.REJECTED);
        req.setDecidedBy(admin.getId());
        req.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
        return requestRepository.save(req);
    }

    private IosPayoutRequest require(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
    }
}
