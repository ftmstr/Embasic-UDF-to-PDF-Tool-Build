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

    @Test void delayedPreviewCallbackCannotOverwriteNewRetry() {
        Object parsed = new Object();
        Object replacement = new Object();
        assertTrue(DesktopApp.isCurrentPreview(false, 7, 7, parsed, parsed));
        assertFalse(DesktopApp.isCurrentPreview(false, 7, 8, parsed, parsed),
                "old EDT callback must be rejected after explicit retry");
        assertFalse(DesktopApp.isCurrentPreview(false, 8, 8, parsed, replacement),
                "replacement document must invalidate old thumbnail");
        assertFalse(DesktopApp.isCurrentPreview(true, 8, 8, parsed, parsed),
                "removed card must not receive a thumbnail");
    }
}
