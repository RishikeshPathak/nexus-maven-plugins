/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2012 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.maven.staging;

import java.io.File;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.sonatype.maven.mojo.execution.MojoExecution;
import org.sonatype.nexus.maven.staging.deploy.DeployMojo;
import org.sonatype.nexus.maven.staging.workflow.CloseStageRepositoryMojo;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

/**
 * Base class for nexus-staging-maven-plugin mojos, gathering the most common parameters.
 *
 * @author cstamas
 */
public abstract class AbstractStagingMojo
    extends AbstractMojo
{
    // Maven sourced stuff

    /**
     * Maven Session.
     */
    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private MavenSession mavenSession;

    /**
     * Plugin groupId. While these are accessible from {@link #mojoExecution}, the methods are present only in Maven 3+.
     * Requirement is to work in Maven2 also, hence, we use a "compatible way" to obtain these values instead.
     */
    @Parameter( defaultValue = "${plugin.groupId}", readonly = true, required = true )
    private String pluginGroupId;

    /**
     * Plugin artifactId. While these are accessible from {@link #mojoExecution}, the methods are present only in Maven
     * 3+. Requirement is to work in Maven2 also, hence, we use a "compatible way" to obtain these values instead.
     */
    @Parameter( defaultValue = "${plugin.artifactId}", readonly = true, required = true )
    private String pluginArtifactId;

    /**
     * Plugin version. While these are accessible from {@link #mojoExecution}, the methods are present only in Maven 3+.
     * Requirement is to work in Maven2 also, hence, we use a "compatible way" to obtain these values instead.
     */
    @Parameter( defaultValue = "${plugin.version}", readonly = true, required = true )
    private String pluginVersion;

    /**
     * Mojo execution.
     */
    @Parameter( defaultValue = "${mojoExecution}", readonly = true, required = true )
    private org.apache.maven.plugin.MojoExecution mojoExecution;

    /**
     * Flag whether Maven is currently in online/offline mode.
     */
    @Parameter( defaultValue = "${settings.offline}", readonly = true, required = true )
    private boolean offline;

    /**
     * Sec Dispatcher.
     */
    @Component
    private SecDispatcher secDispatcher;

    // user supplied parameters (staging related)

    /**
     * Specifies an alternative staging directory to which the project artifacts should be "locally staged". By default,
     * staging directory will be looked for under {@code $}{{@code project.build.directory} {@code /nexus-staging}
     * folder of the first encountered module that has this Mojo defined for execution (Warning: this means, if top
     * level POM is an aggregator, it will be NOT in top level!).
     */
    @Parameter( property = "altStagingDirectory" )
    private File altStagingDirectory;

    /**
     * The base URL for a Nexus Professional instance that includes the nexus-staging-plugin.
     */
    @Parameter( property = "nexusUrl" )
    private String nexusUrl;

    /**
     * The ID of the server entry in the Maven settings.xml from which to pick credentials to contact remote Nexus.
     */
    @Parameter( property = "serverId" )
    private String serverId;

    /**
     * The repository "description" to pass to Nexus when repository staging workflow step is made. If none passed in,
     * plugin defaults are applied.
     */
    @Parameter( property = "description" )
    private String description;

    /**
     * Controls whether the staging repository is kept or not (it will be dropped) in case of staging rule failure when
     * "close" action is performed against it. This is applied in both cases, {@link DeployMojo} and
     * {@link CloseStageRepositoryMojo} invocations.
     */
    @Parameter( property = "keepStagingRepositoryOnCloseRuleFailure" )
    private boolean keepStagingRepositoryOnCloseRuleFailure;

    // == getters for stuff above

    protected String getNexusUrl()
    {
        return nexusUrl;
    }

    protected String getServerId()
    {
        return serverId;
    }

    protected String getDescription()
    {
        return description;
    }

    public boolean isKeepStagingRepositoryOnCloseRuleFailure()
    {
        return keepStagingRepositoryOnCloseRuleFailure;
    }

    protected MavenSession getMavenSession()
    {
        return mavenSession;
    }

    protected SecDispatcher getSecDispatcher()
    {
        return secDispatcher;
    }

    protected String getPluginGav()
    {
        return pluginGroupId + ":" + pluginArtifactId + ":" + pluginVersion;
    }

    // == common methods

    /**
     * Throws {@link MojoFailureException} if Maven is invoked offline, as nexus-staging-maven-plugin MUST WORK online.
     *
     * @throws MojoFailureException if Maven is invoked offline.
     */
    protected void failIfOffline()
        throws MojoFailureException
    {
        if ( offline )
        {
            throw new MojoFailureException(
                "Cannot use Staging features in Offline mode, as REST Requests are needed to be made against Nexus even while locally staging only." );
        }
    }

    /**
     * Returns the first project in reactor that has this plugin defined.
     *
     * @return
     */
    protected MavenProject getFirstProjectWithThisPluginDefined()
    {
        return MojoExecution.getFirstProjectWithMojoInExecution( mavenSession, pluginGroupId, pluginArtifactId, null );
    }

    /**
     * In case of "ordinary" (reactor) build, it returns {@code true} if the current project is the last one being
     * executed in this build that has this Mojo defined. In case of direct invocation of this Mojo over CLI, it returns
     * {@code true} if the current project is the last one being executed in this build.
     *
     * @return true if last project is being built.
     */
    protected boolean isThisLastProjectWithThisMojoInExecution()
    {
        if ( "default-cli".equals( mojoExecution.getExecutionId() ) )
        {
            return MojoExecution.isCurrentTheLastProjectInExecution( mavenSession );
        }
        else
        {
            // method mojoExecution.getGoal() is added in maven3!
            return isThisLastProjectWithMojoInExecution( mojoExecution.getMojoDescriptor().getGoal() );
        }
    }

    /**
     * Returns true if the current project is the last one being executed in this build that has passed in goal
     * execution defined.
     *
     * @return true if last project is being built.
     */
    protected boolean isThisLastProjectWithMojoInExecution( final String goal )
    {
        return MojoExecution.isCurrentTheLastProjectWithMojoInExecution( mavenSession, pluginGroupId, pluginArtifactId,
                                                                         goal );
    }

    /**
     * Returns the staging directory root, that is either set explictly by user in plugin configuration (see
     * {@link #altStagingDirectory} parameter), or it's location is calculated taking as base the first project in this
     * reactor that will/was executing this plugin.
     *
     * @return
     */
    protected File getStagingDirectoryRoot()
    {
        if ( altStagingDirectory != null )
        {
            return altStagingDirectory;
        }
        else
        {
            final MavenProject firstWithThisMojo = getFirstProjectWithThisPluginDefined();
            if ( firstWithThisMojo != null )
            {
                final File firstWithThisMojoBuildDir;
                if ( firstWithThisMojo.getBuild() != null && firstWithThisMojo.getBuild().getDirectory() != null )
                {
                    firstWithThisMojoBuildDir =
                        new File( firstWithThisMojo.getBuild().getDirectory() ).getAbsoluteFile();
                }
                else
                {
                    firstWithThisMojoBuildDir = new File( firstWithThisMojo.getBasedir().getAbsoluteFile(), "target" );
                }
                return new File( firstWithThisMojoBuildDir, "nexus-staging" );
            }
            else
            {
                // top level (invocation place with some sensible defaults)
                // TODO: what can we do here? Do we have MavenProject at all?
                return new File( getMavenSession().getExecutionRootDirectory() + "/target/nexus-staging" );
            }
        }
    }
}