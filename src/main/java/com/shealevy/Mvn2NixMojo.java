package com.shealevy;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.util.Iterator;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.MalformedURLException;

import javax.json.Json;
import javax.json.stream.JsonGenerator;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.ArtifactDescriptorReaderDelegate;
import org.apache.maven.model.Plugin;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;

@Mojo(name = "mvn2nix")
public class Mvn2NixMojo extends AbstractMojo
{
	@Component
	private MavenProject project;

	@Component
	private RepositorySystem repoSystem;

	@Component
	private RepositoryLayoutProvider layoutProvider;

	@Parameter(defaultValue="${repositorySystemSession}", readonly=true)
	private DefaultRepositorySystemSession repoSession;

	private Exclusion mavenExclusionToExclusion(
			org.apache.maven.model.Exclusion excl) {
		return new Exclusion(excl.getGroupId(),
				excl.getArtifactId(),
				null,
				null);
	}

	private Dependency mavenDependencyToDependency(
			org.apache.maven.model.Dependency dep) {
		Artifact art = new DefaultArtifact(dep.getGroupId(),
			dep.getArtifactId(),
			dep.getClassifier(),
			dep.getType(),
			dep.getVersion());
		Collection<Exclusion> excls = new HashSet<Exclusion>();
		for (org.apache.maven.model.Exclusion excl :
			dep.getExclusions()) {
			excls.add(mavenExclusionToExclusion(excl));
		}
		return new Dependency(art,
			dep.getScope(),
			new Boolean(dep.isOptional()),
			excls);
	}

	private Artifact mavenArtifactToArtifact(
		org.apache.maven.artifact.Artifact art) {
		return new DefaultArtifact(art.getGroupId(),
			art.getArtifactId(),
			art.getClassifier(),
			art.getType(),
			art.getVersion());
	}

	private void emitArtifactBody(Artifact art, Collection<Dependency> deps,
		JsonGenerator gen) {
		gen.write("artifactId", art.getArtifactId());
		gen.write("groupId", art.getGroupId());
		gen.write("version", art.getVersion());
		gen.write("classifier", art.getClassifier());
		gen.write("extension", art.getExtension());
		if (deps != null) {
			gen.writeStartArray("dependencies");
			for (Dependency dep : deps) {
				gen.writeStartObject();

				emitArtifactBody(dep.getArtifact(), null, gen);

				gen.write("scope", dep.getScope());
				gen.write("optional", dep.isOptional());

				gen.writeStartArray("exclusions");
				for (Exclusion excl : dep.getExclusions()) {
					gen.writeStartObject();
					gen.write("artifactId",
						excl.getArtifactId());
					gen.write("classifier",
						excl.getClassifier());
					gen.write("extension",
						excl.getExtension());
					gen.write("groupId",
						excl.getGroupId());
					gen.writeEnd();
				}
				gen.writeEnd();

				gen.writeEnd();
			}
			gen.writeEnd();
		}
	}

	private class ArtifactDownloadInfo
	{
		public String url;
		public String hash;
	}

