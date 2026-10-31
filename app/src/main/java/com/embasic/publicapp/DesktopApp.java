package com.embasic.publicapp;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.Timer;
import javax.swing.Icon;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Public-only Swing preview MVP. Native UYAP output parity and all UDF versions are NOT claimed. */
public final class DesktopApp extends JFrame {
    private static final int PREVIEW_WIDTH = 244, PREVIEW_HEIGHT = 322;
    private final List<Item> items = new ArrayList<>();
    private final LinkedHashSet<Item> selected = new LinkedHashSet<>();
    // UDF parsing must not queue behind slow PDFBox font discovery/rasterization.
    private final LinkedBlockingDeque<Runnable> parseQueue = new LinkedBlockingDeque<>(192);
    private final ThreadPoolExecutor parsers = new ThreadPoolExecutor(2, 2, 0L,
            TimeUnit.MILLISECONDS, parseQueue, job -> {
        Thread thread = new Thread(job, "embasic-parse");
        thread.setDaemon(true); return thread;
    });
    private final LinkedBlockingDeque<Runnable> previewQueue = new LinkedBlockingDeque<>(192);
    private final ThreadPoolExecutor previews = new ThreadPoolExecutor(2, 2, 0L,
            TimeUnit.MILLISECONDS, previewQueue, job -> {
        Thread thread = new Thread(job, "embasic-preview");
        thread.setDaemon(true); return thread;
    });
    // Paint the drop target above the rendered cards, without installing a
    // Swing child that could swallow mouse releases or external file drops.
    private final JPanel grid = new JPanel() {
        @Override protected void paintChildren(java.awt.Graphics graphics) {
            super.paintChildren(graphics);
            Rectangle indicator = GridDropIndicator.bounds(virtualGrid,
                    pointerDragging ? dropTargetIndex : -1, dropAfter);
            if (indicator == null) return;
            Graphics2D draw=(Graphics2D)graphics.create();
            try {
                draw.setColor(accent());
                draw.fillRoundRect(indicator.x,indicator.y,indicator.width,
                        indicator.height,3,3);
            } finally { draw.dispose(); }
        }
    };
    private TransferHandler fileDrop;
    private final JScrollPane scroll = new JScrollPane(grid);
    private final JLabel status = new JLabel("Dosyaları çalışma alanına bırakın veya + kartına basın");
    private final JButton saveButton = new JButton("PDF olarak kaydet");
    private final JButton addButton = new JButton("+ UDF ekle");
    private final JButton moveLeftButton = new JButton("← Taşı");
    private final JButton moveRightButton = new JButton("Taşı →");
    private final JButton removeButton = new JButton("Kaldır");
    private final JButton clearButton = new JButton("Temizle");
    private final JButton themeButton = new JButton("Aydınlık tema");
    private final JSlider thumbnailZoom = new JSlider(ThumbnailSizing.MIN_WIDTH,
            ThumbnailSizing.MAX_WIDTH, ThumbnailSizing.DEFAULT_WIDTH);
    private final JLabel zoomLabel = new JLabel();
    private final Timer zoomRefresh = new Timer(90, e -> rebuild());
    private final Timer previewRefresh = new Timer(110, e -> { rebuild(); updateFileStatus(); });
    private final Timer viewportRefresh = new Timer(16, e -> refreshViewport(false));
    private final Timer dragScroll = new Timer(45, e -> scrollWhileDragging());
    private JPanel capturedCard;
    private Item capturedItem;
    private boolean pointerHeld;
    private boolean pointerDragging;
    private Point pointerViewport;
    private int dropTargetIndex=-1;
    private boolean dropAfter;
    private VirtualGridLayout virtualGrid;
    private VirtualGridLayout.Visible drawnRange;
    private int drawnZoom=-1, drawnColumns=-1, drawnCount=-1;
    private boolean dark = true, converting;
    private Item anchor;
    private Item focused;
    private int lastColumns = 0;

    private static final class Item {
        final Path path;
        volatile UdfReader.DocumentSpec document;
        volatile ImageIcon thumbnail;
        volatile String info = "Okunuyor...";
        volatile boolean cancelled;
        volatile Future<?> parseTask;
        volatile Future<?> previewTask;
        Item(Path path) { this.path = path; }
    }

