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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.GlobalSearchScope;
import net.egork.chelper.actions.ArchiveAction;
import net.egork.chelper.actions.TopCoderAction;
import net.egork.chelper.task.Task;
import net.egork.chelper.ui.TaskConfigurationEditor;
import net.egork.chelper.util.FileUtils;
import net.egork.chelper.util.InputReader;
import net.egork.chelper.util.ProjectUtils;
import net.egork.chelper.util.TaskUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.InputMismatchException;

/**
 * @author Egor Kulikov (kulikov@devexperts.com)
 */
public class TaskConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule> {
    private Task configuration;

    public TaskConfiguration(String name, Project project, Task configuration, ConfigurationFactory factory) {
        super(name, new JavaRunConfigurationModule(project, false), factory);
        this.configuration = configuration;
        saveConfiguration(configuration);
    }

    @Override
    public Collection<Module> getValidModules() {
        return JavaRunConfigurationModule.getModulesForClass(getProject(), configuration.taskClass);
    }

    @Override
    protected ModuleBasedConfiguration createInstance() {
        return new TaskConfiguration(getName(), getProject(), configuration, getFactory());
    }

    @Override
    public Collection<Module> getAllModules() {
        return getValidModules();
    }

    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new TaskConfigurationEditor(this);
    }

    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env)
        throws ExecutionException {
        TaskUtils.createSourceFile(configuration, getProject());
        JavaCommandLineState state = new JavaCommandLineState(env) {
            @Override
            protected JavaParameters createJavaParameters() throws ExecutionException {
                JavaParameters parameters = new JavaParameters();
                PsiDirectory directory = FileUtils.getPsiDirectory(getProject(), configuration.location);
                Module module = ProjectRootManager.getInstance(getProject()).getFileIndex().getModuleForFile(
                    directory.getVirtualFile());
                parameters.configureByModule(module, JavaParameters.JDK_AND_CLASSES);
                parameters.setWorkingDirectory(getProject().getBaseDir().getPath());
                parameters.setMainClass("net.egork.chelper.tester.NewTester");
                String[] vmParameters = configuration.vmArgs.split(" ");
                for (String parameter : vmParameters)
                    parameters.getVMParametersList().add(parameter);
                if (configuration.failOnOverflow) {
                    String path = TopCoderAction.getJarPathForClass(com.github.cojac.CojacAgent.class);
                    parameters.getVMParametersList().add("-javaagent:" + path + "=-Cints -Clongs -Ccasts -Cmath");
                }
                String taskFileName = TaskUtils.getTaskFileName(configuration.location, configuration.name);
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

    public Task getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Task configuration) {
        this.configuration = configuration;
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(getProject());
        if (psiDocumentManager.hasUncommitedDocuments()) {
            psiDocumentManager.commitAllDocuments();
        }
        saveConfiguration(configuration);
    }

    private void saveConfiguration(Task configuration) {
        if (configuration != null && configuration.location != null && configuration.name != null && configuration.name.length() != 0)
            FileUtils.saveConfiguration(configuration.location, ArchiveAction.canonize(configuration.name) + ".task", configuration, getProject());
    }

    // Used to support previous versions of JDK6.0
    @Nullable
    public GlobalSearchScope getSearchScope() {
        Module[] modules = getModules();
        if (modules.length == 0) {
            return null;
        } else {
            GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(modules[0], true);
            for (int idx = 1; idx < modules.length; idx++) {
                Module module = modules[idx];
                scope = scope.uniteWith(GlobalSearchScope.moduleRuntimeScope(module, true));
            }
            return scope;
        }
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
        super.readExternal(element);
        String fileName = element.getChildText("taskConf");
        if (fileName != null && fileName.trim().length() != 0) {
            try {
                configuration = FileUtils.readTask(fileName, getProject());
            } catch (NullPointerException ignored) {
            }
        }
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
        super.writeExternal(element);
        Element configurationElement = new Element("taskConf");
        element.addContent(configurationElement);
        String configurationFile = TaskUtils.getTaskFileName(configuration.location, configuration.name);
        if (configurationFile != null)
            configurationElement.setText(configurationFile);
    }
}
