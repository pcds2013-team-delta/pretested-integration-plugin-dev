package org.jenkinsci.plugins.pretestedintegration;

import static org.mockito.Mockito.*;

import org.junit.*;

import hudson.AbortException;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.plugins.mercurial.MercurialSCM;

public class PretestedIntegrationHgUtilsTest extends PretestedIntegrationTestCase {

	public void testShouldCreateInstance() throws Exception {
		genericTestConstructor(HgUtils.class);
	}
	/*
	public void testShouldCreateArgumentListBuilder() throws Exception {
		BuildListener listener = mock(BuildListener.class);
		Launcher launcher = mock(Launcher.class);
		AbstractProject project = mock(AbstractProject.class);
		when(project.getScm()).thenReturn(new MercurialSCM());
		AbstractBuild build = mock(AbstractBuild.class);
		when(build.getProject()).thenReturn(project);
		boolean thrown = false;
		try{ 
			HgUtils.createArgumentListBuilder(build, launcher, listener);
		} catch(AbortException e){
			thrown=true;
		}
		assertTrue(thrown);
	}*/
	
	public void testShouldGiveAbortException() throws Exception {
		BuildListener listener = mock(BuildListener.class);
		Launcher launcher = mock(Launcher.class);
		AbstractProject project = mock(AbstractProject.class);
		when(project.getScm()).thenReturn(null);
		AbstractBuild build = mock(AbstractBuild.class);
		when(build.getProject()).thenReturn(project);
		boolean thrown = false;
		try{ 
			HgUtils.createArgumentListBuilder(build, launcher, listener);
		} catch(AbortException e){
			thrown=true;
		}
		assertTrue(thrown);
	}
}
