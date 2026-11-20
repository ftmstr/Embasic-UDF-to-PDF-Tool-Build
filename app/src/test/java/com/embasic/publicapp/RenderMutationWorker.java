package com.embasic.publicapp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;

/**
 * TUR220 isolated JDK-only helper. Launched as a separate OS process from
 * JUnit, using synthetic files in a dedicated test temp folder. It is not
 * included in the production shaded JAR or packaged Windows application.
 */
public final class RenderMutationWorker {
    private RenderMutationWorker() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 && args.length != 4)
            throw new IllegalArgumentException("Expected bounded synthetic file mutation");
        String mode = args[0];
        if (!mode.equals("source") && !mode.equals("target") && !mode.equals("newtarget"))
            throw new IllegalArgumentException("Unknown synthetic mutation");
        if (mode.equals("source") != (args.length == 4))
            throw new IllegalArgumentException("Wrong mutation argument count");
        Path replacement = Path.of(args[1]).toAbsolutePath().normalize();
        Path destination = Path.of(args[2]).toAbsolutePath().normalize();
        // The tests generate source and destination in one isolated @TempDir.
        // Do not turn this tiny test worker into a general-purpose file writer.
        if (replacement.equals(destination)
                || !replacement.getParent().equals(destination.getParent())
                || !Files.isRegularFile(replacement)
                || Files.isSymbolicLink(replacement)
                || Files.isSymbolicLink(destination))
            throw new IllegalArgumentException("Mutation outside isolated synthetic directory");
        if (mode.equals("newtarget") && Files.exists(destination))
            throw new IllegalArgumentException("Target already exists");
        if (!mode.equals("newtarget") && !Files.isRegularFile(destination))
            throw new IllegalArgumentException("Expected existing synthetic destination");
        if (mode.equals("newtarget"))
            Files.copy(replacement, destination);
        else
            Files.copy(replacement, destination, StandardCopyOption.REPLACE_EXISTING);
        if (mode.equals("source"))
            Files.setLastModifiedTime(destination, FileTime.fromMillis(Long.parseLong(args[3])));
        System.out.println("EMBASIC_TUR220_CHILD_MUTATION_DONE");
    }
}
