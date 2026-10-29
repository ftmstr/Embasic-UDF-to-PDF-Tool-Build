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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
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
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Public-only Swing preview MVP. Native UYAP output parity and all UDF versions are NOT claimed. */
public final class DesktopApp extends JFrame {
    private static final int CARD_WIDTH = 188, CARD_HEIGHT = 238;
    private final List<Item> items = new ArrayList<>();
    private final LinkedHashSet<Item> selected = new LinkedHashSet<>();
    private final ThreadPoolExecutor previews = new ThreadPoolExecutor(2, 2, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(192), job -> {
        Thread thread = new Thread(job, "embasic-preview");
        thread.setDaemon(true); return thread;
    });
    private final JPanel grid = new JPanel();
    private final JScrollPane scroll = new JScrollPane(grid);
    private final JLabel status = new JLabel("Dosyaları çalışma alanına bırakın veya + kartına basın");
    private final JButton saveButton = new JButton("PDF olarak kaydet");
    private final JButton themeButton = new JButton("Aydınlık tema");
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
        Item(Path path) { this.path = path; }
    }

    private DesktopApp() {
        super("Embasic UDF to PDF — Public MVP");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(850, 610));
        setSize(1120, 870);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(0, 12));

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setBorder(BorderFactory.createEmptyBorder(16, 20, 0, 20));
        JLabel title = new JLabel("EMBASIC  |  UDF to PDF");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 19f));
        top.add(title, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton addButton = new JButton("+ UDF ekle");
        addButton.addActionListener(e -> chooseFiles());
        actions.add(addButton);
        themeButton.addActionListener(e -> { dark = !dark; themeButton.setText(dark ? "Aydınlık tema" : "Karanlık tema"); rebuild(); });
        actions.add(themeButton);
        saveButton.addActionListener(e -> save());
        actions.add(saveButton);
        top.add(actions, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        grid.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        add(scroll, BorderLayout.CENTER);
        scroll.getViewport().addChangeListener(e -> {
            int width = scroll.getViewport().getWidth();
            int cols = Math.max(1, width / (CARD_WIDTH + 17));
            if (cols != lastColumns) { lastColumns = cols; rebuild(); }
        });

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 22, 16, 22));
        bottom.add(status, BorderLayout.WEST);
        JPanel ordered = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton left = new JButton("← Taşı"), right = new JButton("Taşı →"), remove = new JButton("Kaldır"), clear = new JButton("Temizle");
        left.addActionListener(e -> shift(-1)); right.addActionListener(e -> shift(1));
        remove.addActionListener(e -> { for (Item it : selected) it.cancelled = true; items.removeAll(selected); selected.clear(); rebuild(); updateFileStatus(); });
        clear.addActionListener(e -> {
            if (items.isEmpty() || JOptionPane.showConfirmDialog(this, "Tüm dosyalar kaldırılsın mı?", "Temizle",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            for (Item it : items) it.cancelled = true;
            items.clear(); selected.clear(); rebuild(); updateFileStatus();
        });
        ordered.add(left); ordered.add(right); ordered.add(remove); ordered.add(clear);
        bottom.add(ordered, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        TransferHandler fileDrop = new TransferHandler() {
            @Override public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
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
        grid.setTransferHandler(fileDrop);
        scroll.setTransferHandler(fileDrop);
        getRootPane().setTransferHandler(fileDrop);
        var input = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        var action = getRootPane().getActionMap();
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK), "selectAll");
        action.put("selectAll", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { selected.addAll(items); rebuild(); }
        });
        bindArrow(KeyEvent.VK_LEFT, -1);
        bindArrow(KeyEvent.VK_RIGHT, 1);
        bindArrow(KeyEvent.VK_UP, -1, true);
        bindArrow(KeyEvent.VK_DOWN, 1, true);
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK), "toggleFocused");
        action.put("toggleFocused", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (focused == null || !items.contains(focused)) return;
                if (!selected.add(focused)) selected.remove(focused);
                anchor = focused;
                rebuild();
            }
        });
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "remove");
        action.put("remove", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { for (Item it : selected) it.cancelled = true; items.removeAll(selected); selected.clear(); rebuild(); updateFileStatus(); }
        });
        rebuild();
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
        if(index>=0 && index<grid.getComponentCount() &&
                grid.getComponent(index) instanceof JComponent card) {
            card.scrollRectToVisible(new java.awt.Rectangle(0,0,
                    Math.max(1,card.getWidth()),Math.max(1,card.getHeight())));
        }
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("UYAP UDF (*.udf)", "udf"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        List<Path> paths = new ArrayList<>();
        for (java.io.File file : chooser.getSelectedFiles()) paths.add(file.toPath());
        addFiles(paths);
    }

    private void addFiles(List<Path> paths) {
        FileImportPolicy.Selection plan = FileImportPolicy.plan(
                items.stream().map(item -> item.path).toList(), paths);
        for (Path normalized : plan.accepted()) {
            Item item = new Item(normalized);
            items.add(item);
            try {
                previews.submit(() -> {
                    if (item.cancelled) return;
                    try {
                        UdfReader.DocumentSpec parsed = UdfReader.read(item.path);
                        if (item.cancelled) return;
                        BufferedImage image = PdfExporter.firstPage(parsed);
                        if (item.cancelled) return;
                        BufferedImage thumb = new BufferedImage(146, 194, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = thumb.createGraphics();
                        try {
                            g.setColor(Color.WHITE); g.fillRect(0,0,146,194);
                            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            double ratio=Math.min(146.0/image.getWidth(),194.0/image.getHeight());
                            int width=Math.max(1,(int)Math.round(image.getWidth()*ratio));
                            int height=Math.max(1,(int)Math.round(image.getHeight()*ratio));
                            g.drawImage(image,(146-width)/2,(194-height)/2,width,height,null);
                        } finally { g.dispose(); }
                        SwingUtilities.invokeLater(() -> {
                            if (item.cancelled || !items.contains(item)) return;
                            item.document = parsed;
                            item.thumbnail = new ImageIcon(thumb);
                            item.info = "Hazır";
                            rebuild();
                            updateFileStatus();
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            if (item.cancelled || !items.contains(item)) return;
                            item.info = "Desteklenmiyor: " + friendly(ex);
                            rebuild();
                            updateFileStatus();
                        });
                    }
                });
            } catch (RejectedExecutionException queueFull) {
                item.info = "Önizleme kuyruğu dolu — kaldırıp yeniden ekleyin";
            }
        }
        rebuild();
        updateFileStatus();
        if (plan.limitReached())
            JOptionPane.showMessageDialog(this,
                    "En fazla " + FileImportPolicy.MAX_CARDS + " UDF kartı yüklenebilir. "
                    + "Diğer dosyaları eklemek için önce bazı kartları kaldırın.",
                    "Dosya sayısı sınırı", JOptionPane.WARNING_MESSAGE);
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
        card.setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        card.setBackground(cardColor());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(selected.contains(item) || focused == item ? accent() : cardColor(),
                selected.contains(item) ? 2 : 1, true),
            BorderFactory.createEmptyBorder(8, 7, 8, 7)));
        JLabel preview = new JLabel(item.thumbnail == null ? "UDF" : "", item.thumbnail, SwingConstants.CENTER);
        preview.setPreferredSize(new Dimension(154, 190));
        preview.setForeground(foreground());
        card.add(preview, BorderLayout.CENTER);
        JPanel labels = new JPanel(new BorderLayout());
        labels.setOpaque(false);
        String filename = item.path.getFileName().toString();
        JLabel name = new JLabel(filename.length() > 28 ? filename.substring(0, 25) + "..." : filename);
        name.setToolTipText(filename);
        name.setForeground(foreground()); name.setFont(name.getFont().deriveFont(Font.BOLD, 11f));
        JLabel info = new JLabel(item.info);
        info.setForeground(item.document == null && !item.info.equals("Okunuyor...") ?
            new Color(228, 115, 100) : foreground());
        info.setFont(info.getFont().deriveFont(10f));
        labels.add(name, BorderLayout.NORTH); labels.add(info, BorderLayout.SOUTH);
        card.add(labels, BorderLayout.SOUTH);
        MouseAdapter mouse = new MouseAdapter() {
            private Point pressed;
            private boolean collapseOnClick;
            @Override public void mousePressed(MouseEvent e) {
                pressed = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), grid);
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
            @Override public void mouseReleased(MouseEvent e) {
                Point released = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), grid);
                boolean dragged = pressed != null && pressed.distance(released) > 12;
                Component hit = SwingUtilities.getDeepestComponentAt(grid, released.x, released.y);
                while (hit != null && hit.getParent() != grid) hit = hit.getParent();
                Object target = hit instanceof JComponent jc ? jc.getClientProperty("udf-item") : null;
                if (dragged && target instanceof Item other && other != item) {
                    List<Item> moving = new ArrayList<>();
                    for (Item it : items) if (selected.contains(it)) moving.add(it);
                    if (!moving.contains(other)) {
                        items.removeAll(moving);
                        int at = items.indexOf(other);
                        items.addAll(Math.max(0, at), moving);
                    }
                }
                List<Item> released = GridPointerSelection.release(selected, item, collapseOnClick, dragged);
                selected.clear();
                selected.addAll(released);
                if (collapseOnClick && !dragged) anchor = item;
                collapseOnClick = false;
                pressed = null;
                rebuild();
            }
        };
        card.putClientProperty("udf-item", item);
        attachMouse(card, mouse);
        return card;
    }
    private void attachMouse(Component component, MouseAdapter mouse) {
        component.addMouseListener(mouse);
        if (component instanceof java.awt.Container container)
            for (Component child : container.getComponents()) attachMouse(child, mouse);
    }
    private JPanel addCard() {
        JPanel plus = new JPanel(new BorderLayout());
        plus.setBackground(cardColor());
        plus.setBorder(BorderFactory.createDashedBorder(accent(), 4f, 4f));
        plus.setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        JLabel label = new JLabel("<html><center><span style='font-size:24pt'>+</span><br>UDF dosyası seç</center></html>", SwingConstants.CENTER);
        label.setForeground(foreground());
        plus.add(label, BorderLayout.CENTER);
        plus.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { chooseFiles(); } });
        label.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { chooseFiles(); } });
        return plus;
    }
    private void rebuild() {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::rebuild); return; }
        if (focused != null && !items.contains(focused)) focused=null;
        if (anchor != null && !items.contains(anchor)) anchor=null;
        Color bg = background(), fg = foreground();
        getContentPane().setBackground(bg);
        grid.setBackground(bg);
        grid.removeAll();
        int cols = Math.max(1, scroll.getViewport().getWidth() / (CARD_WIDTH + 17));
        lastColumns = cols;
        grid.setLayout(new java.awt.GridLayout(0, cols, 12, 12));
        for (Item it : items) grid.add(card(it));
        grid.add(addCard());
        saveButton.setEnabled(!converting && !items.isEmpty() && items.stream().allMatch(x -> x.document != null));
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
        grid.revalidate(); grid.repaint();
    }
    private void shift(int direction) {
        if (selected.size() != 1) return;
        Item item = selected.iterator().next();
        int from = items.indexOf(item), to = from + direction;
        if (to < 0 || to >= items.size()) return;
        items.remove(from); items.add(to, item); rebuild();
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
        converting = true; saveButton.setEnabled(false); status.setText("PDF oluşturuluyor...");
        new SwingWorker<Integer, Void>() {
            @Override protected Integer doInBackground() throws Exception {
                return PdfExporter.save(docs, output, overwrite);
            }
            @Override protected void done() {
                converting = false; rebuild();
                try {
                    int count = get();
                    status.setText(count + " sayfalık PDF kaydedildi: " + output.getFileName());
                    int action = JOptionPane.showConfirmDialog(DesktopApp.this,
                        "PDF başarıyla kaydedildi (" + count + " sayfa).\nÇıktı klasörü açılsın mı?",
                        "İşlem tamamlandı", JOptionPane.YES_NO_OPTION);
                    if (action == JOptionPane.YES_OPTION && Desktop.isDesktopSupported())
                        Desktop.getDesktop().open(output.getParent().toFile());
                } catch (Exception ex) { status.setText("PDF oluşturulamadı"); showError(ex); }
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
