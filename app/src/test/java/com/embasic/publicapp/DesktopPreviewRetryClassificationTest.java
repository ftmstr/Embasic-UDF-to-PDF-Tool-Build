package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Tur 96: The explicit preview-retry action must never reinterpret an
 * unparsed or unsupported UDF as a recoverable rasterization failure.
 * Synthetic status strings only; no native UYAP compatibility claim.
 */
final class DesktopPreviewRetryClassificationTest {
    @Test void failedPreviewIsRetryableOnlyAfterSuccessfulParse() {
        assertTrue(DesktopApp.isFailedPreview(true, "Önizleme yok: sentetik raster hatası"));
        assertFalse(DesktopApp.isFailedPreview(false, "Önizleme yok: sentetik raster hatası"));
    }

    @Test void importFailuresAndPendingPreviewsAreNotPreviewRetries() {
        assertFalse(DesktopApp.isFailedPreview(true, null));
        assertFalse(DesktopApp.isFailedPreview(true, ""));
        assertFalse(DesktopApp.isFailedPreview(true, "Desteklenmiyor: bilinmeyen UDF öğesi"));
        assertFalse(DesktopApp.isFailedPreview(true, "Önizleme hazırlanıyor..."));
        assertFalse(DesktopApp.isFailedPreview(true, "Önizleme görünür olunca hazırlanacak"));
        assertFalse(DesktopApp.isFailedPreview(true, "Önizleme kuyruğu dolu — PDF kaydedilebilir"));
    }
}
