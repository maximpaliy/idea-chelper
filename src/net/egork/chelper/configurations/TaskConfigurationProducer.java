package net.egork.chelper.configurations;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import net.egork.chelper.task.Task;
import net.egork.chelper.util.FileUtils;
import net.egork.chelper.util.ProjectUtils;
import net.egork.chelper.util.TaskUtils;

import java.util.Map;

/**
 * Created by Scruel on 2017/9/6.
 * Github : https://github.com/scruel
 */
public class TaskConfigurationProducer extends RunConfigurationProducer<TaskConfiguration> {
    protected TaskConfigurationProducer(TaskConfigurationType configurationType) {
        super(configurationType);
    }

    @Override
    protected boolean setupConfigurationFromContext(TaskConfiguration configuration, ConfigurationContext context, Ref sourceElement) {
        final Location location = context.getLocation();
        if (location == null) return false;
        final PsiFile script = location.getPsiElement().getContainingFile();
        if (!isAvailable(script)) return false;
        final VirtualFile file = script.getVirtualFile();
        if (file == null) return false;

        Project project = context.getProject();
        Map<String, Object> map = TaskUtils.getTaskMapWitFile(project, file);
        if (map == null) return false;
        VirtualFile dataFile;
        if (null == (dataFile = (VirtualFile) map.get(TaskUtils.TASK_DATA_KEY)))
            return false;

        Task task = FileUtils.readTask(dataFile);
        if (task == null) return false;
        task = (Task) TaskUtils.fixedTaskByTaskFile(project, task, dataFile);
        if (task == null) return false;
        configuration.setName(task.name);
        configuration.setConfiguration(task);
        ProjectUtils.createConfiguration(project, task, true);
        return true;
    }

    private static boolean isAvailable(final PsiFile script) {
        if (script == null) return false;
        if (!FileUtils.isJavaDirectory(script.getParent()))
            return false;
        final Module module = ModuleUtilCore.findModuleForPsiElement(script);
        return module != null;
    }

    @Override
    public boolean isConfigurationFromContext(TaskConfiguration configuration, ConfigurationContext context) {
        if (!ProjectUtils.isValidConfigurationOrDeleteIfNot(configuration)) return false;
        final Location location = context.getLocation();
        if (location == null) return false;
        final PsiFile script = location.getPsiElement().getContainingFile();
        if (!isAvailable(script)) return false;
        VirtualFile file = script.getVirtualFile();
        if (file == null) return false;
        if (file instanceof LightVirtualFile) return false;

        Project project = context.getProject();
        Map<String, Object> map = TaskUtils.getTaskMapWitFile(project, file);
        if (map == null) return false;
        if (null == (file = (VirtualFile) map.get(TaskUtils.TASK_SOURCE_KEY)))
            return false;

        final String taskLocation = configuration.getConfiguration().location;
        final String classSimpleName = ProjectUtils.getSimpleName(configuration.getConfiguration().taskClass);
        final String path = FileUtils.getRelativePath(context.getProject().getBaseDir(), file);
        return path.equals(taskLocation + "/" + classSimpleName + ".java");
    }
}
