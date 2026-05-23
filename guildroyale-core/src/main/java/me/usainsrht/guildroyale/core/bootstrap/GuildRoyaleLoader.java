package me.usainsrht.guildroyale.core.bootstrap;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

/**
 * Registered in {@code paper-plugin.yml} as the plugin loader.
 *
 * <p>All external runtime libraries are resolved and added to the plugin
 * classpath here instead of being shaded into the jar. Paper caches the
 * downloaded artifacts in {@code <server>/libraries/} so subsequent starts
 * are instant and offline-safe after the first download.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GuildRoyaleLoader implements PluginLoader {

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        // ── Repositories ────────────────────────────────────────────────────
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());

        resolver.addRepository(new RemoteRepository.Builder(
                "arim-mvn-lgpl3", "default", "https://mvn-repo.arim.space/lesser-gpl3/").build());

        // ── Libraries ───────────────────────────────────────────────────────

        // Folia-safe scheduling abstraction (not on Maven Central)
        resolver.addDependency(dep("space.arim.morepaperlib:morepaperlib:0.4.4"));

        // Connection pooling for SQL storage
        resolver.addDependency(dep("com.zaxxer:HikariCP:5.1.0"));

        // SQLite JDBC driver
        resolver.addDependency(dep("org.xerial:sqlite-jdbc:3.46.1.3"));

        // MySQL Connector/J
        resolver.addDependency(dep("com.mysql:mysql-connector-j:9.1.0"));

        // JSON serialization for file storage
        resolver.addDependency(dep("com.google.code.gson:gson:2.11.0"));

        classpathBuilder.addLibrary(resolver);
    }

    /** Shorthand: wrap a {@code group:artifact:version} string into an Aether {@link Dependency}. */
    private static Dependency dep(String coordinates) {
        return new Dependency(new DefaultArtifact(coordinates), null);
    }
}


