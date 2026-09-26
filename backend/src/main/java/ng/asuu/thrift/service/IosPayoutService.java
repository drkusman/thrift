package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.IosPayoutRequest;
import ng.asuu.thrift.domain.IosPayoutRequest.RequestStatus;
import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.repo.IosPayoutRequestRepository;
import ng.asuu.thrift.repo.MemberRepository;
import ng.asuu.thrift.service.LedgerService.UnpaidIosCredit;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A member's payout request never carries a typed-in amount - it always claims one specific unpaid
 * IOS1 credit in full (see LedgerService.unpaidIosCredits), so a member applies for one year's interest
 * at a time rather than a blended lump sum across several. Applying is the only member action;
 * everything after that (paying them externally, then posting the matching IOS2 debit through the
 * monthly contribution upload) happens outside this service, in the admin's own workflow - markPaid()
 * just records that it happened.
 */
@Service
public class IosPayoutService {
    private final IosPayoutRequestRepository requestRepository;
    private final LedgerService ledgerService;
    private final MemberRepository memberRepository;

    public IosPayoutService(IosPayoutRequestRepository requestRepository, LedgerService ledgerService,
                             MemberRepository memberRepository) {
        this.requestRepository = requestRepository;
        this.ledgerService = ledgerService;
        this.memberRepository = memberRepository;
    }

    /** Every unpaid IOS1 credit still free to apply for, oldest first - excludes any already tied up in
     *  a still-pending request, so the same credit can't be requested twice before the first request is
     *  decided. */
    public List<UnpaidIosCredit> unpaidCredits(Long memberId) {
        Set<Long> alreadyPending = requestRepository.findByMemberIdAndStatus(memberId, RequestStatus.PENDING)
                .stream().map(IosPayoutRequest::getSourceLedgerEntryId).collect(Collectors.toSet());
        return ledgerService.unpaidIosCredits(memberId).stream()
                .filter(c -> !alreadyPending.contains(c.ledgerEntryId()))
                .toList();
    }

    public List<IosPayoutRequest> forMember(Long memberId) {
        return requestRepository.findByMemberIdOrderByRequestedAtDesc(memberId);
    }

    public List<IosPayoutRequest> pending() {
        return requestRepository.findByStatusOrderByRequestedAtAsc(RequestStatus.PENDING);
    }

    @Transactional
    public IosPayoutRequest apply(Long memberId, Long ledgerEntryId) {
        UnpaidIosCredit credit = unpaidCredits(memberId).stream()
                .filter(c -> c.ledgerEntryId().equals(ledgerEntryId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That IOS credit isn't available to apply for - it may already be requested, or fully paid."));
        IosPayoutRequest req = new IosPayoutRequest();
        req.setMemberId(memberId);
        req.setRequestedAmount(credit.amount());
        req.setSourceLedgerEntryId(credit.ledgerEntryId());
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

    /** Called when a Monthly Upload row posts an IOS2 debit - if that exact member+amount matches a
     *  still-pending request (oldest first, in case of an unlikely tie), it's auto-marked PAID rather
     *  than making the admin separately click "Mark as paid" for something they just uploaded. Returns
     *  the resolved request's id (for the posting to remember, so deleting the batch can undo this) or
     *  null if nothing matched - an IOS2 upload not tied to any tracked request is perfectly normal. */
    @Transactional
    public Long autoResolveOnUpload(Long memberId, long amount, Long adminId) {
        return requestRepository.findByMemberIdAndStatus(memberId, RequestStatus.PENDING).stream()
                .filter(r -> r.getRequestedAmount() == amount)
                .min(java.util.Comparator.comparing(IosPayoutRequest::getRequestedAt))
                .map(req -> {
                    req.setStatus(RequestStatus.PAID);
                    req.setDecidedBy(adminId);
                    req.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
                    return requestRepository.save(req).getId();
                })
                .orElse(null);
    }

    /** Reverses autoResolveOnUpload when the batch that triggered it gets deleted - only if the request
     *  is still exactly as that upload left it (PAID); if an admin has since taken some other action on
     *  it, leave it alone rather than clobbering that. */
    @Transactional
    public void revertToPending(Long requestId) {
        requestRepository.findById(requestId).ifPresent(req -> {
            if (req.getStatus() != RequestStatus.PAID) return;
            req.setStatus(RequestStatus.PENDING);
            req.setDecidedBy(null);
            req.setDecidedAt(null);
            requestRepository.save(req);
        });
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

    /** The pending queue, formatted so it can be fed straight back into Monthly Upload with KIND=IOS2
     *  (IOS? = Yes) once the admin has paid these out externally - REGNO/AMOUNT are what upload actually
     *  reads; NAME/DATE/DESCRIPTION are there so the admin can see what each row is before re-uploading it,
     *  same as the blank template's own readability-only columns. Uploading it back auto-resolves each
     *  matching request (see MonthlyContributionService's IOS2 branch), which is what actually removes it
     *  from this queue - this export alone doesn't change anything. */
    public byte[] exportPendingXlsx() throws IOException {
        List<IosPayoutRequest> rows = pending();
        Map<Long, Member> membersById = new HashMap<>();
        for (Member m : memberRepository.findAllById(rows.stream().map(IosPayoutRequest::getMemberId).toList())) {
            membersById.put(m.getId(), m);
        }

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Pending IOS Payouts");

            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);

            String[] headers = {"SN", "REGNO", "NAME", "AMOUNT", "KIND", "DATE", "DESCRIPTION"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            int r = 1;
            for (IosPayoutRequest req : rows) {
                Member member = membersById.get(req.getMemberId());
                Row row = sheet.createRow(r);
                row.createCell(0).setCellValue(r);
                row.createCell(1).setCellValue(member != null ? member.getRegno() : "");
                row.createCell(2).setCellValue(member != null ? member.getFullName() : "");
                row.createCell(3).setCellValue(req.getRequestedAmount());
                row.createCell(4).setCellValue("IOS2");
                row.createCell(5).setCellValue(req.getRequestedAt().toLocalDate().toString());
                row.createCell(6).setCellValue("Payment of Dividend - requested " + req.getRequestedAt().toLocalDate());
                r++;
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private IosPayoutRequest require(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
    }
}
