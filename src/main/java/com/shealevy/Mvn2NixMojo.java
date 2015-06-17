package com.shealevy;

import java.util.ArrayList;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.project.MavenProject;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.ArtifactRepositoryMetadata;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import org.apache.commons.codec.binary.Hex;

@Mojo(name = "mvn2nix")
public class Mvn2NixMojo extends AbstractMojo
{
	@Component
	private MavenProject project;

	@Component
	private RepositorySystem repoSystem;

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
		CollectRequest cr = new CollectRequest((Dependency) null, deps,
				project.getRemoteProjectRepositories());
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
		for (ArtifactResult res : results.getArtifactResults()) {
			Artifact art = res.getArtifact();
			MessageDigest md;
			try {
				md = MessageDigest.getInstance("SHA-256");
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
			getLog().info(art.getFile().toString());
			getLog().info(new String(Hex.encodeHex(md.digest())));
		}
	}
}
