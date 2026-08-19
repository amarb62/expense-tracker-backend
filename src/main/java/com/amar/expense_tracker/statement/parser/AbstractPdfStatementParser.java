package com.amar.expense_tracker.statement.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;

/**
 * Shared PDFBox text-extraction plumbing for {@link StatementParser}
 * implementations, so each bank-specific parser only has to own its own
 * layout/regex logic. {@code sortByPosition} defaults to {@code false}
 * (PDFBox's raw content-stream order) since that's what every parser written
 * so far has been tuned against; a new parser can opt into position-sorted
 * extraction (visual row order) if its statement's content stream doesn't
 * preserve row order on its own -- see {@link SbiBankStatementParser}.
 */
public abstract class AbstractPdfStatementParser implements StatementParser {

    protected String extractText(InputStream pdf) {
        return extractText(pdf, false);
    }

    protected String extractText(InputStream pdf, boolean sortByPosition) {
        try {
            byte[] bytes = pdf.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(sortByPosition);
                return stripper.getText(document);
            }
        } catch (IOException e) {
            throw new StatementParsingException("Unable to read PDF content", e);
        }
    }
}
