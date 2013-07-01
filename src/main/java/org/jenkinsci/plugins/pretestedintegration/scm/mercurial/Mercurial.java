package org.jenkinsci.plugins.pretestedintegration.scm.mercurial;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.mercurial.MercurialInstallation;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;
import hudson.util.ArgumentListBuilder;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.pretestedintegration.AbstractCommit;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMInterface;
import org.jenkinsci.plugins.pretestedintegration.Commit;
import org.jenkinsci.plugins.pretestedintegration.SCMInterface;
import org.jenkinsci.plugins.pretestedintegration.SCMInterfaceDescriptor;
import org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMCommit;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class Mercurial extends AbstractSCMInterface implements SCMInterface<String> {

	private String latest;
	private String pattern;
	
	@DataBoundConstructor
	public Mercurial(String latest, String pattern){
		this.latest = latest;
		this.pattern = pattern;
	}
	
	public String getLatest(){
		return this.latest;
	}
	
	public String getPattern() {
		return this.pattern;
	}
	
	/**
	 * The directory in which to execute hg commands
	 */
	private FilePath workingDirectory = null;
	final static String LOG_PREFIX = "[PREINT-HG] ";
	private String currentBuildFile = null; 

	public void setWorkingDirectory(FilePath workingDirectory){
		this.workingDirectory = workingDirectory;
	}
	
	public FilePath getWorkingDirectory(){
		return this.workingDirectory;
	}
	
	public String getCurrentBuildFilePath(){
		return this.currentBuildFile;
	}
	
	public void setCurrentBuildFilePath(String currentBuildFilePath){

		this.currentBuildFile = currentBuildFilePath;
	}
	
	
	public void writeToBuildFile(String rev)
	{

	}

	
	/**
	 * Locate the correct mercurial binary to use for commands
	 * @param build
	 * @param listener
	 * @param allowDebug
	 * @return An ArgumentListBuilder containing the correct hg binary
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private static ArgumentListBuilder findHgExe(AbstractBuild build, TaskListener listener, boolean allowDebug) throws IOException,
			InterruptedException {
		//Cast the current SCM to get the methods we want. 
		//Throw exception on failure
		try{
			SCM scm = build.getProject().getScm();
			MercurialSCM hg = (MercurialSCM) scm;
			
			Node node = build.getBuiltOn();
			// Run through Mercurial installations and check if they correspond to
			// the one used in this job
			for (MercurialInstallation inst
					: MercurialInstallation.allInstallations()) {
				if (inst.getName().equals(hg.getInstallation())) {
					// TODO: what about forEnvironment?
					String home = inst.getExecutable().replace("INSTALLATION",
							inst.forNode(node, listener).getHome());
					ArgumentListBuilder b = new ArgumentListBuilder(home);
					
					if (allowDebug && inst.getDebug()) {
						b.add("--debug");
					}
					return b;
				}
			}
			//Just use the default hg
			return new ArgumentListBuilder(hg.getDescriptor().getHgExe());
		} catch(ClassCastException e) {
			throw new InterruptedException("Configured scm is not mercurial");
		}
	}
	
	/**
	 * Invoke a command with mercurial
	 * @param build
	 * @param launcher
	 * @param listener
	 * @param cmds
	 * @return The exitcode of command
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public int hg(AbstractBuild build, Launcher launcher, TaskListener listener, String... cmds) throws IOException, InterruptedException{
		ArgumentListBuilder hg = findHgExe(build, listener, false);
		hg.add(cmds);
		//if the working directory has not been manually set use the build workspace
		if(workingDirectory == null){
			setWorkingDirectory(build.getWorkspace());
			setCurrentBuildFilePath(getWorkingDirectory().readToString()+"/.hg/curentBuildFile");
		}
		int exitCode = launcher.launch().cmds(hg).pwd(workingDirectory).join();
		return exitCode;
	}

	/**
	 * Invoke a command with mercurial
	 * @param build
	 * @param launcher
	 * @param listener
	 * @param cmds
	 * @return The exitcode of command
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public int hg(AbstractBuild build, Launcher launcher, TaskListener listener,OutputStream out, String... cmds) throws IOException, InterruptedException{
		ArgumentListBuilder hg = findHgExe(build, listener, false);
		hg.add(cmds);
		//if the working directory has not been manually set use the build workspace
		if(workingDirectory == null){
			setWorkingDirectory(build.getWorkspace());

			setCurrentBuildFilePath(getWorkingDirectory().readToString()+"/.hg/currentBuildFile");

		}
		int exitCode = launcher.launch().cmds(hg).stdout(out).pwd(workingDirectory).join();
		return exitCode;
	}
	
	/**
	 * Given a date, search through the revision history and find the first changeset committed on or after the specified date.
	 * @param build
	 * @param launcher
	 * @param listener
	 * @param date
	 * @return A commit representation of the next commit made at the specified date, or null
	 * @throws IOException
	 * @throws InterruptedException
	 */

	public PretestedIntegrationSCMCommit commitFromDate(AbstractBuild build, Launcher launcher, TaskListener listener, Date date) throws IOException, InterruptedException{
		PretestedIntegrationSCMCommit commit = null;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		hg(build, launcher, listener, out, "log","-r","0:tip","-l1","--date",">" + dateFormat.format(date), "--template","{node}");
		String revision = out.toString();
		if(revision.length() > 0)
			commit = new PretestedIntegrationSCMCommit(revision);
		return commit;
	}

	/* (non-Javadoc)
	 * @see org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface#hasNextCommit(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
	 */
	public void prepareWorkspace(AbstractBuild build, Launcher launcher,
			BuildListener listener, AbstractCommit<String> commit)
			throws AbortException, IOException, IllegalArgumentException {
		try {
			//Make sure that we are on the integration branch
			//TODO: Make it dynamic and not just "default"

			hg(build, launcher, listener, "update","-C","default");
			
			//Merge the commit into the integration branch
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			int exitCode = hg(build, launcher, listener, out, "merge",(String) commit.getId(),"--tool","internal:merge");
			if(exitCode != 0)
				throw new AbortException("Merging branches caused conflict");
		} catch(InterruptedException e){
			throw new AbortException("Merge into integration branch exited unexpectedly");
		}	
	}

	/* (non-Javadoc)
	 * @see org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface#hasNextCommit(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
	 */
	public boolean hasNextCommit(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException,
			IllegalArgumentException {
		String revision = "0";
		try {
			
			if(workingDirectory == null){
				setWorkingDirectory(build.getWorkspace());
			}
			int pullExit = hg(build, launcher, listener, "pull");
			File f = new File(build.getWorkspace().readToString()+"/.hg/currentBuildFile");
			System.out.println("file: " + f.getAbsolutePath());
			if(f.exists()) 
			{
				BufferedReader br =  new BufferedReader(new FileReader(f));
				revision = br.readLine();
				br.close();
			}
			
			ByteArrayOutputStream logStdout = new ByteArrayOutputStream();
			int exitCode = hg(build, launcher, listener,logStdout,"log", "-r", "not branch(default) and "+revision+":tip","--template","{node}");

			String outString = logStdout.toString().trim();
			
			if(outString.length() > 40 || revision.equals("0")) {
				return true;
			}
		} catch(InterruptedException e) {
			throw new IOException(e.getMessage());
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface#popCommit(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
	 */
	public AbstractCommit<String> popCommit(AbstractBuild build,
			Launcher launcher, BuildListener listener) throws IOException,
			IllegalArgumentException {
			
					String revision = "0";
					try {
						if(workingDirectory == null){
							setWorkingDirectory(build.getWorkspace());
						}
						
						File file = new File(getWorkingDirectory()+"/.hg/currentBuildFile");
						if(file.exists()) 
						{
							BufferedReader br =  new BufferedReader(new FileReader(file));
							revision = br.readLine();
							br.close();
						}else
						{
							PrintWriter writer = new PrintWriter(file, "UTF-8");
							writer.println("0");
							writer.close();

							//File file = new File(getCurrentBuildFilePath());
        						//BufferedWriter br = new BufferedWriter(new FileWriter(file));
							//br.write("0");
						}
					
					ByteArrayOutputStream logStdout = new ByteArrayOutputStream();
					int exitCode = hg(build, launcher, listener,logStdout,"log", "-r", "not branch(default) and "+revision+":tip","--template","{node}\\n");
					 
					String [] commitArray = logStdout.toString().split("\\n");
					if(commitArray.length > 0){
						
						Commit<String> commit = new Commit<String>(commitArray[0]);

						PrintWriter writer = new PrintWriter(file, "UTF-8");
						System.out.println(commit.getId());
						writer.println(commit.getId());
						writer.close();

						return commit;
					}
				}
				catch(IOException e)
				{
					throw e;
				}
				catch(InterruptedException e)
				{
					throw new IOException(e.getMessage());
				}

		return null;
	}

	/* (non-Javadoc)
	 * @see org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMInterface#handlePostBuild(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener, hudson.model.Result)
	 */
	public void handlePostBuild(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException,
			IllegalArgumentException {
		
		Result result = build.getResult();
		//TODO: make the success criteria configurable
		if(result != null && result.isBetterOrEqualTo(Result.SUCCESS)){ //Commit the changes
			//TODO: get this string dynamic
			try {
				hg(build, launcher, listener,"commit","-m", "Successfully integrated development branch");
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				throw new AbortException("Commiting changes on integration branch exited unexpectedly");
			}
		} else { //Rollback changes
			try {
				hg(build, launcher, listener, "update","-C");
			} catch (InterruptedException e) {
				throw new AbortException("Unable to revert changes in integration branch");
			}
		}
	}
	
	@Extension
	public static final class DescriptorImpl extends SCMInterfaceDescriptor<Mercurial> {
		
		public String getDisplayName(){
			return "Mercurial";
		}
		
		@Override
		public Mercurial newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			Mercurial i = (Mercurial) super.newInstance(req, formData);
			
			
			String latest = formData.getJSONObject("scmInterface").getString("latest");
			String pattern = formData.getJSONObject("scmInterface").getString("pattern");
			i.latest = latest;
			i.pattern = pattern;
			
			save();
			return i;
		}
	}
}