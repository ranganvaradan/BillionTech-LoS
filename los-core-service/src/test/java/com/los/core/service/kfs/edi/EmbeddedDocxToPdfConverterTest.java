package com.los.core.service.kfs.edi;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDocxToPdfConverterTest {

  @TempDir
  Path tempDir;

  @Test
  void convert_producesNonEmptyPdf() throws Exception {
    Path docx = tempDir.resolve("sample.docx");
    try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      XWPFParagraph p = doc.createParagraph();
      XWPFRun r = p.createRun();
      r.setText("EDI KFS sample paragraph");
      doc.write(baos);
      Files.write(docx, baos.toByteArray());
    }

    byte[] pdf = new EmbeddedDocxToPdfConverter().convert(docx);

    assertTrue(pdf.length > 1000, "PDF should be larger than a trivial header");
    assertTrue(new String(pdf, 0, Math.min(5, pdf.length)).startsWith("%PDF"));
  }
}
