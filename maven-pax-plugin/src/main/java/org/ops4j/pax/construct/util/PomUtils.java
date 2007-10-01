package org.ops4j.pax.construct.util;

/*
 * Copyright 2007 Stuart McCulloch, Alin Dreghiciu
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
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

/**
 * Provide API and factory for editing Maven project files
 */
public final class PomUtils
{
    /**
     * Hide constructor for utility class
     */
    private PomUtils()
    {
    }

    /**
     * Thrown when a POM element can't be updated {@link Pom}
     */
    public static class ExistingElementException extends MojoExecutionException
    {
        private static final long serialVersionUID = 1L;

        /**
         * @param element the XML element that couldn't be updated
         */
        public ExistingElementException( String element )
        {
            super( element );
        }
    }

    /**
     * API for editing Maven project files
     */
    public interface Pom
    {
        /**
         * @return unique project identifier
         */
        public String getId();

        /**
         * @return project group id
         */
        public String getGroupId();

        /**
         * @return project artifact id
         */
        public String getArtifactId();

        /**
         * @return project version
         */
        public String getVersion();

        /**
         * @return project packaging
         */
        public String getPackaging();

        /**
         * @return sequence of module names contained in this project
         */
        public List getModules();

        /**
         * @return the physical parent project
         */
        public Pom getContainingPom();

        /**
         * @param module a module contained in this project
         * @return the module POM, null if it doesn't exist
         */
        public Pom getModulePom( String module );

        /**
         * @return the underlying Maven POM file
         */
        public File getFile();

        /**
         * @return the directory containing this Maven project
         */
        public File getBasedir();

        /**
         * @return true if this is an OSGi bundle project, otherwise false
         */
        public boolean isBundleProject();

        /**
         * @return the symbolic name for this project, null if it doesn't define one
         */
        public String getBundleSymbolicName();

        /**
         * @return the final bundle produced by this Maven project
         */
        public File getPackagedBundle();

        /**
         * @param pom the new parent project
         * @param relativePath the relative path from this POM to the parent
         * @param overwrite overwrite element if true, otherwise throw {@link ExistingElementException}
         * @throws ExistingElementException
         */
        public void setParent( Pom pom, String relativePath, boolean overwrite )
            throws ExistingElementException;

        /**
         * @param project the new parent project
         * @param relativePath the relative path from this POM to the parent
         * @param overwrite overwrite element if true, otherwise throw {@link ExistingElementException}
         * @throws ExistingElementException
         */
        public void setParent( MavenProject project, String relativePath, boolean overwrite )
            throws ExistingElementException;

        /**
         * Apply refactoring offset to the relative path element
         * 
         * @param offset positive if POM has been moved down, negative if it has been moved up
         */
        public void adjustRelativePath( int offset );

        /**
         * @param repository a Maven repository
         * @param overwrite overwrite element if true, otherwise throw {@link ExistingElementException}
         * @throws ExistingElementException
         */
        public void addRepository( Repository repository, boolean overwrite )
            throws ExistingElementException;

        /**
         * @param module module name
         * @param overwrite overwrite element if true, otherwise throw {@link ExistingElementException}
         * @throws ExistingElementException
         */
        public void addModule( String module, boolean overwrite )
            throws ExistingElementException;

        /**
         * @param module module name
         * @return true if module was removed from the project, otherwise false
         * @throws ExistingElementException
         */
        public boolean removeModule( String module )
            throws ExistingElementException;

        /**
         * @param dependency project dependency
         * @param overwrite overwrite element if true, otherwise throw {@link ExistingElementException}
         * @throws ExistingElementException
         */
        public void addDependency( Dependency dependency, boolean overwrite )
            throws ExistingElementException;

        /**
         * @param dependency project dependency
         * @return true if dependency was removed from the project, otherwise false
         * @throws ExistingElementException
         */
        public boolean removeDependency( Dependency dependency )
            throws ExistingElementException;

        /**
         * @throws IOException
         */
        public void write()
            throws IOException;
    }

