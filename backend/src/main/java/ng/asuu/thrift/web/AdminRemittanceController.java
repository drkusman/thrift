package ng.asuu.thrift.web;

import ng.asuu.thrift.service.RemittanceExportService;
import ng.asuu.thrift.service.RemittanceScheduleService;
import ng.asuu.thrift.web.dto.RemittanceRowDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/remittance")
public class AdminRemittanceController {
    private final RemittanceScheduleService scheduleService;
    private final RemittanceExportService exportService;

    public AdminRemittanceController(RemittanceScheduleService scheduleService, RemittanceExportService exportService) {
        this.scheduleService = scheduleService;
        this.exportService = exportService;
    }

    @GetMapping("/pay-points")
    public List<String> payPoints() {
        return scheduleService.payPoints();
    }

    @GetMapping
    public List<RemittanceRowDto> generate(@RequestParam String payPoint) {
        return scheduleService.generate(payPoint);
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportExcel(@RequestParam String payPoint, @RequestParam int year, @RequestParam int month) throws IOException {
        byte[] bytes = exportService.toExcel(payPoint, year, month, scheduleService.generate(payPoint));
        return FileDownload.excel(bytes, "remittance-" + payPoint + "-" + year + "-" + month + ".xlsx");
    }

    @GetMapping("/export.pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestParam String payPoint, @RequestParam int year, @RequestParam int month) throws IOException {
        byte[] bytes = exportService.toPdf(payPoint, year, month, scheduleService.generate(payPoint));
        return FileDownload.pdf(bytes, "remittance-" + payPoint + "-" + year + "-" + month + ".pdf");
    }
}
