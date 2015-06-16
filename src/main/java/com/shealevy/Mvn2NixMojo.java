package com.shealevy;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "sayhi")
public class Mvn2NixMojo extends AbstractMojo
{
	@Parameter(defaultValue="${project}", readonly=true)
	private MavenProject project;

	public void execute() throws MojoExecutionException
	{
		getLog().info(project.getName());
	}
}
