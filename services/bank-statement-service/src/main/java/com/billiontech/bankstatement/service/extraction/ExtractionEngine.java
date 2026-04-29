package com.billiontech.bankstatement.service.extraction;

import com.billiontech.bankstatement.exception.StatementProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtractionEngine {

    private final PdfExtractor pdfExtractor;
    private final ExcelExtractor excelExtractor;
    private final CsvExtractor csvExtractor;

    public ParsedStatement extract(MultipartFile file, String password) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new StatementProcessingException("File name is required");
        }

        String lowerName = fileName.toLowerCase();
        try {
            InputStream inputStream = file.getInputStream();
            if (lowerName.endsWith(".pdf")) {
                return pdfExtractor.extract(inputStream, password);
            } else if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
                return excelExtractor.extract(inputStream, fileName);
            } else if (lowerName.endsWith(".csv")) {
                return csvExtractor.extract(inputStream);
            } else {
                throw new StatementProcessingException("Unsupported file format: " + fileName
                        + ". Supported formats: PDF, XLS, XLSX, CSV");
            }
        } catch (StatementProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new StatementProcessingException("Failed to process file: " + e.getMessage(), e);
        }
    }
}
