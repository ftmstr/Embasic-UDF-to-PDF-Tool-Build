package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.awt.Color;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.TransferHandler;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.pdfbox.text.TextPosition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PublicDesktopSmokeTest {
    @TempDir Path root;

    private static String xml(String value) {
        return "<template format_id='1.0'><content>" + value + "</content>"
            + "<elements><paragraph><content startOffset='0' length='" + value.length() + "'/>"
            + "</paragraph></elements></template>";
    }
    private Path udf(String name, String value) throws Exception {
        Path p = root.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(p))) {
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(xml(value).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return p;
    }

    @Test void offscreenPdfThumbnailsWaitUntilTheirCardsBecomeVisible() {
        List<Integer> cards = new ArrayList<>();
        for (int i = 0; i < 160; i++) cards.add(i);
        VirtualGridLayout layout = new VirtualGridLayout(161, 900, ThumbnailSizing.DEFAULT_WIDTH);
        var requested = new java.util.LinkedHashSet<Integer>();
        var firstWindow = layout.visible(new java.awt.Rectangle(0, 0, 900, 600));
        DesktopApp.scheduleVisiblePreviews(cards, firstWindow,
                index -> !requested.contains(index), requested::add);
        assertFalse(requested.isEmpty(), "Visible UDFs must get thumbnails");
        assertTrue(requested.size() < 160, "Offscreen PDFs must not be eagerly rendered");
        assertFalse(requested.contains(159), "Last UDF must remain deferred");
        int before = requested.size();
        DesktopApp.scheduleVisiblePreviews(cards, firstWindow,
                index -> !requested.contains(index), requested::add);
        assertEquals(before, requested.size(), "Repeated viewport refresh must not resubmit");
        var lastWindow = layout.visible(new java.awt.Rectangle(
                0, layout.bounds(159).y, 900, 600));
        DesktopApp.scheduleVisiblePreviews(cards, lastWindow,
                index -> !requested.contains(index), requested::add);
        assertTrue(requested.contains(159), "Scrolling must request the previously deferred PDF");
    }

    @Test void untrustedFilenameAndParserErrorNeverBecomeSwingHtml() throws Exception {
        // Swing's BasicHTML renderer treats a filename beginning with <html>
        // as markup unless explicitly disabled. The tooltip needs the same
        // protection because it is a separate Swing component.
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            for (String text : List.of("<html><b>case.udf</b></html>",
                    "<HTML><font color='red'>unsupported</font></HTML>")) {
                JLabel label = DesktopApp.literalLabel(text);
                assertEquals(text, label.getText(), "Untrusted filename/error must stay literal");
                assertEquals(Boolean.TRUE, label.getClientProperty("html.disable"));
                assertNull(label.getClientProperty(
                        javax.swing.plaf.basic.BasicHTML.propertyKey),
                        "Label must not create an HTML renderer");
                label.setToolTipText(text);
                javax.swing.JToolTip tooltip = label.createToolTip();
                tooltip.setTipText(text);
                assertEquals(text, tooltip.getTipText());
                assertEquals(Boolean.TRUE, tooltip.getClientProperty("html.disable"));
                assertNull(tooltip.getClientProperty(
                        javax.swing.plaf.basic.BasicHTML.propertyKey),
                        "Tooltip must not create an HTML renderer");
            }
        });
    }

    @Test void inGridAddCardSupportsKeyboardActivation() throws Exception {
        java.util.concurrent.atomic.AtomicInteger activations = new java.util.concurrent.atomic.AtomicInteger();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            JPanel tile = new JPanel();
            DesktopApp.installAddCardKeyboard(tile, activations::incrementAndGet);
            assertTrue(tile.isFocusable(), "The add tile must be reachable with Tab");
            assertEquals("+ UDF ekle", tile.getAccessibleContext().getAccessibleName());
            // A JPanel has no native button focus ring. Tab focus must be
            // visibly distinguishable from the idle dashed add-card border.
            assertEquals(Boolean.FALSE, tile.getClientProperty("embasic-add-focus"));
            javax.swing.border.Border idleBorder = tile.getBorder();
            java.awt.event.FocusEvent gained = new java.awt.event.FocusEvent(
                    tile, java.awt.event.FocusEvent.FOCUS_GAINED);
            for (java.awt.event.FocusListener listener : tile.getFocusListeners())
                listener.focusGained(gained);
            assertEquals(Boolean.TRUE, tile.getClientProperty("embasic-add-focus"));
            assertNotSame(idleBorder, tile.getBorder(), "Focused add tile must have a visible ring");
            java.awt.event.FocusEvent lost = new java.awt.event.FocusEvent(
                    tile, java.awt.event.FocusEvent.FOCUS_LOST);
            for (java.awt.event.FocusListener listener : tile.getFocusListeners())
                listener.focusLost(lost);
            assertEquals(Boolean.FALSE, tile.getClientProperty("embasic-add-focus"));
            assertNotNull(tile.getBorder(), "Idle border must be restored");
            for (int key : new int[]{java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.VK_SPACE}) {
                Object binding = tile.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                        .get(javax.swing.KeyStroke.getKeyStroke(key, 0));
                assertNotNull(binding, "Enter and Space must activate the in-grid add tile");
                javax.swing.Action action = tile.getActionMap().get(binding);
                assertNotNull(action);
                action.actionPerformed(new java.awt.event.ActionEvent(tile,
                        java.awt.event.ActionEvent.ACTION_PERFORMED, "add"));
            }
        });
        assertEquals(2, activations.get(), "Both keys must invoke the same add-file action");
    }

    @Test void addTileKeepsIdentityAndKeyboardBindingsAcrossVirtualRefresh() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            JPanel grid = new JPanel(null);
            JPanel addTile = new JPanel();
            addTile.putClientProperty("udf-add-card", Boolean.TRUE);
            java.util.concurrent.atomic.AtomicInteger activations =
                    new java.util.concurrent.atomic.AtomicInteger();
            DesktopApp.installAddCardKeyboard(addTile, activations::incrementAndGet);
            JPanel captured = new JPanel();
            JPanel obsolete = new JPanel();
            grid.add(captured);
            grid.add(addTile);
            grid.add(obsolete);

            assertSame(addTile, DesktopApp.existingAddTile(grid));
            // A preview/zoom/theme rebuild must not remove and recreate the
            // keyboard-focused + tile, or Enter/Space will stop reaching it.
            DesktopApp.clearVirtualCards(grid, captured, addTile);
            assertEquals(2, grid.getComponentCount());
            assertSame(grid, addTile.getParent());
            assertSame(grid, captured.getParent());
            assertNull(obsolete.getParent());
            Object key = addTile.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                    .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0));
            assertNotNull(key);
            addTile.getActionMap().get(key).actionPerformed(
                    new java.awt.event.ActionEvent(addTile,
                            java.awt.event.ActionEvent.ACTION_PERFORMED, "add"));
            assertEquals(1, activations.get());

            // When the add tile scrolls out of the virtual viewport it must
            // be removable, not kept as a hidden focus trap.
            DesktopApp.clearVirtualCards(grid, null, null);
            assertEquals(0, grid.getComponentCount());
            assertNull(DesktopApp.existingAddTile(grid));
        });
    }

    @Test void singleAndOrderedMerge() throws Exception {
        var first = UdfReader.read(udf("first.udf", "SMOKE-A İzmir ğüş"));
        var second = UdfReader.read(udf("second.udf", "SMOKE-B"));
        assertEquals("1.0", first.formatId());
        assertTrue(first.paragraphs().get(0).runs().get(0).text().contains("İzmir"));
        Path pdf = root.resolve("combined.pdf");
        assertEquals(2, PdfExporter.save(List.of(first, second), pdf, false));
        try (PDDocument loaded = Loader.loadPDF(pdf.toFile())) {
            assertEquals(2, loaded.getNumberOfPages());
            PDFTextStripper reader = new PDFTextStripper();
            reader.setStartPage(1); reader.setEndPage(1);
            String a = reader.getText(loaded);
            reader.setStartPage(2); reader.setEndPage(2);
            String b = reader.getText(loaded);
            assertTrue(a.contains("SMOKE-A"), a);
            assertTrue(a.contains("İzmir"), a);
            assertTrue(b.contains("SMOKE-B"), b);
        }
        byte[] original = Files.readAllBytes(pdf);
        assertThrows(IllegalArgumentException.class, () -> PdfExporter.save(List.of(first), pdf, false));
        assertArrayEquals(original, Files.readAllBytes(pdf), "Existing PDF must survive a denied overwrite");
    }

    @Test void previewIsRenderedOnFirstPage() throws Exception {
        var spec = UdfReader.read(udf("preview.udf", "Deneme PDF önizleme"));
        var image = PdfExporter.firstPage(spec);
        assertTrue(image.getWidth() > 100);
        assertTrue(image.getHeight() > 200);
    }

    @Test void invalidZipXmlAndUnsupportedTableFailClosed() throws Exception {
        Path broken = root.resolve("broken.udf");
        Files.writeString(broken, "not a zip");
        assertThrows(Exception.class, () -> UdfReader.read(broken));
        assertThrows(IllegalArgumentException.class,
            () -> UdfReader.parse(xml("abc").replace("format_id='1.0'", "").getBytes(StandardCharsets.UTF_8), "x.udf"));
        assertThrows(IllegalArgumentException.class,
            () -> UdfReader.parse(("<template format_id='1.0'><content>x</content>"
                + "<elements><table><row/></table></elements></template>").getBytes(StandardCharsets.UTF_8), "x.udf"));
        assertThrows(Exception.class, () -> UdfReader.parse(
            "<!DOCTYPE template [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><template format_id='1.0'>"
                .getBytes(StandardCharsets.UTF_8), "x.udf"));
    }

    @Test void unknownArchivePayloadsAreRejectedInsteadOfSilentlyIgnored() throws Exception {
        // Only the public synthetic subset is supported: content.xml and ZIP-local PNGs.
        // Unknown members may carry layout or document content and must not disappear.
        int index = 0;
        for (String extra : List.of("metadata.xml", "notes.txt", "assets/hidden.png",
                "media/orphan.png")) {
            Path input = root.resolve("unknown-member-" + index++ + ".udf");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(input))) {
                zip.putNextEntry(new ZipEntry("content.xml"));
                zip.write(xml("VISIBLE").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry(extra));
                zip.write("UNSUPPORTED".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                    () -> UdfReader.read(input), extra);
            if (extra.equals("media/orphan.png"))
                assertTrue(rejected.getMessage().contains("aktarılmayan"),
                        "An unreferenced ZIP-local PNG must not disappear silently: " + rejected.getMessage());
        }
    }

    @Test void surrogateSplittingAndDuplicateXmlFailClosed() throws Exception {
        var xml = "<template format_id='1.0'><content>😀</content><elements><paragraph>"
                + "<content startOffset='1' length='1'/></paragraph></elements></template>";
        assertThrows(IllegalArgumentException.class,
                () -> UdfReader.parse(xml.getBytes(StandardCharsets.UTF_8), "x.udf"));
        Path duplicate = root.resolve("duplicate.udf");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(duplicate))) {
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(xml("X").getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("./content.xml"));
            zip.write(xml("Y").getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        assertThrows(IllegalArgumentException.class, () -> UdfReader.read(duplicate));
    }

    @Test void unsupportedPageFormatIsRejectedInsteadOfSilentlyRenderedOnA4() throws Exception {
        var unsupported = "<template format_id='1.0'><content>x</content>"
            + "<properties><pageFormat width='800' height='800'/></properties>"
            + "<elements><paragraph><content startOffset='0' length='1'/></paragraph>"
            + "</elements></template>";
        assertThrows(IllegalArgumentException.class, () ->
            UdfReader.parse(unsupported.getBytes(StandardCharsets.UTF_8), "layout.udf"));
    }

    @Test void wrappedLargeFontDoesNotOverlapFollowingParagraph() throws Exception {
        var plain = new UdfReader.Style(11f, false, Color.BLACK);
        var large = new UdfReader.Style(32f, true, Color.BLACK);
        // Tabs place the cursor near the A4 right margin so LARGE has to wrap.
        var first = new UdfReader.Paragraph(List.of(
            new UdfReader.Run("\t".repeat(13), plain),
            new UdfReader.Run("LARGE", large)));
        var next = new UdfReader.Paragraph(List.of(new UdfReader.Run("FOLLOW", plain)));
        var spec = new UdfReader.DocumentSpec("synthetic.udf", "1.0", List.of(first, next));
        try (PDDocument pdf = PdfExporter.render(List.of(spec))) {
            List<Float> largeY = new ArrayList<>();
            List<Float> followY = new ArrayList<>();
            var extractor = new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition glyph) {
                    if ("L".equals(glyph.getUnicode())) largeY.add(glyph.getYDirAdj());
                    if ("F".equals(glyph.getUnicode())) followY.add(glyph.getYDirAdj());
                    super.processTextPosition(glyph);
                }
            };
            String extracted = extractor.getText(pdf);
            assertTrue(extracted.contains("LARGE"), extracted);
            assertTrue(extracted.contains("FOLLOW"), extracted);
            assertFalse(largeY.isEmpty(), "LARGE must be embedded and extractable");
            assertFalse(followY.isEmpty(), "FOLLOW must be embedded and extractable");
            float lineDistance = followY.get(0) - largeY.get(0);
            assertTrue(lineDistance > 38f, "Large-font line must not overlap next paragraph: " + lineDistance);
        }
    }

    @Test void leadingParagraphSpacesPreservePdfIndentation() throws Exception {
        var style = new UdfReader.Style(11f, false, Color.BLACK);
        var plain = new UdfReader.Paragraph(List.of(new UdfReader.Run("INDENT", style)));
        var indented = new UdfReader.Paragraph(List.of(new UdfReader.Run("   INDENT", style)));
        var spec = new UdfReader.DocumentSpec("synthetic.udf", "1.0", List.of(plain, indented));
        try (PDDocument pdf = PdfExporter.render(List.of(spec))) {
            List<Float> firstLetterX = new ArrayList<>();
            var extractor = new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition glyph) {
                    if ("I".equals(glyph.getUnicode())) firstLetterX.add(glyph.getXDirAdj());
                    super.processTextPosition(glyph);
                }
            };
            assertTrue(extractor.getText(pdf).contains("INDENT"));
            assertEquals(2, firstLetterX.size(), "Both paragraph starts must be extractable");
            assertTrue(firstLetterX.get(1) > firstLetterX.get(0) + 5f,
                    "Leading spaces must shift the second paragraph, not be silently dropped: "
                    + firstLetterX);
        }
    }

    @Test void invalidOutputIsRejectedWithoutOverwritingSource() throws Exception {
        Path source = udf("original.udf", "A");
        byte[] before = Files.readAllBytes(source);
        var spec = UdfReader.read(source);
        assertThrows(IllegalArgumentException.class,
            () -> PdfExporter.save(List.of(spec), source, true));
        assertArrayEquals(before, Files.readAllBytes(source));
    }
    @Test void parsingCanFinishWhilePdfThumbnailWorkerIsBlocked() throws Exception {
        ThreadPoolExecutor parser = new ThreadPoolExecutor(1, 1, 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4));
        ThreadPoolExecutor preview = new ThreadPoolExecutor(1, 1, 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4));
        CountDownLatch previewStarted = new CountDownLatch(1);
        CountDownLatch releasePreview = new CountDownLatch(1);
        CountDownLatch parsedReady = new CountDownLatch(1);
        CountDownLatch nextPreviewDone = new CountDownLatch(1);
        CountDownLatch previewQueued = new CountDownLatch(1);
        AtomicBoolean failed = new AtomicBoolean();
        try {
            preview.submit(() -> {
                previewStarted.countDown();
                try { releasePreview.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            });
            assertTrue(previewStarted.await(5, TimeUnit.SECONDS));
            Future<?> parsing = DesktopApp.parseWithoutBlockingPreview(parser,
                    () -> "synthetic parsed document",
                    result -> {
                        if ("synthetic parsed document".equals(result)) parsedReady.countDown();
                        preview.submit(nextPreviewDone::countDown);
                        previewQueued.countDown();
                    },
                    ex -> failed.set(true));
            parsing.get(5, TimeUnit.SECONDS);
            assertTrue(parsedReady.await(5, TimeUnit.SECONDS),
                    "Parsing must publish export readiness without waiting for PDFBox thumbnails");
            assertFalse(failed.get());
            assertTrue(previewQueued.await(5, TimeUnit.SECONDS),
                    "Wait for EDT to enqueue the thumbnail before inspecting its queue");
            assertEquals(1, preview.getQueue().size(),
                    "Thumbnail may remain queued after the document is export-ready");
            releasePreview.countDown();
            assertTrue(nextPreviewDone.await(5, TimeUnit.SECONDS));
        } finally {
            releasePreview.countDown();
            parser.shutdownNow();
            preview.shutdownNow();
            assertTrue(parser.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(preview.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void clearingThumbnailsPurgesObsoletePreviewBacklog() throws Exception {
        ThreadPoolExecutor queue = new ThreadPoolExecutor(1, 1, 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2));
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean staleExecuted = new AtomicBoolean();
        try {
            Future<?> active = queue.submit(() -> {
                running.countDown();
                try { release.await(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            assertTrue(running.await(5, TimeUnit.SECONDS));
            Future<?> stale = queue.submit(() -> staleExecuted.set(true));
            assertEquals(1, queue.getQueue().size());
            DesktopApp.cancelQueuedPreviews(queue, List.of(stale));
            assertTrue(stale.isCancelled());
            assertTrue(queue.getQueue().isEmpty(), "Removed cards must free queue slots immediately");
            Future<Boolean> fresh = queue.submit(() -> true);
            release.countDown();
            active.get(5, TimeUnit.SECONDS);
            assertTrue(fresh.get(5, TimeUnit.SECONDS), "A newly imported UDF must not wait behind cancelled previews");
            assertFalse(staleExecuted.get(), "Removed thumbnail must never render");
        } finally {
            release.countDown();
            queue.shutdownNow();
            assertTrue(queue.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void scrollingPrioritizesVisibleThumbnailWithoutDiscardingOffscreenWork() throws Exception {
        LinkedBlockingDeque<Runnable> pending = new LinkedBlockingDeque<>(4);
        ThreadPoolExecutor queue = new ThreadPoolExecutor(1, 1, 0L,
                TimeUnit.MILLISECONDS, pending);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> rendered = java.util.Collections.synchronizedList(new ArrayList<>());
        try {
            Future<?> running = queue.submit(() -> {
                started.countDown();
                try { release.await(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<?> first = queue.submit(() -> rendered.add("first"));
            Future<?> second = queue.submit(() -> rendered.add("second"));
            Future<?> visible = queue.submit(() -> rendered.add("visible"));
            assertEquals(3, pending.size());
            DesktopApp.prioritizeQueuedPreviews(pending, List.of(visible));
            assertEquals(3, pending.size(), "Prioritization must not lose or duplicate work");
            release.countDown();
            running.get(5, TimeUnit.SECONDS);
            visible.get(5, TimeUnit.SECONDS);
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(List.of("visible", "first", "second"), rendered,
                    "A scrolled-to card should render before queued offscreen cards");
        } finally {
            release.countDown();
            queue.shutdownNow();
            assertTrue(queue.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void scrollingCancelsOnlyQueuedOffscreenThumbnails() throws Exception {
        LinkedBlockingDeque<Runnable> pending = new LinkedBlockingDeque<>(4);
        ThreadPoolExecutor queue = new ThreadPoolExecutor(1, 1, 0L,
                TimeUnit.MILLISECONDS, pending);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean staleRendered = new AtomicBoolean();
        AtomicBoolean visibleRendered = new AtomicBoolean();
        try {
            Future<?> active = queue.submit(() -> {
                started.countDown();
                try { release.await(); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<?> stale = queue.submit(() -> staleRendered.set(true));
            Future<?> visible = queue.submit(() -> visibleRendered.set(true));
            assertEquals(2, pending.size());

            assertTrue(DesktopApp.cancelQueuedPreview(pending, stale),
                    "A no-longer-visible queued preview must free its queue slot");
            assertTrue(stale.isCancelled());
            assertFalse(DesktopApp.cancelQueuedPreview(pending, active),
                    "An active PDFBox render must never be cancelled by scrolling");
            assertFalse(active.isCancelled());
            assertEquals(1, pending.size(), "The still-visible thumbnail stays queued");

            release.countDown();
            active.get(5, TimeUnit.SECONDS);
            visible.get(5, TimeUnit.SECONDS);
            assertTrue(visibleRendered.get());
            assertFalse(staleRendered.get(), "Stale thumbnail must not consume CPU");

            // Scrolling back is allowed to request a fresh preview.
            queue.submit(() -> staleRendered.set(true)).get(5, TimeUnit.SECONDS);
            assertTrue(staleRendered.get());
        } finally {
            release.countDown();
            queue.shutdownNow();
            assertTrue(queue.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void nativeFileDropTargetCoversThumbnailAndNestedLabels() {
        // Headless Swing component contract: OS drag/drop must not depend on
        // hitting the tiny exposed background of a thumbnail card.
        JPanel card = new JPanel();
        JLabel preview = new JLabel("Preview");
        JPanel caption = new JPanel();
        JLabel filename = new JLabel("sample.udf");
        caption.add(filename);
        card.add(preview);
        card.add(caption);
        TransferHandler drop = new TransferHandler() {};
        DesktopApp.installFileDrop(card, drop);
        assertSame(drop, card.getTransferHandler());
        assertSame(drop, preview.getTransferHandler());
        assertSame(drop, caption.getTransferHandler());
        assertSame(drop, filename.getTransferHandler());
        // A short grid leaves blank space inside the scroll viewport.
        // The viewport must accept native file drops there, not only on cards.
        JScrollPane workspace = new JScrollPane(card);
        DesktopApp.installWorkspaceFileDrop(workspace, card, drop);
        assertSame(drop, workspace.getTransferHandler());
        assertSame(drop, workspace.getViewport().getTransferHandler());
        assertSame(drop, card.getTransferHandler());
    }

}
