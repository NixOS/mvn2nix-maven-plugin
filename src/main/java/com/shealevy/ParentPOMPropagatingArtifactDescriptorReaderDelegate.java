package com.shealevy;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.repository.internal.ArtifactDescriptorReaderDelegate;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;

public class ParentPOMPropagatingArtifactDescriptorReaderDelegate
	extends ArtifactDescriptorReaderDelegate
{
	@Override
	public void populateResult(RepositorySystemSession session,
		ArtifactDescriptorResult result,
		Model model) {
		super.populateResult(session, result, model);
		Parent parent = model.getParent();
		if (parent != null) {
			DefaultArtifact art =
				new DefaultArtifact(parent.getGroupId(),
					parent.getArtifactId(),
					"pom",
					parent.getVersion());
			Dependency dep = new Dependency(art, "compile");
			result.addDependency(dep);
		}
	}
}
