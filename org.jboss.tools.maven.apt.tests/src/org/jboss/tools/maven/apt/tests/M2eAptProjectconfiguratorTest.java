/*************************************************************************************
 * Copyright (c) 2012 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Red Hat, Inc - Initial implementation.
 ************************************************************************************/
package org.jboss.tools.maven.apt.tests;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.jdt.apt.core.internal.util.FactoryContainer;
import org.eclipse.jdt.apt.core.internal.util.FactoryPath;
import org.eclipse.jdt.apt.core.util.AptConfig;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.ResolverConfiguration;
import org.eclipse.m2e.tests.common.AbstractMavenProjectTestCase;
import org.jboss.tools.maven.apt.MavenJdtAptPlugin;
import org.jboss.tools.maven.apt.preferences.AnnotationProcessingMode;
import org.jboss.tools.maven.apt.preferences.IPreferencesManager;

@SuppressWarnings("restriction")
public class M2eAptProjectconfiguratorTest extends AbstractMavenProjectTestCase {
	
	public void setUp() throws Exception {
		super.setUp();
		IPreferencesManager preferencesManager = MavenJdtAptPlugin.getDefault().getPreferencesManager();
		preferencesManager.setAnnotationProcessorMode(null, AnnotationProcessingMode.jdt_apt);
	}

	public void testMavenCompilerPluginSupport() throws Exception {
		defaultTest("p1", "target/generated-sources/annotations");
	}

	public void testMavenCompilerPluginDependencies() throws Exception {
		defaultTest("p2", "target/generated-sources/m2e-apt");
	}

	public void testMavenProcessorPluginSupport() throws Exception {
		defaultTest("p3", "target/generated-sources/apt");
	}
	
	public void testDisabledAnnotationProcessing() throws Exception {
		testDisabledAnnotationProcessing("p4");//using <compilerArgument>-proc:none</compilerArgument>
		testDisabledAnnotationProcessing("p5");//using <proc>none</proc>
	}
	
	public void testAnnotationProcessorArguments() throws Exception {
		Map<String, String> expectedOptions = new HashMap<String, String>(2);
		expectedOptions.put("addGenerationDate", "true");
		expectedOptions.put("addGeneratedAnnotation", "true");
		testAnnotationProcessorArguments("p6", expectedOptions);
		testAnnotationProcessorArguments("p7", expectedOptions);
	}
	
	public void testAnnotationProcessorArgumentsMap() throws Exception {
		Map<String, String> expectedOptions = new HashMap<String, String>(2);
		expectedOptions.put("addGenerationDate", "true");
		// this option is false in <compilerArguments> but is overriden by <compilerArgument>
		expectedOptions.put("addGeneratedAnnotation", "true");
		expectedOptions.put("flag", null);
		testAnnotationProcessorArguments("argumentMap", expectedOptions);
	}

