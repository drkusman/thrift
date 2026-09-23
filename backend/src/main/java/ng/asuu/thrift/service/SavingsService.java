package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.domain.SavingsAmountChangeRequest;
import ng.asuu.thrift.domain.SavingsAmountChangeRequest.RequestStatus;
import ng.asuu.thrift.repo.MemberRepository;
import ng.asuu.thrift.repo.SavingsAmountChangeRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Monthly savings amount rule, per Karim: any amount >= 20,000 is allowed; amounts > 70,000
 *  require admin approval before they take effect. Legacy imported amounts outside this range
 *  are left untouched - this rule only governs new/changed amounts going forward. */
@Service
public class SavingsService {
    private static final long MIN_AMOUNT = 20_000;
    private static final long AUTO_APPROVE_CEILING = 70_000;

    private final SavingsAmountChangeRequestRepository requestRepository;
    private final MemberRepository memberRepository;

    public SavingsService(SavingsAmountChangeRequestRepository requestRepository, MemberRepository memberRepository) {
        this.requestRepository = requestRepository;
        this.memberRepository = memberRepository;
    }

    public List<SavingsAmountChangeRequest> forMember(Long memberId) {
        return requestRepository.findByMemberIdOrderByRequestedAtDesc(memberId);
    }

    public List<SavingsAmountChangeRequest> pending() {
        return requestRepository.findByStatusOrderByRequestedAtAsc(RequestStatus.PENDING);
    }

    @Transactional
    public SavingsAmountChangeRequest requestChange(Member principalMember, long requestedAmount) {
        if (requestedAmount < MIN_AMOUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Monthly savings amount must be at least " + MIN_AMOUNT);
        }
        SavingsAmountChangeRequest req = new SavingsAmountChangeRequest();
        req.setMemberId(principalMember.getId());
        req.setRequestedAmount(requestedAmount);

        if (requestedAmount <= AUTO_APPROVE_CEILING) {
            // principalMember comes from the session-cached security principal, not this transaction's
            // persistence context - re-fetch a managed instance so the update actually flushes to the DB.
            Member member = memberRepository.findById(principalMember.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
            req.setStatus(RequestStatus.APPROVED);
            req.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
            member.setMonthlySavingsAmount(requestedAmount);
            member.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            memberRepository.save(member);
        }
        return requestRepository.save(req);
    }

    @Transactional
    public SavingsAmountChangeRequest approve(Member admin, Long requestId) {
        SavingsAmountChangeRequest req = require(requestId);
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request already decided");
        }
        Member member = memberRepository.findById(req.getMemberId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        member.setMonthlySavingsAmount(req.getRequestedAmount());
        member.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        memberRepository.save(member);

        req.setStatus(RequestStatus.APPROVED);
        req.setDecidedBy(admin.getId());
        req.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
        return requestRepository.save(req);
    }

    @Transactional
    public SavingsAmountChangeRequest reject(Member admin, Long requestId) {
        SavingsAmountChangeRequest req = require(requestId);
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request already decided");
        }
        req.setStatus(RequestStatus.REJECTED);
        req.setDecidedBy(admin.getId());
        req.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
        return requestRepository.save(req);
    }

    private SavingsAmountChangeRequest require(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
    }
}