    private DesktopApp() {
        super("Embasic UDF to PDF — Public MVP");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(850, 610));
        setSize(1120, 870);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(0, 12));

        // Stack the heading above the action row: at the 850px minimum window
        // width, placing both in WEST/EAST clips the rightmost PDF button.
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setBorder(BorderFactory.createEmptyBorder(16, 20, 0, 20));
        JLabel title = new JLabel("EMBASIC  |  UDF to PDF");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 19f));
        top.add(title, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        addButton.addActionListener(e -> chooseFiles());
        actions.add(addButton);
        themeButton.addActionListener(e -> { dark = !dark; themeButton.setText(dark ? "Aydınlık tema" : "Karanlık tema"); rebuild(); });
        actions.add(themeButton);
        thumbnailZoom.setPreferredSize(new Dimension(118, 26));
        thumbnailZoom.setToolTipText("Dosya kartlarının boyutunu değiştir (Ctrl + fare tekerleği)");
        zoomLabel.setToolTipText("Kart boyutu");
        updateZoomLabel();
        zoomRefresh.setRepeats(false);
        previewRefresh.setRepeats(false);
        thumbnailZoom.addChangeListener(e -> {
            updateZoomLabel();
            zoomRefresh.restart(); // Collapse a fast slider drag into a single grid rebuild.
        });
        actions.add(zoomLabel);
        actions.add(thumbnailZoom);
        saveButton.addActionListener(e -> save());
        actions.add(saveButton);
        top.add(actions, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        grid.setLayout(null); // Only visible card components exist; offscreen slots are geometry.
        grid.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        viewportRefresh.setRepeats(false);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        add(scroll, BorderLayout.CENTER);
        scroll.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                int steps = -e.getWheelRotation();
                if (steps != 0) thumbnailZoom.setValue(
                        ThumbnailSizing.zoomBy(thumbnailZoom.getValue(), steps));
                e.consume();
            }
        });
        scroll.getViewport().addChangeListener(e -> viewportRefresh.restart());

        // Keep long import/error status text on its own row. The queue controls
        // must remain reachable even with 160 files or an unsupported UDF.
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 22, 16, 22));
        bottom.add(status, BorderLayout.NORTH);
        JPanel ordered = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        moveLeftButton.addActionListener(e -> shift(-1));
        moveRightButton.addActionListener(e -> shift(1));
        removeButton.addActionListener(e -> removeSelected());
        clearButton.addActionListener(e -> {
            if (converting || items.isEmpty() || JOptionPane.showConfirmDialog(this,
                    "Tüm dosyalar kaldırılsın mı?", "Temizle",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            cancelPreviewTasks(items);
            items.clear(); selected.clear(); rebuild(); updateFileStatus();
        });
        ordered.add(moveLeftButton); ordered.add(moveRightButton);
        ordered.add(removeButton); ordered.add(clearButton);
        bottom.add(ordered, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        fileDrop = new TransferHandler() {
            @Override public boolean canImport(TransferSupport support) {
                return !converting && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
            @Override public boolean importData(TransferSupport support) {
                try {
                    if (!canImport(support)) return false;
                    Object data = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!(data instanceof List<?> list)) return false;
                    List<Path> paths = new ArrayList<>();
                    for (Object file : list)
                        if (file instanceof java.io.File f) paths.add(f.toPath());
                    addFiles(paths); return true;
                } catch (Exception ex) { showError(ex); return false; }
            }
        };
        installWorkspaceFileDrop(scroll, grid, fileDrop);
        getRootPane().setTransferHandler(fileDrop);
        var input = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        var action = getRootPane().getActionMap();
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK), "selectAll");
        bindZoomShortcut(KeyEvent.VK_EQUALS, 1);
        bindZoomShortcut(KeyEvent.VK_ADD, 1);
        bindZoomShortcut(KeyEvent.VK_MINUS, -1);
        bindZoomShortcut(KeyEvent.VK_SUBTRACT, -1);
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), "resetZoom");
        action.put("resetZoom", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                thumbnailZoom.setValue(ThumbnailSizing.DEFAULT_WIDTH);
            }
        });
        action.put("selectAll", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                // Ctrl+A must replace any earlier disjoint insertion order.
                selected.clear();
                selected.addAll(items);
                rebuild();
            }
        });
        bindArrow(KeyEvent.VK_LEFT, -1);
        bindArrow(KeyEvent.VK_RIGHT, 1);
        bindArrow(KeyEvent.VK_UP, -1, true);
        bindArrow(KeyEvent.VK_DOWN, 1, true);
        // Explorer-style navigation across the full logical queue, including
        // cards outside the viewport. Shift extends, Ctrl only moves focus.
        bindArrow(KeyEvent.VK_HOME, -Integer.MAX_VALUE);
        bindArrow(KeyEvent.VK_END, Integer.MAX_VALUE);
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK), "toggleFocused");
        action.put("toggleFocused", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (focused == null || !items.contains(focused)) return;
                if (!selected.add(focused)) selected.remove(focused);
                // Ctrl+Space may add a card between two selected cards.
                List<Item> orderedSelection = GridPointerSelection.inVisualOrder(items, selected);
                selected.clear();
                selected.addAll(orderedSelection);
                anchor = focused;
                rebuild();
            }
        });
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "remove");
        action.put("remove", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { removeSelected(); }
        });
        rebuild();
    }

    /**
     * While the pressed card is retained in the virtual grid, keep scrolling
     * toward the pointer at the viewport edge without blocking the Swing EDT.
     */
    private void scrollWhileDragging() {
        if (!pointerHeld || !pointerDragging || pointerViewport == null || converting) {
            dragScroll.stop();
            return;
        }
        int delta = VirtualGridLayout.edgeScrollDelta(
                pointerViewport.y,scroll.getViewport().getHeight());
        if (delta == 0) return;
        var bar=scroll.getVerticalScrollBar();
        int max=Math.max(0,bar.getMaximum()-bar.getVisibleAmount());
        int updated=Math.max(0,Math.min(max,bar.getValue()+delta));
        if (updated != bar.getValue()) bar.setValue(updated);
        // The pointer stays at the viewport edge while document coordinates
        // change under it; recalculate the visible insertion marker.
        updateDropIndicatorFromViewport();
    }

    private void updateDropIndicatorFromViewport() {
        if (!pointerHeld || !pointerDragging || pointerViewport == null ||
                converting || virtualGrid == null) {
            setDropIndicator(-1,false);
            return;
        }
        Point documentPoint=SwingUtilities.convertPoint(
                scroll.getViewport(),pointerViewport,grid);
        int index=GridDropIndicator.targetAt(
                virtualGrid,documentPoint.x,documentPoint.y);
        boolean after=index>=0 && virtualGrid.insertAfter(index,documentPoint.x);
        setDropIndicator(index,after);
    }

    private void setDropIndicator(int index,boolean after) {
        if (index==dropTargetIndex && after==dropAfter) return;
        Rectangle old=GridDropIndicator.bounds(virtualGrid,dropTargetIndex,dropAfter);
        dropTargetIndex=index;
        dropAfter=after;
        Rectangle next=GridDropIndicator.bounds(virtualGrid,dropTargetIndex,dropAfter);
        if (old!=null) grid.repaint(old.x-2,old.y-2,old.width+4,old.height+4);
        if (next!=null) grid.repaint(next.x-2,next.y-2,next.width+4,next.height+4);
    }

    private void updateZoomLabel() {
        zoomLabel.setText("Kart " + thumbnailZoom.getValue() + " px");
    }
    private void bindZoomShortcut(int key, int direction) {
        String actionName="thumbnailZoom." + key;
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK), actionName);
        getRootPane().getActionMap().put(actionName, new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                thumbnailZoom.setValue(ThumbnailSizing.zoomBy(thumbnailZoom.getValue(), direction));
            }
        });
    }
    private void bindArrow(int key, int amount) {
        bindArrow(key, amount, false);
    }
    private void bindArrow(int key, int amount, boolean vertical) {
        int[] modifiers = {0, InputEvent.SHIFT_DOWN_MASK, InputEvent.CTRL_DOWN_MASK,
                InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK};
        for (int mods : modifiers) {
            final int fixedMods = mods;
            String actionName = "gridArrow." + key + "." + mods;
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(key,mods),actionName);
            getRootPane().getActionMap().put(actionName,new javax.swing.AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                    moveKeyboard(amount * (vertical ? Math.max(1,lastColumns) : 1),
                            (fixedMods & InputEvent.SHIFT_DOWN_MASK) != 0,
                            (fixedMods & InputEvent.CTRL_DOWN_MASK) != 0);
                }
            });
        }
    }
    private void moveKeyboard(int step, boolean extend, boolean focusOnly) {
        if (items.isEmpty()) return;
        var next = GridKeyboardSelection.move(items,selected,anchor,focused,step,extend,focusOnly);
        selected.clear();
        selected.addAll(next.selected());
        anchor=next.anchor();
        focused=next.focused();
        rebuild();
        int index=items.indexOf(focused);
        if(index>=0 && virtualGrid!=null) {
            // Offscreen cards have no Swing component; scrolling uses their logical bounds.
            grid.scrollRectToVisible(virtualGrid.bounds(index));
            viewportRefresh.restart();
        }
    }

    private void chooseFiles() {
        if (converting) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("UYAP UDF (*.udf)", "udf"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        List<Path> paths = new ArrayList<>();
        for (java.io.File file : chooser.getSelectedFiles()) paths.add(file.toPath());
        addFiles(paths);
    }

    private void addFiles(List<Path> paths) {
        if (converting) return;
        FileImportPolicy.Selection plan = FileImportPolicy.plan(
                items.stream().map(item -> item.path).toList(), paths);
        for (Path normalized : plan.accepted()) {
            Item item = new Item(normalized);
            items.add(item);
            try {
                item.parseTask = parseWithoutBlockingPreview(parsers,
                        () -> item.cancelled ? null : UdfReader.read(item.path),
                        parsed -> {
                            if (parsed == null || item.cancelled || !items.contains(item)) return;
                            // Export eligibility depends on parsing, never on PDFBox
                            // thumbnail rendering or first-use system font discovery.
                            item.document = parsed;
                            item.info = "Önizleme hazırlanıyor...";
                            try {
                                item.previewTask = previews.submit(() -> renderThumbnail(item, parsed));
                            } catch (RejectedExecutionException queueFull) {
                                item.info = "Önizleme kuyruğu dolu — PDF kaydedilebilir";
                            }
                            previewRefresh.restart();
                        },
                        ex -> {
                            if (item.cancelled || !items.contains(item)) return;
                            item.info = "Desteklenmiyor: " + friendly(ex);
                            previewRefresh.restart();
                        });
            } catch (RejectedExecutionException queueFull) {
                item.info = "UDF okuma kuyruğu dolu — kaldırıp yeniden ekleyin";
            }
        }
        rebuild();
        updateFileStatus();
        // Skipped files must not disappear silently from the merged PDF.
        // Summarize all rejection reasons once per import batch.
        List<String> warnings = new ArrayList<>();
        if (plan.limitReached())
            warnings.add("En fazla " + FileImportPolicy.MAX_CARDS
                    + " UDF kartı yüklenebilir; fazla dosyalar atlandı.");
        if (plan.unsupportedCount() > 0)
            warnings.add(plan.unsupportedCount() + " dosya .udf uzantılı olmadığı için atlandı.");
        if (plan.duplicateCount() > 0)
            warnings.add(plan.duplicateCount() + " yinelenen UDF dosyası atlandı.");
        if (!warnings.isEmpty())
            JOptionPane.showMessageDialog(this,
                    String.join("\n", warnings) + "\nAtlanan dosyalar PDF'ye eklenmedi.",
                    "İçe aktarma uyarısı", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Publish parsing on the EDT, independently of the thumbnail executor.
     * A blocked PDFBox renderer must not hold later UDFs hostage before export.
     */
    static <T> Future<?> parseWithoutBlockingPreview(ThreadPoolExecutor executor,
            Callable<T> parse, Consumer<T> onParsed, Consumer<Exception> onError) {
        return executor.submit(() -> {
            try {
                T result = parse.call();
                SwingUtilities.invokeLater(() -> onParsed.accept(result));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> onError.accept(ex));
            }
        });
    }

    private void renderThumbnail(Item item, UdfReader.DocumentSpec parsed) {
        if (item.cancelled) return;
        try {
            BufferedImage image = PdfExporter.firstPage(parsed);
            if (item.cancelled) return;
            BufferedImage thumb = new BufferedImage(PREVIEW_WIDTH, PREVIEW_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumb.createGraphics();
            try {
                g.setColor(Color.WHITE); g.fillRect(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                double ratio = Math.min((double) PREVIEW_WIDTH / image.getWidth(),
                        (double) PREVIEW_HEIGHT / image.getHeight());
                int width = Math.max(1, (int) Math.round(image.getWidth() * ratio));
                int height = Math.max(1, (int) Math.round(image.getHeight() * ratio));
                g.drawImage(image, (PREVIEW_WIDTH - width) / 2,
                        (PREVIEW_HEIGHT - height) / 2, width, height, null);
            } finally { g.dispose(); }
            SwingUtilities.invokeLater(() -> {
                if (item.cancelled || !items.contains(item)) return;
                item.thumbnail = new ImageIcon(thumb);
                item.info = "Hazır";
                previewRefresh.restart();
            });
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> {
                if (item.cancelled || !items.contains(item)) return;
                // A failed thumbnail must not invalidate a parsed document.
                item.info = "Önizleme yok: " + friendly(ex);
                previewRefresh.restart();
            });
        }
    }

    private void updateFileStatus() {
        if (converting) return;
        long ready = items.stream().filter(item -> item.document != null).count();
        long pending = items.stream().filter(item -> item.document == null &&
                "Okunuyor...".equals(item.info)).count();
        long errors = items.size() - ready - pending;
        status.setText("UDF: " + items.size() + "  |  Hazır: " + ready +
                "  |  İşleniyor: " + pending + "  |  Desteklenmeyen: " + errors +
                (errors > 0 ? "  — Hatalı kartları kaldırmadan PDF kaydedilemez" : ""));
        status.setToolTipText(errors > 0
                ? "Kırmızı durumlu kartlardaki nedenleri kontrol edin; içerik kaybı önleniyor."
                : "Dosyalar kart sırasına göre PDF oluşturur.");
    }

    private Color background() { return dark ? new Color(20, 27, 42) : new Color(237, 242, 249); }
    private Color cardColor() { return dark ? new Color(36, 47, 66) : new Color(250, 251, 254); }
    private Color foreground() { return dark ? new Color(225, 231, 240) : new Color(33, 47, 66); }
    private Color accent() { return new Color(70, 139, 239); }

    private JPanel card(Item item) {
        JPanel card = new JPanel(new BorderLayout(3, 5));
        ThumbnailSizing.Size size=ThumbnailSizing.at(thumbnailZoom.getValue());
        card.setPreferredSize(new Dimension(size.cardWidth(), size.cardHeight()));
        card.setBackground(cardColor());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(selected.contains(item) || focused == item ? accent() : cardColor(),
                selected.contains(item) ? 2 : 1, true),
            BorderFactory.createEmptyBorder(8, 7, 8, 7)));
        JLabel preview = new JLabel(item.thumbnail == null ? "UDF" : "",
                thumbnailIcon(item, size), SwingConstants.CENTER);
        preview.setPreferredSize(new Dimension(size.imageWidth(), size.imageHeight()));
        preview.setForeground(foreground());
        card.add(preview, BorderLayout.CENTER);
        JPanel labels = new JPanel(new BorderLayout());
        labels.setOpaque(false);
        String filename = item.path.getFileName().toString();
        JLabel name = new JLabel(filename.length() > 28 ? filename.substring(0, 25) + "..." : filename);
        name.setToolTipText(filename);
        name.setForeground(foreground()); name.setFont(name.getFont().deriveFont(Font.BOLD, 11f));
        JLabel info = new JLabel(item.info);
        info.setForeground(!item.info.equals("Hazır") && !item.info.equals("Okunuyor...")
                && !item.info.equals("Önizleme hazırlanıyor...") ?
            new Color(228, 115, 100) : foreground());
        info.setFont(info.getFont().deriveFont(10f));
        labels.add(name, BorderLayout.NORTH); labels.add(info, BorderLayout.SOUTH);
        card.add(labels, BorderLayout.SOUTH);
        MouseAdapter mouse = new MouseAdapter() {
            private Point pressed;
            private boolean collapseOnClick;
            @Override public void mousePressed(MouseEvent e) {
                pressed = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), grid);
                capturedCard=card;
                capturedItem=item;
                pointerHeld=true;
                pointerDragging=false;
                setDropIndicator(-1,false);
                pointerViewport=SwingUtilities.convertPoint(
                        (Component)e.getSource(),e.getPoint(),scroll.getViewport());
                var next = GridPointerSelection.press(items, selected, anchor, item,
                        (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0,
                        (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0);
                selected.clear();
                selected.addAll(next.selected());
                anchor = next.anchor();
                collapseOnClick = next.collapseOnClick();
                focused = item;
                // Avoid replacing components while mouse drag is in progress.
                card.setBorder(BorderFactory.createLineBorder(accent(), 2, true));
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (!pointerHeld || converting || pressed == null) return;
                Point position=SwingUtilities.convertPoint((Component)e.getSource(),e.getPoint(),grid);
                if (pressed.distance(position) > 12) pointerDragging=true;
                pointerViewport=SwingUtilities.convertPoint(
                        (Component)e.getSource(),e.getPoint(),scroll.getViewport());
                if (pointerDragging) {
                    updateDropIndicatorFromViewport();
                    if (!dragScroll.isRunning()) dragScroll.start();
                }
            }
            @Override public void mouseReleased(MouseEvent e) {
                Point released=SwingUtilities.convertPoint((Component)e.getSource(),e.getPoint(),grid);
                boolean dragged=pointerDragging || pressed != null && pressed.distance(released)>12;
                dragScroll.stop();
                setDropIndicator(-1,false);
                // Resolve the destination from the logical grid, not the rendered
                // components: an offscreen destination has no JPanel to hit-test.
                int destination=virtualGrid==null ? -1 :
                        virtualGrid.indexAt(released.x,released.y);
                if (dragged && !converting && destination>=0) {
                    List<Item> reordered;
                    if (destination>=items.size()) {
                        reordered=GridReorder.toEnd(items,selected);
                    } else {
                        Item other=items.get(destination);
                        boolean insertAfter=virtualGrid.insertAfter(destination,released.x);
                        reordered=insertAfter ? GridReorder.after(items,selected,other)
                                : GridReorder.before(items,selected,other);
                    }
                    items.clear();
                    items.addAll(reordered);
                }
                List<Item> releasedSelection=GridPointerSelection.release(
                        selected,item,collapseOnClick,dragged);
                selected.clear();
                selected.addAll(releasedSelection);
                if (collapseOnClick && !dragged) anchor=item;
                collapseOnClick=false;
                pressed=null;
                pointerHeld=false;
                pointerDragging=false;
                pointerViewport=null;
                capturedItem=null;
                capturedCard=null;
                rebuild();
            }
        };
        card.putClientProperty("udf-item", item);
        attachMouse(card, mouse);
        // Native Explorer drops target the deepest Swing child under the pointer.
        // Register every visible thumbnail/label, not only its background grid.
        installFileDrop(card, fileDrop);
        return card;
    }
    private void attachMouse(Component component, MouseAdapter mouse) {
        component.addMouseListener(mouse);
        component.addMouseMotionListener(mouse);
        if (component instanceof java.awt.Container container)
            for (Component child : container.getComponents()) attachMouse(child, mouse);
    }
    /**
     * Empty space below the last card belongs to the viewport, not the grid.
     * Explorer drops there must work even when only one small UDF tile is shown.
     */
    static void installWorkspaceFileDrop(JScrollPane pane, JPanel grid, TransferHandler handler) {
        pane.setTransferHandler(handler);
        pane.getViewport().setTransferHandler(handler);
        grid.setTransferHandler(handler);
    }
    /** Make the full card, including its nested preview and filename, an OS file-drop target. */
    static void installFileDrop(JComponent component, TransferHandler handler) {
        component.setTransferHandler(handler);
        for (Component child : component.getComponents())
            if (child instanceof JComponent nested) installFileDrop(nested, handler);
    }
    private Icon thumbnailIcon(Item item, ThumbnailSizing.Size size) {
        if (item.thumbnail == null) return null;
        // Paint scaled from an already-decoded thumbnail; never re-open UDF on slider changes.
        Image image = item.thumbnail.getImage();
        return new Icon() {
            @Override public int getIconWidth() { return size.imageWidth(); }
            @Override public int getIconHeight() { return size.imageHeight(); }
            @Override public void paintIcon(Component component, java.awt.Graphics graphics, int x, int y) {
                Graphics2D draw=(Graphics2D)graphics.create();
                try {
                    draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    double fit=Math.min((double)getIconWidth()/image.getWidth(null),
                            (double)getIconHeight()/image.getHeight(null));
                    int w=Math.max(1,(int)Math.round(image.getWidth(null)*fit));
                    int h=Math.max(1,(int)Math.round(image.getHeight(null)*fit));
                    draw.drawImage(image,x+(getIconWidth()-w)/2,y+(getIconHeight()-h)/2,w,h,null);
                } finally { draw.dispose(); }
            }
        };
    }
    private JPanel addCard() {
        JPanel plus = new JPanel(new BorderLayout());
        plus.putClientProperty("udf-add-card", Boolean.TRUE);
        plus.setBackground(cardColor());
        plus.setBorder(BorderFactory.createDashedBorder(accent(), 4f, 4f));
        ThumbnailSizing.Size size=ThumbnailSizing.at(thumbnailZoom.getValue());
        plus.setPreferredSize(new Dimension(size.cardWidth(), size.cardHeight()));
        JLabel label = new JLabel("<html><center><span style='font-size:24pt'>+</span><br>UDF dosyası seç</center></html>", SwingConstants.CENTER);
        label.setForeground(foreground());
        plus.add(label, BorderLayout.CENTER);
        plus.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { chooseFiles(); } });
        label.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { chooseFiles(); } });
        installFileDrop(plus, fileDrop);
        return plus;
    }
    private void refreshViewport(boolean force) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> refreshViewport(force));
            return;
        }
        int width=Math.max(1,scroll.getViewport().getWidth());
        int zoom=thumbnailZoom.getValue();
        VirtualGridLayout next=new VirtualGridLayout(items.size()+1,width,zoom);
        VirtualGridLayout.Visible window=next.visible(scroll.getViewport().getViewRect());
        // A user who scrolls to a distant card should not wait for every earlier
        // offscreen PDF preview. Only queued work is moved; active work is untouched.
        List<Future<?>> visibleTasks = new ArrayList<>();
        for (int i = window.startInclusive(); i < Math.min(window.endExclusive(), items.size()); i++) {
            Future<?> task = items.get(i).previewTask;
            if (task != null) visibleTasks.add(task);
        }
        prioritizeQueuedPreviews(previewQueue, visibleTasks);
        // Refresh logical geometry even when the visible component window does not change.
        virtualGrid=next;
        lastColumns=next.columns();
        Dimension preferred=next.preferredSize();
        boolean resized=!preferred.equals(grid.getPreferredSize());
        if(resized) grid.setPreferredSize(preferred);
        if(!force && window.equals(drawnRange) && drawnZoom==zoom &&
                drawnColumns==next.columns() && drawnCount==items.size()+1) {
            if(resized) grid.revalidate();
            return;
        }
        drawnRange=window;
        drawnZoom=zoom;
        drawnColumns=next.columns();
        drawnCount=items.size()+1;
        // At most visible rows plus a one-row buffer are instantiated.
        // A pressed source must stay parented to the grid. Removing it during
        // scroll/zoom would lose the mouseReleased event and strand selection.
        boolean retainSource=pointerHeld && capturedCard!=null &&
                capturedCard.getParent()==grid && items.contains(capturedItem);
        if (retainSource) {
            for (Component old:grid.getComponents())
                if(old!=capturedCard)grid.remove(old);
        } else grid.removeAll();
        for(int index=window.startInclusive(); index<window.endExclusive(); index++) {
            JPanel component;
            if (retainSource && index<items.size() && items.get(index)==capturedItem) {
                component=capturedCard;
            } else {
                component=index==items.size() ? addCard() : card(items.get(index));
            }
            component.setBounds(next.bounds(index));
            if(component.getParent()!=grid)grid.add(component);
        }
        grid.revalidate();
        // Recalculate after resizing or virtual row replacement. The marker is
        // geometry-derived and remains outside the Swing component hierarchy.
        if(pointerDragging) updateDropIndicatorFromViewport();
        grid.repaint();
    }

    private void rebuild() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::rebuild);
            return;
        }
        if (focused!=null && !items.contains(focused)) focused=null;
        if (anchor!=null && !items.contains(anchor)) anchor=null;
        Color bg=background(), fg=foreground();
        getContentPane().setBackground(bg);
        grid.setBackground(bg);
        saveButton.setEnabled(!converting && !items.isEmpty()
                && items.stream().allMatch(item -> item.document!=null));
        status.setForeground(fg);
        for (Component cmp : getContentPane().getComponents())
            if (cmp instanceof JPanel panel) {
                if (panel != grid) panel.setBackground(bg);
                for (Component child : panel.getComponents()) {
                    if (child instanceof JLabel label) label.setForeground(fg);
                    if (child instanceof JPanel inner) {
                        inner.setBackground(bg);
                        for (Component button : inner.getComponents())
                            if (button instanceof JButton j) j.setFocusPainted(false);
                    }
                }
            }
        refreshViewport(true);
    }
    /** Keep the visible queue identical to the document snapshot while a PDF is saving. */
    private void setQueueEditingEnabled(boolean enabled) {
        addButton.setEnabled(enabled);
        moveLeftButton.setEnabled(enabled);
        moveRightButton.setEnabled(enabled);
        removeButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
    }

    /**
     * Clearing or removing cards must not leave hundreds of obsolete previews
     * ahead of newly added UDFs in the bounded background queue. Running work
     * is not interrupted; its cancelled flag prevents publishing stale UI.
     */
    private void cancelPreviewTasks(Iterable<Item> removed) {
        List<Future<?>> parseTasks = new ArrayList<>();
        List<Future<?>> previewTasks = new ArrayList<>();
        for (Item item : removed) {
            item.cancelled = true;
            if (item.parseTask != null) parseTasks.add(item.parseTask);
            if (item.previewTask != null) previewTasks.add(item.previewTask);
        }
        cancelQueuedPreviews(parsers, parseTasks);
        cancelQueuedPreviews(previews, previewTasks);
    }

    /**
     * Bring visible, not-yet-running previews to the front in their visual order.
     * Both submit() and this method run on the Swing EDT; workers only take
     * tasks from the deque, so a successful remove always frees an addFirst slot.
     * Do not cancel or duplicate any task: offscreen documents remain queued.
     */
    static void prioritizeQueuedPreviews(LinkedBlockingDeque<Runnable> queue,
                                         Iterable<? extends Future<?>> visibleTasks) {
        List<Runnable> candidates = new ArrayList<>();
        for (Future<?> task : visibleTasks)
            if (task instanceof Runnable runnable && !task.isCancelled() && !task.isDone())
                candidates.add(runnable);
        for (int i = candidates.size() - 1; i >= 0; i--) {
            Runnable task = candidates.get(i);
            if (queue.remove(task)) queue.addFirst(task);
        }
    }

    /** Package-visible for a headless queue cancellation regression. */
    static void cancelQueuedPreviews(ThreadPoolExecutor executor,
                                     Iterable<? extends Future<?>> tasks) {
        for (Future<?> task : tasks) task.cancel(false);
        executor.purge();
    }

    private void removeSelected() {
        if (converting || selected.isEmpty()) return;
        cancelPreviewTasks(selected);
        items.removeAll(selected);
        selected.clear();
        rebuild();
        updateFileStatus();
    }

    private void shift(int direction) {
        if (converting || selected.isEmpty()) return;
        // Toolbar arrows operate on the entire Explorer-style selection,
        // including offscreen cards. Keep their relative file/PDF order.
        List<Item> reordered = GridReorder.step(items, selected, direction);
        if (reordered.equals(items)) return;
        items.clear();
        items.addAll(reordered);
        rebuild();
        if (focused != null && virtualGrid != null) {
            int index = items.indexOf(focused);
            if (index >= 0) grid.scrollRectToVisible(virtualGrid.bounds(index));
        }
    }
    private void save() {
        if (converting || items.isEmpty() || items.stream().anyMatch(x -> x.document == null)) return;
        JFileChooser picker = new JFileChooser();
        picker.setDialogTitle("PDF olarak kaydet");
        picker.setSelectedFile(new java.io.File("Embasic-Birlesik.pdf"));
        if (picker.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path target = picker.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
            target = target.resolveSibling(target.getFileName() + ".pdf");
        boolean overwrite = java.nio.file.Files.exists(target);
        if (overwrite && JOptionPane.showConfirmDialog(this, "Mevcut PDF'nin üzerine yazılsın mı?",
                "Dosya mevcut", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        final Path output = target;
        List<UdfReader.DocumentSpec> docs = items.stream().map(x -> x.document).toList();
        converting = true;
        setQueueEditingEnabled(false);
        saveButton.setEnabled(false);
        status.setText("PDF oluşturuluyor...");
        new SwingWorker<Integer, Void>() {
            @Override protected Integer doInBackground() throws Exception {
                return PdfExporter.save(docs, output, overwrite);
            }
            @Override protected void done() {
                converting = false;
                setQueueEditingEnabled(true);
                rebuild();
                int count;
                try {
                    count = get();
                } catch (Exception ex) {
                    status.setText("PDF oluşturulamadı");
                    showError(ex);
                    return;
                }
                // The PDF is already validated and committed at this point.
                // A Windows Explorer launch failure must never be reported as
                // a failed PDF export or cause the user to retry unnecessarily.
                status.setText(count + " sayfalık PDF kaydedildi: " + output.getFileName());
                int action = JOptionPane.showConfirmDialog(DesktopApp.this,
                    "PDF başarıyla kaydedildi (" + count + " sayfa).\nÇıktı klasörü açılsın mı?",
                    "İşlem tamamlandı", JOptionPane.YES_NO_OPTION);
                if (action == JOptionPane.YES_OPTION && Desktop.isDesktopSupported()) {
                    try {
                        Desktop.getDesktop().open(output.getParent().toFile());
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(DesktopApp.this,
                            "PDF kaydedildi ancak çıktı klasörü açılamadı: " + friendly(ex),
                            "Klasör açılamadı", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }
        }.execute();
    }
    private static String friendly(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        String message = root.getMessage();
        return message == null ? root.getClass().getSimpleName() : message;
    }
    private void showError(Throwable e) {
        JOptionPane.showMessageDialog(this, friendly(e), "Embasic UDF hatası", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            try {
                if (args.length < 3 || !args[0].equals("--merge"))
                    throw new IllegalArgumentException("Kullanım: --merge output.pdf file1.udf [file2.udf ...]");
                Path output = Path.of(args[1]);
                List<UdfReader.DocumentSpec> parsed = new ArrayList<>();
                for (int i = 2; i < args.length; i++)
                    parsed.add(UdfReader.read(Path.of(args[i])));
                int count = PdfExporter.save(parsed, output, false);
                System.out.println("OK " + count + " page(s): " + output.getFileName());
            } catch (Exception e) {
                System.err.println("ERROR: " + friendly(e));
                System.exit(1);
            }
            return;
        }
        SwingUtilities.invokeLater(() -> new DesktopApp().setVisible(true));
    }
}