	public void testNoAnnotationProcessor() throws Exception {
		IProject p = importProject("projects/p0/pom.xml");
		waitForJobsToComplete();

		// Import doesn't build, so we trigger it manually
		p.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
		waitForJobsToComplete();

		IJavaProject javaProject = JavaCore.create(p);
		assertNotNull(javaProject);
		
		assertFalse("Annotation processing is enabled for "+p, AptConfig.isEnabled(javaProject));
        String expectedOutputFolder = "target/generated-sources/annotations";
		IFolder annotationsFolder = p.getFolder(expectedOutputFolder );
        assertFalse(annotationsFolder  + " was generated", annotationsFolder.exists());
	}
	
	
	public void testRuntimePluginDependency() throws Exception {
		
		IProject p = importProject("projects/eclipselink/pom.xml");
		waitForJobsToComplete();

		// Import doesn't build, so we trigger it manually
		p.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
		waitForJobsToComplete();

		IJavaProject javaProject = JavaCore.create(p);
		assertNotNull(javaProject);
		
		assertTrue("Annotation processing is disabled for "+p, AptConfig.isEnabled(javaProject));
        String expectedOutputFolder = "target/generated-sources/annotations";
		IFolder annotationsFolder = p.getFolder(expectedOutputFolder );
        assertTrue(annotationsFolder  + " was not generated", annotationsFolder.exists());
     
        FactoryPath factoryPath = (FactoryPath) AptConfig.getFactoryPath(javaProject);
        String modelGen = "org.eclipse.persistence.jpa.modelgen.processor-2.3.2.jar";
        boolean foundRuntimeDependency = false;
        for (FactoryContainer container : factoryPath.getEnabledContainers().keySet()) {
			if (("M2_REPO/org/eclipse/persistence/org.eclipse.persistence.jpa.modelgen.processor/2.3.2/" + modelGen).equals(container.getId())){
				foundRuntimeDependency = true;
				break;
			}
		}
        assertTrue(modelGen + " was not found", foundRuntimeDependency);

		/*
		There's an ugly bug in Tycho which makes 
		JavaModelManager.getJavaModelManager().createAnnotationProcessorManager() return null
		as a consequence, no annotation processors are run during Tycho builds
		See http://dev.eclipse.org/mhonarc/lists/tycho-user/msg02344.html
		
		For the time being, only APT configuration can be tested, not APT build outcomes
		*/
        if (JavaModelManager.getJavaModelManager().createAnnotationProcessorManager() == null) {
        	return;
        }

        IFile generatedFile = p.getFile(expectedOutputFolder + "/foo/bar/Dummy_.java");
        if (!generatedFile.exists()) {
        	//APT was triggered during project configuration, i.e. before META-INF/persistence.xml was copied to 
        	//target/classes by the maven-resource-plugin build participant. eclipselink modelgen could not find it 
        	// and skipped model generation. Pretty annoying and I dunno how to fix that ... yet.
        	
        	//Let's check a nudge to Dummy.java fixes this.
        	IFile dummy = p.getFile("src/main/java/foo/bar/Dummy.java");
        	dummy.touch(monitor);
        	p.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
        	waitForJobsToComplete();
        }
        
        assertTrue(generatedFile + " was not generated", generatedFile.exists());
		assertNoErrors(p);
	}
	
	public void testDisableAnnotationProcessingFromWorkspace() throws Exception {
		IPreferencesManager preferencesManager = MavenJdtAptPlugin.getDefault().getPreferencesManager();
		try {
			preferencesManager.setAnnotationProcessorMode(null, AnnotationProcessingMode.disabled);
			IProject p = importProject("projects/p1/pom.xml");
			waitForJobsToComplete();
			IJavaProject javaProject = JavaCore.create(p);	
			assertFalse("JDT APT support was enabled", AptConfig.isEnabled(javaProject));
			
			IFolder annotationsFolder = p.getFolder("target/generated-sources/annotations");
		    assertFalse(annotationsFolder  + " was generated", annotationsFolder.exists());
			
		} finally {
			preferencesManager.setAnnotationProcessorMode(null, AnnotationProcessingMode.jdt_apt);
		}
	}	

	public void testDisableAnnotationProcessingFromProject() throws Exception {
		IProject p = importProject("projects/p1/pom.xml");
		waitForJobsToComplete();
		IJavaProject javaProject = JavaCore.create(p);	
		assertTrue("JDT APT support was not enabled", AptConfig.isEnabled(javaProject));

		//Manually disable APT support 
		AptConfig.setEnabled(javaProject, false);
		
		//Disable m2e-apt on the project
		IPreferencesManager preferencesManager =MavenJdtAptPlugin.getDefault().getPreferencesManager();
		preferencesManager.setAnnotationProcessorMode(p, AnnotationProcessingMode.disabled);
		
		//Update Maven Configuration
		updateProject(p);
		
		//Check APT support is still disabled
		assertFalse("JDT APT support was enabled", AptConfig.isEnabled(javaProject));
			
	}	
	
	
	public void testPluginExecutionDelegation() throws Exception {
		IPreferencesManager preferencesManager = MavenJdtAptPlugin.getDefault().getPreferencesManager();
		try {
			preferencesManager.setAnnotationProcessorMode(null, AnnotationProcessingMode.maven_execution);
			IProject p = importProject("projects/p3/pom.xml");
			waitForJobsToComplete();
			
			p.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
			waitForJobsToComplete();
			
			IJavaProject javaProject = JavaCore.create(p);	
			assertFalse("JDT APT support was enabled", AptConfig.isEnabled(javaProject));
			
			IFolder annotationsFolder = p.getFolder("target/generated-sources/apt");
		    assertTrue(annotationsFolder  + " was not generated", annotationsFolder.exists());

			IFolder testAnnotationsFolder = p.getFolder("target/generated-sources/apt-test");
		    assertTrue(testAnnotationsFolder  + " was not generated", testAnnotationsFolder.exists());

		} finally {
			preferencesManager.setAnnotationProcessorMode(null, AnnotationProcessingMode.jdt_apt);
		}
	}	


