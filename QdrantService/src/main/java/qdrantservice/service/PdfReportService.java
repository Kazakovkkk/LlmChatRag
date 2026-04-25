package qdrantservice.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Slf4j
@Service
public class PdfReportService {

    // ─── Общий метод создания шрифта с поддержкой кириллицы ──
    private Font createFont(float size, int style, Color color) {
        try {
            BaseFont bf = BaseFont.createFont(
                    BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.EMBEDDED);
            return new Font(bf, size, style, color);
        } catch (Exception e) {
            return new Font(Font.HELVETICA, size, style, color);
        }
    }

    // ─── PDF 1: Очищенный текст после фильтрации ─────────────
    public byte[] generateCleanTextPdf(String cleanText, String filename) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // Заголовок
            Font titleFont = createFont(18, Font.BOLD, new Color(30, 30, 120));
            Font subtitleFont = createFont(11, Font.ITALIC, Color.GRAY);
            Font bodyFont = createFont(10, Font.NORMAL, Color.BLACK);

            Paragraph title = new Paragraph("Очищенный текст документа", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            Paragraph subtitle = new Paragraph(
                    "Файл: " + filename + " | Символов после фильтрации: " + cleanText.length(),
                    subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(20);
            doc.add(subtitle);

            // Разделитель
            doc.add(new LineSeparator());
            doc.add(Chunk.NEWLINE);

            // Текст
            Paragraph body = new Paragraph(cleanText, bodyFont);
            body.setLeading(14);
            doc.add(body);

            doc.close();
            log.info("PDF 1 (очищенный текст) создан | {} символов", cleanText.length());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Ошибка создания PDF очищенного текста: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ─── PDF 2: Предложения с косинусным сходством ───────────
    public byte[] generateSentencesSimilarityPdf(
            List<String> sentences,
            List<float[]> embeddings,
            String filename) {
        try {
            double schodstvo = 0.78;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font titleFont = createFont(18, Font.BOLD, new Color(30, 30, 120));
            Font subtitleFont = createFont(11, Font.ITALIC, Color.GRAY);
            Font numFont = createFont(10, Font.BOLD, new Color(50, 50, 180));
            Font sentFont = createFont(10, Font.NORMAL, Color.BLACK);
            Font simFont = createFont(9, Font.ITALIC, new Color(80, 80, 80));
            Font highSimFont = createFont(9, Font.ITALIC, new Color(0, 140, 0));
            Font lowSimFont = createFont(9, Font.ITALIC, new Color(180, 0, 0));

            // Заголовок
            Paragraph title = new Paragraph(
                    "Предложения и косинусное сходство", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            Paragraph subtitle = new Paragraph(
                    "Файл: " + filename + " | Предложений: " + sentences.size(),
                    subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(10);
            doc.add(subtitle);

            // Легенда
            Font legendFont = createFont(9, Font.NORMAL, Color.DARK_GRAY);
            Paragraph legend = new Paragraph(
                    "Легенда: сходство > 0.79 = высокое (зелёный) | < 0.79 = низкое (красный) = граница чанка",
                    legendFont);
            legend.setAlignment(Element.ALIGN_CENTER);
            legend.setSpacingAfter(15);
            doc.add(legend);

            doc.add(new LineSeparator());
            doc.add(Chunk.NEWLINE);

            // Каждое предложение
            for (int i = 0; i < sentences.size(); i++) {
                // Номер + предложение
                Phrase phrase = new Phrase();
                phrase.add(new Chunk((i + 1) + ". ", numFont));
                phrase.add(new Chunk(sentences.get(i), sentFont));
                Paragraph sentPara = new Paragraph(phrase);
                sentPara.setLeading(13);
                doc.add(sentPara);

                // Сходство с предыдущим
                if (i > 0 && embeddings != null && embeddings.size() > i) {
                    double simPrev = cosineSimilarity(
                            embeddings.get(i - 1), embeddings.get(i));
                    Font colorFont = simPrev >= schodstvo ? highSimFont : lowSimFont;
                    String arrow = simPrev >= schodstvo ? "↑" : "↓";
                    Paragraph simPara = new Paragraph(
                            "   " + arrow + " Сходство с предыдущим [" + (i) + "]: "
                                    + String.format("%.4f", simPrev)
                                    + (simPrev < schodstvo ? " ← ГРАНИЦА ЧАНКА" : ""),
                            colorFont);
                    simPara.setSpacingBefore(2);
                    doc.add(simPara);
                }

                // Сходство со следующим
                if (i < sentences.size() - 1 && embeddings != null
                        && embeddings.size() > i + 1) {
                    double simNext = cosineSimilarity(
                            embeddings.get(i), embeddings.get(i + 1));
                    Font colorFont = simNext >= schodstvo ? highSimFont : lowSimFont;
                    String arrow = simNext >= schodstvo ? "↑" : "↓";
                    Paragraph simPara = new Paragraph(
                            "   " + arrow + " Сходство со следующим [" + (i + 2) + "]: "
                                    + String.format("%.4f", simNext)
                                    + (simNext < schodstvo ? " ← ГРАНИЦА ЧАНКА" : ""),
                            colorFont);
                    simPara.setSpacingAfter(8);
                    doc.add(simPara);
                }

                doc.add(Chunk.NEWLINE);
            }

            doc.close();
            log.info("PDF 2 (предложения + сходство) создан | {} предложений",
                    sentences.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Ошибка создания PDF предложений: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ─── PDF 3: Финальные чанки ───────────────────────────────
    public byte[] generateChunksPdf(List<String> chunks, String filename) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font titleFont = createFont(18, Font.BOLD, new Color(30, 30, 120));
            Font subtitleFont = createFont(11, Font.ITALIC, Color.GRAY);
            Font numFont = createFont(12, Font.BOLD, new Color(50, 50, 180));
            Font chunkFont = createFont(10, Font.NORMAL, Color.BLACK);
            Font statsFont = createFont(9, Font.ITALIC, new Color(100, 100, 100));

            // Заголовок
            Paragraph title = new Paragraph("Семантические чанки", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            Paragraph subtitle = new Paragraph(
                    "Файл: " + filename + " | Чанков: " + chunks.size(),
                    subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(20);
            doc.add(subtitle);

            doc.add(new LineSeparator());
            doc.add(Chunk.NEWLINE);

            // Каждый чанк
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                int wordCount = chunk.split("\\s+").length;

                // Заголовок чанка
                Paragraph chunkTitle = new Paragraph(
                        "Чанк #" + (i + 1), numFont);
                chunkTitle.setSpacingBefore(10);
                doc.add(chunkTitle);

                // Статистика чанка
                Paragraph stats = new Paragraph(
                        "Слов: " + wordCount + " | Символов: " + chunk.length(),
                        statsFont);
                stats.setSpacingAfter(5);
                doc.add(stats);

                // Текст чанка в рамке
                PdfPTable table = new PdfPTable(1);
                table.setWidthPercentage(100);

                PdfPCell cell = new PdfPCell();
                cell.setPadding(10);
                cell.setBackgroundColor(new Color(245, 245, 255));
                cell.setBorderColor(new Color(150, 150, 200));
                cell.addElement(new Paragraph(chunk, chunkFont));
                table.addCell(cell);

                doc.add(table);
                doc.add(Chunk.NEWLINE);
            }

            // Итоговая статистика
            doc.add(new LineSeparator());
            doc.add(Chunk.NEWLINE);

            int totalWords = chunks.stream()
                    .mapToInt(c -> c.split("\\s+").length)
                    .sum();
            int avgWords = totalWords / Math.max(1, chunks.size());

            Font summaryFont = createFont(10, Font.BOLD, new Color(30, 30, 120));
            doc.add(new Paragraph("Итоговая статистика:", summaryFont));
            doc.add(new Paragraph("Всего чанков: " + chunks.size(), statsFont));
            doc.add(new Paragraph("Всего слов: " + totalWords, statsFont));
            doc.add(new Paragraph("Среднее слов на чанк: " + avgWords, statsFont));

            doc.close();
            log.info("PDF 3 (чанки) создан | {} чанков", chunks.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Ошибка создания PDF чанков: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}