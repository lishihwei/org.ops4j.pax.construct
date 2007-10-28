package org.ops4j.pax.construct.archetype;

/*
 * Copyright 2007 Stuart McCulloch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.SelectorUtils;
import org.ops4j.pax.construct.util.BndUtils.Bnd;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Create a new bundle project inside an existing Pax-Construct OSGi project
 * 
 * <code><pre>
 *   mvn pax:create-bundle -Dpackage=... [-DbundleName=...] [-Dversion=...]
 * </pre></code>
 * 
 * or create a standalone version which doesn't require an existing project
 * 
 * <code><pre>
 *   cd some-empty-folder
 *   mvn org.ops4j:maven-pax-plugin:create-bundle ...etc...
 * </pre></code>
 * 
 * @extendsPlugin archetype
 * @extendsGoal create
 * @goal create-bundle
 */
public class OSGiBundleArchetypeMojo extends AbstractPaxArchetypeMojo
{
    /**
     * The logical parent of the new project (use artifactId or groupId:artifactId).
     * 
     * @parameter expression="${parentId}" default-value="compiled-bundle-settings"
     */
    private String parentId;

    /**
     * The key Java package contained inside the bundle.
     * 
     * @parameter expression="${package}"
     * @required
     */
    private String packageName;

    /**
     * The symbolic-name for the bundle (defaults to packageName if empty).
     * 
     * @parameter expression="${bundleName}"
     */
    private String bundleName;

    /**
     * The version of the bundle.
     * 
     * @parameter expression="${version}" default-value="1.0-SNAPSHOT"
     */
    private String version;

    /**
     * When true, provide an example service API.
     * 
     * @parameter expression="${interface}" default-value="true"
     */
    private boolean provideInterface;

    /**
     * When true, provide some example implementation code.
     * 
     * @parameter expression="${internals}" default-value="true"
     */
    private boolean provideInternals;

    /**
     * When true, provide an example Bundle-Activator class.
     * 
     * @parameter expression="${activator}" default-value="true"
     */
    private boolean provideActivator;

    /**
     * Add basic support for the selected JUnit version.
     * 
     * @parameter expression="${junitVersion}"
     */
    private String junitVersion;

    /**
     * Add basic support for the selected Spring version.
     * 
     * @parameter expression="${springVersion}"
     */
    private String springVersion;

    /**
     * When true, do not add any dependencies to the project (useful when they are already provided by another POM).
     * 
     * @parameter expression="${noDependencies}"
     */
    private boolean noDependencies;

    /**
     * {@inheritDoc}
     */
    protected String getParentId()
    {
        return parentId;
    }

    /**
     * {@inheritDoc}
     */
    protected void updateExtensionFields()
    {
        // use the Java package as the symbolic name if no name given
        if( null == bundleName || bundleName.trim().length() == 0 )
        {
            bundleName = packageName;
        }

        // should we provide code samples?
        if( provideInterface || provideInternals )
        {
            // OSGi service + activator example
            scheduleArchetype( PAX_ARCHETYPE_GROUP_ID, "maven-archetype-osgi-service", getArchetypeVersion() );
            if( springVersion != null )
            {
                // Spring Dynamic-Modules bean example
                scheduleArchetype( PAX_ARCHETYPE_GROUP_ID, "maven-archetype-spring-bean", getArchetypeVersion() );
            }
        }

        getArchetypeMojo().setField( "archetypeArtifactId", "maven-archetype-osgi-bundle" );

        getArchetypeMojo().setField( "groupId", getInternalGroupId() );
        getArchetypeMojo().setField( "artifactId", bundleName );
        getArchetypeMojo().setField( "version", version );

        getArchetypeMojo().setField( "packageName", packageName );
    }

    /**
     * Provide Velocity template with customized Bundle-SymbolicName
     * 
     * @return bundle symbolic name
     */
    public String getBundleSymbolicName()
    {
        return getCompoundId( getInternalGroupId(), bundleName );
    }

