package com.shealevy;

import java.util.ArrayList;

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

@Mojo(name = "sayhi")
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
		for (ArtifactResult res : results.getArtifactResults()) {
			Artifact art = res.getArtifact();
			if (art != null && art.getFile() != null) {
				getLog().info(art.getFile().toString());
			} else {
				getLog().info("Not resolved");
			}
		}
	}
}
