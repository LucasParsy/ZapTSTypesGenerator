// Trigger source for the generateTypes task. It is compiled with -proc:only and produces no
// .class output: its only job is to carry the @Java2TS annotation, without which javac never
// invokes the processor.
//
// The ZAP and add-on types are NOT listed here -- they are discovered from the scanned artifacts
// via the -Ats.scan options set by the task. Only non-project (JDK) types worth adding to the
// generated API belong below; edit freely.
//
// Collection and functional-interface types (List, Map, Set, Stream, Optional, Runnable,
// Function, ...) are already built into the processor's REQUIRED_TYPES, so they are omitted.
//
// This file MUST sit at the root of the fileTree javac scans, otherwise it is not treated as a
// compilation unit and the @Java2TS annotation is never processed (the path/package mismatch is
// tolerated for package-info.java).
@Java2TS(
        declare = {
            @Type(value = java.nio.file.Files.class, export = true),
            @Type(value = java.nio.file.Path.class),
            @Type(value = java.nio.file.Paths.class, export = true),
            @Type(value = java.util.Arrays.class, export = true),
            @Type(value = java.net.URI.class, export = true)
        })
package org.zaproxy.api;

import org.bsc.processor.annotation.Java2TS;
import org.bsc.processor.annotation.Type;