    /**
     * {@inheritDoc}
     */
    protected void postProcess( Pom pom, Bnd bnd )
        throws MojoExecutionException
    {
        if( null == pom.getParentId() )
        {
            makeStandalone( pom );
        }

        markBogusFiles();

        updatePomDependencies( pom );
        updateBndInstructions( bnd );
    }

    /**
     * Add various dependencies to the Maven project to allow out-of-the-box compilation
     * 
     * @param pom Maven project model
     * @throws MojoExecutionException
     */
    private void updatePomDependencies( Pom pom )
        throws MojoExecutionException
    {
        if( !noDependencies )
        {
            addCoreOSGiSupport( pom );

            if( junitVersion != null )
            {
                addJUnitTestSupport( pom );
            }
            if( springVersion != null )
            {
                addSpringBeanSupport( pom );
            }
        }
    }

    /**
     * Mark any temporary or unnecessary files
     */
    private void markBogusFiles()
    {
        String packagePath = packageName.replace( '.', '/' );

        if( !provideInterface )
        {
            addTempFiles( "src/main/java/" + packagePath + "/*.java" );
        }
        if( !provideInternals )
        {
            addTempFiles( "src/main/resources/" );
            addTempFiles( "src/main/java/" + packagePath + "/internal/" );
        }
        if( !provideInternals || null == junitVersion )
        {
            addTempFiles( "src/test/resources/" );
            addTempFiles( "src/test/java/" + packagePath + "/internal/" );
        }
        if( !provideActivator )
        {
            addTempFiles( "src/main/java/" + packagePath + "/internal/*Activator.java" );
        }

        // poms no longer needed
        addTempFiles( "poms/" );
    }

    /**
     * Add additional POM elements to make it work standalone
     * 
     * @param pom Maven project model
     * @throws MojoExecutionException
     */
    private void makeStandalone( Pom pom )
        throws MojoExecutionException
    {
        File baseDir = pom.getBasedir();
        Pom compiledSettings;
        Pom pluginSettings;

        try
        {
            compiledSettings = PomUtils.readPom( new File( baseDir, "poms/compiled" ) );
            pluginSettings = PomUtils.readPom( new File( baseDir, "poms" ) );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Unable to find settings POM" );
        }

        // Must merge plugin fragment first, so child elements combine properly!
        pom.mergeSection( pluginSettings, "build/pluginManagement/plugins", "build", false );
        pom.mergeSection( compiledSettings, "build/plugins", "build", false );

        // always tie the pax-plugin to a specific version (helps with reproducible builds)
        pom.updatePluginVersion( "org.ops4j", "maven-pax-plugin", getArchetypeVersion() );

        // for latest bundle plugin
        Repository repository = new Repository();
        repository.setId( "ops4j-snapshots" );
        repository.setUrl( "http://repository.ops4j.org/mvn-snapshots" );

        pom.addRepository( repository, true, false, true, true );
    }

    /**
     * Adds the standard R4 OSGi API to the build path
     * 
     * @param pom Maven project model
     * @throws MojoExecutionException
     */
    private void addCoreOSGiSupport( Pom pom )
        throws MojoExecutionException
    {
        Dependency osgiCore = new Dependency();
        osgiCore.setGroupId( "org.osgi" );
        osgiCore.setArtifactId( "osgi_R4_core" );
        if( null == pom.getParentId() )
        {
            osgiCore.setVersion( "1.0" );
            osgiCore.setScope( Artifact.SCOPE_PROVIDED );
            osgiCore.setOptional( true );
        }

        pom.addDependency( osgiCore, canOverwrite() );

        Dependency osgiCompendium = new Dependency();
        osgiCompendium.setGroupId( "org.osgi" );
        osgiCompendium.setArtifactId( "osgi_R4_compendium" );
        if( null == pom.getParentId() )
        {
            osgiCompendium.setVersion( "1.0" );
            osgiCompendium.setScope( Artifact.SCOPE_PROVIDED );
            osgiCompendium.setOptional( true );
        }

        pom.addDependency( osgiCompendium, canOverwrite() );
    }