	private ArtifactDownloadInfo getDownloadInfo(Artifact art,
			RepositoryLayout layout,
			String base) throws MojoExecutionException {
		URI rel = layout.getLocation(art, false);
		URI abs;
		try {
			abs = new URI(base + "/" + rel);
		} catch (URISyntaxException e) {
			throw new MojoExecutionException(
				"Parsing repository URI",
				e);
		}
		ArtifactDownloadInfo res = new ArtifactDownloadInfo();
		res.url = abs.toString();

		abs = null;
		for (RepositoryLayout.Checksum ck :
				layout.getChecksums(art, false, rel)) {
			if (ck.getAlgorithm().equals("SHA-1")) {
				try {
					abs = new URI(base + "/" +
							ck.getLocation());
				} catch (URISyntaxException e) {
					throw new MojoExecutionException(
						"Parsing repository URI",
						e);
				}
			}
		}
		if (abs == null) {
			throw new MojoExecutionException(
				"No SHA-1 for " + art.toString());
		}

		InputStream i;
		try {
			i = abs.toURL().openStream();
		} catch (MalformedURLException e) {
			throw new MojoExecutionException(
				"Invalid URL for checksum of " + art.toString(),
				e);
		} catch (IOException e) {
			throw new MojoExecutionException(
				"Unable to connect to " + abs.toString(),
				e);
		}

		byte[] buf = new byte[40];
		try {
			int read = i.read(buf);
			if (read == -1) {
				throw new MojoExecutionException(
					"Early EOF downloading " +
					abs.toString());
			}
			while (read < buf.length) {
				int count =
					i.read(buf, read, buf.length - read);
				if (count == -1) {
					throw new MojoExecutionException(
						"Early EOF downloading " +
						abs.toString());
				}
				read += count;
			}
		} catch (IOException e) {
			throw new MojoExecutionException(
				"Downloading " + abs.toString(),
				e);
		}

		try {
			res.hash = new String(buf, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new MojoExecutionException(
				"Your jvm doesn't support UTF-8, fix that",
				e);
		}
		return res;
	}

	private void handleDependency(Dependency dep,
		List<RemoteRepository> repos,
		Set<Dependency> work,
		Set<Artifact> printed,
		JsonGenerator gen) throws MojoExecutionException {
		Artifact art = dep.getArtifact();
		ArtifactDescriptorRequest req = new ArtifactDescriptorRequest(
			art,
			repos,
			null);
		ArtifactDescriptorResult res;
		try {
			res = repoSystem.readArtifactDescriptor(repoSession,
				req);
		} catch (ArtifactDescriptorException e) {
			throw new MojoExecutionException(
				"getting descriptor for " + art.toString(),
				e);
		}

		/* Ensure we're keying on the things we care about */
		Artifact artKey = new DefaultArtifact(art.getGroupId(),
			art.getArtifactId(),
			art.getClassifier(),
			art.getExtension(),
			art.getVersion());
		if (printed.add(artKey)) {
			gen.writeStartObject();
			emitArtifactBody(art,
				res.getDependencies(),
				gen);
			if (res.getRepository() instanceof RemoteRepository) {
				RemoteRepository repo = (RemoteRepository) res
					.getRepository();
				gen.write("authenticated",
					repo.getAuthentication() != null);
				RepositoryLayout layout;
				try {
					layout = layoutProvider
						.newRepositoryLayout(
							repoSession,
							repo);
				} catch (NoRepositoryLayoutException e) {
					throw new MojoExecutionException(
						"Getting repository layout",
						e);
				}

				String base = repo.getUrl();
				ArtifactDownloadInfo info =
					getDownloadInfo(art, layout, base);
				gen.write("url", info.url);
				gen.write("sha1", info.hash);

				gen.writeStartArray("relocations");
				for (Artifact rel : res.getRelocations()) {
					Artifact relPom = new DefaultArtifact(
						rel.getGroupId(),
						rel.getArtifactId(),
						rel.getClassifier(),
						"pom",
						rel.getVersion());
					gen.writeStartObject();
					info = getDownloadInfo(art, layout,
						base);
					gen.write("url", info.url);
					gen.write("sha1", info.hash);
					gen.writeEnd();
				}
				gen.writeEnd();
			}
			gen.writeEnd();
		}

		if (!art.getExtension().equals("pom")) {
			Artifact pomArt = new DefaultArtifact(art.getGroupId(),
				art.getArtifactId(),
				null,
				"pom",
				art.getVersion());
			Dependency pomDep = new Dependency(pomArt,
				"compile",
				new Boolean(false),
				dep.getExclusions());
			work.add(pomDep);
		}

		for (Dependency subDep : res.getDependencies()) {
			if (subDep.isOptional()) {
				continue;
			}
			String scope = subDep.getScope();
			if (scope != null && (scope.equals("provided")
				|| scope.equals("test")
				|| scope.equals("system"))) {
				continue;
			}
			Artifact subArt = subDep.getArtifact();
			HashSet<Exclusion> excls = new HashSet<Exclusion>();
			boolean excluded = false;
			for (Exclusion excl : dep.getExclusions()) {
				if (excl.getArtifactId().equals(
						subArt.getArtifactId()) &&
					excl.getGroupId().equals(
						subArt.getGroupId())) {
					excluded = true;
					break;
				}
				excls.add(excl);
			}
			if (excluded) {
				continue;
			}
			for (Exclusion excl : subDep.getExclusions()) {
				excls.add(excl);
			}

			Dependency newDep = new Dependency(subArt,
				dep.getScope(),
				dep.getOptional(),
				excls);
			work.add(newDep);
		}
	}

	public void execute() throws MojoExecutionException
	{
		repoSession = new DefaultRepositorySystemSession(repoSession);
		ParentPOMPropagatingArtifactDescriptorReaderDelegate d = new
			ParentPOMPropagatingArtifactDescriptorReaderDelegate();
		repoSession.setConfigProperty(
			ArtifactDescriptorReaderDelegate.class.getName(),
			d);
		repoSession.setReadOnly();

		Set<Dependency> work = new HashSet<Dependency>();
		Set<Dependency> seen = new HashSet<Dependency>();
		Set<Artifact> printed = new HashSet<Artifact>();
		for (Plugin p : project.getBuildPlugins()) {
			Artifact art = new DefaultArtifact(p.getGroupId(),
				p.getArtifactId(),
				null,
				"jar",
				p.getVersion());
			Dependency dep = new Dependency(art, "compile");
			work.add(dep);
			for (org.apache.maven.model.Dependency subDep :
					p.getDependencies()) {
				work.add(mavenDependencyToDependency(subDep));
			}
		}
		for (org.apache.maven.model.Dependency dep :
				project.getDependencies()) {
			work.add(mavenDependencyToDependency(dep));
		}
		try (JsonGenerator gen = Json.createGenerator(
					new FileOutputStream("deps.json"))) {
			gen.writeStartObject();

			gen.writeStartObject("project");
			emitArtifactBody(
				mavenArtifactToArtifact(project.getArtifact()),
				work,
				gen);
			gen.writeEnd();

			gen.writeStartArray("dependencies");
			List<RemoteRepository> repos =
				new ArrayList<RemoteRepository>(
					project.getRemoteProjectRepositories());
			repos.addAll(project.getRemotePluginRepositories());
			while (!work.isEmpty()) {
				Iterator<Dependency> it = work.iterator();
				Dependency dep = it.next();
				it.remove();

				if (seen.add(dep)) {
					handleDependency(dep,
						repos,
						work,
						printed,
						gen);
				}
			}
			gen.writeEnd();

			gen.writeEnd();
		} catch (FileNotFoundException e) {
			throw new MojoExecutionException(
					"Opening deps.json",
					e);
		}
	}
}
