package ng.asuu.thrift.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Shared attachment-response builders for the Excel/PDF exports across MeController and
 *  AdminLoanController, so the content type and Content-Disposition header aren't duplicated. */
final class FileDownload {
    private FileDownload() {}

    static ResponseEntity<byte[]> excel(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }

    static ResponseEntity<byte[]> pdf(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }

    /** For re-serving a file exactly as it was originally uploaded (e.g. a saved workbook), whose type
     *  isn't necessarily xlsx (an admin could've uploaded .xls) - the filename's own extension is what
     *  tells the browser what to do with it. */
    static ResponseEntity<byte[]> raw(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }
}