    /**
     * Add additional POM elements to support testing Spring beans
     * 
     * @param pom Maven project model
     * @throws MojoExecutionException
     */
    private void addSpringBeanSupport( Pom pom )
        throws MojoExecutionException
    {
        // Spring milestone repository
        Repository repository = new Repository();
        repository.setId( "spring-milestones" );
        repository.setUrl( "http://s3.amazonaws.com/maven.springframework.org/milestone" );

        pom.addRepository( repository, false, true, canOverwrite(), false );

        if( junitVersion != null )
        {
            Dependency springTest = new Dependency();
            springTest.setGroupId( "org.springframework" );
            springTest.setArtifactId( "spring-test" );
            springTest.setVersion( springVersion );
            springTest.setScope( Artifact.SCOPE_TEST );

            pom.addDependency( springTest, canOverwrite() );
        }

        // mark as optional so we don't force deployment
        Dependency springContext = new Dependency();
        springContext.setGroupId( "org.springframework" );
        springContext.setArtifactId( "spring-context" );
        springContext.setVersion( springVersion );
        springContext.setScope( Artifact.SCOPE_PROVIDED );
        springContext.setOptional( true );

        pom.addDependency( springContext, canOverwrite() );
    }

    /**
     * Add additional POM elements to support testing with JUnit
     * 
     * @param pom Maven project model
     * @throws MojoExecutionException
     */
    private void addJUnitTestSupport( Pom pom )
        throws MojoExecutionException
    {
        Dependency junit = new Dependency();
        junit.setGroupId( "junit" );
        junit.setArtifactId( "junit" );
        junit.setVersion( junitVersion );
        junit.setScope( Artifact.SCOPE_TEST );

        pom.addDependency( junit, canOverwrite() );
    }

    /**
     * Updates the default BND instructions to match the remaining contents
     * 
     * @param bnd Bnd instructions
     * @throws MojoExecutionException
     */
    private void updateBndInstructions( Bnd bnd )
        throws MojoExecutionException
    {
        boolean haveActivator = false;
        boolean haveInternals = false;
        boolean haveInterface = false;

        /*
         * check the source code in case we need to override the basic BND settings
         */
        Set filenames = getFinalFilenames();
        for( Iterator i = filenames.iterator(); i.hasNext(); )
        {
            String name = (String) i.next();

            if( SelectorUtils.matchPath( "src/main/java/**/*Activator.java", name ) )
            {
                haveActivator = true;
            }

            if( SelectorUtils.matchPath( "src/main/java/**/internal/*.java", name ) )
            {
                haveInternals = true;
            }
            else if( SelectorUtils.matchPath( "src/main/java/**/*.java", name ) )
            {
                haveInterface = true;
            }
        }

        applyBndInstructions( bnd, haveActivator, haveInternals, haveInterface );
    }

    /**
     * Apply the new Bnd instructions to the current project
     * 
     * @param bnd Bnd instructions
     * @param haveActivator true if there is an Activator file
     * @param haveInternals true if there are internal packages
     * @param haveInterface true if there are non-internal packages
     * @throws MojoExecutionException
     */
    private void applyBndInstructions( Bnd bnd, boolean haveActivator, boolean haveInternals, boolean haveInterface )
        throws MojoExecutionException
    {
        if( !haveActivator )
        {
            bnd.removeInstruction( "Bundle-Activator" );
        }
        if( !haveInternals )
        {
            bnd.setInstruction( "Private-Package", null, canOverwrite() );
        }
        if( !haveInterface )
        {
            bnd.setInstruction( "Export-Package", null, canOverwrite() );
        }
    }
}
