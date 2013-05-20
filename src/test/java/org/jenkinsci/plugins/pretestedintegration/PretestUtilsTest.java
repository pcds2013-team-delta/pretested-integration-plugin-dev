package org.jenkinsci.plugins.pretestedintegration;

import java.io.PrintStream;

import hudson.model.TaskListener;

import static org.mockito.Mockito.*;

public class PretestUtilsTest extends PretestedIntegrationTestCase {

	public void testShouldCreateInstance() throws Exception {
		genericTestConstructor(PretestUtils.class);
	}
	
	public void testShouldPrintLogMessage(){
		String message = "This is a message";
		PrintStream printStream = mock(PrintStream.class);
		TaskListener listener = mock(TaskListener.class);
		when(listener.getLogger()).thenReturn(printStream);
		PretestUtils.logMessage(listener, message);
		verify(printStream).println("[PREINT] " + message);
	}
	
}