	private void testDisabledAnnotationProcessing(String projectName) throws Exception {
		IProject p = importProject("projects/"+projectName+"/pom.xml");
		waitForJobsToComplete();
		IJavaProject javaProject = JavaCore.create(p);
		assertNotNull(javaProject);
		assertFalse(AptConfig.isEnabled(javaProject));
	}

	private void testAnnotationProcessorArguments(String projectName, Map<String, String> expectedOptions) throws Exception {
		IProject p = importProject("projects/"+projectName+"/pom.xml");
		waitForJobsToComplete();
		IJavaProject javaProject = JavaCore.create(p);
		assertNotNull(javaProject);
		assertTrue("Annotation processing is disabled for "+projectName, AptConfig.isEnabled(javaProject));
		Map<String, String> options = AptConfig.getProcessorOptions(javaProject);
		for (Map.Entry<String, String> option : expectedOptions.entrySet()) {
			assertEquals(option.getValue(), options.get(option.getKey()));
			if (option.getValue() == null) {
				assertTrue(options.containsKey(option.getKey()));
			}
		}
	}
	
	private void defaultTest(String projectName, String expectedOutputFolder) throws Exception {

		IProject p = importProject("projects/"+projectName+"/pom.xml");
		waitForJobsToComplete();

		p.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
		waitForJobsToComplete();

		IJavaProject javaProject = JavaCore.create(p);
		assertNotNull(javaProject);
		
		assertTrue("Annotation processing is disabled for "+p, AptConfig.isEnabled(javaProject));
        IFolder annotationsFolder = p.getFolder(expectedOutputFolder);
        assertTrue(annotationsFolder  + " was not generated", annotationsFolder.exists());
     
        FactoryPath factoryPath = (FactoryPath) AptConfig.getFactoryPath(javaProject);
        Iterator<FactoryContainer> ite = factoryPath.getEnabledContainers().keySet().iterator();
        FactoryContainer jpaModelGen = ite.next();
        assertEquals(FactoryContainer.FactoryType.VARJAR, jpaModelGen.getType());
        assertEquals("M2_REPO/org/hibernate/hibernate-jpamodelgen/1.1.1.Final/hibernate-jpamodelgen-1.1.1.Final.jar", jpaModelGen.getId());

        FactoryContainer jpaApi = ite.next();
        assertEquals(FactoryContainer.FactoryType.VARJAR, jpaApi.getType());
        assertEquals("M2_REPO/org/hibernate/javax/persistence/hibernate-jpa-2.0-api/1.0.0.Final/hibernate-jpa-2.0-api-1.0.0.Final.jar", jpaApi.getId());

		/*
		There's an ugly bug in Tycho which makes 
		JavaModelManager.getJavaModelManager().createAnnotationProcessorManager() return null
		as a consequence, no annotation processors are run during Tycho builds
		See http://dev.eclipse.org/mhonarc/lists/tycho-user/msg02344.html
		
		For the time being, only APT configuration can be tested, not APT build outcomes
		*/
        if (JavaModelManager.getJavaModelManager().createAnnotationProcessorManager() == null) {
        	return;
        }

        IFile generatedFile = p.getFile(expectedOutputFolder + "/foo/bar/Dummy_.java");
		assertTrue(generatedFile + " was not generated", generatedFile.exists());
		assertNoErrors(p);
	}
	
	 protected void updateProject(IProject project) throws Exception {    
	    updateProject(project, null);
	  }	
	
	 protected void updateProject(IProject project, String newPomName) throws Exception {    
		    
	    if (newPomName != null) {
	      copyContent(project, newPomName, "pom.xml");
	    }
	    
	    IProjectConfigurationManager configurationManager = MavenPlugin.getProjectConfigurationManager();
	    ResolverConfiguration configuration = new ResolverConfiguration();
	    configurationManager.enableMavenNature(project, configuration, monitor);
	    configurationManager.updateProjectConfiguration(project, monitor);
	    waitForJobsToComplete();
	    project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
	    waitForJobsToComplete();
	  }	
	
}
