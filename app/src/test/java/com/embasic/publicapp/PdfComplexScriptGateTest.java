package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** All fixtures are synthetic, with no native UYAP schema or protected documents. */
final class PdfComplexScriptGateTest {
    private static UdfReader.DocumentSpec paragraph(String text) throws Exception {
        String escaped=text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
        String xml="<template format_id='1.0'><content>"+escaped+"</content>"
                +"<elements><paragraph><content startOffset='0' length='"
                +text.length()+"'/></paragraph></elements></template>";
        return UdfReader.parse(xml.getBytes(StandardCharsets.UTF_8),"synthetic.udf");
    }
    @Test void rejectsRightToLeftAndComplexScriptsBeforeFontLookup() throws Exception {
        for(String text:List.of("مرحبا","שלום","हिन्दी","ภาษาไทย","မြန်မာ")) {
            var document=paragraph(text);
            var error=assertThrows(IllegalArgumentException.class,
                    ()->PdfExporter.render(List.of(document)),text);
            assertTrue(error.getMessage().contains("Unicode"),text);
        }
    }
    @Test void rejectsCombiningMarksInsteadOfMisplacingDiacritics() throws Exception {
        for(String text:List.of("e\u0301","a\u0308","x\u20dd")) {
            var document=paragraph(text);
            assertThrows(IllegalArgumentException.class,
                    ()->PdfExporter.render(List.of(document)),text);
        }
    }
    @Test void scansTableTextThroughSameLossPreventionGate() throws Exception {
        String xml="<template format_id='1.0'><content>x</content><elements>"
                +"<paragraph><content startOffset='0' length='1'/></paragraph>"
                +"<table widths='220'><row height='30'><cell>שלום</cell></row></table>"
                +"</elements></template>";
        var document=UdfReader.parse(xml.getBytes(StandardCharsets.UTF_8),"table.udf");
        assertThrows(IllegalArgumentException.class,()->PdfExporter.render(List.of(document)));
    }
    @Test void ordinaryTurkishLatinTextPassesComplexScriptValidation() throws Exception {
        var document=paragraph("İzmir Çeşme Öğrenci Şubat");
        // Real font availability and PDF engine smoke are independently covered
        // by the Windows/Linux CI. This preflight must not reject Turkish Latin.
        try(var pdf=PdfExporter.render(List.of(document))) {
            assertEquals(1,pdf.getNumberOfPages());
        }
    }
}
