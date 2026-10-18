# Upstream dependency evidence plan

This document covers analysis only, not an authorization to redistribute source code, logos, or binaries.

The original private Maven POM specifies `com.lowagie:itext:2.1.7`. Maven Central metadata identifies its original MPL license record (with a stated LGPL alternative) and direct dependencies on three Bouncy Castle components at version 138.

Evidence still required: complete, actual Maven resolved dependency tree, checksums of all four upstream JARs and POMs, inspection of their notices and manifests, compatibility of the selected license route with a shaded JAR, and exact bytes of notices included with Windows app-image/ZIP.

Keep logs free of personal data. Do not upload original application source, historic Git objects, private engineering reports, real UDF/PDF documents, or unapproved graphic assets to this public workspace.

Primary references:
- https://central.sonatype.com/artifact/com.lowagie/itext/2.1.7
- https://central.sonatype.com/artifact/bouncycastle/bcprov-jdk14/138
- https://www.mozilla.org/MPL/1.1/
- https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
- https://www.bouncycastle.org/license/
