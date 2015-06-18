package com.shealevy;

import java.util.List;
import java.util.ArrayList;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.net.URI;
import java.net.URISyntaxException;

import javax.json.Json;
import javax.json.stream.JsonGenerator;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.project.MavenProject;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;

import org.apache.commons.codec.binary.Hex;

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
	private RepositorySystemSession repoSession;

	public void execute() throws MojoExecutionException
	{
		ArrayList<Dependency> deps = new ArrayList<Dependency>();
		for (org.apache.maven.model.Dependency mvnDep :
				project.getDependencies()) {
			Artifact art = new DefaultArtifact(mvnDep.getGroupId(),
					mvnDep.getArtifactId(),
					mvnDep.getClassifier(),
					mvnDep.getType(),
					mvnDep.getVersion());
			String scope = mvnDep.getScope();
			Boolean opt = new Boolean(mvnDep.isOptional());
			ArrayList<Exclusion> excls = new ArrayList<Exclusion>();
			for (org.apache.maven.model.Exclusion mvnExcl :
					mvnDep.getExclusions()) {
				Exclusion excl = new Exclusion(
						mvnExcl.getGroupId(),
						mvnExcl.getArtifactId(),
						null,
						null);
				excls.add(excl);
			}
			Dependency dep = new Dependency(art, scope, opt, excls);
			deps.add(dep);
		}
		List<RemoteRepository> projectRepos =
			project.getRemoteProjectRepositories();
		CollectRequest cr = new CollectRequest((Dependency) null, deps,
				projectRepos);
		DependencyRequest dr = new DependencyRequest(cr, null);
		DependencyResult results;
		try {
			results =
				repoSystem.resolveDependencies(repoSession, dr);
		} catch (DependencyResolutionException e) {
			throw new MojoExecutionException(
					"Resolving dependencies",
					e);
		}
		ByteBuffer buf = ByteBuffer.allocateDirect(512 * 125);
		try (JsonGenerator gen = Json.createGenerator(
					new FileOutputStream("deps.json"))) {
			gen.writeStartArray();
			for (ArtifactResult res :
					results.getArtifactResults()) {
				gen.writeStartObject();

				Artifact art = res.getArtifact();
				gen.writeStartObject("artifact");
				gen.write("artifact-id", art.getArtifactId());
				gen.write("base-version", art.getBaseVersion());
				gen.write("classifier", art.getClassifier());
				gen.write("extension", art.getExtension());
				gen.write("group-id", art.getGroupId());
				gen.write("version", art.getVersion());
				gen.write("snapshot", art.isSnapshot());
				gen.writeEnd();

				RemoteRepository repo =
					(RemoteRepository) res.getRepository();
				gen.writeStartObject("repository");
				gen.write(
					"authenticated",
					repo.getAuthentication() != null);
				gen.write("content-type",
						repo.getContentType());
				gen.write("id", repo.getId());
				gen.write("url", repo.getUrl());
				gen.writeEnd();

				DependencyNode node = res.getRequest()
					.getDependencyNode();
				if (node != null) {
					Dependency dep = node.getDependency();
					gen.writeStartObject("dependency");
					gen.write("scope", dep.getScope());
					gen.write("optional", dep.isOptional());
					gen.writeStartArray("exclusions");
					for (Exclusion excl :
							dep.getExclusions()) {
						gen.writeStartObject();
						gen.write(
							"artifact-id",
							excl.getArtifactId());
						gen.write(
							"classifier",
							excl.getClassifier());
						gen.write(
							"extension",
							excl.getExtension());
						gen.write(
							"group-id",
							excl.getGroupId());
						gen.writeEnd();
					}
					gen.writeEnd();
					gen.writeEnd();
				}

				MessageDigest md;
				try {
					md = MessageDigest.getInstance(
							"SHA-256");
				} catch (NoSuchAlgorithmException e) {
					throw new MojoExecutionException(
							"Creating SHA-256 hash",
							e);
				}
				try (FileChannel fc = FileChannel.open(
						art.getFile().toPath())) {
					while (fc.read(buf) != -1) {
						buf.flip();
						md.update(buf);
						buf.clear();
					}
				} catch (IOException e) {
					throw new MojoExecutionException(
							"Reading artifact",
							e);
				}
				gen.write("sha256", Hex.encodeHexString(
							md.digest()));

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

				URI abs;
				try {
					abs = new URI(repo.getUrl())
						.resolve(layout.getLocation(art,
								false));
				} catch (URISyntaxException e) {
					throw new MojoExecutionException(
						"Parsing repository URI",
						e);
				}
				gen.write("url", abs.toString());
				gen.writeEnd();
			}
			gen.writeEnd();
		} catch (FileNotFoundException e) {
			throw new MojoExecutionException(
					"Opening deps.json",
					e);
		}
	}
}
