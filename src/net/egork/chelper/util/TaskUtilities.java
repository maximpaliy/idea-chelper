package net.egork.chelper.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import net.egork.chelper.actions.ArchiveAction;
import net.egork.chelper.codegeneration.SolutionGenerator;
import net.egork.chelper.task.Task;

/**
 * @author Egor Kulikov (egorku@yandex-team.ru)
 */
public class TaskUtilities {
    public static void createSourceFile(Task task, Project project) {
        SolutionGenerator.createSourceFile(task, project);
    }

    public static VirtualFile getFile(String location, String name, Project project) {
        return FileUtilities.getFile(project, location + "/" + name + ".java");
    }

    public static String getTaskFileName(String location, String name) {
        if (location != null && name != null)
            return location + "/" + ArchiveAction.canonize(name) + ".task";
        return null;
    }

    public static String getTopCoderTaskFileName(String location, String name) {
        if (location != null && name != null)
            return location + "/" + name + ".tctask";
        return null;
    }

    /**
     * 获取同级目录中同名的 .task 文件
     *
     * @param file
     * @return
     */
    public static VirtualFile getTaskFile(VirtualFile file) {
        if (file == null) {
            return null;
        }
        String extension = file.getExtension();
        if (!"java".equals(extension) && !"task".equals(extension)) {
            return null;
        }
        String taskName = file.getNameWithoutExtension();
        VirtualFile parentFile = file.getParent();

        VirtualFile taskFile;
        if ("task".equals(extension)) {
            if (parentFile.findChild(taskName + ".java") == null) return null;
        }
        taskFile = parentFile.findChild(taskName + ".task");
        return taskFile;
    }
}