    /**
     * Factory method that provides an editor for a given Maven project file
     * 
     * @param here a Maven POM, or a directory containing a file named 'pom.xml'
     * @return simple Maven project editor
     * @throws IOException
     */
    public static Pom readPom( File here )
        throws IOException
    {
        File candidate = here;

        if( here.isDirectory() )
        {
            candidate = new File( here, "pom.xml" );
        }

        return new XppPom( candidate );
    }

    /**
     * Factory method that provides an editor for a new Maven project file
     * 
     * @param here a file, or a directory for the Maven project
     * @return simple Maven project editor
     */
    public static Pom createPom( File here, String groupId, String artifactId )
    {
        File candidate = here;

        if( here.isDirectory() )
        {
            candidate = new File( here, "pom.xml" );
        }

        return new XppPom( candidate, groupId, artifactId );
    }

    /**
     * @param project Maven project
     * @return true if this is an OSGi bundle project, otherwise false
     */
    public static boolean isBundleProject( MavenProject project )
    {
        return isBundleProject( project, null, null, null, false );
    }

    /**
     * @param project Maven project
     * @param resolver artifact resolver
     * @param remoteRepos sequence of remote repositories
     * @param localRepo local Maven repository
     * @param testMetadata check jar manifest for OSGi attributes if true
     * @return true if this is an OSGi bundle project, otherwise false
     */
    public static boolean isBundleProject( MavenProject project, ArtifactResolver resolver, List remoteRepos,
        ArtifactRepository localRepo, boolean testMetadata )
    {
        String packaging = project.getPackaging();
        if( packaging != null && packaging.indexOf( "bundle" ) >= 0 )
        {
            return true;
        }
        else
        {
            return isBundleArtifact( project.getArtifact(), resolver, remoteRepos, localRepo, testMetadata );
        }
    }

    /**
     * @param artifact Maven artifact
     * @param resolver artifact resolver
     * @param remoteRepos sequence of remote repositories
     * @param localRepo local Maven repository
     * @param testMetadata check jar manifest for OSGi attributes if true
     * @return true if this is an OSGi bundle artifact, otherwise false
     */
    public static boolean isBundleArtifact( Artifact artifact, ArtifactResolver resolver, List remoteRepos,
        ArtifactRepository localRepo, boolean testMetadata )
    {
        String type = artifact.getType();
        if( null != type && type.indexOf( "bundle" ) >= 0 )
        {
            return true;
        }
        else if( !testMetadata )
        {
            return false;
        }

        try
        {
            if( artifact.getFile() == null || !artifact.getFile().exists() )
            {
                resolver.resolve( artifact, remoteRepos, localRepo );
            }

            JarFile jarFile = new JarFile( artifact.getFile() );
            Manifest manifest = jarFile.getManifest();

            Attributes mainAttributes = manifest.getMainAttributes();

            return mainAttributes.getValue( "Bundle-SymbolicName" ) != null
                || mainAttributes.getValue( "Bundle-Name" ) != null;
        }
        catch( ArtifactResolutionException e )
        {
            return false;
        }
        catch( ArtifactNotFoundException e )
        {
            return false;
        }
        catch( IOException e )
        {
            return false;
        }
        catch( NullPointerException e )
        {
            return false;
        }
    }

    /**
     * Try to combine overlapping group and artifact identifiers to remove duplicate segments
     * 
     * @param groupId project group id
     * @param artifactId project artifact id
     * @return the combined group and artifact sequence
     */
    public static String getCompoundName( String groupId, String artifactId )
    {
        if( artifactId.startsWith( groupId + '.' ) || artifactId.equals( groupId ) )
        {
            return artifactId;
        }
        else if( groupId.endsWith( '.' + artifactId ) )
        {
            return groupId;
        }

        return groupId + '.' + artifactId;
    }

    /**
     * Find the symbolic (meta) Maven version, such as 1.0-SNAPSHOT
     * 
     * @param artifact Maven artifact
     * @return meta version for the artifact
     */
    public static String getMetaVersion( Artifact artifact )
    {
        try
        {
            return artifact.getSelectedVersion().toString();
        }
        catch( OverConstrainedVersionException e )
        {
            return artifact.getVersion();
        }
        catch( NullPointerException e )
        {
            return artifact.getVersion();
        }
    }
}
