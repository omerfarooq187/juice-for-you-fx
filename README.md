# Juice For U

JavaFX point-of-sale application. Current version: **2.0.2**.

## Build and test

Use JDK 21 and the included Maven wrapper:

```bash
./mvnw clean verify
```

On Windows, run `mvnw.cmd clean verify` instead. The runnable JAR is
`target/JuiceForU-2.0.2-shaded.jar` on the build machine's platform.

## Windows installers

The [Windows installer workflow](.github/workflows/build-msi.yml) runs the tests
and creates two self-contained MSIs on Windows 2025 using JDK 21 and `jpackage`:

- **Juice For U**, using `juiceforyou.db` under the Juice For U app-data folder.
- **Mandra Pizza Hut**, using `pizza_hut.db` under the Mandra Pizza Hut app-data folder.

Both installers are built from the same tested application code. Branding,
receipt information, logos, data folders, database names, and installer upgrade
identities are selected by the packaged brand profile.

It runs for pushes to `main`, pull requests to `main`, version tags (for example
`v2.0.2`), and manual dispatches. Download either the
`JuiceForU-Windows-MSI-*` or `MandraPizzaHut-Windows-MSI-*` artifact from the
workflow run; each includes the installer and a SHA-256 checksum.
The tag must match the version in `pom.xml`.

The installer is per-user and includes its own Java runtime. It only packages
the application JAR—never a working database or build output. Application data
is stored outside the installation directory in
`%APPDATA%\Juice For U`. Back up the database from Settings before upgrading
or moving to another computer. The installer is currently unsigned, so Windows
may show a publisher warning until a code-signing certificate is configured.
