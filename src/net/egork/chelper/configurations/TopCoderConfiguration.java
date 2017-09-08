package net.egork.chelper.configurations;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import net.egork.chelper.actions.TopCoderAction;
import net.egork.chelper.codegeneration.SolutionGenerator;
import net.egork.chelper.task.TopCoderTask;
import net.egork.chelper.ui.TopCoderConfigurationEditor;
import net.egork.chelper.util.FileUtils;
import net.egork.chelper.util.InputReader;
import net.egork.chelper.util.ProjectUtils;
import net.egork.chelper.util.TaskUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.InputMismatchException;

/**
 * @author Egor Kulikov (kulikov@devexperts.com)
 */
public class TopCoderConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule> {
    private TopCoderTask configuration;

    public TopCoderConfiguration(String name, Project project, TopCoderTask configuration, ConfigurationFactory factory) {
        super(name, new JavaRunConfigurationModule(project, false), factory);
        this.configuration = configuration;
        saveConfiguration(configuration);
    }

    @Override
    public Collection<Module> getValidModules() {
        return JavaRunConfigurationModule.getModulesForClass(getProject(), configuration.fqn);
    }

    @Override
    public Collection<Module> getAllModules() {
        return getValidModules();
    }

    @Override
    protected ModuleBasedConfiguration createInstance() {
        return new TopCoderConfiguration(getName(), getProject(), configuration, getFactory());
    }

    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new TopCoderConfigurationEditor(this);
    }

    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env)
        throws ExecutionException {
        SolutionGenerator.createSourceFile(configuration, getProject());
        JavaCommandLineState state = new JavaCommandLineState(env) {
            @Override
            protected JavaParameters createJavaParameters() throws ExecutionException {
                JavaParameters parameters = new JavaParameters();
                PsiDirectory directory = FileUtils.getPsiDirectory(getProject(),
                    ProjectUtils.getData(getProject()).defaultDirectory);
                Module module = ProjectRootManager.getInstance(getProject()).getFileIndex().getModuleForFile(
                    directory.getVirtualFile());
                parameters.configureByModule(module, JavaParameters.JDK_AND_CLASSES);
                parameters.setMainClass("net.egork.chelper.tester.NewTopCoderTester");
                parameters.getVMParametersList().add("-Xmx" + configuration.memoryLimit);
                if (configuration.failOnOverflow) {
                    String path = TopCoderAction.getJarPathForClass(com.github.cojac.CojacAgent.class);
                    parameters.getVMParametersList().add("-javaagent:" + path + "=-Cints -Clongs -Ccasts -Cmath");
                }
                parameters.setWorkingDirectory(getProject().getBaseDir().getPath());
                String taskFileName = TaskUtils.getTopCoderTaskFileName(ProjectUtils.getData(getProject()).defaultDirectory, configuration.name);
                parameters.getProgramParametersList().add(taskFileName);
                if (ProjectUtils.getData(getProject()).smartTesting) {
                    VirtualFile report = FileUtils.getFile(getProject(), "CHelperReport.txt");
                    if (report != null) {
                        try {
                            InputReader reader = new InputReader(report.getInputStream());
                            if (reader.readString().equals(taskFileName)) {
                                int failedTestCount = reader.readInt();
                                if (failedTestCount != 0) {
                                    int firstFailed = reader.readInt();
                                    parameters.getProgramParametersList().add(Integer.toString(firstFailed));
                                }
                            }
                        } catch (IOException ignored) {
                        } catch (InputMismatchException ignored) {
                        }
                    }
                }
                return parameters;
            }
        };
        state.setConsoleBuilder(new TextConsoleBuilderImpl(getProject()));
        return state;
    }

    public TopCoderTask getConfiguration() {
        return configuration;
    }

    public void setConfiguration(TopCoderTask configuration) {
        this.configuration = configuration;
        saveConfiguration(configuration);
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
        super.readExternal(element);
        String fileName = element.getChildText("taskConf");
        if (fileName != null && fileName.trim().length() != 0) {
            try {
                configuration = FileUtils.readTopCoderTask(fileName, getProject());
            } catch (NullPointerException ignored) {
            }
        }
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
        super.writeExternal(element);
        Element configurationElement = new Element("taskConf");
        element.addContent(configurationElement);
        String configurationFile = TaskUtils.getTopCoderTaskFileName(ProjectUtils.getData(getProject()).defaultDirectory, configuration.name);
        if (configurationFile != null && configuration.tests != null)
            configurationElement.setText(configurationFile);
    }

    private void saveConfiguration(TopCoderTask configuration) {
        if (ProjectUtils.getData(getProject()) == null)
            return;
        String location = ProjectUtils.getData(getProject()).defaultDirectory;
        if (configuration != null && location != null && configuration.name != null && configuration.name.length() != 0 && configuration.tests != null)
            FileUtils.saveConfiguration(location, configuration.name + ".tctask", configuration, getProject());
    }


}
